package io.github.danielnguyen327.ledgersync.bank;

import io.github.danielnguyen327.ledgersync.auth.SignedInUser;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Tools for trying LedgerSync on Plaid's Sandbox. Outside the Sandbox they don't exist (404). */
@RestController
@RequestMapping("/api/sandbox")
class SandboxController {

    private final BankService banks;
    private final SyncService syncs;
    private final boolean sandbox;

    SandboxController(BankService banks, SyncService syncs, @Value("${plaid.environment}") String environment) {
        this.banks = banks;
        this.syncs = syncs;
        this.sandbox = environment.equals("sandbox");
    }

    /** Makes one of your banks need a new sign-in, the way real banks sometimes do, so you can try reconnecting. */
    @PostMapping("/banks/{id}/expire-sign-in")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void expireSignIn(@AuthenticationPrincipal SignedInUser signedIn, @PathVariable UUID id) {
        if (!sandbox) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        banks.expireSandboxLogin(signedIn.id(), id);
        try {
            syncs.sync(signedIn.id(), id); // sees ITEM_LOGIN_REQUIRED and marks the bank, as the schedule would
        } catch (ResponseStatusException expected) {
            // 409: the bank now needs a new sign-in, which is what this tool is for
        }
    }
}
