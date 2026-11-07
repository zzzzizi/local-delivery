package com.localdelivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localdelivery.service.DemoUserService;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicLong;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.accounts.rate-limit-enabled=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatApiTest {
    private static final PostgresTestDatabase DATABASE = new PostgresTestDatabase();
    private static final AtomicLong TIME = new AtomicLong(System.currentTimeMillis());
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
    @MockitoBean Clock clock;
    long request;

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo(DATABASE.schema);
        jdbc.execute("TRUNCATE users, delivery_requests RESTART IDENTITY CASCADE");
        demo.seed();
        jdbc.update("INSERT INTO users(id, name, email) VALUES (3, 'Other Helper', 'other@example.com'), (4, 'Unrelated', 'unrelated@example.com')");
        TIME.addAndGet(Duration.ofDays(1).toMillis());
        when(clock.millis()).thenAnswer(call -> TIME.get());
        request = newRequest();
    }
    long newRequest() {
        return jdbc.queryForObject("""
                INSERT INTO delivery_requests(customer_id, category, title, description, pickup_address,
                delivery_address, helper_reward, deadline) VALUES (1, 'PACKAGE', 'Parcel to Frogner',
                'Test parcel', 'Shop', 'Home', 50, now() + interval '1 day') RETURNING id
                """, Long.class);
    }
    JsonNode postJson(String path, long actor, Object body, int status) throws Exception {
        return json.readTree(mvc.perform(post("/api" + path).with(user(Long.toString(actor))).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().is(status)).andReturn().getResponse().getContentAsString());
    }
    JsonNode getJson(String path, long actor, int status) throws Exception {
        return json.readTree(mvc.perform(get("/api" + path).with(user(Long.toString(actor))))
                .andExpect(status().is(status)).andReturn().getResponse().getContentAsString());
    }
    long start(long actor) throws Exception { return postJson("/requests/" + request + "/conversation", actor, Map.of(), 200).get("id").asLong(); }
    JsonNode send(long id, long actor, String text) throws Exception {
        return postJson("/conversations/" + id + "/messages", actor, Map.of("body", text, "clientMessageId", UUID.randomUUID()), 200);
    }
    void state(String status, Long helper) {
        jdbc.update("UPDATE delivery_requests SET status = ?::request_status, helper_id = ? WHERE id = ?", status, helper, request);
    }

    @Test void helpersCanAskBeforeAcceptingAndCustomersCanReply() throws Exception {
        long id = start(2);
        assertThat(start(2)).isEqualTo(id);
        var question = send(id, 2, "  Can I collect at 17:00?  ");
        assertThat(question.get("body").asText()).isEqualTo("Can I collect at 17:00?");
        assertThat(question.get("senderId").asLong()).isEqualTo(2);
        send(id, 1, "Yes, thank you!");
        assertThat(getJson("/conversations/" + id + "/messages", 1, 200).get("items").size()).isEqualTo(2);
        assertThat(getJson("/requests/" + request, 1, 200).get("status").asText()).isEqualTo("OPEN");
        assertThat(getJson("/conversations/" + id, 2, 200).has("email")).isFalse();
    }
    @Test void separateHelperConversationsAndNoEnumerationOrIdentitySpoofing() throws Exception {
        long first = start(2), second = start(3);
        assertThat(first).isNotEqualTo(second);
        send(first, 2, "Private question");
        for (String suffix : new String[]{"", "/messages"}) getJson("/conversations/" + first + suffix, 3, 404);
        postJson("/conversations/" + first + "/messages", 3, Map.of("body", "Intruder", "clientMessageId", UUID.randomUUID()), 404);
        assertThat(getJson("/conversations", 1, 200).get("items").size()).isEqualTo(2);
        assertThat(getJson("/conversations", 2, 200).get("items").size()).isEqualTo(1);
        assertThat(getJson("/conversations", 4, 200).get("items").size()).isZero();
        assertThat(getJson("/conversations?requestId=" + newRequest(), 1, 200).get("items").size()).isZero();
        postJson("/conversations/" + first + "/messages", 2, Map.of("body", "Hello", "clientMessageId", UUID.randomUUID(), "senderId", 1), 422);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages", Integer.class)).isEqualTo(1);
    }
    @Test void anonymousAndMissingCsrfCannotReadOrWrite() throws Exception {
        long id = start(2);
        for (String path : new String[]{"/conversations", "/conversations/" + id, "/conversations/" + id + "/messages"})
            mvc.perform(get("/api" + path)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/requests/" + request + "/conversation").with(user("2"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/conversations/" + id + "/messages").with(user("2")).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        postJson("/requests/" + request + "/conversation", 1, Map.of(), 403);
        postJson("/requests/999999/conversation", 2, Map.of(), 404);
    }
    @Test void unchosenHelpersKeepOnlyTheirHistory() throws Exception {
        long chosen = start(2), other = start(3);
        send(other, 3, "Earlier question");
        var application = postJson("/requests/" + request + "/applications", 2, Map.of(), 200);
        postJson("/requests/" + request + "/applications/" + application.get("id").asLong() + "/approve", 1, Map.of(), 200);
        send(chosen, 2, "On my way"); send(chosen, 1, "Thanks");
        for (long actor : new long[]{1, 3}) {
            assertThat(getJson("/conversations/" + other, actor, 200).get("canSend").asBoolean()).isFalse();
            postJson("/conversations/" + other + "/messages", actor, Map.of("body", "Too late", "clientMessageId", UUID.randomUUID()), 409);
        }
        assertThat(getJson("/conversations/" + other, 3, 200).get("canViewRequest").asBoolean()).isFalse();
        assertThat(getJson("/conversations/" + other + "/messages", 3, 200).get("items").size()).isEqualTo(1);
        assertThat(start(3)).isEqualTo(other);
        postJson("/requests/" + request + "/conversation", 4, Map.of(), 409);
        getJson("/requests/" + request, 3, 403);
    }
    @ParameterizedTest @ValueSource(strings = {"ACCEPTED", "PICKED_UP", "DELIVERING"})
    void assignedHelperCanStartAndContinueDuringDelivery(String status) throws Exception {
        state(status, 2L);
        long id = start(2);
        send(id, 1, "Delivery instructions"); send(id, 2, "Understood");
        postJson("/requests/" + request + "/conversation", 3, Map.of(), 409);
    }
    @ParameterizedTest @ValueSource(strings = {"DELIVERED", "CANCELLED"})
    void closedRequestsRetainHistoryAndRejectNewMessages(String status) throws Exception {
        long id = start(2);
        send(id, 1, "Saved history"); state(status, 2L);
        postJson("/conversations/" + id + "/messages", 1, Map.of("body", "Late", "clientMessageId", UUID.randomUUID()), 409);
        postJson("/requests/" + request + "/conversation", 3, Map.of(), 409);
        assertThat(getJson("/conversations/" + id + "/messages", 2, 200).get("items").size()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings = {"", " ", "\n\t", "\u2003"})
    void emptyMessagesRejected(String body) throws Exception {
        long id = start(2);
        postJson("/conversations/" + id + "/messages", 2, Map.of("body", body, "clientMessageId", UUID.randomUUID()), 422);
    }
    @Test void lengthAndUuidValidation() throws Exception {
        long id = start(2);
        send(id, 2, "x".repeat(2000));
        postJson("/conversations/" + id + "/messages", 2, Map.of("body", "x".repeat(2001), "clientMessageId", UUID.randomUUID()), 422);
        postJson("/conversations/" + id + "/messages", 2, Map.of("body", "Hello"), 422);
        postJson("/conversations/" + id + "/messages", 2, Map.of("body", "Hello", "clientMessageId", "bad"), 422);
        for (String query : new String[]{"?before=0", "?after=-1"}) getJson("/conversations/" + id + "/messages" + query, 2, 422);
        getJson("/conversations?page=-1", 2, 422);
        getJson("/conversations?page=100001", 2, 422);
    }
    @Test void retriesReturnOriginalEvenAfterClosingButCannotChangeText() throws Exception {
        long id = start(2);
        var body = Map.of("body", "Hello", "clientMessageId", UUID.randomUUID());
        var first = postJson("/conversations/" + id + "/messages", 2, body, 200);
        state("CANCELLED", null);
        assertThat(postJson("/conversations/" + id + "/messages", 2, body, 200)).isEqualTo(first);
        postJson("/conversations/" + id + "/messages", 2, Map.of("body", "Changed", "clientMessageId", body.get("clientMessageId")), 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages", Integer.class)).isEqualTo(1);
    }
    @Test void concurrentStartsAndRetriesCreateOneConversationAndOneMessage() throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { barrier.await(); return start(2); });
            var b = pool.submit(() -> { barrier.await(); return start(2); });
            long id = a.get(); assertThat(b.get()).isEqualTo(id);
            var body = Map.of("body", "One message", "clientMessageId", UUID.randomUUID());
            var x = pool.submit(() -> { barrier.await(); return postJson("/conversations/" + id + "/messages", 2, body, 200); });
            var y = pool.submit(() -> { barrier.await(); return postJson("/conversations/" + id + "/messages", 2, body, 200); });
            assertThat(x.get()).isEqualTo(y.get());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages", Integer.class)).isEqualTo(1);
        }
    }
    @Test void cursorPaginationHasNoGapsAndListIsBounded() throws Exception {
        long id = start(2);
        jdbc.update("INSERT INTO chat_messages(conversation_id, sender_id, client_message_id, body) SELECT ?, 2, gen_random_uuid(), 'Message ' || n FROM generate_series(1, 120) n", id);
        var latest = getJson("/conversations/" + id + "/messages", 2, 200);
        assertThat(latest.get("items").size()).isEqualTo(50);
        assertThat(latest.get("items").get(0).get("body").asText()).isEqualTo("Message 71");
        assertThat(latest.get("hasMore").asBoolean()).isTrue();
        var older = getJson("/conversations/" + id + "/messages?before=" + latest.get("items").get(0).get("id"), 2, 200);
        assertThat(older.get("items").get(49).get("body").asText()).isEqualTo("Message 70");
        var first = getJson("/conversations/" + id + "/messages?after=0", 2, 200);
        assertThat(first.get("items").get(0).get("body").asText()).isEqualTo("Message 1");
        var next = getJson("/conversations/" + id + "/messages?after=" + first.get("items").get(49).get("id"), 2, 200);
        assertThat(next.get("items").get(0).get("body").asText()).isEqualTo("Message 51");
        for (int i = 0; i < 26; i++) jdbc.update("INSERT INTO conversations(request_id, helper_id) VALUES (?, 2)", newRequest());
        var list = getJson("/conversations", 1, 200);
        assertThat(list.get("items").size()).isEqualTo(25);
        assertThat(list.get("hasMore").asBoolean()).isTrue();
        assertThat(getJson("/conversations?page=1", 1, 200).get("items").size()).isEqualTo(2);
    }
    @Test void throttleBoundsNewSendsButAllowsRetriesAndResets() throws Exception {
        long id = start(2);
        JsonNode last = null;
        for (int i = 0; i < 30; i++) last = send(id, 2, "Hello " + i);
        postJson("/conversations/" + id + "/messages", 2, Map.of("body", last.get("body").asText(), "clientMessageId", last.get("clientMessageId").asText()), 200);
        var blocked = mvc.perform(post("/api/conversations/" + id + "/messages").with(user("2")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("body", "Excess", "clientMessageId", UUID.randomUUID()))));
        blocked.andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        send(id, 1, "Other participant has a separate allowance");
        TIME.addAndGet(60001); send(id, 2, "After reset");
    }

    @Test void creatingManyConversationsIsThrottledWithoutBlockingExistingOnes() throws Exception {
        long original = request;
        long id = start(2);
        for (int i = 1; i < 20; i++) { request = newRequest(); start(2); }
        request = newRequest();
        postJson("/requests/" + request + "/conversation", 2, Map.of(), 429);
        request = original;
        assertThat(start(2)).isEqualTo(id);
        TIME.addAndGet(Duration.ofMinutes(16).toMillis());
        request = newRequest(); start(2);
    }
}
