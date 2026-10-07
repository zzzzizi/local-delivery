package com.localdelivery;

import com.localdelivery.controller.DeliveryRequestController;
import com.localdelivery.dto.AcceptRequestDto;
import com.localdelivery.exception.ApiExceptionHandler;
import com.localdelivery.service.DeliveryRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
            when(service.get(1L)).thenThrow(failure);
            var mvc = MockMvcBuilders.standaloneSetup(new DeliveryRequestController(service))
                    .setControllerAdvice(new ApiExceptionHandler()).build();
            for (String path : new String[]{"/api/requests", "/api/requests/1"}) {
                mvc.perform(get(path)).andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.status").value(503))
                        .andExpect(jsonPath("$.message").value("Database temporarily unavailable. Try again later."))
                        .andExpect(content().string(not(containsString("private database details"))));
            }
        }
    }

    @Test
    void concurrentAcceptanceReturnsSafeConflict() throws Exception {
        var service = mock(DeliveryRequestService.class);
        when(service.accept(1L, new AcceptRequestDto(2L)))
                .thenThrow(new ObjectOptimisticLockingFailureException("private persistence details", null));
        var mvc = MockMvcBuilders.standaloneSetup(new DeliveryRequestController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/requests/1/accept").contentType("application/json").content("{\"helperId\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Delivery request changed during acceptance. Refresh and try again."))
                .andExpect(content().string(not(containsString("private persistence details"))));
    }

    @Test
    void handlesDatabaseConflict() throws Exception {
        var service = mock(DeliveryRequestService.class);
        when(service.get(1L)).thenThrow(new DataIntegrityViolationException("private details"));
        var mvc = MockMvcBuilders.standaloneSetup(new DeliveryRequestController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/requests/1")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Request conflicts with existing data."));
    }
}
