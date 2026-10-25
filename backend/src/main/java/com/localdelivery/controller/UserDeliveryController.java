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
public class UserDeliveryController {
    private final DeliveryRequestService service;

    public UserDeliveryController(DeliveryRequestService service) {
        this.service = service;
    }

    @GetMapping("/users/{userId}/deliveries")
    public List<DeliveryRequestResponse> list(@PathVariable @Positive Long userId, Principal principal) {
        DeliveryRequestService.checkClaim(userId, Long.valueOf(principal.getName()));
        return service.listByHelper(userId);
    }
    @GetMapping("/me/deliveries")
    public List<DeliveryRequestResponse> mine(Principal principal) {
        return service.listByHelper(Long.valueOf(principal.getName()));
    }
}
