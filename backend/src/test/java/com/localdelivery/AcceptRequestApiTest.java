package com.localdelivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localdelivery.dto.AcceptRequestDto;
import com.localdelivery.dto.DeliveryRequestResponse;
import com.localdelivery.model.DeliveryStatus;
import com.localdelivery.model.RequestCategory;
import com.localdelivery.model.User;
import com.localdelivery.repository.DeliveryRequestRepository;
import com.localdelivery.repository.UserRepository;
import com.localdelivery.service.DeliveryRequestService;
import com.localdelivery.service.DemoUserService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.*;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AcceptRequestApiTest {
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
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetTestData() {
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo(DATABASE.schema);
        jdbc.execute("TRUNCATE delivery_requests, users RESTART IDENTITY CASCADE");
        demoUsers.seed();
        assertThat(users.saveAndFlush(new User("Second Helper", "second-helper@example.com")).getId()).isEqualTo(3L);
    }

    private JsonNode create(RequestCategory category) throws Exception {
        var body = json.createObjectNode().put("customerId", 1).put("category", category.name())
                .put("title", "Stage 8 test").put("description", "A test errand")
                .put("pickupAddress", "Majorstuen, Oslo").put("deliveryAddress", "Frogner, Oslo")
                .put("helperReward", 80).put("deadline", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).toString());
        return json.readTree(mvc.perform(post("/api/requests").contentType("application/json").content(body.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private MvcResult accept(long id, long helperId) throws Exception {
        return mvc.perform(post("/api/requests/" + id + "/accept").contentType("application/json")
                .content("{\"helperId\":" + helperId + "}")).andReturn();
    }

    @ParameterizedTest
    @EnumSource(RequestCategory.class)
    void acceptsRequestAndRemovesItFromAvailableJobs(RequestCategory category) throws Exception {
        JsonNode created = create(category);
        long id = created.get("id").asLong();
        var response = accept(id, 2).getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode accepted = json.readTree(response.getContentAsString());
        assertThat(accepted.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(accepted.get("helperId").asLong()).isEqualTo(2);
        assertThat(OffsetDateTime.parse(accepted.get("updatedAt").asText()))
                .isAfter(OffsetDateTime.parse(created.get("updatedAt").asText()));
        created.fieldNames().forEachRemaining(field -> {
            if (!List.of("status", "helperId", "updatedAt").contains(field)) {
                assertThat(accepted.get(field)).as(field).isEqualTo(created.get(field));
            }
        });
        assertThat(accepted.size()).isEqualTo(14);
        JsonNode detail = json.readTree(mvc.perform(get("/api/requests/" + id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(detail).isEqualTo(accepted);
        mvc.perform(get("/api/requests")).andExpect(status().isOk()).andExpect(content().json("[]"));
        var stored = jdbc.queryForMap("SELECT helper_id, status::text, version FROM delivery_requests WHERE id = ?", id);
        assertThat(stored.get("helper_id")).isEqualTo(2L);
        assertThat(stored.get("status")).isEqualTo("ACCEPTED");
        assertThat(stored.get("version")).isEqualTo(1L);
    }

    @Test
    void missingRequestReturns404() throws Exception {
        var result = accept(Long.MAX_VALUE, 2);
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(json.readTree(result.getResponse().getContentAsString()).get("message").asText())
                .isEqualTo("Delivery request not found.");
        assertThat(requests.count()).isZero();
    }

    @Test
    void missingHelperReturns404WithoutChangingRequest() throws Exception {
        long id = create(RequestCategory.PACKAGE).get("id").asLong();
        var before = jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id);
        var response = accept(id, Long.MAX_VALUE).getResponse();
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(json.readTree(response.getContentAsString()).get("message").asText()).isEqualTo("Helper not found.");
        assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id)).isEqualTo(before);
    }

    @Test
    void customerCannotAcceptOwnRequest() throws Exception {
        long id = create(RequestCategory.BUY).get("id").asLong();
        var before = jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id);
        var response = accept(id, 1).getResponse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(json.readTree(response.getContentAsString()).get("message").asText())
                .isEqualTo("Customers cannot accept their own delivery requests.");
        assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id)).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, names = "OPEN", mode = EnumSource.Mode.EXCLUDE)
    void nonOpenRequestCannotBeAccepted(DeliveryStatus status) throws Exception {
        long id = create(RequestCategory.PICKUP).get("id").asLong();
        jdbc.update("UPDATE delivery_requests SET status = ?::request_status, helper_id = ? WHERE id = ?",
                status.name(), status == DeliveryStatus.CANCELLED ? null : 2L, id);
        var before = jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id);
        assertThat(accept(id, 3).getResponse().getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(longs = {2, 3})
    void duplicateAcceptanceKeepsOriginalAssignment(long helperId) throws Exception {
        long id = create(RequestCategory.BUY).get("id").asLong();
        assertThat(accept(id, 2).getResponse().getStatus()).isEqualTo(200);
        var before = jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id);
        assertThat(accept(id, helperId).getResponse().getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id)).isEqualTo(before);
    }

    @Test
    void preexistingHelperIsNeverOverwrittenEvenIfStatusIsOpen() throws Exception {
        long id = create(RequestCategory.BUY).get("id").asLong();
        jdbc.update("UPDATE delivery_requests SET helper_id = 2 WHERE id = ?", id);
        var before = jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id);
        assertThat(accept(id, 3).getResponse().getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"helperId\":null}", "{\"helperId\":0}", "{\"helperId\":-1}",
            "{\"helperId\":1.5}", "{\"helperId\":\"2\"}", "{\"helperId\":true}",
            "{\"helperId\":9223372036854775808}", "{\"helperId\":2,\"status\":\"ACCEPTED\"}",
            "{\"helperId\":2,\"version\":0}", "null", "[]", "{"})
    void invalidBodyReturns422WithoutChangingRequest(String body) throws Exception {
        long id = create(RequestCategory.BUY).get("id").asLong();
        var before = jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id);
        mvc.perform(post("/api/requests/" + id + "/accept").contentType("application/json").content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.status").value(422));
        assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "9223372036854775808"})
    void invalidRequestIdReturns422(String id) throws Exception {
        mvc.perform(post("/api/requests/" + id + "/accept").contentType("application/json").content("{\"helperId\":2}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private record AcceptanceAttempt(long helperId, DeliveryRequestResponse response, RuntimeException failure) {}

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent test was interrupted.", error);
        } catch (BrokenBarrierException | TimeoutException error) {
            throw new IllegalStateException("Concurrent test did not reach both readers.", error);
        }
    }

    @Test
    @Timeout(30)
    void competingTransactionsReadingSameVersionCannotBothAccept() throws Exception {
        long id = create(RequestCategory.BUY).get("id").asLong();
        var bothRead = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(2L, 3L).stream().map(helperId -> executor.submit(() -> {
                try {
                    var response = new TransactionTemplate(transactionManager).execute(transaction -> {
                        // Both real transactions cache the same OPEN version before either attempts an update.
                        var initial = requests.findById(id).orElseThrow();
                        assertThat(initial.getVersion()).isZero();
                        assertThat(initial.getStatus()).isEqualTo(DeliveryStatus.OPEN);
                        await(bothRead);
                        return service.accept(id, new AcceptRequestDto(helperId));
                    });
                    return new AcceptanceAttempt(helperId, response, null);
                } catch (RuntimeException failure) {
                    return new AcceptanceAttempt(helperId, null, failure);
                }
            })).toList();
            var results = List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS));
            var winners = results.stream().filter(result -> result.failure() == null).toList();
            var losers = results.stream().filter(result -> result.failure() != null).toList();
            assertThat(winners).hasSize(1);
            assertThat(losers).hasSize(1);
            assertThat(losers.getFirst().failure()).isInstanceOf(OptimisticLockingFailureException.class);
            assertWinner(id, winners.getFirst().helperId());
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @Timeout(30)
    void simultaneousHttpRequestsReturnOneSuccessAndOneConflict() throws Exception {
        long id = create(RequestCategory.PACKAGE).get("id").asLong();
        var start = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(2L, 3L).stream().map(helperId -> executor.submit(() -> {
                await(start);
                return accept(id, helperId).getResponse();
            })).toList();
            var responses = List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS));
            assertThat(responses).extracting(response -> response.getStatus()).containsExactlyInAnyOrder(200, 409);
            var winner = responses.stream().filter(response -> response.getStatus() == 200).findFirst().orElseThrow();
            assertWinner(id, json.readTree(winner.getContentAsString()).get("helperId").asLong());
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertWinner(long id, long helperId) {
        var stored = requests.findById(id).orElseThrow();
        assertThat(stored.getHelper().getId()).isEqualTo(helperId);
        assertThat(stored.getStatus()).isEqualTo(DeliveryStatus.ACCEPTED);
        assertThat(stored.getVersion()).isEqualTo(1L);
        assertThat(requests.count()).isEqualTo(1);
    }
}
