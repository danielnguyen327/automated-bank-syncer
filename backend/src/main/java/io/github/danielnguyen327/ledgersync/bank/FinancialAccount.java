package io.github.danielnguyen327.ledgersync.bank;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One account (checking, savings, credit card) at a connected bank. A row in financial_account. */
@Entity
@Table(name = "financial_account")
public class FinancialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;

    private UUID itemId;

    private String plaidAccountId;

    private String name;

    private @Nullable String officialName;

    private @Nullable String mask;

    private String type;

    private @Nullable String subtype;

    private @Nullable Long currentBalanceCents;

    private @Nullable Long availableBalanceCents;

    private @Nullable String isoCurrencyCode;

    protected FinancialAccount() {
        // for JPA
    }

    FinancialAccount(UUID userId, UUID itemId, PlaidGateway.PlaidAccount account) {
        this.userId = userId;
        this.itemId = itemId;
        this.plaidAccountId = account.accountId();
        this.name = account.name();
        this.officialName = account.officialName();
        this.mask = account.mask();
        this.type = account.type();
        this.subtype = account.subtype();
        this.currentBalanceCents = account.currentCents();
        this.availableBalanceCents = account.availableCents();
        this.isoCurrencyCode = account.isoCurrencyCode();
    }

    public UUID getId() {
        return id;
    }

    public UUID getItemId() {
        return itemId;
    }

    public String getName() {
        return name;
    }

    public @Nullable String getMask() {
        return mask;
    }

    public String getType() {
        return type;
    }

    public @Nullable String getSubtype() {
        return subtype;
    }

    public @Nullable Long getCurrentBalanceCents() {
        return currentBalanceCents;
    }

    public @Nullable Long getAvailableBalanceCents() {
        return availableBalanceCents;
    }

    public @Nullable String getIsoCurrencyCode() {
        return isoCurrencyCode;
    }

    /** Plaid's id for this account. Each transaction from Plaid names its account by this id. */
    String getPlaidAccountId() {
        return plaidAccountId;
    }
}
