package io.github.danielnguyen327.ledgersync.bank;

import com.google.gson.Gson;
import com.plaid.client.ApiClient;
import com.plaid.client.model.AccountBalance;
import com.plaid.client.model.AccountBase;
import com.plaid.client.model.AccountType;
import com.plaid.client.model.AccountsGetRequest;
import com.plaid.client.model.AccountsGetResponse;
import com.plaid.client.model.CountryCode;
import com.plaid.client.model.Item;
import com.plaid.client.model.ItemPublicTokenExchangeRequest;
import com.plaid.client.model.ItemPublicTokenExchangeResponse;
import com.plaid.client.model.ItemRemoveRequest;
import com.plaid.client.model.LinkTokenCreateRequest;
import com.plaid.client.model.LinkTokenCreateRequestUser;
import com.plaid.client.model.PersonalFinanceCategory;
import com.plaid.client.model.PlaidError;
import com.plaid.client.model.Products;
import com.plaid.client.model.RemovedTransaction;
import com.plaid.client.model.Transaction;
import com.plaid.client.model.TransactionsSyncRequest;
import com.plaid.client.model.TransactionsSyncResponse;
import com.plaid.client.request.PlaidApi;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import retrofit2.Call;
import retrofit2.Response;

/** The real PlaidGateway, built on Plaid's Java library. */
@Component
class PlaidApiGateway implements PlaidGateway {

    private static final Gson GSON = new Gson();

    private final PlaidApi plaid;
    private final boolean configured;

    PlaidApiGateway(@Value("${plaid.client-id}") String clientId, @Value("${plaid.secret}") String secret,
            @Value("${plaid.environment}") String environment) {
        ApiClient client = new ApiClient(Map.of("clientId", clientId, "secret", secret));
        client.setPlaidAdapter(environment.equals("production") ? ApiClient.Production : ApiClient.Sandbox);
        this.plaid = client.createService(PlaidApi.class);
        this.configured = !clientId.isBlank() && !secret.isBlank();
    }

    @Override
    public String createLinkToken(UUID userId) {
        LinkTokenCreateRequest request = new LinkTokenCreateRequest()
            .user(new LinkTokenCreateRequestUser().clientUserId(userId.toString()))
            .clientName("LedgerSync")
            .products(List.of(Products.TRANSACTIONS)) // read-only: no account numbers, no moving money
            .countryCodes(List.of(CountryCode.US))
            .language("en");
        return call(plaid.linkTokenCreate(request)).getLinkToken();
    }

    @Override
    public ExchangedItem exchangePublicToken(String publicToken) {
        ItemPublicTokenExchangeResponse response = call(plaid.itemPublicTokenExchange(
            new ItemPublicTokenExchangeRequest().publicToken(publicToken)));
        return new ExchangedItem(response.getItemId(), response.getAccessToken());
    }

    @Override
    public ItemDetails getItemDetails(String accessToken) {
        AccountsGetResponse response = call(plaid.accountsGet(new AccountsGetRequest().accessToken(accessToken)));
        Item item = response.getItem();
        String institutionName = Objects.requireNonNullElse(item.getInstitutionName(), "Your bank");
        List<PlaidAccount> accounts = response.getAccounts().stream().map(PlaidApiGateway::toAccount).toList();
        return new ItemDetails(item.getInstitutionId(), institutionName, accounts);
    }

    @Override
    public void removeItem(String accessToken) {
        call(plaid.itemRemove(new ItemRemoveRequest().accessToken(accessToken)));
    }

    @Override
    public TransactionsPage syncTransactions(String accessToken, @Nullable String cursor) {
        // 500 is the most Plaid sends in one page
        TransactionsSyncRequest request = new TransactionsSyncRequest().accessToken(accessToken).count(500);
        if (cursor != null) {
            request.cursor(cursor);
        }
        TransactionsSyncResponse response = call(plaid.transactionsSync(request));
        return new TransactionsPage(
            response.getAdded().stream().map(PlaidApiGateway::toTransaction).toList(),
            response.getModified().stream().map(PlaidApiGateway::toTransaction).toList(),
            response.getRemoved().stream().map(RemovedTransaction::getTransactionId).toList(),
            Objects.requireNonNullElse(response.getNextCursor(), ""),
            Boolean.TRUE.equals(response.getHasMore()));
    }

    private static PlaidTransaction toTransaction(Transaction transaction) {
        PersonalFinanceCategory category = transaction.getPersonalFinanceCategory();
        return new PlaidTransaction(transaction.getTransactionId(), transaction.getAccountId(),
            transaction.getPendingTransactionId(), Objects.requireNonNull(cents(transaction.getAmount())),
            transaction.getIsoCurrencyCode(), transaction.getDate(), transaction.getAuthorizedDate(),
            transaction.getName(), transaction.getMerchantName(), Boolean.TRUE.equals(transaction.getPending()),
            category == null ? null : category.getPrimary(), category == null ? null : category.getDetailed());
    }

    private static PlaidAccount toAccount(AccountBase account) {
        AccountBalance balances = account.getBalances();
        String subtype = account.getSubtype() == null ? null : account.getSubtype().getValue();
        return new PlaidAccount(account.getAccountId(), account.getName(), account.getOfficialName(),
            account.getMask(), accountType(account.getType()), subtype, cents(balances.getCurrent()),
            cents(balances.getAvailable()), balances.getIsoCurrencyCode());
    }

    /** The database allows five account types. Plaid's "brokerage" is an investment account. */
    private static String accountType(AccountType type) {
        return switch (type) {
            case DEPOSITORY, CREDIT, LOAN, INVESTMENT -> type.getValue();
            case BROKERAGE -> "investment";
            default -> "other";
        };
    }

    /** Plaid sends dollars with decimals (12.5); the database stores whole cents (1250). */
    private static @Nullable Long cents(@Nullable Double dollars) {
        if (dollars == null) {
            return null;
        }
        return BigDecimal.valueOf(dollars).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Sends one request to Plaid and returns its answer, or throws PlaidException with Plaid's error code. */
    private <T> T call(Call<T> request) {
        if (!configured) {
            throw new PlaidException("NOT_CONFIGURED",
                "Plaid isn't set up yet. Add PLAID_CLIENT_ID and PLAID_SECRET to backend/.env.",
                "Missing Plaid keys");
        }
        try {
            Response<T> response = request.execute();
            T body = response.body();
            if (response.isSuccessful() && body != null) {
                return body;
            }
            String errorJson = response.errorBody() == null ? "{}" : response.errorBody().string();
            PlaidError error = GSON.fromJson(errorJson, PlaidError.class);
            throw new PlaidException(Objects.requireNonNullElse(error.getErrorCode(), "PLAID_ERROR"),
                error.getDisplayMessage(), Objects.requireNonNullElse(error.getErrorMessage(), "Plaid error"));
        } catch (IOException e) {
            throw new PlaidException("NETWORK_ERROR", "Couldn't reach Plaid. Try again in a minute.", e.getMessage());
        }
    }
}
