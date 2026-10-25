package com.localdelivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localdelivery.dto.RegisterRequest;
import com.localdelivery.service.AccountService;
import com.localdelivery.service.DemoUserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SecurityApiTest {
    private static final PostgresTestDatabase DATABASE = new PostgresTestDatabase();
    private static final String PASSWORD = "Test-only long passphrase 20";
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
    @Autowired AccountService accounts;
    @Autowired DemoUserService demo;
    @Autowired PasswordEncoder passwords;
    long customerId;
    long helperId;
    MockHttpSession customer;
    MockHttpSession helper;

    @BeforeEach void setUp() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo(DATABASE.schema);
        jdbc.execute("TRUNCATE delivery_requests, users RESTART IDENTITY CASCADE");
        demo.seed();
        customerId = accounts.register(new RegisterRequest("Customer", "real-customer@example.com", PASSWORD)).id();
        helperId = accounts.register(new RegisterRequest("Helper", "real-helper@example.com", PASSWORD)).id();
        customer = login("real-customer@example.com");
        helper = login("real-helper@example.com");
    }

    private MockHttpSession login(String email) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    private String payload() throws Exception {
        return json.writeValueAsString(Map.of("category", "BUY", "title", "Authenticated errand",
                "description", "Test only", "pickupAddress", "Demo shop", "deliveryAddress", "Demo home",
                "helperReward", 80, "shoppingBudget", 200, "deadline", OffsetDateTime.now().plusDays(2).toString()));
    }
    private long create() throws Exception {
        return json.readTree(mvc.perform(post("/api/requests").session(customer).with(csrf())
                .contentType("application/json").content(payload())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
    private void accept(long id) throws Exception {
        mvc.perform(post("/api/requests/" + id + "/accept").session(helper).with(csrf())
                .contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.helperId").value(helperId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/me", "/api/requests", "/api/requests/1", "/api/me/requests",
            "/api/me/deliveries", "/api/users/1/requests", "/api/users/2/deliveries"})
    void anonymousReadsCannotExposePrivateData(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test void anonymousWriteWithValidCsrfStillRequiresLogin() throws Exception {
        mvc.perform(post("/api/requests").with(csrf()).contentType("application/json").content(payload()))
                .andExpect(status().isUnauthorized());
    }

    @Test void registrationNormalizesEmailHashesPasswordsAndExposesOnlyProfile() throws Exception {
        var body = json.readTree(mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", " New User ", "email", " NEW@Example.COM ", "password", PASSWORD))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(body.size()).isEqualTo(3);
        assertThat(body.get("name").asText()).isEqualTo("New User");
        assertThat(body.get("email").asText()).isEqualTo("new@example.com");
        var hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, body.get("id").asLong());
        assertThat(hash).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, hash)).isTrue();
        assertThat(hash).isNotEqualTo(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, customerId));
        mvc.perform(get("/api/auth/me").session(login("NEW@example.com"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(body.get("id").asLong())).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test void duplicateEmailCannotOverwriteExistingAccount() throws Exception {
        var before = jdbc.queryForList("SELECT * FROM users ORDER BY id");
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "Duplicate", "email", "REAL-CUSTOMER@EXAMPLE.COM", "password", PASSWORD))))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForList("SELECT * FROM users ORDER BY id")).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\"\",\"email\":\"invalid\",\"password\":\"short\"}",
            "{\"name\":\"Test\",\"email\":\"test@example.com\",\"password\":\"long-enough-password\",\"id\":1}"})
    void registrationValidatesInput(String body) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test void passwordLengthIsBounded() throws Exception {
        for (var password : new String[]{"short", "a".repeat(129)}) {
            mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                    .content(json.writeValueAsString(Map.of("name", "Test", "email", "test@example.com", "password", password))))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fields.password").exists());
        }
    }

    @Test void missingUnknownAndLegacyCredentialsFailWithSameSafeMessage() throws Exception {
        for (var email : new String[]{"missing@example.com", "real-customer@example.com", "customer@example.com"}) {
            mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                    .content(json.writeValueAsString(Map.of("email", email, "password", "incorrect password"))))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message")
                            .value("Invalid email or password. Please log in again."));
        }
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = 1", String.class)).isNull();
    }

    @Test void loginRotatesSessionIdAndCsrfTokenAndLogoutInvalidatesSession() throws Exception {
        var tokenResult = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) tokenResult.getRequest().getSession(false);
        var oldId = session.getId();
        JsonNode csrf = json.readTree(tokenResult.getResponse().getContentAsString());
        mvc.perform(post("/api/auth/login").session(session).header(csrf.get("headerName").asText(), csrf.get("token").asText())
                .contentType("application/json").content(json.writeValueAsString(Map.of("email", "real-customer@example.com", "password", PASSWORD))))
                .andExpect(status().isOk());
        assertThat(session.getId()).isNotEqualTo(oldId);
        mvc.perform(post("/api/auth/logout").session(session).header(csrf.get("headerName").asText(), csrf.get("token").asText()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void csrfRequiredForLoginRegistrationLogoutAndMutations() throws Exception {
        for (var path : new String[]{"/api/auth/login", "/api/auth/register", "/api/auth/logout", "/api/requests"}) {
            mvc.perform(post(path).session(customer).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post(path).session(customer).with(csrf().useInvalidToken()).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
        long id = create();
        mvc.perform(patch("/api/requests/" + id + "/status").session(customer).contentType("application/json")
                .content("{\"status\":\"CANCELLED\"}")).andExpect(status().isForbidden());
    }

    @Test void suppliedIdentityCannotImpersonateAnotherUser() throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(payload());
        body.put("customerId", helperId);
        mvc.perform(post("/api/requests").session(customer).with(csrf()).contentType("application/json").content(body.toString()))
                .andExpect(status().isForbidden());
        long id = create();
        mvc.perform(post("/api/requests/" + id + "/accept").session(helper).with(csrf()).contentType("application/json")
                .content("{\"helperId\":" + customerId + "}")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT helper_id FROM delivery_requests WHERE id = ?", Long.class, id)).isNull();
    }

    @Test void customerCannotAcceptOwnRequest() throws Exception {
        long id = create();
        mvc.perform(post("/api/requests/" + id + "/accept").session(customer).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void trackingListsArePrivateAndDerivedFromSession() throws Exception {
        long id = create(); accept(id);
        mvc.perform(get("/api/me/requests").session(customer)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/me/requests").session(helper)).andExpect(content().json("[]"));
        mvc.perform(get("/api/me/deliveries").session(helper)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/users/" + customerId + "/requests").session(helper)).andExpect(status().isForbidden());
        mvc.perform(get("/api/users/" + helperId + "/deliveries").session(customer)).andExpect(status().isForbidden());
    }

    @Test void onlyCustomerCanCancelBeforePickup() throws Exception {
        long id = create(); accept(id);
        var before = jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id);
        mvc.perform(patch("/api/requests/" + id + "/status").session(helper).with(csrf()).contentType("application/json")
                .content("{\"status\":\"CANCELLED\"}")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id)).isEqualTo(before);
        mvc.perform(patch("/api/requests/" + id + "/status").session(customer).with(csrf()).contentType("application/json")
                .content("{\"status\":\"CANCELLED\"}")).andExpect(status().isOk());
    }

    @Test void onlyAssignedHelperCanAdvanceAndOtherUsersCannotReadAssignedDetails() throws Exception {
        long id = create(); accept(id);
        accounts.register(new RegisterRequest("Stranger", "stranger@example.com", PASSWORD));
        var stranger = login("stranger@example.com");
        mvc.perform(get("/api/requests/" + id).session(stranger)).andExpect(status().isForbidden());
        var before = jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id);
        for (var actor : new MockHttpSession[]{customer, stranger}) {
            mvc.perform(patch("/api/requests/" + id + "/status").session(actor).with(csrf()).contentType("application/json")
                    .content("{\"status\":\"PICKED_UP\"}")).andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForMap("SELECT * FROM delivery_requests WHERE id = ?", id)).isEqualTo(before);
        for (var status : new String[]{"PICKED_UP", "DELIVERING", "DELIVERED"}) {
            mvc.perform(patch("/api/requests/" + id + "/status").session(helper).with(csrf()).contentType("application/json")
                    .content("{\"status\":\"" + status + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(status));
        }
        mvc.perform(patch("/api/requests/" + id + "/status").session(customer).with(csrf()).contentType("application/json")
                .content("{\"status\":\"CANCELLED\"}")).andExpect(status().isConflict());
    }
}
