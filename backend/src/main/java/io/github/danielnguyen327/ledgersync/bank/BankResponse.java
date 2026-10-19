package io.github.danielnguyen327.ledgersync.bank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A connected bank as the API shows it. It never includes the access token. */
public record BankResponse(UUID id, String institutionName, String status, @Nullable Instant lastSyncedAt,
    long transactionCount, List<AccountResponse> accounts, List<SyncRunResponse> recentSyncs) {

    static BankResponse from(PlaidItem item, List<FinancialAccount> accounts, long transactionCount,
            List<SyncRun> recentSyncs) {
        return new BankResponse(item.getId(), item.getInstitutionName(), item.getStatus(), item.getLastSyncedAt(),
            transactionCount, accounts.stream().map(AccountResponse::from).toList(),
            recentSyncs.stream().map(SyncRunResponse::from).toList());
    }

    /** One recent sync. error is a sentence for people, set only when the sync failed. */
    public record SyncRunResponse(Instant startedAt, String triggeredBy, String status, int added, int updated,
        int removed, @Nullable String error) {

        static SyncRunResponse from(SyncRun run) {
            String error = !run.getStatus().equals("failed") ? null
                : "ITEM_LOGIN_REQUIRED".equals(run.getErrorCode()) ? "The bank needs you to sign in again."
                : "The sync didn't finish. The next one will try again.";
            return new SyncRunResponse(run.getStartedAt(), run.getTriggeredBy(), run.getStatus(), run.getAddedCount(),
                run.getModifiedCount(), run.getRemovedCount(), error);
        }
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
