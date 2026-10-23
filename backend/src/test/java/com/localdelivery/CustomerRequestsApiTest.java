package com.localdelivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.localdelivery.model.DeliveryStatus;
import com.localdelivery.model.RequestCategory;
import com.localdelivery.service.DemoUserService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CustomerRequestsApiTest {
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
    @Autowired DemoUserService demoUsers;

    @BeforeEach
    void resetTestData() {
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo(DATABASE.schema);
        jdbc.execute("TRUNCATE delivery_requests, users RESTART IDENTITY CASCADE");
        demoUsers.seed();
    }

    private JsonNode create(long customerId, RequestCategory category) throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("customerId", customerId);
        body.put("category", category.name());
        body.put("title", "Customer " + customerId + " request");
        body.put("description", "Tracking test");
        body.put("pickupAddress", "Demo shop, Oslo");
        body.put("deliveryAddress", "Demo home, Oslo");
        body.putNull("shoppingBudget");
        body.put("helperReward", 0);
        body.put("deadline", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).withNano(0).toString());
        return json.readTree(mvc.perform(post("/api/requests").contentType("application/json").content(body.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode list(long customerId) throws Exception {
        return json.readTree(mvc.perform(get("/api/users/" + customerId + "/requests"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void existingCustomerWithoutRequestsReturnsEmptyArray() throws Exception {
        create(2, RequestCategory.BUY);
        assertThat(list(1)).isEqualTo(json.createArrayNode());
    }

    @Test
    void missingCustomerReturnsConsistentNotFound() throws Exception {
        mvc.perform(get("/api/users/9223372036854775807/requests"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Customer not found."))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.fields").isMap());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "9223372036854775808"})
    void invalidCustomerIdReturnsValidationError(String id) throws Exception {
        mvc.perform(get("/api/users/" + id + "/requests"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").isString());
    }

    @ParameterizedTest
    @EnumSource(RequestCategory.class)
    void returnsCompleteDtoForEachCategory(RequestCategory category) throws Exception {
        JsonNode created = create(1, category);
        JsonNode result = list(1);
        assertThat(result).isEqualTo(json.createArrayNode().add(created));
        assertThat(result.get(0).size()).isEqualTo(14);
        assertThat(result.get(0).get("shoppingBudget").isNull()).isTrue();
        assertThat(result.get(0).get("helperReward").asText()).isEqualTo("0.00");
        assertThat(result.get(0).get("helperId").isNull()).isTrue();
    }

    @Test
    void includesEveryStatusAndOnlyRequestsCreatedByCustomer() throws Exception {
        var expected = json.createArrayNode();
        for (DeliveryStatus state : DeliveryStatus.values()) {
            long ownId = create(1, RequestCategory.BUY).get("id").asLong();
            long otherId = create(2, RequestCategory.PACKAGE).get("id").asLong();
            Long ownHelper = state == DeliveryStatus.OPEN ? null : 2L;
            Long otherHelper = state == DeliveryStatus.OPEN ? null : 1L;
            jdbc.update("UPDATE delivery_requests SET status = ?::request_status, helper_id = ? WHERE id = ?",
                    state.name(), ownHelper, ownId);
            jdbc.update("UPDATE delivery_requests SET status = ?::request_status, helper_id = ? WHERE id = ?",
                    state.name(), otherHelper, otherId);
            JsonNode details = json.readTree(mvc.perform(get("/api/requests/" + ownId))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            expected.insert(0, details);
        }
        var requestsBefore = jdbc.queryForList("SELECT * FROM delivery_requests ORDER BY id");
        var usersBefore = jdbc.queryForList("SELECT * FROM users ORDER BY id");
        assertThat(list(1)).isEqualTo(expected);
        assertThat(list(1)).isEqualTo(expected);
        JsonNode other = list(2);
        assertThat(other.size()).isEqualTo(DeliveryStatus.values().length);
        other.forEach(item -> assertThat(item.get("customerId").asLong()).isEqualTo(2));
        assertThat(jdbc.queryForList("SELECT * FROM delivery_requests ORDER BY id")).isEqualTo(requestsBefore);
        assertThat(jdbc.queryForList("SELECT * FROM users ORDER BY id")).isEqualTo(usersBefore);
    }

    @Test
    void newestFirstUsesCreationTimeThenIdForTies() throws Exception {
        long first = create(1, RequestCategory.BUY).get("id").asLong();
        long older = create(1, RequestCategory.BUY).get("id").asLong();
        long tied = create(1, RequestCategory.BUY).get("id").asLong();
        create(2, RequestCategory.BUY);
        jdbc.update("UPDATE delivery_requests SET created_at = TIMESTAMP '2026-01-02 12:00:00'");
        jdbc.update("UPDATE delivery_requests SET created_at = TIMESTAMP '2026-01-01 12:00:00' WHERE id = ?", older);
        mvc.perform(get("/api/users/1/requests")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(tied))
                .andExpect(jsonPath("$[1].id").value(first))
                .andExpect(jsonPath("$[2].id").value(older));
    }

    @Test
    void trackingReflectsCommittedAcceptanceAndDeliveryUpdates() throws Exception {
        long id = create(1, RequestCategory.PICKUP).get("id").asLong();
        assertThat(list(1).get(0).get("status").asText()).isEqualTo("OPEN");
        JsonNode accepted = json.readTree(mvc.perform(post("/api/requests/" + id + "/accept")
                .contentType("application/json").content("{\"helperId\":2}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(list(1).get(0)).isEqualTo(accepted);
        assertThat(list(2)).isEmpty();
        for (String state : new String[]{"PICKED_UP", "DELIVERING", "DELIVERED"}) {
            JsonNode updated = json.readTree(mvc.perform(patch("/api/requests/" + id + "/status")
                    .contentType("application/json").content("{\"status\":\"" + state + "\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(list(1).get(0)).isEqualTo(updated);
        }
    }
}
