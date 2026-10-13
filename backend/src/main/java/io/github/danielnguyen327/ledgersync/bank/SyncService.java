package io.github.danielnguyen327.ledgersync.bank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Downloads a bank's new, changed and removed transactions from Plaid and applies them. Plaid's cursor
 * marks how far the last sync got, so each sync only receives what changed since.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    /** Plaid's answer when the bank's data changed while we were paging through it. */
    private static final String DATA_CHANGED = "TRANSACTIONS_SYNC_MUTATION_DURING_PAGINATION";
    private static final int MAX_ATTEMPTS = 3;

    private final PlaidGateway plaid;
    private final TokenCipher cipher;
    private final PlaidItemRepository items;
    private final FinancialAccountRepository accounts;
    private final BankTransactionRepository transactions;
    private final TransactionTemplate transaction;

    SyncService(PlaidGateway plaid, TokenCipher cipher, PlaidItemRepository items, FinancialAccountRepository accounts,
            BankTransactionRepository transactions, TransactionTemplate transaction) {
        this.plaid = plaid;
        this.cipher = cipher;
        this.items = items;
        this.accounts = accounts;
        this.transactions = transactions;
        this.transaction = transaction;
    }

    /** What one sync changed. ready is false while Plaid is still gathering a new bank's transactions. */
    public record SyncResult(boolean ready, int added, int updated, int removed) {

        static final SyncResult NOT_READY = new SyncResult(false, 0, 0, 0);
        static final SyncResult NOTHING_NEW = new SyncResult(true, 0, 0, 0);
    }

    /** Syncs one of the user's banks. */
    public SyncResult sync(UUID userId, UUID bankId) {
        PlaidItem item = items.findByIdAndUserId(bankId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That bank isn't connected."));
        return sync(item);
    }

    /**
     * Downloads every page of changes first, then saves them and the new cursor together in one database
     * transaction. If anything fails, nothing is saved and the next sync starts again from the old cursor.
     * Saving a transaction twice updates the same row, so starting again never creates duplicates.
     */
    private SyncResult sync(PlaidItem item) {
        String accessToken = cipher.decrypt(item.getAccessTokenEncrypted());
        String startCursor = item.getSyncCursor();
        Changes changes = download(accessToken, startCursor);
        if (changes.cursor.isEmpty()) {
            return SyncResult.NOT_READY; // nothing to save yet, so the next sync starts from the beginning
        }
        return transaction.execute(status -> save(item.getId(), startCursor, changes));
    }

    /** Asks Plaid for pages until there are no more. If Plaid's data changes partway, starts over. */
    private Changes download(String accessToken, @Nullable String startCursor) {
        for (int attempt = 1; ; attempt++) {
            try {
                Changes changes = new Changes();
                String cursor = startCursor;
                PlaidGateway.TransactionsPage page;
                do {
                    page = plaid.syncTransactions(accessToken, cursor);
                    changes.add(page);
                    cursor = page.nextCursor();
                } while (page.hasMore());
                changes.cursor = page.nextCursor();
                return changes;
            } catch (PlaidException e) {
                if (!e.code().equals(DATA_CHANGED) || attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                log.info("Plaid's data changed during a sync, so it's starting over from the saved cursor");
            }
        }
    }

    private SyncResult save(UUID itemId, @Nullable String startCursor, Changes changes) {
        PlaidItem item = items.findLockedById(itemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That bank isn't connected."));
        if (!Objects.equals(item.getSyncCursor(), startCursor)) {
            return SyncResult.NOTHING_NEW; // another sync of this bank finished first and already saved these changes
        }
        UUID userId = item.getUserId();
        Map<String, UUID> accountIds = accounts.findByItemId(itemId).stream()
            .collect(Collectors.toMap(FinancialAccount::getPlaidAccountId, FinancialAccount::getId));
        Map<String, BankTransaction> existing = changes.upserts.isEmpty() ? Map.of()
            : transactions.findByUserIdAndPlaidTransactionIdIn(userId, changes.upserts.keySet()).stream()
                .collect(Collectors.toMap(BankTransaction::getPlaidTransactionId, Function.identity()));

        int added = 0;
        int updated = 0;
        int skipped = 0;
        List<BankTransaction> rows = new ArrayList<>();
        for (PlaidGateway.PlaidTransaction from : changes.upserts.values()) {
            UUID accountId = accountIds.get(from.accountId());
            if (accountId == null) {
                skipped++; // an account LedgerSync doesn't have, so there's nowhere to put it
                continue;
            }
            BankTransaction row = existing.get(from.transactionId());
            if (row == null) {
                row = new BankTransaction(userId, from.transactionId());
                added++;
            } else {
                updated++;
            }
            row.update(accountId, from);
            rows.add(row);
        }
        transactions.saveAll(rows);
        int removed = changes.removed.isEmpty() ? 0
            : transactions.deleteByPlaidTransactionIds(userId, changes.removed);
        if (skipped > 0) {
            log.warn("Skipped {} transactions from accounts LedgerSync doesn't have", skipped);
        }
        item.markSynced(changes.cursor, Instant.now()); // saved when the transaction commits
        return new SyncResult(true, added, updated, removed);
    }

    /** Every page's changes combined, in order, so a later page wins over an earlier one. */
    private static final class Changes {

        final Map<String, PlaidGateway.PlaidTransaction> upserts = new LinkedHashMap<>();
        final Set<String> removed = new LinkedHashSet<>();
        String cursor = "";

        void add(PlaidGateway.TransactionsPage page) {
            Stream.concat(page.added().stream(), page.modified().stream()).forEach(t -> {
                upserts.put(t.transactionId(), t);
                removed.remove(t.transactionId());
            });
            page.removed().forEach(id -> {
                upserts.remove(id);
                removed.add(id);
            });
        }
    }
}
