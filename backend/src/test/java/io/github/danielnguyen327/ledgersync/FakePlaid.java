package io.github.danielnguyen327.ledgersync;

import io.github.danielnguyen327.ledgersync.bank.PlaidException;
import io.github.danielnguyen327.ledgersync.bank.PlaidGateway;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A pretend Plaid for tests. It never touches the network, and remembers every access token it gave
 * out, every bank it was asked to remove and every sync request, so tests can check them.
 */
class FakePlaid implements PlaidGateway {

    /** Public tokens the fake accepts, and the bank each one connects. "public-broken" fails after the exchange. */
    private static final Map<String, String> BANKS = Map.of(
        "public-tartan", "Tartan Bank",
        "public-platypus", "First Platypus Bank",
        "public-broken", "Broken Bank");

    /** Changes per page. Real Plaid sends up to 500; a small page makes tests cover paging. */
    static final int PAGE_SIZE = 3;

    final List<String> issuedAccessTokens = new CopyOnWriteArrayList<>();
    final List<String> removedAccessTokens = new CopyOnWriteArrayList<>();
    /** The cursor of every sync request, in order. A sync from the beginning shows up as "start". */
    final List<String> requestedCursors = new CopyOnWriteArrayList<>();
    private final Map<String, String> bankByAccessToken = new ConcurrentHashMap<>();
    /** Balances a test changed, by Plaid account id. Other accounts keep their starting balance. */
    private final Map<String, Long> currentBalances = new ConcurrentHashMap<>();

    /** Every change at "Plaid", per bank. A cursor is a position in this list. */
    private final Map<String, List<Change>> changesByAccessToken = new ConcurrentHashMap<>();
    private final Set<String> notReady = ConcurrentHashMap.newKeySet();
    /** Plaid's error code for banks whose syncs should fail, by access token. */
    private final Map<String, String> syncErrors = new ConcurrentHashMap<>();
    private final Set<String> midSync = ConcurrentHashMap.newKeySet();
    private final AtomicInteger dataChangesToReport = new AtomicInteger();
    private final AtomicReference<@Nullable Runnable> beforeNextPage = new AtomicReference<>();

    private record Change(String kind, @Nullable PlaidTransaction transaction, @Nullable String removedId) {
    }

    void reset() {
        issuedAccessTokens.clear();
        removedAccessTokens.clear();
        requestedCursors.clear();
        bankByAccessToken.clear();
        currentBalances.clear();
        changesByAccessToken.clear();
        notReady.clear();
        syncErrors.clear();
        midSync.clear();
        dataChangesToReport.set(0);
        beforeNextPage.set(null);
    }

    @Override
    public String createLinkToken(UUID userId) {
        return "link-sandbox-" + userId;
    }

    @Override
    public ExchangedItem exchangePublicToken(String publicToken) {
        String bank = BANKS.get(publicToken);
        if (bank == null) {
            throw new PlaidException("INVALID_PUBLIC_TOKEN", null, "unknown public token");
        }
        String accessToken = "access-sandbox-" + UUID.randomUUID();
        issuedAccessTokens.add(accessToken);
        bankByAccessToken.put(accessToken, bank);
        return new ExchangedItem("item-" + UUID.randomUUID(), accessToken);
    }

    @Override
    public ItemDetails getItemDetails(String accessToken) {
        String bank = bankByAccessToken.get(accessToken);
        if (bank.equals("Broken Bank")) {
            throw new PlaidException("INTERNAL_SERVER_ERROR", null, "Plaid had a problem");
        }
        String checking = checking(accessToken);
        String card = card(accessToken);
        return new ItemDetails("ins_" + bank.length(), bank, List.of(
            new PlaidAccount(checking, "Plaid Checking", "Plaid Gold Standard 0% Interest Checking", "0000",
                "depository", "checking", currentBalances.getOrDefault(checking, 11000L), 10000L, "USD"),
            new PlaidAccount(card, "Plaid Credit Card", "Plaid Diamond 12.5% APR Interest Credit Card", "3333",
                "credit", "credit card", currentBalances.getOrDefault(card, 41000L), null, "USD")));
    }

    @Override
    public void removeItem(String accessToken) {
        removedAccessTokens.add(accessToken);
    }

