package io.github.danielnguyen327.ledgersync.bank;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Syncs every bank on its own, on the schedule in ledgersync.sync.cron (every 3 hours). */
@Component
class SyncScheduler {

    private final SyncService syncs;

    SyncScheduler(SyncService syncs) {
        this.syncs = syncs;
    }

    @Scheduled(cron = "${ledgersync.sync.cron}", zone = "UTC")
    void syncEveryBank() {
        syncs.syncEveryBank();
    }
}
