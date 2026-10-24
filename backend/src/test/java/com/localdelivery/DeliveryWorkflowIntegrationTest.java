package com.localdelivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.localdelivery.model.RequestCategory;
import com.localdelivery.service.DemoUserService;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP and committed PostgreSQL transactions; no mocked service or test transaction. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeliveryWorkflowIntegrationTest {
    private static final PostgresTestDatabase DATABASE = new PostgresTestDatabase();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::url);
        registry.add("spring.datasource.username", () -> DATABASE.username);
        registry.add("spring.datasource.password", () -> DATABASE.password);
        registry.add("app.seed-demo-users", () -> false);
    }

    @AfterAll
    static void closeResources() {
        HTTP.close();
        DATABASE.close();
    }

    private final int port;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final DemoUserService demoUsers;

    @Autowired
    DeliveryWorkflowIntegrationTest(@LocalServerPort int port, ObjectMapper json,
                                    JdbcTemplate jdbc, DemoUserService demoUsers) {
        this.port = port;
        this.json = json;
        this.jdbc = jdbc;
        this.demoUsers = demoUsers;
    }

    @BeforeEach
    void seedIsolatedDatabase() {
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo(DATABASE.schema);
        jdbc.execute("TRUNCATE delivery_requests, users RESTART IDENTITY CASCADE");
        demoUsers.seed();
    }

    @ParameterizedTest
    @EnumSource(RequestCategory.class)
    void customerToHelperToDeliveredAcrossEveryView(RequestCategory category) throws Exception {
        // Controls catch a listing that accidentally removes or exposes unrelated requests.
        JsonNode waiting = create(2, RequestCategory.PACKAGE);
        JsonNode otherAssigned = accept(create(2, RequestCategory.PICKUP).get("id").asLong(), 1, 200);
        var controlsBefore = jdbc.queryForList("SELECT * FROM delivery_requests ORDER BY id");
        var usersBefore = jdbc.queryForList("SELECT * FROM users ORDER BY id");

        JsonNode created = create(1, category);
        long id = created.get("id").asLong();
        assertThat(created.get("category").asText()).isEqualTo(category.name());
        assertThat(created.get("status").asText()).isEqualTo("OPEN");
        assertThat(created.get("helperId").isNull()).isTrue();
        assertThat(created.get("helperReward").asText()).isEqualTo("80.50");
        if (category == RequestCategory.BUY) {
            assertThat(created.get("shoppingBudget").asText()).isEqualTo("325.75");
        } else {
            assertThat(created.get("shoppingBudget").isNull()).isTrue();
        }
        assertAllViews(created, waiting, otherAssigned);
        assertStored(created, 0);

        JsonNode current = accept(id, 2, 200);
        assertThat(current.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(current.get("helperId").asLong()).isEqualTo(2);
        assertStableFields(created, current);
        assertAllViews(current, waiting, otherAssigned);
        assertStored(current, 1);

        long version = 1;
        for (String next : new String[]{"PICKED_UP", "DELIVERING", "DELIVERED"}) {
            JsonNode updated = update(id, next, 200);
            assertThat(updated.get("status").asText()).isEqualTo(next);
            assertThat(updated.get("helperId").asLong()).isEqualTo(2);
            assertThat(OffsetDateTime.parse(updated.get("updatedAt").asText()))
                    .isAfterOrEqualTo(OffsetDateTime.parse(current.get("updatedAt").asText()));
            assertStableFields(created, updated);
            assertAllViews(updated, waiting, otherAssigned);
            assertStored(updated, ++version);
            current = updated;
        }

        // Fresh reads after completion must preserve the committed result and every control row.
        var completed = jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id);
        assertAllViews(current, waiting, otherAssigned);
        assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id)).isEqualTo(completed);
        assertThat(jdbc.queryForList("SELECT * FROM delivery_requests WHERE id <> ? ORDER BY id", id))
                .isEqualTo(controlsBefore);
        assertThat(jdbc.queryForList("SELECT * FROM users ORDER BY id")).isEqualTo(usersBefore);
    }

    @Test
    void rejectedActionsDoNotCorruptTheJourneyOrItsTrackingViews() throws Exception {
        JsonNode waiting = create(2, RequestCategory.PACKAGE);
        JsonNode otherAssigned = accept(create(2, RequestCategory.PICKUP).get("id").asLong(), 1, 200);
        JsonNode current = create(1, RequestCategory.BUY);
        long id = current.get("id").asLong();

        // A client cannot bypass helper assignment via a status PATCH.
        var before = jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id);
        assertConflict(update(id, "ACCEPTED", 409));
        assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id)).isEqualTo(before);
        assertAllViews(current, waiting, otherAssigned);

        current = accept(id, 2, 200);
        before = jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id);
        assertConflict(accept(id, 2, 409));
        assertConflict(update(id, "DELIVERED", 409));
        assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id)).isEqualTo(before);
        assertAllViews(current, waiting, otherAssigned);

        // Recover using the valid next step, then exercise the stale tab at each stage.
        for (String next : new String[]{"PICKED_UP", "DELIVERING", "DELIVERED"}) {
            current = update(id, next, 200);
            before = jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id);
            assertConflict(update(id, next, 409));
            assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id)).isEqualTo(before);
            assertAllViews(current, waiting, otherAssigned);
        }
        assertConflict(update(id, "PICKED_UP", 409));
        assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id)).isEqualTo(before);
        assertAllViews(current, waiting, otherAssigned);
        assertStored(current, 4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM delivery_requests", Long.class)).isEqualTo(3L);
    }

    private void assertAllViews(JsonNode current, JsonNode waiting, JsonNode otherAssigned) throws Exception {
        long id = current.get("id").asLong();
        boolean open = current.get("status").asText().equals("OPEN");
        assertThat(get("/api/requests/" + id)).isEqualTo(current);
        assertThat(get("/api/requests")).isEqualTo(open
                ? json.createArrayNode().add(current).add(waiting) : json.createArrayNode().add(waiting));
        assertThat(get("/api/users/1/requests")).isEqualTo(json.createArrayNode().add(current));
        assertThat(get("/api/users/2/requests")).isEqualTo(json.createArrayNode().add(otherAssigned).add(waiting));
        assertThat(get("/api/users/2/deliveries")).isEqualTo(open
                ? json.createArrayNode() : json.createArrayNode().add(current));
        assertThat(get("/api/users/1/deliveries")).isEqualTo(json.createArrayNode().add(otherAssigned));
    }

    private void assertStableFields(JsonNode created, JsonNode current) {
        for (String field : new String[]{"id", "customerId", "category", "title", "description", "pickupAddress",
                "deliveryAddress", "shoppingBudget", "helperReward", "deadline", "createdAt"}) {
            assertThat(current.get(field)).as(field).isEqualTo(created.get(field));
        }
    }

    private void assertStored(JsonNode current, long version) {
        var row = jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", current.get("id").asLong());
        assertThat(row.get("customer_id")).isEqualTo(1L);
        assertThat(row.get("helper_id")).isEqualTo(current.get("helperId").isNull() ? null : 2L);
        assertThat(row.get("status").toString()).isEqualTo(current.get("status").asText());
        assertThat(row.get("helper_reward")).isEqualTo(new BigDecimal("80.50"));
        assertThat(row.get("version")).isEqualTo(version);
    }

    private void assertConflict(JsonNode error) {
        assertThat(error.get("status").asInt()).isEqualTo(409);
        assertThat(error.get("error").asText()).isEqualTo("Conflict");
        assertThat(error.get("message").asText()).isNotBlank();
        assertThat(error.hasNonNull("timestamp")).isTrue();
    }

    private JsonNode create(long customerId, RequestCategory category) throws Exception {
        ObjectNode body = json.createObjectNode().put("customerId", customerId).put("category", category.name())
                .put("title", "Stage 18 " + category + " workflow").put("description", "Isolated integration test")
                .put("pickupAddress", "Demo shop, Oslo").put("deliveryAddress", "Demo home, Oslo")
                .put("helperReward", new BigDecimal("80.50"))
                .put("deadline", OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).withNano(0).toString());
        if (category == RequestCategory.BUY) body.put("shoppingBudget", new BigDecimal("325.75"));
        else body.putNull("shoppingBudget");
        return request("POST", "/api/requests", body, 201);
    }

    private JsonNode accept(long id, long helperId, int expectedStatus) throws Exception {
        return request("POST", "/api/requests/" + id + "/accept",
                json.createObjectNode().put("helperId", helperId), expectedStatus);
    }

    private JsonNode update(long id, String next, int expectedStatus) throws Exception {
        return request("PATCH", "/api/requests/" + id + "/status",
                json.createObjectNode().put("status", next), expectedStatus);
    }

    private JsonNode get(String path) throws Exception { return request("GET", path, null, 200); }

    private JsonNode request(String method, String path, JsonNode body, int expectedStatus) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s: %s", method, path, response.body()).isEqualTo(expectedStatus);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        return json.readTree(response.body());
    }
}
