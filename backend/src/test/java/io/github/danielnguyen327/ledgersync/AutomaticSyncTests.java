package io.github.danielnguyen327.ledgersync;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.danielnguyen327.ledgersync.bank.PlaidGateway.PlaidTransaction;
import io.github.danielnguyen327.ledgersync.bank.SyncService;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The schedule, "sync all", the record of each sync, banks that need a new sign-in, and daily balances. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, FakePlaid.Config.class})
class AutomaticSyncTests {

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
    private String platypus;
    private String platypusToken;
    private String tartan;
    private String tartanToken;

    @BeforeEach
    void samConnectsTwoBanks() {
        jdbc.update("delete from spring_session");
        jdbc.update("delete from app_user");
        plaid.reset();
        sam = signedUp("sam@example.com");
        platypus = connect(sam, "public-platypus");
        platypusToken = plaid.issuedAccessTokens.getLast();
        tartan = connect(sam, "public-tartan");
        tartanToken = plaid.issuedAccessTokens.getLast();
    }

    @Test
    void everySyncIsRecorded() {
        plaid.add(platypusToken, purchase(platypusToken, "p1"), purchase(platypusToken, "p2"));

        sam.post("/api/banks/" + platypus + "/sync", "");

        assertThat(jdbc.queryForMap("select status, triggered_by, added_count from sync_run"))
            .containsEntry("status", "succeeded")
            .containsEntry("triggered_by", "manual")
            .containsEntry("added_count", 2);
        JsonNode recent = bank(platypus).get("recentSyncs");
        assertThat(recent.size()).isEqualTo(1);
        assertThat(recent.get(0).get("added").asInt()).isEqualTo(2);
        assertThat(recent.get(0).get("error").isNull()).isTrue();
    }

    @Test
    void aFailedSyncIsRecordedWithWhatWentWrong() {
        plaid.failSyncs(platypusToken, "INTERNAL_SERVER_ERROR");

        assertThat(sam.post("/api/banks/" + platypus + "/sync", "").status()).isEqualTo(502);

        assertThat(jdbc.queryForMap("select status, error_code from sync_run"))
            .containsEntry("status", "failed")
            .containsEntry("error_code", "INTERNAL_SERVER_ERROR");
        assertThat(bank(platypus).get("recentSyncs").get(0).get("error").asString())
            .isEqualTo("The sync didn't finish. The next one will try again.");
    }

    @Test
    void whenTheBankEndsTheConnectionTheUserIsAskedToSignInAgain() {
        plaid.requireLogin(platypusToken);

        Browser.Response response = sam.post("/api/banks/" + platypus + "/sync", "");

        assertThat(response.status()).isEqualTo(409);
        assertThat(body(response).get("detail").asString()).isEqualTo("First Platypus Bank needs you to sign in again.");
        assertThat(bank(platypus).get("status").asString()).isEqualTo("login_required");
        assertThat(bank(platypus).get("recentSyncs").get(0).get("error").asString())
            .isEqualTo("The bank needs you to sign in again.");

        plaid.stopFailing(platypusToken); // the user signed in to their bank again

        assertThat(sam.post("/api/banks/" + platypus + "/sync", "").status()).isEqualTo(200);
        assertThat(bank(platypus).get("status").asString()).isEqualTo("active");
    }

    @Test
    void syncAllSyncsEveryBankEvenWhenOneFails() {
        plaid.requireLogin(platypusToken);
        plaid.add(tartanToken, purchase(tartanToken, "t1"));

        JsonNode results = body(sam.post("/api/banks/sync", ""));

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get(0).get("bankName").asString()).isEqualTo("First Platypus Bank");
        assertThat(results.get(0).get("error").asString()).isEqualTo("First Platypus Bank needs you to sign in again.");
        assertThat(results.get(1).get("bankName").asString()).isEqualTo("Tartan Bank");
        assertThat(results.get(1).get("result").get("added").asInt()).isEqualTo(1);
        assertThat(results.get(1).get("error").isNull()).isTrue();
    }

    @Test
    void theScheduleSyncsEveryUsersBanksButSkipsOnesWaitingForASignIn() {
        Browser alex = signedUp("alex@example.com");
        connect(alex, "public-platypus");
        String alexToken = plaid.issuedAccessTokens.getLast();
        plaid.add(platypusToken, purchase(platypusToken, "p1"));
        plaid.add(alexToken, purchase(alexToken, "a1"));
        jdbc.update("update plaid_item set status = 'login_required' where id = ?", UUID.fromString(tartan));

        syncs.syncEveryBank();

        assertThat(jdbc.queryForObject("select count(*) from bank_transaction", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("select triggered_by from sync_run", String.class)).containsExactly("schedule", "schedule");
        assertThat(jdbc.queryForObject("select count(*) from sync_run where item_id = ?", Integer.class,
            UUID.fromString(tartan))).isZero();
    }

    @Test
    void oneBankFailingDoesntStopTheSchedule() {
        plaid.failSyncs(platypusToken, "INTERNAL_SERVER_ERROR");
        plaid.add(tartanToken, purchase(tartanToken, "t1"));

        syncs.syncEveryBank();

        assertThat(jdbc.queryForObject("select count(*) from bank_transaction", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from sync_run where item_id = ?", String.class,
            UUID.fromString(platypus))).isEqualTo("failed");
    }

    @Test
    void eachSyncKeepsOneBalancePerAccountPerDay() {
        sam.post("/api/banks/" + platypus + "/sync", "");
        plaid.changeBalance(FakePlaid.checking(platypusToken), 25075);

        sam.post("/api/banks/" + platypus + "/sync", ""); // the same day, so it replaces the first one

        var snapshots = jdbc.queryForList("""
            select s.snapshot_date, s.current_cents from balance_snapshot s
            join financial_account a on a.id = s.account_id
            where a.plaid_account_id = ?""", FakePlaid.checking(platypusToken));
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.getFirst())
            .containsEntry("snapshot_date", Date.valueOf(LocalDate.now(ZoneOffset.UTC)))
            .containsEntry("current_cents", 25075L);
    }

    @Test
    void aSyncThatFoundPlaidNotReadyLeavesNoRecord() {
        plaid.notReadyYet(platypusToken);

        sam.post("/api/banks/" + platypus + "/sync", "");

        assertThat(jdbc.queryForObject("select count(*) from sync_run", Integer.class)).isZero();
    }

    private Browser signedUp(String email) {
        Browser browser = new Browser(port);
        browser.post("/api/auth/register", """
            {"name": "Test User", "email": "%s", "password": "correct horse battery"}
            """.formatted(email));
        return browser;
    }

    private String connect(Browser browser, String publicToken) {
        return body(browser.post("/api/banks", """
            {"publicToken": "%s"}
            """.formatted(publicToken))).get("id").asString();
    }

    private static PlaidTransaction purchase(String accessToken, String id) {
        return FakePlaid.purchase(FakePlaid.checking(accessToken), id, 1250, "Coffee");
    }

    private JsonNode bank(String bankId) {
        for (JsonNode bank : body(sam.get("/api/banks"))) {
            if (bank.get("id").asString().equals(bankId)) {
                return bank;
            }
        }
        throw new AssertionError("bank not found: " + bankId);
    }

    private JsonNode body(Browser.Response response) {
        return json.readTree(response.body());
    }
}
