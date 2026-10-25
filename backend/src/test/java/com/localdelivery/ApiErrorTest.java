package com.localdelivery;

import com.localdelivery.controller.DeliveryRequestController;
import com.localdelivery.dto.AcceptRequestDto;
import com.localdelivery.dto.UpdateStatusRequest;
import com.localdelivery.exception.ApiExceptionHandler;
import com.localdelivery.model.DeliveryStatus;
import com.localdelivery.service.DeliveryRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiErrorTest {
    @Test
    void hidesDatabaseFailureDetails() throws Exception {
        for (RuntimeException failure : new RuntimeException[]{
                new DataAccessResourceFailureException("private database details"),
                new CannotCreateTransactionException("private database details")}) {
            var service = mock(DeliveryRequestService.class);
            when(service.listOpen()).thenThrow(failure);
            when(service.get(1L, 1L)).thenThrow(failure);
            when(service.updateStatus(1L, new UpdateStatusRequest(DeliveryStatus.PICKED_UP), 1L)).thenThrow(failure);
            var mvc = MockMvcBuilders.standaloneSetup(new DeliveryRequestController(service))
                    .setControllerAdvice(new ApiExceptionHandler()).defaultRequest(get("/").principal(() -> "1")).build();
            for (var request : new MockHttpServletRequestBuilder[]{
                    get("/api/requests"), get("/api/requests/1"),
                    patch("/api/requests/1/status").contentType("application/json").content("{\"status\":\"PICKED_UP\"}")}) {
                mvc.perform(request).andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.status").value(503))
                        .andExpect(jsonPath("$.message").value("Database temporarily unavailable. Try again later."))
                        .andExpect(content().string(not(containsString("private database details"))));
            }
        }
    }

    @Test
    void concurrentUpdatesReturnSafeConflict() throws Exception {
        var service = mock(DeliveryRequestService.class);
        when(service.accept(1L, new AcceptRequestDto(2L), 1L))
                .thenThrow(new ObjectOptimisticLockingFailureException("private persistence details", null));
        when(service.updateStatus(1L, new UpdateStatusRequest(DeliveryStatus.PICKED_UP), 1L))
                .thenThrow(new ObjectOptimisticLockingFailureException("private persistence details", null));
        var mvc = MockMvcBuilders.standaloneSetup(new DeliveryRequestController(service))
                .setControllerAdvice(new ApiExceptionHandler()).defaultRequest(get("/").principal(() -> "1")).build();
        for (var request : new MockHttpServletRequestBuilder[]{
                post("/api/requests/1/accept").content("{\"helperId\":2}"),
                patch("/api/requests/1/status").content("{\"status\":\"PICKED_UP\"}")}) {
            mvc.perform(request.contentType("application/json"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Delivery request changed during update. Refresh and try again."))
                .andExpect(content().string(not(containsString("private persistence details"))));
        }
    }

    @Test
    void handlesDatabaseConflict() throws Exception {
        var service = mock(DeliveryRequestService.class);
        when(service.get(1L, 1L)).thenThrow(new DataIntegrityViolationException("private details"));
        var mvc = MockMvcBuilders.standaloneSetup(new DeliveryRequestController(service))
                .setControllerAdvice(new ApiExceptionHandler()).defaultRequest(get("/").principal(() -> "1")).build();
        mvc.perform(get("/api/requests/1")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Request conflicts with existing data."));
    }
}
