package io.github.danielnguyen327.ledgersync.bank;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One sync of one bank: when it ran, what started it, and what it changed or why it failed. A row in sync_run. */
@Entity
@Table(name = "sync_run")
public class SyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;

    private UUID itemId;

    private String triggeredBy;

    private String status = "running";

    private Instant startedAt;

    private @Nullable Instant finishedAt;

    private int addedCount;

    private int modifiedCount;

    private int removedCount;

    private @Nullable String errorCode;

    private @Nullable String errorMessage;

    protected SyncRun() {
        // for JPA
    }

    SyncRun(UUID userId, UUID itemId, String triggeredBy) {
        this.userId = userId;
        this.itemId = itemId;
        this.triggeredBy = triggeredBy;
        this.startedAt = Instant.now();
    }

    void succeeded(SyncService.SyncResult result) {
        this.status = "succeeded";
        this.finishedAt = Instant.now();
        this.addedCount = result.added();
        this.modifiedCount = result.updated();
        this.removedCount = result.removed();
    }

    void failed(String code, @Nullable String message) {
        this.status = "failed";
        this.finishedAt = Instant.now();
        this.errorCode = code;
        this.errorMessage = message;
    }

    public UUID getItemId() {
        return itemId;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public String getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public int getAddedCount() {
        return addedCount;
    }

    public int getModifiedCount() {
        return modifiedCount;
    }

    public int getRemovedCount() {
        return removedCount;
    }

    public @Nullable String getErrorCode() {
        return errorCode;
    }
}
