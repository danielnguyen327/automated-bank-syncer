package io.github.danielnguyen327.ledgersync.bank;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncRunRepository extends JpaRepository<SyncRun, UUID> {

    /** The user's most recent syncs across all their banks, newest first. */
    List<SyncRun> findTop50ByUserIdOrderByStartedAtDesc(UUID userId);
}
