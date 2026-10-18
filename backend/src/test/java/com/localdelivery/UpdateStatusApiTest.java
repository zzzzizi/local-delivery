package com.localdelivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localdelivery.dto.AcceptRequestDto;
import com.localdelivery.dto.DeliveryRequestResponse;
import com.localdelivery.dto.UpdateStatusRequest;
import com.localdelivery.model.DeliveryStatus;
import com.localdelivery.model.RequestCategory;
import com.localdelivery.repository.DeliveryRequestRepository;
import com.localdelivery.service.DeliveryRequestService;
import com.localdelivery.service.DemoUserService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UpdateStatusApiTest {
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
    @Autowired DeliveryRequestRepository requests;
    @Autowired DemoUserService demoUsers;
    @Autowired DeliveryRequestService service;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetTestData() {
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo(DATABASE.schema);
        jdbc.execute("TRUNCATE delivery_requests, users RESTART IDENTITY CASCADE");
        demoUsers.seed();
    }

    private JsonNode create(RequestCategory category) throws Exception {
        var body = json.createObjectNode().put("customerId", 1).put("category", category.name())
                .put("title", "Stage 9 test").put("description", "A test errand")
                .put("pickupAddress", "Majorstuen, Oslo").put("deliveryAddress", "Frogner, Oslo")
                .put("shoppingBudget", 120.50).put("helperReward", 80)
                .put("deadline", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).toString());
        return bodyOf(mvc.perform(post("/api/requests").contentType("application/json").content(body.toString()))
                .andExpect(status().isCreated()));
    }

    private JsonNode bodyOf(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode detail(long id) throws Exception {
        return bodyOf(mvc.perform(get("/api/requests/" + id)).andExpect(status().isOk()));
    }

    private ResultActions update(long id, DeliveryStatus next) throws Exception {
        return mvc.perform(patch("/api/requests/" + id + "/status").contentType("application/json")
                .content("{\"status\":\"" + next + "\"}"));
    }

    private long createInState(DeliveryStatus current) throws Exception {
        long id = create(RequestCategory.BUY).get("id").asLong();
        // Independent fixtures exercise every persisted state, including terminal states.
        jdbc.update("UPDATE delivery_requests SET status = ?::request_status, helper_id = ? WHERE id = ?",
                current.name(), current == DeliveryStatus.OPEN ? null : 2L, id);
        return id;
    }

    static Stream<Arguments> transitions() {
        Set<String> allowed = Set.of("OPEN:CANCELLED", "ACCEPTED:PICKED_UP", "ACCEPTED:CANCELLED",
                "PICKED_UP:DELIVERING", "DELIVERING:DELIVERED");
        return Arrays.stream(DeliveryStatus.values()).flatMap(current ->
                Arrays.stream(DeliveryStatus.values()).map(next ->
                        Arguments.of(current, next, allowed.contains(current + ":" + next))));
    }

    @ParameterizedTest(name = "{0} -> {1}: allowed={2}")
    @MethodSource("transitions")
    void enforcesEveryStatusPairWithoutChangingOtherFields(DeliveryStatus current, DeliveryStatus next,
                                                          boolean allowed) throws Exception {
        long id = createInState(current);
        JsonNode before = detail(id);
        var storedBefore = jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id);
        var result = update(id, next);
        if (allowed) {
            JsonNode updated = bodyOf(result.andExpect(status().isOk()));
            assertThat(updated.get("status").asText()).isEqualTo(next.name());
            assertThat(OffsetDateTime.parse(updated.get("updatedAt").asText()))
                    .isAfter(OffsetDateTime.parse(before.get("updatedAt").asText()));
            before.fieldNames().forEachRemaining(field -> {
                if (!List.of("status", "updatedAt").contains(field)) {
                    assertThat(updated.get(field)).as(field).isEqualTo(before.get(field));
                }
            });
            assertThat(updated.size()).isEqualTo(14);
            assertThat(detail(id)).isEqualTo(updated);
            var stored = jdbc.queryForMap("SELECT status::text, version, updated_at FROM delivery_requests WHERE id = ?", id);
            assertThat(stored.get("status")).isEqualTo(next.name());
            assertThat(stored.get("version")).isEqualTo(1L);
            assertThat(stored.get("updated_at")).isNotEqualTo(storedBefore.get("updated_at"));
            mvc.perform(get("/api/requests")).andExpect(status().isOk()).andExpect(content().json("[]"));
        } else {
            result.andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("Conflict"))
                    .andExpect(jsonPath("$.timestamp").isString());
            if (current == DeliveryStatus.OPEN && next == DeliveryStatus.ACCEPTED) {
                result.andExpect(jsonPath("$.message").value(
                        "Use POST /api/requests/{id}/accept to assign a helper and accept this request."));
            } else {
                result.andExpect(jsonPath("$.message").value(
                        "Cannot change delivery status from " + current + " to " + next + "."));
            }
            assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id)).isEqualTo(storedBefore);
            assertThat(detail(id)).isEqualTo(before);
        }
    }

    @ParameterizedTest
    @EnumSource(RequestCategory.class)
    void progressesFromCreationThroughAcceptanceToDelivered(RequestCategory category) throws Exception {
        long id = create(category).get("id").asLong();
        JsonNode previous = bodyOf(mvc.perform(post("/api/requests/" + id + "/accept")
                .contentType("application/json").content("{\"helperId\":2}")).andExpect(status().isOk()));
        long version = 1;
        for (var next : List.of(DeliveryStatus.PICKED_UP, DeliveryStatus.DELIVERING, DeliveryStatus.DELIVERED)) {
            JsonNode updated = bodyOf(update(id, next).andExpect(status().isOk()));
            assertThat(updated.get("status").asText()).isEqualTo(next.name());
            assertThat(updated.get("helperId").asLong()).isEqualTo(2L);
            assertThat(updated.get("createdAt")).isEqualTo(previous.get("createdAt"));
            assertThat(OffsetDateTime.parse(updated.get("updatedAt").asText()))
                    .isAfter(OffsetDateTime.parse(previous.get("updatedAt").asText()));
            assertThat(detail(id)).isEqualTo(updated);
            assertThat(requests.findById(id).orElseThrow().getVersion()).isEqualTo(++version);
            previous = updated;
        }
        mvc.perform(get("/api/requests")).andExpect(status().isOk()).andExpect(content().json("[]"));
        var completed = jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id);
        update(id, DeliveryStatus.DELIVERING).andExpect(status().isConflict());
        assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id)).isEqualTo(completed);
    }

    @Test
    void missingRequestReturns404() throws Exception {
        update(Long.MAX_VALUE, DeliveryStatus.PICKED_UP).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Delivery request not found."));
        assertThat(requests.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{\"status\":null}", "{\"status\":\"UNKNOWN\"}",
            "{\"status\":\"picked_up\"}", "{\"status\":1}", "{\"status\":true}", "{\"status\":[]}",
            "{\"status\":{}}", "[]", "{", "{\"status\":\"PICKED_UP\",\"helperId\":2}",
            "{\"status\":\"PICKED_UP\",\"version\":0}"})
    void invalidBodiesReturn422WithoutMutation(String body) throws Exception {
        long id = createInState(DeliveryStatus.ACCEPTED);
        var before = jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id);
        mvc.perform(patch("/api/requests/" + id + "/status").contentType("application/json").content(body))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "9223372036854775808"})
    void invalidPathIdsReturn422(String id) throws Exception {
        mvc.perform(patch("/api/requests/" + id + "/status").contentType("application/json")
                .content("{\"status\":\"PICKED_UP\"}")).andExpect(status().isUnprocessableEntity());
        assertThat(requests.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"ACCEPTED,PICKED_UP", "PICKED_UP,DELIVERING", "DELIVERING,DELIVERED"})
    void legacyUnassignedRequestsCannotProgress(DeliveryStatus current, DeliveryStatus next) throws Exception {
        long id = createInState(current);
        jdbc.update("UPDATE delivery_requests SET helper_id = NULL WHERE id = ?", id);
        var before = jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id);
        update(id, next).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Delivery request has no assigned helper."));
        assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id)).isEqualTo(before);
    }

    private record UpdateAttempt(DeliveryRequestResponse response, RuntimeException failure) {}

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        } catch (BrokenBarrierException | TimeoutException error) {
            throw new IllegalStateException(error);
        }
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, names = {"OPEN", "ACCEPTED"})
    @Timeout(30)
    void cancellationRacingWithAcceptanceOrPickupCommitsOnlyOneUpdate(DeliveryStatus initialStatus) throws Exception {
        long id = createInState(initialStatus);
        var forward = initialStatus == DeliveryStatus.OPEN ? DeliveryStatus.ACCEPTED : DeliveryStatus.PICKED_UP;
        var bothRead = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(forward, DeliveryStatus.CANCELLED).stream().map(next -> executor.submit(() -> {
                try {
                    var response = new TransactionTemplate(transactionManager).execute(transaction -> {
                        // Hold the same row version in both persistence contexts before either writes.
                        var initial = requests.findById(id).orElseThrow();
                        assertThat(initial.getVersion()).isZero();
                        assertThat(initial.getStatus()).isEqualTo(initialStatus);
                        await(bothRead);
                        return next == DeliveryStatus.ACCEPTED ? service.accept(id, new AcceptRequestDto(2L))
                                : service.updateStatus(id, new UpdateStatusRequest(next));
                    });
                    return new UpdateAttempt(response, null);
                } catch (RuntimeException failure) {
                    return new UpdateAttempt(null, failure);
                }
            })).toList();
            var results = List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS));
            var winners = results.stream().filter(result -> result.failure() == null).toList();
            var losers = results.stream().filter(result -> result.failure() != null).toList();
            assertThat(winners).hasSize(1);
            assertThat(losers).hasSize(1);
            assertThat(losers.getFirst().failure()).isInstanceOf(OptimisticLockingFailureException.class);
            assertThat(detail(id)).isEqualTo(json.readTree(json.writeValueAsString(winners.getFirst().response())));
            var stored = requests.findById(id).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(winners.getFirst().response().status());
            assertThat(stored.getVersion()).isEqualTo(1L);
            Long expectedHelper = initialStatus == DeliveryStatus.ACCEPTED || stored.getStatus() == DeliveryStatus.ACCEPTED
                    ? 2L : null;
            assertThat(stored.getHelper() == null ? null : stored.getHelper().getId()).isEqualTo(expectedHelper);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @Timeout(30)
    void simultaneousPickupCallsReturnOneSuccessAndOneConflict() throws Exception {
        long id = createInState(DeliveryStatus.ACCEPTED);
        var start = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> attempt = () -> {
                await(start);
                return update(id, DeliveryStatus.PICKED_UP).andReturn().getResponse().getStatus();
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            var stored = requests.findById(id).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
            assertThat(stored.getHelper().getId()).isEqualTo(2L);
            assertThat(stored.getVersion()).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