    @Override
    public TransactionsPage syncTransactions(String accessToken, @Nullable String cursor) {
        requestedCursors.add(cursor == null ? "start" : cursor);
        Runnable action = beforeNextPage.getAndSet(null);
        if (action != null) {
            action.run();
        }
        String error = syncErrors.get(accessToken);
        if (error != null) {
            throw new PlaidException(error, null, "sync failed: " + error);
        }
        if (notReady.contains(accessToken)) {
            return new TransactionsPage(List.of(), List.of(), List.of(), "", false);
        }
        if (midSync.remove(accessToken) && dataChangesToReport.getAndUpdate(n -> Math.max(n - 1, 0)) > 0) {
            throw new PlaidException("TRANSACTIONS_SYNC_MUTATION_DURING_PAGINATION", null, "data changed");
        }
        List<Change> all = changesByAccessToken.getOrDefault(accessToken, List.of());
        int start = cursor == null ? 0 : Integer.parseInt(cursor.substring("cursor-".length()));
        int end = Math.min(start + PAGE_SIZE, all.size());
        List<PlaidTransaction> added = new ArrayList<>();
        List<PlaidTransaction> modified = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        for (Change change : all.subList(start, end)) {
            switch (change.kind()) {
                case "added" -> added.add(change.transaction());
                case "modified" -> modified.add(change.transaction());
                default -> removed.add(change.removedId());
            }
        }
        boolean hasMore = end < all.size();
        if (hasMore) {
            midSync.add(accessToken);
        }
        return new TransactionsPage(added, modified, removed, "cursor-" + end, hasMore);
    }

    /** New transactions appear at the bank. */
    void add(String accessToken, PlaidTransaction... transactions) {
        for (PlaidTransaction transaction : transactions) {
            changes(accessToken).add(new Change("added", transaction, null));
        }
    }

    /** Transactions the bank already sent are changed, like a new amount or name. */
    void modify(String accessToken, PlaidTransaction... transactions) {
        for (PlaidTransaction transaction : transactions) {
            changes(accessToken).add(new Change("modified", transaction, null));
        }
    }

    /** Transactions disappear at the bank, like a pending one that has now posted. */
    void remove(String accessToken, String... transactionIds) {
        for (String id : transactionIds) {
            changes(accessToken).add(new Change("removed", null, id));
        }
    }

    /** The bank now reports a different current balance for this account. */
    void changeBalance(String accountId, long currentCents) {
        currentBalances.put(accountId, currentCents);
    }

    /** Every sync of this bank fails with Plaid's error code, until stopFailing. */
    void failSyncs(String accessToken, String plaidErrorCode) {
        syncErrors.put(accessToken, plaidErrorCode);
    }

    /** The bank ended the connection: syncs fail with ITEM_LOGIN_REQUIRED until stopFailing. */
    void requireLogin(String accessToken) {
        failSyncs(accessToken, "ITEM_LOGIN_REQUIRED");
    }

    void stopFailing(String accessToken) {
        syncErrors.remove(accessToken);
    }

    /** Plaid is still gathering this bank's transactions, like in the first seconds after connecting. */
    void notReadyYet(String accessToken) {
        notReady.add(accessToken);
    }

    /** The next times a sync asks for its second or later page, Plaid says its data changed meanwhile. */
    void reportDataChanged(int times) {
        dataChangesToReport.set(times);
    }

    /** Runs once, the next time a sync asks for a page. */
    void beforeNextPage(Runnable action) {
        beforeNextPage.set(action);
    }

    private List<Change> changes(String accessToken) {
        return changesByAccessToken.computeIfAbsent(accessToken, token -> new CopyOnWriteArrayList<>());
    }

    /** Plaid's id for the checking account of the bank connected with this access token. */
    static String checking(String accessToken) {
        return accessToken + "-checking";
    }

    /** Plaid's id for the credit card of the bank connected with this access token. */
    static String card(String accessToken) {
        return accessToken + "-card";
    }

    /** A posted purchase on this account. */
    static PlaidTransaction purchase(String accountId, String id, long amountCents, String name) {
        return transaction(accountId, id, null, amountCents, name, false);
    }

    static PlaidTransaction transaction(String accountId, String id, @Nullable String pendingTransactionId,
            long amountCents, String name, boolean pending) {
        return new PlaidTransaction(id, accountId, pendingTransactionId, amountCents, "USD", LocalDate.of(2026, 10, 1),
            null, name, name, pending, "GENERAL_MERCHANDISE", "GENERAL_MERCHANDISE_OTHER_GENERAL_MERCHANDISE");
    }

    /** Add with @Import to swap the real Plaid for this fake. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        FakePlaid fakePlaid() {
            return new FakePlaid();
        }
    }
}
