package io.github.danielnguyen327.ledgersync.bank;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One transaction from a connected account. A row in bank_transaction. */
@Entity
@Table(name = "bank_transaction")
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;

    private UUID accountId;

    private String plaidTransactionId;

    private @Nullable String pendingTransactionId;

    private long amountCents;

    private @Nullable String isoCurrencyCode;

    private LocalDate transactionDate;

    private @Nullable LocalDate authorizedDate;

    private String name;

    private @Nullable String merchantName;

    private boolean pending;

    private @Nullable String pfcPrimary;

    private @Nullable String pfcDetailed;

    protected BankTransaction() {
        // for JPA
    }

    BankTransaction(UUID userId, String plaidTransactionId) {
        this.userId = userId;
        this.plaidTransactionId = plaidTransactionId;
    }

    /** Copies Plaid's latest version of this transaction. Used for new and changed transactions alike. */
    void update(UUID accountId, PlaidGateway.PlaidTransaction from) {
        this.accountId = accountId;
        this.pendingTransactionId = from.pendingTransactionId();
        this.amountCents = from.amountCents();
        this.isoCurrencyCode = from.isoCurrencyCode();
        this.transactionDate = from.date();
        this.authorizedDate = from.authorizedDate();
        this.name = from.name();
        this.merchantName = from.merchantName();
        this.pending = from.pending();
        this.pfcPrimary = from.categoryPrimary();
        this.pfcDetailed = from.categoryDetailed();
    }

    String getPlaidTransactionId() {
        return plaidTransactionId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    /** Positive is money leaving the account, negative is money coming in (Plaid's convention). */
    public long getAmountCents() {
        return amountCents;
    }

    public @Nullable String getIsoCurrencyCode() {
        return isoCurrencyCode;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public String getName() {
        return name;
    }

    public @Nullable String getMerchantName() {
        return merchantName;
    }

    public boolean isPending() {
        return pending;
    }

    public @Nullable String getPfcPrimary() {
        return pfcPrimary;
    }
}
