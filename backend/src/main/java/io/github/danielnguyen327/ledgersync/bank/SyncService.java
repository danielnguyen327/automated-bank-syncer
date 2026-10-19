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
 * Downloads a bank's new, changed and removed transactions and its latest balances from Plaid, and
 * applies them. Plaid's cursor marks how far the last sync got, so each sync only receives what changed since.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    /** Plaid's answer when the bank's data changed while we were paging through it. */
    private static final String DATA_CHANGED = "TRANSACTIONS_SYNC_MUTATION_DURING_PAGINATION";
    /** Plaid's answer when the bank ended the connection and the user has to sign in again. */
    private static final String LOGIN_REQUIRED = "ITEM_LOGIN_REQUIRED";
    private static final int MAX_ATTEMPTS = 3;

    private final PlaidGateway plaid;
    private final TokenCipher cipher;
    private final PlaidItemRepository items;
    private final FinancialAccountRepository accounts;
    private final BankTransactionRepository transactions;
    private final SyncRunRepository runs;
    private final BalanceSnapshots snapshots;
    private final TransactionTemplate transaction;

    SyncService(PlaidGateway plaid, TokenCipher cipher, PlaidItemRepository items, FinancialAccountRepository accounts,
            BankTransactionRepository transactions, SyncRunRepository runs, BalanceSnapshots snapshots,
            TransactionTemplate transaction) {
        this.plaid = plaid;
        this.cipher = cipher;
        this.items = items;
        this.accounts = accounts;
        this.transactions = transactions;
        this.runs = runs;
        this.snapshots = snapshots;
        this.transaction = transaction;
    }

    /** What one sync changed. ready is false while Plaid is still gathering a new bank's transactions. */
    public record SyncResult(boolean ready, int added, int updated, int removed) {

        static final SyncResult NOT_READY = new SyncResult(false, 0, 0, 0);
        static final SyncResult NOTHING_NEW = new SyncResult(true, 0, 0, 0);
    }

    /** One bank's part of "sync all": what changed, or why it failed. */
    public record BankSync(UUID bankId, String bankName, @Nullable SyncResult result, @Nullable String error) {
    }

    /** Syncs one of the user's banks. */
    public SyncResult sync(UUID userId, UUID bankId) {
        PlaidItem item = items.findByIdAndUserId(bankId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That bank isn't connected."));
        return run(item, "manual");
    }

    /** Syncs every bank the user has. One bank failing doesn't stop the others. */
    public List<BankSync> syncAll(UUID userId) {
        return items.findByUserIdOrderByCreatedAt(userId).stream().map(item -> {
            try {
                return new BankSync(item.getId(), item.getInstitutionName(), run(item, "manual"), null);
            } catch (ResponseStatusException e) {
                return new BankSync(item.getId(), item.getInstitutionName(), null, e.getReason());
            } catch (PlaidException e) {
                return new BankSync(item.getId(), item.getInstitutionName(), null, e.userMessage());
            } catch (RuntimeException e) {
                log.warn("Sync of bank {} failed", item.getId(), e);
                return new BankSync(item.getId(), item.getInstitutionName(), null,
                    "Something went wrong. Try again in a minute.");
            }
        }).toList();
    }

    /**
     * Syncs every bank of every user, for the 3-hourly schedule. Banks waiting for the user to sign in
     * again are skipped, and one bank failing doesn't stop the others.
     */
    public void syncEveryBank() {
        List<PlaidItem> active = items.findByStatus("active");
        int failed = 0;
        for (PlaidItem item : active) {
            try {
                run(item, "schedule");
            } catch (RuntimeException e) {
                failed++;
                log.warn("Scheduled sync of bank {} failed: {}", item.getId(), e.getMessage());
            }
        }
        log.info("Scheduled sync finished: {} banks, {} failed", active.size(), failed);
    }

    /** Runs one sync and records it in sync_run, whether it worked or not. */
    private SyncResult run(PlaidItem item, String triggeredBy) {
        SyncRun run = runs.save(new SyncRun(item.getUserId(), item.getId(), triggeredBy));
        try {
            SyncResult result = sync(item);
            if (!result.ready()) {
                runs.delete(run); // Plaid wasn't ready, so there was nothing to sync yet
                return result;
            }
            run.succeeded(result);
            runs.save(run);
            return result;
        } catch (PlaidException e) {
            recordFailure(run, e.code(), e.getMessage());
            if (e.code().equals(LOGIN_REQUIRED)) {
                transaction.executeWithoutResult(status ->
                    items.findById(item.getId()).ifPresent(PlaidItem::markLoginRequired));
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    item.getInstitutionName() + " needs you to sign in again.");
            }
            throw e;
        } catch (RuntimeException e) {
            recordFailure(run, "INTERNAL_ERROR", e.getMessage());
            throw e;
        }
    }

    private void recordFailure(SyncRun run, String code, @Nullable String message) {
        run.failed(code, message);
        try {
            runs.save(run);
        } catch (RuntimeException e) {
            log.warn("Couldn't record a failed sync: {}", e.getMessage()); // the bank was disconnected meanwhile
        }
    }

    /**
     * Downloads every page of changes and the latest balances first, then saves them and the new cursor
     * together in one database transaction. If anything fails, nothing is saved and the next sync starts
     * again from the old cursor. Saving a transaction twice updates the same row, so starting again never
     * creates duplicates.
     */
    private SyncResult sync(PlaidItem item) {
        String accessToken = cipher.decrypt(item.getAccessTokenEncrypted());
        String startCursor = item.getSyncCursor();
        Changes changes = download(accessToken, startCursor);
        if (changes.cursor.isEmpty()) {
            return SyncResult.NOT_READY; // nothing to save yet, so the next sync starts from the beginning
        }
        List<PlaidGateway.PlaidAccount> balances = plaid.getItemDetails(accessToken).accounts();
        return transaction.execute(status -> save(item.getId(), startCursor, changes, balances));
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

    private SyncResult save(UUID itemId, @Nullable String startCursor, Changes changes,
            List<PlaidGateway.PlaidAccount> balances) {
        PlaidItem item = items.findLockedById(itemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That bank isn't connected."));
        if (!Objects.equals(item.getSyncCursor(), startCursor)) {
            return SyncResult.NOTHING_NEW; // another sync of this bank finished first and already saved these changes
        }
        UUID userId = item.getUserId();
        List<FinancialAccount> bankAccounts = accounts.findByItemId(itemId);
        updateBalances(bankAccounts, balances);
        bankAccounts.forEach(account -> snapshots.saveToday(userId, account));
        Map<String, UUID> accountIds = bankAccounts.stream()
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

    /** Copies Plaid's latest balances onto the accounts LedgerSync has. Accounts Plaid didn't mention keep theirs. */
    private static void updateBalances(List<FinancialAccount> bankAccounts, List<PlaidGateway.PlaidAccount> balances) {
        Map<String, PlaidGateway.PlaidAccount> latest = balances.stream()
            .collect(Collectors.toMap(PlaidGateway.PlaidAccount::accountId, Function.identity()));
        for (FinancialAccount account : bankAccounts) {
            PlaidGateway.PlaidAccount from = latest.get(account.getPlaidAccountId());
            if (from != null) {
                account.updateBalances(from); // saved when the transaction commits
            }
        }
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
