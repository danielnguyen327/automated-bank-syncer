package io.github.danielnguyen327.ledgersync.bank;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
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

    /** The user's saved balances from this day on, by account and then by day. */
    Map<UUID, Map<LocalDate, Long>> since(UUID userId, LocalDate from) {
        Map<UUID, Map<LocalDate, Long>> saved = new HashMap<>();
        jdbc.query("""
            select account_id, snapshot_date, current_cents from balance_snapshot
            where user_id = ? and snapshot_date >= ? and current_cents is not null""",
            (RowCallbackHandler) row -> saved
                .computeIfAbsent(row.getObject("account_id", UUID.class), id -> new HashMap<>())
                .put(row.getObject("snapshot_date", LocalDate.class), row.getLong("current_cents")),
            userId, from);
        return saved;
    }
}
