package com.localdelivery.controller;

import com.localdelivery.dto.CreateDeliveryRequestRequest;
import com.localdelivery.dto.AcceptRequestDto;
import com.localdelivery.dto.DeliveryRequestResponse;
import com.localdelivery.dto.UpdateStatusRequest;
import com.localdelivery.service.DeliveryRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.security.Principal;
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
    public DeliveryRequestResponse create(@Valid @RequestBody CreateDeliveryRequestRequest request, Principal principal) {
        return service.create(request, Long.valueOf(principal.getName()));
    }

    @GetMapping
    public List<DeliveryRequestResponse> list() {
        return service.listOpen();
    }

    @PostMapping("/{id}/accept")
    public DeliveryRequestResponse accept(@PathVariable @Positive Long id,
                                          @Valid @RequestBody AcceptRequestDto request, Principal principal) {
        return service.accept(id, request, Long.valueOf(principal.getName()));
    }

    @PatchMapping("/{id}/status")
    public DeliveryRequestResponse updateStatus(@PathVariable @Positive Long id,
                                                @Valid @RequestBody UpdateStatusRequest request, Principal principal) {
        return service.updateStatus(id, request, Long.valueOf(principal.getName()));
    }

    @GetMapping("/{id}")
    public DeliveryRequestResponse get(@PathVariable @Positive Long id, Principal principal) {
        return service.get(id, Long.valueOf(principal.getName()));
    }
}
