package io.github.danielnguyen327.ledgersync;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.danielnguyen327.ledgersync.bank.PlaidGateway.PlaidTransaction;
import io.github.danielnguyen327.ledgersync.bank.SyncService;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, FakePlaid.Config.class})
class SyncTests {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private FakePlaid plaid;

    @Autowired
    private SyncService syncs;

    private Browser sam;
    private String bankId;
    private String accessToken;

    @BeforeEach
    void samConnectsABank() {
        jdbc.update("delete from spring_session");
        jdbc.update("delete from app_user");
        plaid.reset();
        sam = new Browser(port);
        sam.post("/api/auth/register", """
            {"name": "Sam Rivera", "email": "sam@example.com", "password": "correct horse battery"}
            """);
        bankId = body(sam.post("/api/banks", """
            {"publicToken": "public-tartan"}
            """)).get("id").asString();
        accessToken = plaid.issuedAccessTokens.getLast();
    }

    @Test
    void theFirstSyncDownloadsEveryPage() {
        plaid.add(accessToken, purchases(7)); // three pages: 3 + 3 + 1

        JsonNode result = sync();

        assertThat(result.get("ready").asBoolean()).isTrue();
        assertThat(result.get("added").asInt()).isEqualTo(7);
        assertThat(plaid.requestedCursors).containsExactly("start", "cursor-3", "cursor-6");
        assertThat(transactionCount()).isEqualTo(7);
        assertThat(jdbc.queryForObject("select sync_cursor from plaid_item", String.class)).isEqualTo("cursor-7");
        JsonNode bank = body(sam.get("/api/banks")).get(0);
        assertThat(bank.get("transactionCount").asLong()).isEqualTo(7);
        assertThat(bank.get("lastSyncedAt").isNull()).isFalse();
    }

    @Test
    void syncingAgainNeverCreatesDuplicates() {
        plaid.add(accessToken, purchases(4));
        sync();

        JsonNode again = sync(); // nothing changed at Plaid

        assertThat(again.get("added").asInt()).isZero();
        assertThat(transactionCount()).isEqualTo(4);

        // As if the app crashed after saving the transactions but before saving the cursor.
        jdbc.update("update plaid_item set sync_cursor = null");
        JsonNode replayed = sync(); // Plaid sends all four again

        assertThat(replayed.get("added").asInt()).isZero();
        assertThat(replayed.get("updated").asInt()).isEqualTo(4);
        assertThat(transactionCount()).isEqualTo(4);
    }

    @Test
    void changedTransactionsAreUpdatedInPlace() {
        String checking = FakePlaid.checking(accessToken);
        plaid.add(accessToken, FakePlaid.purchase(checking, "t1", 1250, "Coffee"));
        sync();

        plaid.modify(accessToken, FakePlaid.purchase(checking, "t1", 1500, "Blue Bottle Coffee"));

        assertThat(sync().get("updated").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForMap("select name, amount_cents from bank_transaction"))
            .containsEntry("name", "Blue Bottle Coffee")
            .containsEntry("amount_cents", 1500L);
    }

    @Test
    void aPendingTransactionIsReplacedByItsPostedVersion() {
        String checking = FakePlaid.checking(accessToken);
        plaid.add(accessToken, FakePlaid.transaction(checking, "pending-1", null, 2000, "Grocery Store", true));
        sync();

        // When it posts, Plaid removes the pending transaction and adds the posted one, which points back to it.
        plaid.remove(accessToken, "pending-1");
        plaid.add(accessToken, FakePlaid.transaction(checking, "posted-1", "pending-1", 2000, "Grocery Store", false));
        JsonNode result = sync();

        assertThat(result.get("added").asInt()).isEqualTo(1);
        assertThat(result.get("removed").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForMap("select plaid_transaction_id, pending, amount_cents from bank_transaction"))
            .containsEntry("plaid_transaction_id", "posted-1")
            .containsEntry("pending", false)
            .containsEntry("amount_cents", 2000L); // counted once, not twice
    }

    @Test
    void ifPlaidsDataChangesMidSyncItStartsOver() {
        plaid.add(accessToken, purchases(7));
        plaid.reportDataChanged(1);

        JsonNode result = sync();

        assertThat(plaid.requestedCursors).containsExactly("start", "cursor-3", "start", "cursor-3", "cursor-6");
        assertThat(result.get("added").asInt()).isEqualTo(7);
        assertThat(transactionCount()).isEqualTo(7);
    }

