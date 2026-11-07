package com.localdelivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localdelivery.service.DemoUserService;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JobApplicationApiTest {
    private static final PostgresTestDatabase DATABASE = new PostgresTestDatabase();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::url);
        registry.add("spring.datasource.username", () -> DATABASE.username);
        registry.add("spring.datasource.password", () -> DATABASE.password);
        registry.add("app.seed-demo-users", () -> false);
    }
    @AfterAll static void close() { DATABASE.close(); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired DemoUserService demo;
    long request;
    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo(DATABASE.schema);
        jdbc.execute("TRUNCATE users, delivery_requests RESTART IDENTITY CASCADE");
        demo.seed();
        jdbc.update("INSERT INTO users(id, name, email) VALUES (3, 'Another Helper', 'other@example.com'), (4, 'Stranger', 'stranger@example.com')");
        request = newRequest();
    }
    long newRequest() {
        return jdbc.queryForObject("""
                INSERT INTO delivery_requests(customer_id, category, title, description, pickup_address,
                delivery_address, helper_reward, deadline) VALUES (1, 'PACKAGE', 'Parcel',
                'Small parcel', 'Shop', 'Home', 50, now() + interval '1 day') RETURNING id
                """, Long.class);
    }
    String path(long id) { return "/api/requests/" + request + "/applications/" + id; }
    JsonNode postAs(String path, long actor, Object body, int expected) throws Exception {
        return json.readTree(mvc.perform(post(path).with(user(Long.toString(actor))).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }
    JsonNode getAs(String path, long actor) throws Exception {
        return json.readTree(mvc.perform(get(path).with(user(Long.toString(actor))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    JsonNode apply(long actor) throws Exception { return postAs("/api/requests/" + request + "/applications", actor, Map.of(), 200); }
    JsonNode decide(long id, String action, long actor, int expected) throws Exception { return postAs(path(id) + "/" + action, actor, Map.of(), expected); }

    @Test void requestingDoesNotAcceptReserveAssignOrAllowDelivery() throws Exception {
        var before = jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", request);
        var first = apply(2); var second = apply(3);
        assertThat(first.get("status").asText()).isEqualTo("PENDING");
        assertThat(second.get("status").asText()).isEqualTo("PENDING");
        assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", request)).isEqualTo(before);
        assertThat(getAs("/api/requests", 2).size()).isEqualTo(1);
        assertThat(getAs("/api/me/deliveries", 2).size()).isZero();
        for (String state : new String[]{"ACCEPTED", "PICKED_UP", "DELIVERING", "DELIVERED"})
            mvc.perform(patch("/api/requests/" + request + "/status").with(user("2")).with(csrf()).contentType("application/json")
                    .content(json.writeValueAsString(Map.of("status", state)))).andExpect(status().isForbidden());
        var legacy = postAs("/api/requests/" + request + "/accept", 2, Map.of(), 200);
        assertThat(legacy).isEqualTo(first);
        assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", request)).isEqualTo(before);
    }
    @Test void onlyCustomerCanApproveAndOtherPendingHelpersAreNotSelected() throws Exception {
        long first = apply(2).get("id").asLong(), second = apply(3).get("id").asLong();
        for (long actor : new long[]{2, 3, 4}) { decide(first, "approve", actor, 403); decide(first, "decline", actor, 403); }
        var approved = decide(first, "approve", 1, 200);
        assertThat(approved.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(approved.get("helperId").asLong()).isEqualTo(2);
        assertThat(getAs("/api/requests", 3).size()).isZero();
        assertThat(getAs("/api/me/deliveries", 2).size()).isEqualTo(1);
        assertThat(getAs("/api/me/deliveries", 3).size()).isZero();
        assertThat(getAs("/api/me/applications", 3).get("items").get(0).get("status").asText()).isEqualTo("NOT_SELECTED");
        decide(second, "approve", 1, 409);
        mvc.perform(get("/api/requests/" + request).with(user("3"))).andExpect(status().isForbidden());
        assertThat(getAs("/api/me/applications", 3).toString()).doesNotContain("pickupAddress", "deliveryAddress", "email");
    }
    @Test void approvalAndSubmissionRetriesAreIdempotent() throws Exception {
        var application = apply(2);
        assertThat(apply(2)).isEqualTo(application);
        long id = application.get("id").asLong();
        var approved = decide(id, "approve", 1, 200);
        assertThat(decide(id, "approve", 1, 200)).isEqualTo(approved);
        assertThat(apply(2).get("status").asText()).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT version FROM delivery_requests WHERE id = ?", Long.class, request)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM job_applications", Integer.class)).isEqualTo(1);
    }
    @Test void customersSeeAllApplicantsWhileHelpersSeeOnlyThemselves() throws Exception {
        apply(2); apply(3);
        assertThat(getAs("/api/requests/" + request + "/applications", 1).get("items").size()).isEqualTo(2);
        for (long helper : new long[]{2, 3}) {
            var rows = getAs("/api/requests/" + request + "/applications", helper).get("items");
            assertThat(rows.size()).isEqualTo(1);
            assertThat(rows.get(0).get("helperId").asLong()).isEqualTo(helper);
            assertThat(getAs("/api/me/applications", helper).get("items")).isEqualTo(rows);
        }
        assertThat(getAs("/api/requests/" + request + "/applications", 4).get("items").size()).isZero();
        assertThat(getAs("/api/me/applications", 1).get("items").size()).isZero();
    }
    @Test void declineLeavesJobOpenAndCannotBeOverriddenByHelper() throws Exception {
        long id = apply(2).get("id").asLong();
        assertThat(decide(id, "decline", 1, 200).get("status").asText()).isEqualTo("DECLINED");
        decide(id, "decline", 1, 200); decide(id, "approve", 1, 409); decide(id, "withdraw", 2, 409);
        postAs("/api/requests/" + request + "/applications", 2, Map.of(), 409);
        assertThat(getAs("/api/requests/" + request, 2).get("status").asText()).isEqualTo("OPEN");
        decide(apply(3).get("id").asLong(), "approve", 1, 200);
    }
    @Test void onlyApplicantMayWithdrawAndTheyCanReapplyWhileOpen() throws Exception {
        long id = apply(2).get("id").asLong();
        for (long actor : new long[]{1, 3, 4}) decide(id, "withdraw", actor, 403);
        assertThat(decide(id, "withdraw", 2, 200).get("status").asText()).isEqualTo("WITHDRAWN");
        decide(id, "withdraw", 2, 200); decide(id, "approve", 1, 409);
        var again = apply(2);
        assertThat(again.get("id").asLong()).isEqualTo(id);
        assertThat(again.get("status").asText()).isEqualTo("PENDING");
        decide(id, "approve", 1, 200); decide(id, "withdraw", 2, 409);
    }
    @Test void cancellationClosesPendingApplicationsAndPreservesTheirHistory() throws Exception {
        long id = apply(2).get("id").asLong(); apply(3);
        mvc.perform(patch("/api/requests/" + request + "/status").with(user("1")).with(csrf()).contentType("application/json")
                .content("{\"status\":\"CANCELLED\"}")).andExpect(status().isOk());
        assertThat(getAs("/api/me/applications", 2).get("items").get(0).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM job_applications WHERE status = 'PENDING'", Integer.class)).isZero();
        decide(id, "approve", 1, 409); postAs("/api/requests/" + request + "/applications", 2, Map.of(), 409);
    }
    @Test void mismatchedJobAndApplicationIdsCannotAssignAnotherJob() throws Exception {
        long id = apply(2).get("id").asLong();
        long other = newRequest();
        postAs("/api/requests/" + other + "/applications/" + id + "/approve", 1, Map.of(), 404);
        decide(99999, "approve", 1, 404);
        assertThat(getAs("/api/requests", 1).size()).isEqualTo(2);
    }
    @Test void identityValidationCsrfAndAuthenticationProtectEveryDecision() throws Exception {
        long id = apply(2).get("id").asLong();
        for (String action : new String[]{"approve", "decline", "withdraw"}) {
            mvc.perform(post(path(id) + "/" + action).with(csrf())).andExpect(status().isUnauthorized());
            mvc.perform(post(path(id) + "/" + action).with(user("1"))).andExpect(status().isForbidden());
        }
        for (String path : new String[]{"/api/me/applications", "/api/requests/" + request + "/applications"})
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        postAs("/api/requests/" + request + "/applications", 2, Map.of("helperId", 3), 403);
        postAs("/api/requests/" + request + "/applications", 1, Map.of(), 400);
        mvc.perform(get("/api/me/applications?page=-1").with(user("2"))).andExpect(status().isUnprocessableEntity());
    }
    @ParameterizedTest @ValueSource(strings = {"withdraw", "decline"}) @Timeout(30)
    void approvalRacesWithWithdrawalOrDeclineWithoutResurrectingTheApplication(String otherAction) throws Exception {
        long id = apply(2).get("id").asLong();
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return mvc.perform(post(path(id) + "/approve").with(user("1")).with(csrf())).andReturn().getResponse().getStatus(); });
            var b = pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return mvc.perform(post(path(id) + "/" + otherAction).with(user(otherAction.equals("withdraw") ? "2" : "1")).with(csrf())).andReturn().getResponse().getStatus(); });
            assertThat(java.util.List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
            String state = jdbc.queryForObject("SELECT status FROM job_applications WHERE id = ?", String.class, id);
            String deliveryState = jdbc.queryForObject("SELECT status::text FROM delivery_requests WHERE id = ?", String.class, request);
            assertThat(deliveryState).isEqualTo(state.equals("APPROVED") ? "ACCEPTED" : "OPEN");
        }
    }
    @Test void paginationIsBoundedAndOwned() throws Exception {
        for (int i = 0; i < 27; i++) jdbc.update("INSERT INTO job_applications(request_id, helper_id) VALUES (?, 2)", newRequest());
        var page = getAs("/api/me/applications", 2);
        assertThat(page.get("items").size()).isEqualTo(25);
        assertThat(page.get("hasMore").asBoolean()).isTrue();
        assertThat(getAs("/api/me/applications?page=1", 2).get("items").size()).isEqualTo(2);
        assertThat(getAs("/api/me/applications", 3).get("items").size()).isZero();
    }
}
