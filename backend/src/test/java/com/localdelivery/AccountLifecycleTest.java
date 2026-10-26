package com.localdelivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localdelivery.service.DemoUserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.accounts.require-verification=true", "app.accounts.mail-enabled=true", "app.accounts.rate-limit-enabled=true"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountLifecycleTest {
    private static final PostgresTestDatabase DATABASE = new PostgresTestDatabase();
    private static final AtomicLong TIME = new AtomicLong(Instant.now().toEpochMilli());
    private static final String EMAIL = "trial@example.com";
    private static final String PASSWORD = "Original test password 21";
    private static final String NEW_PASSWORD = "Replacement test password 21";
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
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
    @Autowired ThreadPoolTaskExecutor accountMailExecutor;
    @MockitoBean JavaMailSender sender;
    @MockitoBean Clock clock;
    final CopyOnWriteArrayList<SimpleMailMessage> messages = new CopyOnWriteArrayList<>();

    @BeforeEach void prepare() {
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo(DATABASE.schema);
        jdbc.execute("TRUNCATE delivery_requests, account_tokens, users RESTART IDENTITY CASCADE");
        TIME.addAndGet(Duration.ofDays(2).toMillis());
        when(clock.instant()).thenAnswer(invocation -> Instant.ofEpochMilli(TIME.get()));
        when(clock.millis()).thenAnswer(invocation -> TIME.get());
        doAnswer(invocation -> { messages.add(new SimpleMailMessage(invocation.getArgument(0))); return null; })
                .when(sender).send(any(SimpleMailMessage.class));
    }
    @AfterEach void drained() { await().atMost(Duration.ofSeconds(8)).until(() -> accountMailExecutor.getActiveCount() == 0 && accountMailExecutor.getThreadPoolExecutor().getQueue().isEmpty()); }
    private MvcResult postJson(String path, Object body, int status) throws Exception {
        return mvc.perform(post("/api/auth/" + path).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(body))).andExpect(status().is(status)).andReturn();
    }
    private String mailToken(int index) {
        await().atMost(Duration.ofSeconds(8)).until(() -> messages.size() > index);
        String text = messages.get(index).getText();
        assertThat(text).contains("http://127.0.0.1:3000/").doesNotContain("?token=");
        var matcher = java.util.regex.Pattern.compile("#token=([A-Za-z0-9_-]{43})").matcher(text);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
    private String register() throws Exception {
        postJson("register", Map.of("name", "Trial User", "email", EMAIL, "password", PASSWORD), 201);
        return mailToken(0);
    }
    private MockHttpSession login(String password) throws Exception {
        return (MockHttpSession) postJson("login", Map.of("email", EMAIL, "password", password), 200).getRequest().getSession(false);
    }
    private void verified() throws Exception { postJson("verify-email", Map.of("token", register()), 200); }

    @Test void registrationRequiresVerificationAndStoresOnlyTokenHash() throws Exception {
        String token = register();
        assertThat(jdbc.queryForObject("SELECT token_hash FROM account_tokens", String.class)).hasSize(64).isNotEqualTo(token);
        postJson("login", Map.of("email", EMAIL, "password", "wrong password"), 401);
        var rejected = postJson("login", Map.of("email", EMAIL, "password", PASSWORD), 403);
        assertThat(rejected.getResponse().getContentAsString()).contains("Verify your email");
        // Opening a link (including email scanner GETs) cannot consume it.
        mvc.perform(get("/api/auth/verify-email")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/auth/verify-email").contentType("application/json").content(json.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isForbidden());
        postJson("verify-email", Map.of("token", token), 200);
        postJson("verify-email", Map.of("token", token), 400);
        mvc.perform(get("/api/auth/me").session(login(PASSWORD))).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(EMAIL));
        assertThat(jdbc.queryForObject("SELECT email_verified FROM users", Boolean.class)).isTrue();
    }

    @Test void resetIsSingleUseAndInvalidatesEveryOldSession() throws Exception {
        verified();
        MockHttpSession first = login(PASSWORD), second = login(PASSWORD);
        var known = postJson("forgot-password", Map.of("email", EMAIL), 202);
        var unknown = postJson("forgot-password", Map.of("email", "unknown@example.com"), 202);
        assertThat(known.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());
        String token = mailToken(1);
        String oldHash = jdbc.queryForObject("SELECT password_hash FROM users", String.class);
        postJson("verify-email", Map.of("token", token), 400);
        postJson("reset-password", Map.of("token", token, "password", "short"), 422);
        postJson("reset-password", Map.of("token", token, "password", NEW_PASSWORD), 200);
        postJson("reset-password", Map.of("token", token, "password", PASSWORD), 400);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users", String.class)).isNotEqualTo(oldHash);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account_tokens", Integer.class)).isZero();
        mvc.perform(get("/api/auth/me").session(first)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/requests").session(second)).andExpect(status().isUnauthorized());
        assertThat(first.isInvalid()).isTrue(); assertThat(second.isInvalid()).isTrue();
        postJson("login", Map.of("email", EMAIL, "password", PASSWORD), 401);
        login(NEW_PASSWORD);
        await().atMost(Duration.ofSeconds(8)).until(() -> messages.size() == 3);
        assertThat(messages.get(2).getSubject()).contains("password changed");
        assertThat(messages.get(2).getText()).doesNotContain(NEW_PASSWORD).doesNotContain("#token=");
    }

    @Test void replacementLinksRevokePreviousLinksAndExpiredLinksCannotBeUsed() throws Exception {
        String old = register();
        postJson("resend-verification", Map.of("email", " TRIAL@EXAMPLE.COM "), 202);
        String latest = mailToken(1);
        assertThat(latest).isNotEqualTo(old);
        postJson("verify-email", Map.of("token", old), 400);
        TIME.addAndGet(Duration.ofHours(25).toMillis());
        postJson("verify-email", Map.of("token", latest), 400);
        assertThat(jdbc.queryForObject("SELECT email_verified FROM users", Boolean.class)).isFalse();
        postJson("resend-verification", Map.of("email", EMAIL), 202);
        postJson("verify-email", Map.of("token", mailToken(2)), 200);
        postJson("forgot-password", Map.of("email", EMAIL), 202);
        String reset = mailToken(3);
        TIME.addAndGet(Duration.ofMinutes(31).toMillis());
        postJson("reset-password", Map.of("token", reset, "password", NEW_PASSWORD), 400);
        login(PASSWORD);
    }

    @Test void recoveryCannotClaimLegacyOrUnverifiedAccounts() throws Exception {
        demo.seed(); register();
        for (String email : new String[]{"customer@example.com", EMAIL, "missing@example.com"})
            postJson("forgot-password", Map.of("email", email), 202);
        postJson("resend-verification", Map.of("email", "customer@example.com"), 202);
        drained();
        assertThat(messages).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id=1", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account_tokens", Integer.class)).isEqualTo(1);
    }

    @Test void authenticationAndMailThrottlesApplyToUnknownAccountsAndRecoverAfterWindow() throws Exception {
        for (int i = 0; i < 10; i++) postJson("login", Map.of("email", "LIMIT@EXAMPLE.COM", "password", PASSWORD), 401);
        var blocked = postJson("login", Map.of("email", " limit@example.com ", "password", PASSWORD), 429);
        assertThat(blocked.getResponse().getHeader("Retry-After")).isEqualTo("900");
        postJson("login", Map.of("email", "different@example.com", "password", PASSWORD), 401);
        for (int i = 0; i < 3; i++) postJson("forgot-password", Map.of("email", EMAIL), 202);
        postJson("resend-verification", Map.of("email", EMAIL), 429);
        TIME.addAndGet(Duration.ofMinutes(15).toMillis());
        postJson("login", Map.of("email", "limit@example.com", "password", PASSWORD), 401);
        postJson("forgot-password", Map.of("email", EMAIL), 202);
    }

    @Test void simultaneousResetSubmissionsHaveExactlyOneWinner() throws Exception {
        verified(); postJson("forgot-password", Map.of("email", EMAIL), 202);
        String token = mailToken(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var task = (java.util.concurrent.Callable<Integer>) () -> {
                start.await();
                return mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("token", token, "password", NEW_PASSWORD))))
                        .andReturn().getResponse().getStatus();
            };
            var one = pool.submit(task); var two = pool.submit(task); start.countDown();
            assertThat(java.util.List.of(one.get(), two.get())).containsExactlyInAnyOrder(200, 400);
        }
        assertThat(jdbc.queryForObject("SELECT session_version FROM users", Long.class)).isEqualTo(1L);
        login(NEW_PASSWORD);
    }

    @Test void csrfAndPayloadValidationCoverEveryRecoveryEndpoint() throws Exception {
        for (String path : new String[]{"forgot-password", "resend-verification", "reset-password", "verify-email"}) {
            mvc.perform(post("/api/auth/" + path).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
            postJson(path, Map.of(), 422);
        }
        postJson("reset-password", Map.of("token", "a".repeat(43), "password", "x".repeat(129)), 422);
        postJson("verify-email", Map.of("token", "a".repeat(43)), 400);
        postJson("forgot-password", Map.of("email", "invalid"), 422);
    }

    @Test void smtpFailureDoesNotExposeDetailsAndResendCanRecover() throws Exception {
        doThrow(new org.springframework.mail.MailSendException("Private SMTP detail")).when(sender).send(any(SimpleMailMessage.class));
        postJson("register", Map.of("name", "Trial", "email", EMAIL, "password", PASSWORD), 201);
        drained();
        doAnswer(invocation -> { messages.add(new SimpleMailMessage(invocation.getArgument(0))); return null; })
                .when(sender).send(any(SimpleMailMessage.class));
        var result = postJson("resend-verification", Map.of("email", EMAIL), 202);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("SMTP").doesNotContain("token");
        postJson("verify-email", Map.of("token", mailToken(0)), 200);
        login(PASSWORD);
    }
}
