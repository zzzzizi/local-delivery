package com.localdelivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.localdelivery.dto.CreateDeliveryRequestRequest;
import com.localdelivery.model.*;
import com.localdelivery.repository.*;
import com.localdelivery.service.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.DriverManager;
import java.time.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeliveryApiTest {
    private static final PostgresTestDatabase DATABASE = new PostgresTestDatabase();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::url);
        registry.add("spring.datasource.username", () -> DATABASE.username);
        registry.add("spring.datasource.password", () -> DATABASE.password);
        registry.add("app.seed-demo-users", () -> false);
    }

    @AfterAll
    static void removeSchema() { DATABASE.close(); }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired DeliveryRequestRepository requests;
    @Autowired DemoUserService demoUsers;
    @Autowired DeliveryRequestService service;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetTestData() {
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo(DATABASE.schema);
        jdbc.execute("TRUNCATE delivery_requests, users RESTART IDENTITY CASCADE");
        demoUsers.seed();
    }

    private ObjectNode payload() {
        ObjectNode body = json.createObjectNode();
        body.put("customerId", 1);
        body.put("category", "BUY");
        body.put("title", "Buy groceries");
        body.put("description", "Please buy milk and bread.");
        body.put("pickupAddress", "KIWI Majorstuen, Oslo");
        body.put("deliveryAddress", "Frogner, Oslo");
        body.put("shoppingBudget", new BigDecimal("300.10"));
        body.put("helperReward", new BigDecimal("80.25"));
        body.put("deadline", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).withNano(0).toString());
        return body;
    }

    private JsonNode create(ObjectNode body) throws Exception {
        String response = mvc.perform(post("/api/requests").contentType("application/json").content(body.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    @Test
    void healthEndpoint() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(content().json("{\"status\":\"ok\"}"));
    }

    @Test
    void emptyList() throws Exception {
        mvc.perform(get("/api/requests")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @ParameterizedTest
    @EnumSource(RequestCategory.class)
    void createListAndRetrieveAllCategories(RequestCategory category) throws Exception {
        ObjectNode body = payload().put("category", category.name());
        JsonNode created = create(body);
        long id = created.get("id").asLong();
        assertThat(created.get("customerId").asLong()).isEqualTo(1);
        assertThat(created.get("helperId").isNull()).isTrue();
        assertThat(created.get("status").asText()).isEqualTo("OPEN");
        assertThat(created.get("category").asText()).isEqualTo(category.name());
        assertThat(created.has("customer_id")).isFalse();
        for (String field : new String[]{"title", "description", "pickupAddress", "deliveryAddress"}) {
            assertThat(created.get(field)).isEqualTo(body.get(field));
        }
        assertThat(new BigDecimal(created.get("helperReward").asText())).isEqualByComparingTo("80.25");
        assertThat(new BigDecimal(created.get("shoppingBudget").asText())).isEqualByComparingTo("300.10");
        assertThat(OffsetDateTime.parse(created.get("deadline").asText()).toInstant())
                .isEqualTo(OffsetDateTime.parse(body.get("deadline").asText()).toInstant());
        assertThat(created.size()).isEqualTo(14);
        assertThat(created.get("createdAt").asText()).endsWith("Z");
        assertThat(created.get("updatedAt").asText()).endsWith("Z");

        mvc.perform(get("/api/requests/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.status").value("OPEN"));
        mvc.perform(get("/api/requests")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));

        // Independent JDBC connection confirms an actual commit, not a test transaction.
        try (var connection = DriverManager.getConnection(DATABASE.url(), DATABASE.username, DATABASE.password);
             var statement = connection.prepareStatement("SELECT title, helper_reward, deadline FROM delivery_requests WHERE id = ?")) {
            statement.setLong(1, id);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("title")).isEqualTo("Buy groceries");
                assertThat(result.getBigDecimal("helper_reward")).isEqualByComparingTo("80.25");
                assertThat(result.getObject("deadline", LocalDateTime.class))
                        .isEqualTo(OffsetDateTime.parse(body.get("deadline").asText()).toLocalDateTime());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "80", "80.2"})
    void creationResponseMatchesCommittedRecord(String amount) throws Exception {
        OffsetDateTime deadline = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30))
                .plusDays(1).withNano(123456789);
        ObjectNode body = payload().put("shoppingBudget", new BigDecimal(amount))
                .put("helperReward", new BigDecimal(amount)).put("deadline", deadline.toString());
        JsonNode created = create(body);
        long id = created.get("id").asLong();
        JsonNode retrieved = json.readTree(mvc.perform(get("/api/requests/" + id))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(created).isEqualTo(retrieved);

        // Verify the POST represents the actual committed values, including PostgreSQL precision.
        try (var connection = DriverManager.getConnection(DATABASE.url(), DATABASE.username, DATABASE.password);
             var statement = connection.prepareStatement(
                     "SELECT shopping_budget, helper_reward, deadline, created_at, updated_at FROM delivery_requests WHERE id = ?")) {
            statement.setLong(1, id);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(created.get("shoppingBudget").asText()).isEqualTo(result.getBigDecimal("shopping_budget").toPlainString());
                assertThat(created.get("helperReward").asText()).isEqualTo(result.getBigDecimal("helper_reward").toPlainString());
                assertThat(OffsetDateTime.parse(created.get("deadline").asText()).toLocalDateTime())
                        .isEqualTo(result.getObject("deadline", LocalDateTime.class));
                assertThat(OffsetDateTime.parse(created.get("createdAt").asText()).toLocalDateTime())
                        .isEqualTo(result.getObject("created_at", LocalDateTime.class));
                assertThat(OffsetDateTime.parse(created.get("updatedAt").asText()).toLocalDateTime())
                        .isEqualTo(result.getObject("updated_at", LocalDateTime.class));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"customerId", "category", "title", "description", "pickupAddress",
            "deliveryAddress", "helperReward", "deadline"})
    void requiredFields(String field) throws Exception {
        ObjectNode body = payload();
        body.remove(field);
        mvc.perform(post("/api/requests").contentType("application/json").content(body.toString()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fields." + field).exists());
        assertThat(requests.count()).isZero();
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of("category", "MEDICINE"), Arguments.of("category", "buy"),
                Arguments.of("category", 0), Arguments.of("category", "0"),
                Arguments.of("helperReward", -1), Arguments.of("helperReward", "not a number"),
                Arguments.of("helperReward", "NaN"), Arguments.of("helperReward", "Infinity"),
                Arguments.of("helperReward", new BigDecimal("12.345")),
                Arguments.of("helperReward", new BigDecimal("100000000.00")),
                Arguments.of("helperReward", null), Arguments.of("shoppingBudget", -1),
                Arguments.of("shoppingBudget", new BigDecimal("12.345")),
                Arguments.of("shoppingBudget", new BigDecimal("100000000.00")),
                Arguments.of("title", "   "), Arguments.of("title", "x".repeat(201)),
                Arguments.of("description", "\n\t"), Arguments.of("pickupAddress", ""),
                Arguments.of("deliveryAddress", "x".repeat(501)), Arguments.of("customerId", 0),
                Arguments.of("customerId", -1), Arguments.of("customerId", true),
                Arguments.of("customerId", 1.5), Arguments.of("customerId", "1"),
                Arguments.of("customerId", new BigInteger("9223372036854775808")),
                Arguments.of("deadline", "invalid date"), Arguments.of("deadline", "2000-01-01T12:00:00+01:00"),
                Arguments.of("deadline", 123), Arguments.of("deadline", "2027-03-28T02:30:00"),
                Arguments.of("deadline", "2027-10-31T02:30:00"));
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void invalidInputDoesNotSave(String field, Object value) throws Exception {
        ObjectNode body = payload();
        body.set(field, json.valueToTree(value));
        mvc.perform(post("/api/requests").contentType("application/json").content(body.toString()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.status").value(422));
        assertThat(requests.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "helperId", "status"})
    void cannotSetServerOwnedFields(String field) throws Exception {
        ObjectNode body = payload().put(field, "1");
        mvc.perform(post("/api/requests").contentType("application/json").content(body.toString()))
                .andExpect(status().isUnprocessableEntity());
        assertThat(requests.count()).isZero();
    }

    @Test
    void optionalBudgetAndZeroReward() throws Exception {
        ObjectNode body = payload().put("helperReward", 0).put("title", "  Buy groceries  ");
        body.remove("shoppingBudget");
        JsonNode result = create(body);
        assertThat(result.get("shoppingBudget").isNull()).isTrue();
        assertThat(result.get("title").asText()).isEqualTo("Buy groceries");
        assertThat(new BigDecimal(result.get("helperReward").asText())).isZero();
    }

    @Test
    void naiveDeadlineUsesOsloTime() throws Exception {
        LocalDateTime local = LocalDate.now(ZoneId.of("Europe/Oslo")).plusDays(2).atTime(18, 0);
        JsonNode response = create(payload().put("deadline", local.toString()));
        assertThat(OffsetDateTime.parse(response.get("deadline").asText()).toInstant())
                .isEqualTo(local.atZone(ZoneId.of("Europe/Oslo")).toInstant());
    }

    @Test
    void explicitOffsetPreservesInstant() throws Exception {
        OffsetDateTime input = OffsetDateTime.now(ZoneOffset.ofHours(5)).plusDays(2).withNano(0);
        JsonNode response = create(payload().put("deadline", input.toString()));
        assertThat(OffsetDateTime.parse(response.get("deadline").asText()).toInstant()).isEqualTo(input.toInstant());
        JsonNode retrieved = json.readTree(mvc.perform(get("/api/requests/" + response.get("id").asLong()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(OffsetDateTime.parse(retrieved.get("deadline").asText()).toInstant()).isEqualTo(input.toInstant());
        assertThat(jdbc.queryForObject("SELECT deadline FROM delivery_requests WHERE id = ?",
                LocalDateTime.class, response.get("id").asLong()))
                .isEqualTo(input.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
    }

    @Test
    void unknownCustomer() throws Exception {
        mvc.perform(post("/api/requests").contentType("application/json").content(payload().put("customerId", 9999).toString()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Customer not found."));
        assertThat(requests.count()).isZero();
    }

    @Test
    void unknownRequest() throws Exception {
        mvc.perform(get("/api/requests/9223372036854775807"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Delivery request not found."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "9223372036854775808"})
    void invalidPathId(String id) throws Exception {
        mvc.perform(get("/api/requests/" + id)).andExpect(status().isUnprocessableEntity());
    }

    @ParameterizedTest
    @EnumSource(DeliveryStatus.class)
    void everyStatusCanBeRetrievedButOnlyOpenIsListed(DeliveryStatus status) throws Exception {
        long id = create(payload()).get("id").asLong();
        jdbc.update("UPDATE delivery_requests SET status = ?::request_status, helper_id = ? WHERE id = ?",
                status.name(), status == DeliveryStatus.OPEN || status == DeliveryStatus.CANCELLED ? null : 2L, id);
        mvc.perform(get("/api/requests/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status.name()));
        mvc.perform(get("/api/requests")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(status == DeliveryStatus.OPEN ? 1 : 0));
    }

    @Test
    void deterministicNewestFirstOrder() throws Exception {
        long first = create(payload()).get("id").asLong();
        long older = create(payload()).get("id").asLong();
        long tied = create(payload()).get("id").asLong();
        jdbc.update("UPDATE delivery_requests SET created_at = TIMESTAMP '2026-01-02 12:00:00'");
        jdbc.update("UPDATE delivery_requests SET created_at = TIMESTAMP '2026-01-01 12:00:00' WHERE id = ?", older);
        mvc.perform(get("/api/requests")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(tied)).andExpect(jsonPath("$[1].id").value(first))
                .andExpect(jsonPath("$[2].id").value(older));
    }

    @Test
    void userRepositoryPersistsFields() {
        User user = new User("New User", "new@example.com");
        user.setPhone("+4700000000");
        user.setRating(new BigDecimal("4.50"));
        long id = users.saveAndFlush(user).getId();
        User saved = users.findById(id).orElseThrow();
        assertThat(id).isGreaterThan(2);
        assertThat(saved.getName()).isEqualTo("New User");
        assertThat(saved.getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getPhone()).isEqualTo("+4700000000");
        assertThat(saved.getRating()).isEqualByComparingTo("4.50");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void customerAndHelperRelationshipsAndTimestamps() throws Exception {
        long id = create(payload()).get("id").asLong();
        var original = requests.findById(id).orElseThrow();
        var originalUpdated = original.getUpdatedAt();
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            var request = requests.findById(id).orElseThrow();
            assertThat(request.getCustomer().getEmail()).isEqualTo("customer@example.com");
            assertThat(request.getHelper()).isNull();
            request.setHelper(users.findById(2L).orElseThrow());
            request.setTitle("Updated title");
            requests.flush();
        });
        var updated = requests.findById(id).orElseThrow();
        assertThat(updated.getHelper().getId()).isEqualTo(2L);
        assertThat(updated.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(updated.getUpdatedAt()).isAfter(originalUpdated);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "INSERT INTO users(name, email) VALUES ('Duplicate', 'customer@example.com')",
            "UPDATE users SET rating = -0.01 WHERE id = 1",
            "UPDATE users SET rating = 5.01 WHERE id = 1",
            "UPDATE delivery_requests SET helper_reward = -1",
            "UPDATE delivery_requests SET shopping_budget = -1",
            "UPDATE delivery_requests SET customer_id = 99999",
            "UPDATE delivery_requests SET helper_id = 99999",
            "UPDATE delivery_requests SET category = 'INVALID'",
            "UPDATE delivery_requests SET status = 'INVALID'"
    })
    void databaseConstraintsRemainEnforced(String sql) throws Exception {
        create(payload());
        assertThatThrownBy(() -> jdbc.execute(sql)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void failedServiceTransactionRollsBack() {
        var input = new CreateDeliveryRequestRequest(1L, RequestCategory.PACKAGE, "Parcel", "A parcel",
                "Majorstuen", "Frogner", null, new BigDecimal("-1"), OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
        assertThatThrownBy(() -> service.create(input)).isInstanceOf(DataAccessException.class);
        assertThat(requests.count()).isZero();
    }

    @Test
    void seedIsRepeatableAndSequenceAdvances() {
        demoUsers.seed();
        demoUsers.seed();
        assertThat(users.count()).isEqualTo(2);
        assertThat(users.findById(1L).orElseThrow().getName()).isEqualTo("Customer Test");
        assertThat(users.findById(2L).orElseThrow().getName()).isEqualTo("Helper Test");
        assertThat(users.saveAndFlush(new User("Third", "third@example.com")).getId()).isGreaterThan(2);
    }

    @Test
    void seedNeverOverwritesAnExistingUser() {
        jdbc.update("UPDATE users SET email = 'existing@example.com' WHERE id = 1");
        assertThatThrownBy(demoUsers::seed).isInstanceOf(IllegalStateException.class);
        assertThat(users.findById(1L).orElseThrow().getEmail()).isEqualTo("existing@example.com");
    }

    @Test
    void openApiDocumentsOnlyImplementedOperations() throws Exception {
        mvc.perform(get("/api/openapi.json")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/requests'].post").exists())
                .andExpect(jsonPath("$.paths['/api/requests'].get").exists())
                .andExpect(jsonPath("$.paths['/api/requests/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/requests/{id}/accept']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/requests/{id}/status']").doesNotExist())
                .andExpect(jsonPath("$.paths['/test/dto/accept']").doesNotExist())
                .andExpect(jsonPath("$.paths['/test/dto/status']").doesNotExist());
    }
}
