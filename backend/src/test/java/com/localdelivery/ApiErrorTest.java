package com.localdelivery;

import com.localdelivery.controller.DeliveryRequestController;
import com.localdelivery.exception.ApiExceptionHandler;
import com.localdelivery.service.DeliveryRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;

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
            var mvc = MockMvcBuilders.standaloneSetup(new DeliveryRequestController(service))
                    .setControllerAdvice(new ApiExceptionHandler()).build();
            mvc.perform(get("/api/requests")).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").value("Database temporarily unavailable. Try again later."));
        }
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
