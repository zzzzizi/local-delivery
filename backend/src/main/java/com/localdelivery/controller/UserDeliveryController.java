package com.localdelivery.controller;

import com.localdelivery.dto.DeliveryRequestResponse;
import com.localdelivery.service.DeliveryRequestService;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}/deliveries")
@Validated
public class UserDeliveryController {
    private final DeliveryRequestService service;

    public UserDeliveryController(DeliveryRequestService service) {
        this.service = service;
    }

    @GetMapping
    public List<DeliveryRequestResponse> list(@PathVariable @Positive Long userId) {
        return service.listByHelper(userId);
    }
}
