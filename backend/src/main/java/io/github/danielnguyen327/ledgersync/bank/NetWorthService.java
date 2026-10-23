package io.github.danielnguyen327.ledgersync.bank;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

/**
 * Net worth: everything in the user's accounts minus what they owe on cards and loans, today and for
 * each of the last 90 days.
 */
@Service
public class NetWorthService {

    private static final int DAYS = 90;

    private final FinancialAccountRepository accounts;
    private final BankTransactionRepository transactions;
    private final BalanceSnapshots snapshots;

    NetWorthService(FinancialAccountRepository accounts, BankTransactionRepository transactions,
            BalanceSnapshots snapshots) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.snapshots = snapshots;
    }

    public record NetWorthPoint(LocalDate date, long netCents) {
    }

    /** Days before estimatedBefore are rebuilt from transactions, because no balance was saved for them. */
    public record NetWorth(long assetsCents, long owedCents, long netCents, LocalDate estimatedBefore,
        List<NetWorthPoint> history) {
    }

    /**
     * Walks back from today's balances one day at a time, undoing each day's transactions. Wherever a
     * balance was saved for a day (one per sync, Day 5), the saved balance wins and the walk continues
     * from it, so real data always beats the estimate.
     */
    public NetWorth netWorth(UUID userId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(DAYS - 1);
        Map<UUID, Map<LocalDate, Long>> saved = snapshots.since(userId, start);
        Map<UUID, Map<LocalDate, Long>> flows = new HashMap<>();
        for (BankTransaction t : transactions.findByUserIdAndTransactionDateBetween(userId, start, today)) {
            if (!t.isPending()) {
                flows.computeIfAbsent(t.getAccountId(), id -> new HashMap<>())
                    .merge(t.getTransactionDate(), t.getAmountCents(), Long::sum);
            }
        }

        long assets = 0;
        long owed = 0;
        long[] net = new long[DAYS];
        for (FinancialAccount account : accounts.findByUserIdOrderByName(userId)) {
            Long current = account.getCurrentBalanceCents();
            if (current == null) {
                continue;
            }
            boolean debt = isDebt(account);
            if (debt) {
                owed += current;
            } else {
                assets += current;
            }
            Map<LocalDate, Long> savedForAccount = saved.getOrDefault(account.getId(), Map.of());
            Map<LocalDate, Long> flowsForAccount = flows.getOrDefault(account.getId(), Map.of());
            long balance = current; // at the end of today
            for (int i = DAYS - 1; i >= 0; i--) {
                LocalDate day = start.plusDays(i);
                if (i < DAYS - 1) {
                    // A positive amount is money out: it lowered a bank balance and raised what's owed on a card.
                    long nextDay = flowsForAccount.getOrDefault(day.plusDays(1), 0L);
                    balance = debt ? balance - nextDay : balance + nextDay;
                    balance = savedForAccount.getOrDefault(day, balance);
                }
                net[i] += debt ? -balance : balance;
            }
        }

        LocalDate firstSaved = saved.values().stream()
            .flatMap(days -> days.keySet().stream())
            .min(Comparator.naturalOrder())
            .orElse(today);
        List<NetWorthPoint> history = IntStream.range(0, DAYS)
            .mapToObj(i -> new NetWorthPoint(start.plusDays(i), net[i]))
            .toList();
        return new NetWorth(assets, owed, assets - owed, firstSaved, history);
    }

    private static boolean isDebt(FinancialAccount account) {
        return account.getType().equals("credit") || account.getType().equals("loan");
    }
}
