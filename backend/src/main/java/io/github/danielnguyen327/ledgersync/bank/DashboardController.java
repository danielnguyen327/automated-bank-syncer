package io.github.danielnguyen327.ledgersync.bank;

import io.github.danielnguyen327.ledgersync.auth.SignedInUser;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** What the dashboard and the transactions page read. accountType is "depository" or "credit", or left out for all. */
@RestController
@RequestMapping("/api")
class DashboardController {

    private final TransactionService transactions;

    DashboardController(TransactionService transactions) {
        this.transactions = transactions;
    }

    @GetMapping("/transactions")
    TransactionService.TransactionPage transactions(@AuthenticationPrincipal SignedInUser signedIn,
            @RequestParam(required = false) @Nullable String accountType,
            @RequestParam(required = false) @Nullable String category,
            @RequestParam(required = false) @Nullable String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return transactions.list(signedIn.id(), accountType, category, search, page, size);
    }

    @GetMapping("/spending")
    TransactionService.SpendingSummary spending(@AuthenticationPrincipal SignedInUser signedIn,
            @RequestParam(required = false) @Nullable String accountType) {
        return transactions.spending(signedIn.id(), accountType);
    }
}
