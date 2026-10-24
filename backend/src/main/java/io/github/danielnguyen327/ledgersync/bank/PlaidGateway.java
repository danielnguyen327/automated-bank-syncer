package io.github.danielnguyen327.ledgersync.bank;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Everything LedgerSync asks Plaid. Tests swap in a fake, so they never call Plaid. */
public interface PlaidGateway {

    /** A one-time token that opens Plaid Link (Plaid's bank sign-in window) for this user. */
    String createLinkToken(UUID userId);

    /** A one-time token that reopens Plaid Link for a bank already connected, so the user can sign in again. */
    String createUpdateLinkToken(UUID userId, String accessToken);

    /** Trades the short-lived public token from Plaid Link for the long-lived access token. */
    ExchangedItem exchangePublicToken(String publicToken);

    /** The bank's name, and its accounts with their balances. */
    ItemDetails getItemDetails(String accessToken);

    /** Tells Plaid to forget this bank connection. Its access token stops working. */
    void removeItem(String accessToken);

    /**
     * One page of transaction changes since the cursor (null means "from the beginning"). While hasMore is
     * true, ask again with nextCursor.
     */
    TransactionsPage syncTransactions(String accessToken, @Nullable String cursor);

    /** Sandbox only: Plaid starts treating this bank as needing a new sign-in, so reconnecting can be tried. */
    void expireSandboxLogin(String accessToken);

    record ExchangedItem(String itemId, String accessToken) {

        @Override
        public String toString() {
            return "ExchangedItem[itemId=" + itemId + ", accessToken=hidden]"; // never let the token reach a log
        }
    }

    record ItemDetails(@Nullable String institutionId, String institutionName, List<PlaidAccount> accounts) {
    }

    record PlaidAccount(String accountId, String name, @Nullable String officialName, @Nullable String mask,
        String type, @Nullable String subtype, @Nullable Long currentCents, @Nullable Long availableCents,
        @Nullable String isoCurrencyCode) {
    }

    /** nextCursor is empty while Plaid is still gathering a new bank's transactions. */
    record TransactionsPage(List<PlaidTransaction> added, List<PlaidTransaction> modified, List<String> removed,
        String nextCursor, boolean hasMore) {
    }

    /** Positive amounts are money leaving the account (Plaid's convention); negative amounts are money coming in. */
    record PlaidTransaction(String transactionId, String accountId, @Nullable String pendingTransactionId,
        long amountCents, @Nullable String isoCurrencyCode, LocalDate date, @Nullable LocalDate authorizedDate,
        String name, @Nullable String merchantName, boolean pending, @Nullable String categoryPrimary,
        @Nullable String categoryDetailed) {
    }
}
