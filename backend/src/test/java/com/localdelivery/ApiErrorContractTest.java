package com.localdelivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localdelivery.controller.DeliveryRequestController;
import com.localdelivery.exception.*;
import com.localdelivery.model.DeliveryStatus;
import com.localdelivery.service.DeliveryRequestService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeliveryRequestController.class)
class ApiErrorContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean DeliveryRequestService service;

    static Stream<Arguments> serviceErrors() {
        return Stream.of(
                Arguments.of(new ResourceNotFoundException("Delivery request not found."), 404,
                        "Delivery request not found."),
                Arguments.of(new InvalidStatusTransitionException(DeliveryStatus.DELIVERED, DeliveryStatus.OPEN), 409,
                        "Cannot change delivery status from DELIVERED to OPEN."),
                Arguments.of(new RequestAlreadyAcceptedException(), 409, "Delivery request has already been accepted."),
                Arguments.of(new RequestNotOpenException(), 409, "Delivery request is no longer open for acceptance."),
                Arguments.of(new SelfAcceptanceException(), 400, "Customers cannot accept their own delivery requests."),
                Arguments.of(new DataIntegrityViolationException("private database details"), 409,
                        "Request conflicts with existing data."),
                Arguments.of(new ObjectOptimisticLockingFailureException("private persistence details", null), 409,
                        "Delivery request changed during update. Refresh and try again."),
                Arguments.of(new DataAccessResourceFailureException("private database details"), 503,
                        "Database temporarily unavailable. Try again later."),
                Arguments.of(new CannotCreateTransactionException("private database details"), 503,
                        "Database temporarily unavailable. Try again later."),
                Arguments.of(new IllegalStateException("private implementation details"), 500,
                        "An unexpected server error occurred."),
                Arguments.of(new HttpMessageNotWritableException("private serialization details"), 500,
                        "An unexpected server error occurred."),
                Arguments.of(new ResponseStatusException(HttpStatus.BAD_REQUEST, "private exception details"), 400,
                        "Invalid HTTP request.")
        );
    }

    @ParameterizedTest
    @MethodSource("serviceErrors")
    void businessAndServerFailuresUseTheSameSafeContract(RuntimeException failure, int status, String message)
            throws Exception {
        when(service.get(1L)).thenThrow(failure);
        var body = assertError(mvc.perform(get("/api/requests/1")), status, message);
        assertThat(body.get("fields").isEmpty()).isTrue();
    }

    static Stream<Arguments> frameworkErrors() {
        return Stream.of(
                Arguments.of(get("/api/missing-endpoint"), 404, "Endpoint not found."),
                Arguments.of(delete("/api/requests/1"), 405, "HTTP method not allowed for this endpoint."),
                Arguments.of(put("/api/requests/1/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PICKED_UP\"}"), 405, "HTTP method not allowed for this endpoint."),
                Arguments.of(post("/api/requests/1/accept").contentType(MediaType.TEXT_PLAIN)
                        .content("{\"helperId\":2}"), 415, "Unsupported request content type. Use application/json."),
                Arguments.of(get("/api/requests").accept(MediaType.APPLICATION_XML), 406,
                        "Requested response format is not supported.")
        );
    }

    @ParameterizedTest
    @MethodSource("frameworkErrors")
    void frameworkFailuresKeepTheirStatusAndUseJson(MockHttpServletRequestBuilder request, int status, String message)
            throws Exception {
        var result = mvc.perform(request);
        assertThat(assertError(result, status, message).get("fields").isEmpty()).isTrue();
        if (status == 405) {
            result.andExpect(header().string("Allow", containsString(request.buildRequest(
                    new org.springframework.mock.web.MockServletContext()).getRequestURI().endsWith("/status")
                    ? "PATCH" : "GET")));
        }
        if (status == 415) {
            result.andExpect(header().string("Accept", containsString("application/json")));
        }
        if (status != 406) verifyNoInteractions(service);
    }

    @Test
    void creationValidationIncludesAllFieldErrors() throws Exception {
        var body = assertError(mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content("{}")),
                422, "Validation failed.");
        var fields = new ArrayList<String>();
        body.get("fields").fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("customerId", "category", "title", "description",
                "pickupAddress", "deliveryAddress", "helperReward", "deadline");
        body.get("fields").forEach(message -> assertThat(message.asText()).isNotBlank());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"accept,helperId", "status,status"})
    void workflowValidationIncludesTheRelevantField(String operation, String field) throws Exception {
        var request = operation.equals("accept") ? post("/api/requests/1/accept") : patch("/api/requests/1/status");
        var body = assertError(mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{}")),
                422, "Validation failed.");
        assertThat(body.get("fields").size()).isEqualTo(1);
        assertThat(body.get("fields").get(field).asText()).isNotBlank();
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void invalidPathParametersUseTheSharedContract(String id) throws Exception {
        assertError(mvc.perform(get("/api/requests/" + id)), 422, "Invalid request body or parameter.");
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{", "{\"status\":\"private unknown enum\"}",
            "{\"status\":1}", "{\"status\":\"PICKED_UP\",\"privateExtraField\":1}"})
    void malformedAndUnreadableBodiesDoNotLeakParserDetails(String content) throws Exception {
        var body = assertError(mvc.perform(patch("/api/requests/1/status").contentType(MediaType.APPLICATION_JSON)
                .content(content)), 422, "Invalid request body or parameter.");
        assertThat(body.get("fields").isEmpty()).isTrue();
        verifyNoInteractions(service);
    }

    private JsonNode assertError(ResultActions result, int status, String message) throws Exception {
        var response = result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse();
        var body = json.readTree(response.getContentAsString());
        var keys = new ArrayList<String>();
        body.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("timestamp", "status", "error", "message", "fields");
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("error").asText()).isEqualTo(HttpStatus.valueOf(status).getReasonPhrase());
        assertThat(body.get("message").asText()).isEqualTo(message);
        assertThat(body.get("fields").isObject()).isTrue();
        var timestamp = OffsetDateTime.parse(body.get("timestamp").asText());
        assertThat(timestamp.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(timestamp).isBetween(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
                OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(1));
        assertThat(response.getContentAsString()).doesNotContain("private", "Exception", "stackTrace");
        return body;
    }
}
