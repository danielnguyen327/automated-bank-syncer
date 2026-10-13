package io.github.danielnguyen327.ledgersync.bank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A connected bank as the API shows it. It never includes the access token. */
public record BankResponse(UUID id, String institutionName, String status, @Nullable Instant lastSyncedAt,
    long transactionCount, List<AccountResponse> accounts) {

    static BankResponse from(PlaidItem item, List<FinancialAccount> accounts, long transactionCount) {
        return new BankResponse(item.getId(), item.getInstitutionName(), item.getStatus(), item.getLastSyncedAt(),
            transactionCount, accounts.stream().map(AccountResponse::from).toList());
    }

    public record AccountResponse(UUID id, String name, @Nullable String mask, String type, @Nullable String subtype,
        @Nullable Long currentBalanceCents, @Nullable Long availableBalanceCents, @Nullable String isoCurrencyCode) {

        static AccountResponse from(FinancialAccount account) {
            return new AccountResponse(account.getId(), account.getName(), account.getMask(), account.getType(),
                account.getSubtype(), account.getCurrentBalanceCents(), account.getAvailableBalanceCents(),
                account.getIsoCurrencyCode());
        }
    }
}
