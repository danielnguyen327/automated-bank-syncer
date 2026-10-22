package io.github.danielnguyen327.ledgersync.bank;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** The user's transactions, and their spending by category. Every query starts from the user's id. */
@Service
public class TransactionService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ACCOUNT_TYPES = Set.of("depository", "credit");

    private final BankTransactionRepository transactions;
    private final FinancialAccountRepository accounts;

    TransactionService(BankTransactionRepository transactions, FinancialAccountRepository accounts) {
        this.transactions = transactions;
        this.accounts = accounts;
    }

    public record TransactionResponse(UUID id, LocalDate date, String name, @Nullable String merchantName,
        long amountCents, @Nullable String isoCurrencyCode, boolean pending, String category, UUID accountId,
        String accountName, @Nullable String accountMask, String accountType) {
    }

    public record TransactionPage(List<TransactionResponse> transactions, boolean hasMore) {
    }

    public record CategoryTotal(String category, long cents, long previousCents) {
    }

    /** Spending this month so far, by category, next to the same days of last month. Days are UTC days. */
    public record SpendingSummary(LocalDate from, LocalDate to, long totalCents, LocalDate previousFrom,
        LocalDate previousTo, long previousTotalCents, List<CategoryTotal> categories) {
    }

    /** Newest first. Each filter is optional: account type, category name, and text in the name or merchant. */
    public TransactionPage list(UUID userId, @Nullable String accountType, @Nullable String category,
            @Nullable String search, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be 0 or more, and size 1 to 100.");
        }
        Map<UUID, FinancialAccount> userAccounts = accountsOf(userId, null);
        Specification<BankTransaction> query = (root, q, cb) -> cb.equal(root.get("userId"), userId);
        if (accountType != null) {
            Set<UUID> ids = accountsOf(userId, accountType).keySet();
            query = query.and((root, q, cb) -> ids.isEmpty() ? cb.disjunction() : root.get("accountId").in(ids));
        }
        if (category != null && !category.isBlank()) {
            query = query.and(inCategory(category));
        }
        if (search != null && !search.isBlank()) {
            String pattern = "%" + escapeLike(search.strip().toLowerCase(Locale.ROOT)) + "%";
            query = query.and((root, q, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern, '\\'),
                cb.like(cb.lower(root.get("merchantName")), pattern, '\\')));
        }
        Sort newestFirst = Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.asc("id"));
        Page<BankTransaction> found = transactions.findAll(query, PageRequest.of(page, size, newestFirst));
        List<TransactionResponse> rows = found.getContent().stream()
            .map(t -> toResponse(t, userAccounts.get(t.getAccountId())))
            .toList();
        return new TransactionPage(rows, found.hasNext());
    }

    public SpendingSummary spending(UUID userId, @Nullable String accountType) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = today.withDayOfMonth(1);
        LocalDate previousFrom = from.minusMonths(1);
        LocalDate previousTo = previousFrom.plusDays(today.getDayOfMonth() - 1L);
        if (!previousTo.isBefore(from)) {
            previousTo = from.minusDays(1); // e.g. on March 30, compare with all of February
        }
        Set<UUID> counted = accountsOf(userId, accountType).keySet();
        Map<String, Long> now = new HashMap<>();
        Map<String, Long> before = new HashMap<>();
        for (BankTransaction t : transactions.findByUserIdAndTransactionDateBetween(userId, previousFrom, today)) {
            if (!counted.contains(t.getAccountId()) || !Categories.isSpending(t.getPfcPrimary())) {
                continue;
            }
            LocalDate date = t.getTransactionDate();
            Map<String, Long> period = !date.isBefore(from) ? now : !date.isAfter(previousTo) ? before : null;
            if (period != null) {
                period.merge(Categories.nameOf(t.getPfcPrimary()), t.getAmountCents(), Long::sum);
            }
        }
        List<CategoryTotal> categories = now.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .map(e -> new CategoryTotal(e.getKey(), e.getValue(), before.getOrDefault(e.getKey(), 0L)))
            .toList();
        return new SpendingSummary(from, today, total(now), previousFrom, previousTo, total(before), categories);
    }

    /** The user's accounts of one type ("depository" or "credit"), or all of them when the type is null. */
    private Map<UUID, FinancialAccount> accountsOf(UUID userId, @Nullable String accountType) {
        if (accountType != null && !ACCOUNT_TYPES.contains(accountType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountType must be depository or credit.");
        }
        return accounts.findByUserIdOrderByName(userId).stream()
            .filter(a -> accountType == null || a.getType().equals(accountType))
            .collect(Collectors.toMap(FinancialAccount::getId, Function.identity()));
    }

    private static Specification<BankTransaction> inCategory(String category) {
        if (category.equals(Categories.OTHER)) {
            return (root, q, cb) -> cb.or(cb.isNull(root.get("pfcPrimary")),
                cb.not(root.get("pfcPrimary").in(Categories.knownCodes())));
        }
        Set<String> codes = Categories.codesNamed(category);
        return (root, q, cb) -> codes.isEmpty() ? cb.disjunction() : root.get("pfcPrimary").in(codes);
    }

    private static TransactionResponse toResponse(BankTransaction t, FinancialAccount account) {
        return new TransactionResponse(t.getId(), t.getTransactionDate(), t.getName(), t.getMerchantName(),
            t.getAmountCents(), t.getIsoCurrencyCode(), t.isPending(), Categories.nameOf(t.getPfcPrimary()),
            account.getId(), account.getName(), account.getMask(), account.getType());
    }

    /** Makes %, _ and \ in the user's search text match themselves, not act as wildcards. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static long total(Map<String, Long> byCategory) {
        return byCategory.values().stream().mapToLong(Long::longValue).sum();
    }
}
