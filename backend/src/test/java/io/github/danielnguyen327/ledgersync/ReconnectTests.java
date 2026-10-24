package io.github.danielnguyen327.ledgersync;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reconnecting a bank that needs a new sign-in, and the Sandbox tool that makes one need it. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, FakePlaid.Config.class})
class ReconnectTests {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private FakePlaid plaid;

    private Browser sam;
    private String bankId;
    private String accessToken;

    @BeforeEach
    void samConnectsABank() {
        jdbc.update("delete from spring_session");
        jdbc.update("delete from app_user");
        plaid.reset();
        sam = signedUp("sam@example.com");
        bankId = body(sam.post("/api/banks", """
            {"publicToken": "public-platypus"}
            """)).get("id").asString();
        accessToken = plaid.issuedAccessTokens.getLast();
    }

    @Test
    void reconnectingReopensPlaidForThatBank() {
        Browser.Response response = sam.post("/api/banks/" + bankId + "/link-token", "");

        assertThat(response.status()).isEqualTo(200);
        assertThat(body(response).get("linkToken").asString()).startsWith("link-sandbox-update-");
        assertThat(response.body()).doesNotContain(accessToken);
        assertThat(plaid.reconnectedAccessTokens).containsExactly(accessToken);
    }

    @Test
    void aBankThatNeededANewSignInWorksAgainAfterReconnecting() {
        assertThat(sam.post("/api/sandbox/banks/" + bankId + "/expire-sign-in", "").status()).isEqualTo(204);
        assertThat(status()).isEqualTo("login_required");

        sam.post("/api/banks/" + bankId + "/link-token", ""); // Plaid's window opens here and the user signs in
        plaid.stopFailing(accessToken);
        Browser.Response sync = sam.post("/api/banks/" + bankId + "/sync", "");

        assertThat(sync.status()).isEqualTo(200);
        assertThat(status()).isEqualTo("active");
    }

    @Test
    void onlyYourOwnBanksCanBeReconnectedOrExpired() {
        Browser alex = signedUp("alex@example.com");

        assertThat(alex.post("/api/banks/" + bankId + "/link-token", "").status()).isEqualTo(404);
        assertThat(alex.post("/api/sandbox/banks/" + bankId + "/expire-sign-in", "").status()).isEqualTo(404);
        assertThat(plaid.reconnectedAccessTokens).isEmpty();
        assertThat(status()).isEqualTo("active");
    }

    private String status() {
        return body(sam.get("/api/banks")).get(0).get("status").asString();
    }

    private Browser signedUp(String email) {
        Browser browser = new Browser(port);
        browser.post("/api/auth/register", """
            {"name": "Test User", "email": "%s", "password": "correct horse battery"}
            """.formatted(email));
        return browser;
    }

    private JsonNode body(Browser.Response response) {
        return json.readTree(response.body());
    }
}
