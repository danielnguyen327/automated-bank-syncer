package io.github.danielnguyen327.ledgersync;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

/** With real banks (Plaid's Production), the Sandbox tools must not exist. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "plaid.environment=production")
@Import({TestcontainersConfiguration.class, FakePlaid.Config.class})
class SandboxToolsOffInProductionTests {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper json;

    @Autowired
    private FakePlaid plaid;

    @Test
    void theExpireSignInToolAnswersNotFound() {
        Browser sam = new Browser(port);
        sam.post("/api/auth/register", """
            {"name": "Sam Rivera", "email": "sam@example.com", "password": "correct horse battery"}
            """);
        String bankId = json.readTree(sam.post("/api/banks", """
            {"publicToken": "public-platypus"}
            """).body()).get("id").asString();

        assertThat(sam.post("/api/sandbox/banks/" + bankId + "/expire-sign-in", "").status()).isEqualTo(404);
        assertThat(sam.post("/api/banks/" + bankId + "/sync", "").status()).isEqualTo(200); // still signed in
    }
}
