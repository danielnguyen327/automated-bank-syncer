package io.github.danielnguyen327.ledgersync.bank;

import org.jspecify.annotations.Nullable;

/** Plaid said no, or couldn't be reached. The code is Plaid's own, like ITEM_LOGIN_REQUIRED. */
public class PlaidException extends RuntimeException {

    private final String code;
    private final @Nullable String displayMessage;

    public PlaidException(String code, @Nullable String displayMessage, String message) {
        super(message);
        this.code = code;
        this.displayMessage = displayMessage;
    }

    public String code() {
        return code;
    }

    /** A sentence Plaid wrote for people, when it has one. */
    public @Nullable String displayMessage() {
        return displayMessage;
    }

    /** What to tell the user: Plaid's own sentence when it wrote one, otherwise a general one. */
    public String userMessage() {
        return displayMessage != null ? displayMessage : "Plaid couldn't finish that. Try again in a minute.";
    }
}
