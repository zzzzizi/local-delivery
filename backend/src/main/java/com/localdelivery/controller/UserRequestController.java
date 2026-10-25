package com.localdelivery.controller;

import com.localdelivery.dto.DeliveryRequestResponse;
import com.localdelivery.service.DeliveryRequestService;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.security.Principal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class UserRequestController {
    private final DeliveryRequestService service;

    public UserRequestController(DeliveryRequestService service) {
        this.service = service;
    }

    @GetMapping("/users/{userId}/requests")
    public List<DeliveryRequestResponse> list(@PathVariable @Positive Long userId, Principal principal) {
        DeliveryRequestService.checkClaim(userId, Long.valueOf(principal.getName()));
        return service.listByCustomer(userId);
    }
    @GetMapping("/me/requests")
    public List<DeliveryRequestResponse> mine(Principal principal) {
        return service.listByCustomer(Long.valueOf(principal.getName()));
    }
}
