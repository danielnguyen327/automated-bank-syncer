package io.github.danielnguyen327.ledgersync.bank;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * One balance per account per day, kept for the net-worth chart. Days are UTC days. A later sync on
 * the same day overwrites that day's row, so each day keeps its last balance.
 */
@Repository
class BalanceSnapshots {

    private static final String UPSERT = """
        insert into balance_snapshot (user_id, account_id, snapshot_date, current_cents, available_cents)
        values (?, ?, ?, ?, ?)
        on conflict (account_id, snapshot_date)
        do update set current_cents = excluded.current_cents, available_cents = excluded.available_cents
        """;

    private final JdbcTemplate jdbc;

    BalanceSnapshots(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void saveToday(UUID userId, FinancialAccount account) {
        jdbc.update(UPSERT, userId, account.getId(), LocalDate.now(ZoneOffset.UTC), account.getCurrentBalanceCents(),
            account.getAvailableBalanceCents());
    }
}
