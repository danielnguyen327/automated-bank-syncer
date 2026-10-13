package io.github.danielnguyen327.ledgersync.bank;

import io.github.danielnguyen327.ledgersync.auth.SignedInUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/banks")
class BankController {

    record LinkTokenResponse(String linkToken) {
    }

    record ConnectRequest(@NotBlank(message = "The bank connection didn't finish. Try again.") String publicToken) {
    }

    private final BankService banks;
    private final SyncService syncs;

    BankController(BankService banks, SyncService syncs) {
        this.banks = banks;
        this.syncs = syncs;
    }

    @PostMapping("/link-token")
    LinkTokenResponse linkToken(@AuthenticationPrincipal SignedInUser signedIn) {
        return new LinkTokenResponse(banks.createLinkToken(signedIn.id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BankResponse connect(@AuthenticationPrincipal SignedInUser signedIn, @Valid @RequestBody ConnectRequest body) {
        return banks.connect(signedIn.id(), body.publicToken());
    }

    @GetMapping
    List<BankResponse> list(@AuthenticationPrincipal SignedInUser signedIn) {
        return banks.banks(signedIn.id());
    }

    /** Downloads the bank's newest transactions from Plaid. */
    @PostMapping("/{id}/sync")
    SyncService.SyncResult sync(@AuthenticationPrincipal SignedInUser signedIn, @PathVariable UUID id) {
        return syncs.sync(signedIn.id(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void disconnect(@AuthenticationPrincipal SignedInUser signedIn, @PathVariable UUID id) {
        banks.disconnect(signedIn.id(), id);
    }
}