    @Test
    void aSyncThatFailsSavesNothing() {
        plaid.add(accessToken, purchases(7));
        plaid.changeBalance(FakePlaid.checking(accessToken), 25075);
        plaid.reportDataChanged(3); // more times than the sync is willing to start over

        Browser.Response response = sam.post("/api/banks/" + bankId + "/sync", "");

        assertThat(response.status()).isEqualTo(502);
        assertThat(transactionCount()).isZero();
        assertThat(jdbc.queryForObject("select sync_cursor from plaid_item", String.class)).isNull();
        assertThat(currentBalance(FakePlaid.checking(accessToken))).isEqualTo(11000); // still the old balance
    }

    @Test
    void aSyncRefreshesBalances() {
        plaid.changeBalance(FakePlaid.checking(accessToken), 25075);
        plaid.changeBalance(FakePlaid.card(accessToken), 52010);

        sync();

        assertThat(currentBalance(FakePlaid.checking(accessToken))).isEqualTo(25075);
        assertThat(currentBalance(FakePlaid.card(accessToken))).isEqualTo(52010);
        JsonNode checking = body(sam.get("/api/banks")).get(0).get("accounts").get(0);
        assertThat(checking.get("name").asString()).isEqualTo("Plaid Checking");
        assertThat(checking.get("currentBalanceCents").asLong()).isEqualTo(25075);
    }

    @Test
    void twoSyncsOfOneBankAtOnceDontSaveAnythingTwice() {
        plaid.add(accessToken, purchases(4));
        UUID samId = jdbc.queryForObject("select id from app_user", UUID.class);
        AtomicReference<SyncService.SyncResult> other = new AtomicReference<>();
        plaid.beforeNextPage(() -> other.set(syncs.sync(samId, UUID.fromString(bankId)))); // runs mid-download

        JsonNode first = sync();

        assertThat(other.get().added()).isEqualTo(4);
        assertThat(first.get("added").asInt()).isZero(); // the other sync already saved them,
        assertThat(first.get("updated").asInt()).isZero(); // so this one saved nothing at all
        assertThat(transactionCount()).isEqualTo(4);
    }

    @Test
    void untilPlaidIsReadyNothingIsSaved() {
        plaid.notReadyYet(accessToken);

        JsonNode result = sync();

        assertThat(result.get("ready").asBoolean()).isFalse();
        assertThat(jdbc.queryForMap("select sync_cursor, last_synced_at from plaid_item"))
            .containsEntry("sync_cursor", null)
            .containsEntry("last_synced_at", null);
    }

    @Test
    void transactionsFromAccountsLedgerSyncDoesntHaveAreSkipped() {
        plaid.add(accessToken,
            FakePlaid.purchase(FakePlaid.checking(accessToken), "t1", 1000, "Bookstore"),
            FakePlaid.purchase("an-account-sam-didnt-share", "t2", 9900, "Electronics"));

        assertThat(sync().get("added").asInt()).isEqualTo(1);
        assertThat(transactionCount()).isEqualTo(1);
    }

    @Test
    void usersCanOnlySyncTheirOwnBanks() {
        Browser alex = new Browser(port);
        alex.post("/api/auth/register", """
            {"name": "Alex Kim", "email": "alex@example.com", "password": "correct horse battery"}
            """);

        assertThat(alex.post("/api/banks/" + bankId + "/sync", "").status()).isEqualTo(404);
        assertThat(plaid.requestedCursors).isEmpty();
    }

    @Test
    void disconnectingABankDeletesItsTransactions() {
        plaid.add(accessToken, purchases(2));
        sync();

        sam.delete("/api/banks/" + bankId);

        assertThat(transactionCount()).isZero();
    }

    private JsonNode sync() {
        Browser.Response response = sam.post("/api/banks/" + bankId + "/sync", "");
        assertThat(response.status()).isEqualTo(200);
        return body(response);
    }

    /** Purchases t1, t2, … on the checking account. */
    private PlaidTransaction[] purchases(int count) {
        String checking = FakePlaid.checking(accessToken);
        return IntStream.rangeClosed(1, count)
            .mapToObj(n -> FakePlaid.purchase(checking, "t" + n, 100L * n, "Purchase " + n))
            .toArray(PlaidTransaction[]::new);
    }

    private int transactionCount() {
        return jdbc.queryForObject("select count(*) from bank_transaction", Integer.class);
    }

    private long currentBalance(String plaidAccountId) {
        return jdbc.queryForObject("select current_balance_cents from financial_account where plaid_account_id = ?",
            Long.class, plaidAccountId);
    }

    private JsonNode body(Browser.Response response) {
        return json.readTree(response.body());
    }
}
