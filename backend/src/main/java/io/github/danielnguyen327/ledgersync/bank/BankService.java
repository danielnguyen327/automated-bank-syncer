package io.github.danielnguyen327.ledgersync.bank;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BankService {

    private static final Logger log = LoggerFactory.getLogger(BankService.class);

    /** Plaid's answers when it has already forgotten a bank. Removing it again is then not an error. */
    private static final Set<String> ALREADY_REMOVED = Set.of("ITEM_NOT_FOUND", "INVALID_ACCESS_TOKEN");

    private final PlaidGateway plaid;
    private final TokenCipher cipher;
    private final PlaidItemRepository items;
    private final FinancialAccountRepository accounts;
    private final BankTransactionRepository transactions;
    private final SyncRunRepository runs;
    private final TransactionTemplate transaction;

    BankService(PlaidGateway plaid, TokenCipher cipher, PlaidItemRepository items, FinancialAccountRepository accounts,
            BankTransactionRepository transactions, SyncRunRepository runs, TransactionTemplate transaction) {
        this.plaid = plaid;
        this.cipher = cipher;
        this.items = items;
        this.accounts = accounts;
        this.transactions = transactions;
        this.runs = runs;
        this.transaction = transaction;
    }

    public String createLinkToken(UUID userId) {
        return plaid.createLinkToken(userId);
    }

    /**
     * Saves a bank the user just connected in Plaid Link. Plaid is called first, outside the database
     * transaction, so a slow Plaid never holds a database connection.
     */
    public BankResponse connect(UUID userId, String publicToken) {
        PlaidGateway.ExchangedItem exchanged = plaid.exchangePublicToken(publicToken);
        try {
            PlaidGateway.ItemDetails details = plaid.getItemDetails(exchanged.accessToken());
            String encrypted = cipher.encrypt(exchanged.accessToken());
            return transaction.execute(status -> {
                PlaidItem item = items.save(new PlaidItem(userId, exchanged.itemId(), details.institutionId(),
                    details.institutionName(), encrypted));
                List<FinancialAccount> saved = accounts.saveAll(details.accounts().stream()
                    .map(account -> new FinancialAccount(userId, item.getId(), account))
                    .toList());
                return BankResponse.from(item, saved, 0, List.of()); // nothing synced yet
            });
        } catch (RuntimeException e) {
            removeQuietly(exchanged.accessToken()); // don't leave a connection at Plaid that isn't saved here
            throw e;
        }
    }

    /** The user's banks, oldest first, each with its accounts, transaction count and last 3 syncs. */
    public List<BankResponse> banks(UUID userId) {
        Map<UUID, List<FinancialAccount>> accountsByBank = accounts.findByUserIdOrderByName(userId).stream()
            .collect(Collectors.groupingBy(FinancialAccount::getItemId));
        Map<UUID, Long> transactionsByBank = transactions.countByBank(userId).stream()
            .collect(Collectors.toMap(BankTransactionRepository.BankCount::getBankId,
                BankTransactionRepository.BankCount::getTotal));
        Map<UUID, List<SyncRun>> syncsByBank = runs.findTop50ByUserIdOrderByStartedAtDesc(userId).stream()
            .collect(Collectors.groupingBy(SyncRun::getItemId));
        return items.findByUserIdOrderByCreatedAt(userId).stream()
            .map(item -> BankResponse.from(item, accountsByBank.getOrDefault(item.getId(), List.of()),
                transactionsByBank.getOrDefault(item.getId(), 0L),
                syncsByBank.getOrDefault(item.getId(), List.of()).stream().limit(3).toList()))
            .toList();
    }

    /** Disconnects one bank: Plaid forgets it, then its row is deleted, and its accounts with it. */
    public void disconnect(UUID userId, UUID bankId) {
        PlaidItem item = items.findByIdAndUserId(bankId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That bank isn't connected."));
        forget(item);
    }

    /** Disconnects every bank the user has, before their account is deleted. */
    public void disconnectAll(UUID userId) {
        items.findByUserIdOrderByCreatedAt(userId).forEach(this::forget);
    }

    private void forget(PlaidItem item) {
        try {
            plaid.removeItem(cipher.decrypt(item.getAccessTokenEncrypted()));
        } catch (PlaidException e) {
            if (!ALREADY_REMOVED.contains(e.code())) {
                throw e;
            }
        }
        items.delete(item); // "on delete cascade" removes its accounts and transactions too
    }

    private void removeQuietly(String accessToken) {
        try {
            plaid.removeItem(accessToken);
        } catch (RuntimeException e) {
            log.warn("Couldn't remove a Plaid item that failed to save: {}", e.getMessage());
        }
    }
}
