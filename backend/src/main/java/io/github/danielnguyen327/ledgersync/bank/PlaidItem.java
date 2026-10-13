package io.github.danielnguyen327.ledgersync.bank;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One bank connection (Plaid calls it an "item"). A row in plaid_item. */
@Entity
@Table(name = "plaid_item")
public class PlaidItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;

    private String plaidItemId;

    private @Nullable String institutionId;

    private String institutionName;

    private String accessTokenEncrypted;

    private @Nullable String syncCursor;

    private String status = "active";

    private @Nullable Instant lastSyncedAt;

    @Column(insertable = false, updatable = false) // the database fills it in
    private Instant createdAt;

    protected PlaidItem() {
        // for JPA
    }

    PlaidItem(UUID userId, String plaidItemId, @Nullable String institutionId, String institutionName,
            String accessTokenEncrypted) {
        this.userId = userId;
        this.plaidItemId = plaidItemId;
        this.institutionId = institutionId;
        this.institutionName = institutionName;
        this.accessTokenEncrypted = accessTokenEncrypted;
    }

    public UUID getId() {
        return id;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getStatus() {
        return status;
    }

    public @Nullable Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    String getAccessTokenEncrypted() {
        return accessTokenEncrypted;
    }

    /** Where the last finished sync stopped. The next sync asks Plaid for changes after this point. */
    @Nullable String getSyncCursor() {
        return syncCursor;
    }

    void markSynced(String cursor, Instant at) {
        this.syncCursor = cursor;
        this.lastSyncedAt = at;
    }
}
