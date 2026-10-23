package com.localdelivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localdelivery.model.DeliveryStatus;
import com.localdelivery.model.RequestCategory;
import com.localdelivery.service.DemoUserService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.*;
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
class HelperDeliveriesApiTest {
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

    private long create(long customerId, RequestCategory category) throws Exception {
        var body = json.createObjectNode().put("customerId", customerId).put("category", category.name())
                .put("title", "Helper workflow test").put("description", "A demo delivery")
                .put("pickupAddress", "Demo shop, Oslo").put("deliveryAddress", "Demo home, Oslo")
                .putNull("shoppingBudget").put("helperReward", 0)
                .put("deadline", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).withNano(0).toString());
        return json.readTree(mvc.perform(post("/api/requests").contentType("application/json").content(body.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode accept(long id, long helperId) throws Exception {
        return json.readTree(mvc.perform(post("/api/requests/" + id + "/accept")
                .contentType("application/json").content("{\"helperId\":" + helperId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode list(long helperId) throws Exception {
        return json.readTree(mvc.perform(get("/api/users/" + helperId + "/deliveries"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void existingHelperWithNoAssignmentsReturnsEmptyEvenIfTheyCreatedRequests() throws Exception {
        create(2, RequestCategory.BUY);
        create(1, RequestCategory.BUY);
        assertThat(list(2)).isEqualTo(json.createArrayNode());
    }

    @Test
    void missingHelperReturnsConsistentNotFound() throws Exception {
        mvc.perform(get("/api/users/9223372036854775807/deliveries"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Helper not found."))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.timestamp").isString()).andExpect(jsonPath("$.fields").isMap());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "9223372036854775808"})
    void invalidHelperIdReturnsValidationError(String id) throws Exception {
        mvc.perform(get("/api/users/" + id + "/deliveries"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.status").value(422));
    }

    @ParameterizedTest
    @EnumSource(RequestCategory.class)
    void returnsCompleteAssignedDtoForEveryCategory(RequestCategory category) throws Exception {
        JsonNode accepted = accept(create(1, category), 2);
        assertThat(list(2)).isEqualTo(json.createArrayNode().add(accepted));
        assertThat(accepted.size()).isEqualTo(14);
        assertThat(accepted.get("helperId").asLong()).isEqualTo(2);
        assertThat(accepted.get("shoppingBudget").isNull()).isTrue();
        assertThat(accepted.get("helperReward").asText()).isEqualTo("0.00");
    }

    @Test
    void includesAssignedTerminalStatesButExcludesOtherHelpersAndUnassignedRequests() throws Exception {
        var expected = json.createArrayNode();
        for (DeliveryStatus state : DeliveryStatus.values()) {
            if (state == DeliveryStatus.OPEN) continue;
            long own = create(1, RequestCategory.BUY);
            long other = create(2, RequestCategory.PACKAGE);
            accept(own, 2);
            accept(other, 1);
            jdbc.update("UPDATE delivery_requests SET status = ?::request_status WHERE id IN (?, ?)", state.name(), own, other);
            JsonNode details = json.readTree(mvc.perform(get("/api/requests/" + own))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            expected.insert(0, details);
        }
        create(1, RequestCategory.BUY);
        long unassigned = create(2, RequestCategory.PICKUP);
        jdbc.update("UPDATE delivery_requests SET status = 'CANCELLED' WHERE id = ?", unassigned);
        var before = jdbc.queryForList("SELECT * FROM delivery_requests ORDER BY id");
        var usersBefore = jdbc.queryForList("SELECT * FROM users ORDER BY id");
        assertThat(list(2)).isEqualTo(expected);
        assertThat(list(2)).isEqualTo(expected);
        JsonNode other = list(1);
        assertThat(other.size()).isEqualTo(5);
        other.forEach(item -> assertThat(item.get("helperId").asLong()).isEqualTo(1));
        assertThat(jdbc.queryForList("SELECT * FROM delivery_requests ORDER BY id")).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM users ORDER BY id")).isEqualTo(usersBefore);
    }

    @Test
    void newestFirstUsesCreationTimeThenIdForTies() throws Exception {
        long first = create(1, RequestCategory.BUY), older = create(1, RequestCategory.BUY), tied = create(1, RequestCategory.BUY);
        accept(first, 2); accept(older, 2); accept(tied, 2);
        jdbc.update("UPDATE delivery_requests SET created_at = TIMESTAMP '2026-01-02 12:00:00'");
        jdbc.update("UPDATE delivery_requests SET created_at = TIMESTAMP '2026-01-01 12:00:00' WHERE id = ?", older);
        mvc.perform(get("/api/users/2/deliveries")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0].id").value(tied))
                .andExpect(jsonPath("$[1].id").value(first)).andExpect(jsonPath("$[2].id").value(older));
    }

    @Test
    void workflowUpdatesBothHelperAndCustomerTrackingAndRejectsStaleActions() throws Exception {
        long id = create(1, RequestCategory.PICKUP);
        assertThat(list(2)).isEmpty();
        JsonNode accepted = accept(id, 2);
        assertThat(list(2).get(0)).isEqualTo(accepted);
        for (String next : new String[]{"PICKED_UP", "DELIVERING", "DELIVERED"}) {
            JsonNode updated = json.readTree(mvc.perform(patch("/api/requests/" + id + "/status")
                    .contentType("application/json").content("{\"status\":\"" + next + "\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(list(2).get(0)).isEqualTo(updated);
            JsonNode customer = json.readTree(mvc.perform(get("/api/users/1/requests"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(customer.get(0)).isEqualTo(updated);
            var before = jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id);
            mvc.perform(patch("/api/requests/" + id + "/status").contentType("application/json")
                    .content("{\"status\":\"" + next + "\"}")).andExpect(status().isConflict());
            assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id)).isEqualTo(before);
        }
        assertThat(jdbc.queryForObject("SELECT version FROM delivery_requests WHERE id = ?", Long.class, id)).isEqualTo(4L);
    }
}
