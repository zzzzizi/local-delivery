package com.localdelivery.controller;

import com.localdelivery.dto.CreateDeliveryRequestRequest;
import com.localdelivery.dto.AcceptRequestDto;
import com.localdelivery.dto.DeliveryRequestResponse;
import com.localdelivery.service.DeliveryRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/requests")
@Validated
public class DeliveryRequestController {
    private final DeliveryRequestService service;

    public DeliveryRequestController(DeliveryRequestService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryRequestResponse create(@Valid @RequestBody CreateDeliveryRequestRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<DeliveryRequestResponse> list() {
        return service.listOpen();
    }

    @PostMapping("/{id}/accept")
    public DeliveryRequestResponse accept(@PathVariable @Positive Long id,
                                          @Valid @RequestBody AcceptRequestDto request) {
        return service.accept(id, request);
    }

    @GetMapping("/{id}")
    public DeliveryRequestResponse get(@PathVariable @Positive Long id) {
        return service.get(id);
    }
}
