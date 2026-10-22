package io.github.danielnguyen327.ledgersync.bank;

import static java.util.Map.entry;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Names people read for Plaid's category codes ("FOOD_AND_DRINK" becomes "Food & drink"). Until Claude
 * sorts transactions into LedgerSync's own categories, these are the categories the app shows.
 */
final class Categories {

    static final String OTHER = "Other";

    /** Money coming in, or moving between your own accounts. None of it is spending. */
    private static final Set<String> NOT_SPENDING = Set.of("INCOME", "TRANSFER_IN", "TRANSFER_OUT", "LOAN_PAYMENTS");

    private static final Map<String, String> NAMES = Map.ofEntries(
        entry("BANK_FEES", "Bank fees"),
        entry("ENTERTAINMENT", "Entertainment"),
        entry("FOOD_AND_DRINK", "Food & drink"),
        entry("GENERAL_MERCHANDISE", "Shopping"),
        entry("GENERAL_SERVICES", "Services"),
        entry("GOVERNMENT_AND_NON_PROFIT", "Government & nonprofits"),
        entry("HOME_IMPROVEMENT", "Home improvement"),
        entry("INCOME", "Income"),
        entry("LOAN_PAYMENTS", "Loan payments"),
        entry("MEDICAL", "Medical"),
        entry("PERSONAL_CARE", "Personal care"),
        entry("RENT_AND_UTILITIES", "Rent & utilities"),
        entry("TRANSFER_IN", "Transfers in"),
        entry("TRANSFER_OUT", "Transfers out"),
        entry("TRANSPORTATION", "Transportation"),
        entry("TRAVEL", "Travel"));

    private Categories() {
    }

    static String nameOf(@Nullable String plaidCode) {
        return plaidCode == null ? OTHER : NAMES.getOrDefault(plaidCode, OTHER);
    }

    static boolean isSpending(@Nullable String plaidCode) {
        return plaidCode == null || !NOT_SPENDING.contains(plaidCode);
    }

    /** The Plaid codes shown under this name. */
    static Set<String> codesNamed(String name) {
        return NAMES.entrySet().stream()
            .filter(e -> e.getValue().equals(name))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    static Set<String> knownCodes() {
        return NAMES.keySet();
    }
}
