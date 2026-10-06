package com.localdelivery;

import com.localdelivery.dto.AcceptRequestDto;
import com.localdelivery.dto.UpdateStatusRequest;
import com.localdelivery.model.DeliveryStatus;
import jakarta.validation.Valid;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkflowDtoValidationTest.ValidationProbe.class)
@Import(WorkflowDtoValidationTest.ValidationProbe.class)
class WorkflowDtoValidationTest {
    @Autowired MockMvc mvc;

    // Only this MVC slice imports these probes; they never become application endpoints.
    @TestComponent
    @RestController
    static class ValidationProbe {
        @PostMapping("/test/dto/accept")
        AcceptRequestDto accept(@Valid @RequestBody AcceptRequestDto input) {
            return input;
        }

        @PostMapping("/test/dto/status")
        UpdateStatusRequest status(@Valid @RequestBody UpdateStatusRequest input) {
            return input;
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, Long.MAX_VALUE})
    void acceptsPositiveHelperIds(long helperId) throws Exception {
        mvc.perform(post("/test/dto/accept").contentType("application/json")
                        .content("{\"helperId\":" + helperId + "}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"helperId\":" + helperId + "}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"helperId\":null}", "{\"helperId\":0}", "{\"helperId\":-1}"})
    void requiresPositiveHelperId(String body) throws Exception {
        mvc.perform(post("/test/dto/accept").contentType("application/json").content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.fields.helperId").isString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"helperId\":1.5}", "{\"helperId\":\"2\"}", "{\"helperId\":true}",
            "{\"helperId\":9223372036854775808}", "{\"helperId\":2,\"status\":\"ACCEPTED\"}",
            "{\"helper_id\":2}", "null", "[]", "{"
    })
    void rejectsInvalidAcceptanceBodies(String body) throws Exception {
        assertInvalidBody("/test/dto/accept", body);
    }

    @ParameterizedTest
    @EnumSource(DeliveryStatus.class)
    void acceptsEveryKnownStatusValue(DeliveryStatus deliveryStatus) throws Exception {
        String body = "{\"status\":\"" + deliveryStatus.name() + "\"}";
        mvc.perform(post("/test/dto/status").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(content().json(body));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}"})
    void requiresStatus(String body) throws Exception {
        mvc.perform(post("/test/dto/status").contentType("application/json").content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.fields.status").isString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"status\":\"UNKNOWN\"}", "{\"status\":\"picked_up\"}", "{\"status\":\"\"}",
            "{\"status\":0}", "{\"status\":\"0\"}", "{\"status\":true}",
            "{\"status\":\"PICKED_UP\",\"helperId\":2}", "null", "[]", "{"
    })
    void rejectsInvalidStatusBodies(String body) throws Exception {
        assertInvalidBody("/test/dto/status", body);
    }

    private void assertInvalidBody(String path, String body) throws Exception {
        mvc.perform(post(path).contentType("application/json").content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.message").value("Invalid request body or parameter."));
    }
}
