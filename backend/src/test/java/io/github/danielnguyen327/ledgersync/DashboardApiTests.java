package io.github.danielnguyen327.ledgersync;

import static io.github.danielnguyen327.ledgersync.FakePlaid.spent;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.danielnguyen327.ledgersync.bank.PlaidGateway.PlaidTransaction;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The transactions list and spending by category. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, FakePlaid.Config.class})
class DashboardApiTests {

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

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
    private String checking;
    private String card;

    @BeforeEach
    void samConnectsABank() {
        jdbc.update("delete from spring_session");
        jdbc.update("delete from app_user");
        plaid.reset();
        sam = signedUp("sam@example.com");
        bankId = body(sam.post("/api/banks", """
            {"publicToken": "public-tartan"}
            """)).get("id").asString();
        String accessToken = plaid.issuedAccessTokens.getLast();
        checking = FakePlaid.checking(accessToken);
        card = FakePlaid.card(accessToken);
    }

    @Test
    void transactionsComeNewestFirstWithTheirCategoryAndAccount() {
        add(spent(checking, "Groceries", 4200, "FOOD_AND_DRINK", TODAY.minusDays(3)),
            spent(card, "Flight", 30000, "TRAVEL", TODAY));

        JsonNode page = body(sam.get("/api/transactions"));

        JsonNode newest = page.get("transactions").get(0);
        assertThat(newest.get("name").asString()).isEqualTo("Flight");
        assertThat(newest.get("category").asString()).isEqualTo("Travel");
        assertThat(newest.get("accountName").asString()).isEqualTo("Plaid Credit Card");
        assertThat(newest.get("accountType").asString()).isEqualTo("credit");
        assertThat(newest.get("amountCents").asLong()).isEqualTo(30000);
        assertThat(page.get("transactions").get(1).get("category").asString()).isEqualTo("Food & drink");
        assertThat(page.get("hasMore").asBoolean()).isFalse();
    }

    @Test
    void transactionsCanBeFilteredSearchedAndPaged() {
        add(spent(checking, "Coffee 1", 450, "FOOD_AND_DRINK", TODAY),
            spent(checking, "Coffee 2", 450, "FOOD_AND_DRINK", TODAY),
            spent(checking, "Coffee 3", 450, "FOOD_AND_DRINK", TODAY),
            spent(checking, "Coffee 4", 450, "FOOD_AND_DRINK", TODAY),
            spent(checking, "Coffee 5", 450, "FOOD_AND_DRINK", TODAY),
            spent(card, "Flight out", 30000, "TRAVEL", TODAY),
            spent(card, "Flight back", 30000, "TRAVEL", TODAY));

        assertThat(count("/api/transactions?accountType=credit")).isEqualTo(2);
        assertThat(count("/api/transactions?category=Travel")).isEqualTo(2);
        assertThat(count("/api/transactions?search=flight")).isEqualTo(2);
        assertThat(count("/api/transactions?search=100%25")).isZero(); // a % in the search isn't a wildcard
        JsonNode first = body(sam.get("/api/transactions?size=3"));
        assertThat(first.get("transactions").size()).isEqualTo(3);
        assertThat(first.get("hasMore").asBoolean()).isTrue();
        JsonNode last = body(sam.get("/api/transactions?size=3&page=2"));
        assertThat(last.get("transactions").size()).isEqualTo(1);
        assertThat(last.get("hasMore").asBoolean()).isFalse();
        assertThat(sam.get("/api/transactions?accountType=savings").status()).isEqualTo(400);
    }

    @Test
    void spendingIsTotalledByCategoryAndBankPlusCardEqualsTheTotal() {
        LocalDate sameDayLastMonth = TODAY.minusMonths(1).withDayOfMonth(1);
        add(spent(checking, "Groceries", 8000, "FOOD_AND_DRINK", TODAY),
            spent(card, "Dinner", 4500, "FOOD_AND_DRINK", TODAY),
            spent(card, "Flight", 30000, "TRAVEL", TODAY),
            spent(checking, "Paycheck", -250000, "INCOME", TODAY),
            spent(checking, "Card payment", 50000, "LOAN_PAYMENTS", TODAY),
            spent(card, "Payment received", -50000, "TRANSFER_IN", TODAY),
            spent(card, "Last month's dinner", 2000, "FOOD_AND_DRINK", sameDayLastMonth));

        JsonNode all = body(sam.get("/api/spending"));
        long bank = body(sam.get("/api/spending?accountType=depository")).get("totalCents").asLong();
        long cards = body(sam.get("/api/spending?accountType=credit")).get("totalCents").asLong();

        assertThat(all.get("totalCents").asLong()).isEqualTo(42500); // income and payments aren't spending
        assertThat(all.get("previousTotalCents").asLong()).isEqualTo(2000);
        assertThat(all.get("categories").get(0).get("category").asString()).isEqualTo("Travel");
        assertThat(all.get("categories").get(1).get("category").asString()).isEqualTo("Food & drink");
        assertThat(all.get("categories").get(1).get("cents").asLong()).isEqualTo(12500);
        assertThat(all.get("categories").get(1).get("previousCents").asLong()).isEqualTo(2000);
        assertThat(bank + cards).isEqualTo(all.get("totalCents").asLong()); // Day 6's "done when"
    }

    @Test
    void nobodyElseSeesYourMoney() {
        add(spent(card, "Flight", 30000, "TRAVEL", TODAY));
        Browser alex = signedUp("alex@example.com");

        assertThat(body(alex.get("/api/transactions")).get("transactions").size()).isZero();
        assertThat(body(alex.get("/api/spending")).get("totalCents").asLong()).isZero();
    }

    private void add(PlaidTransaction... transactions) {
        plaid.add(plaid.issuedAccessTokens.getLast(), transactions);
        assertThat(sam.post("/api/banks/" + bankId + "/sync", "").status()).isEqualTo(200);
    }

    private int count(String path) {
        return body(sam.get(path)).get("transactions").size();
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
