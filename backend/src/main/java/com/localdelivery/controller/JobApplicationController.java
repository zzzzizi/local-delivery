package com.localdelivery.controller;

import com.localdelivery.dto.AcceptRequestDto;
import com.localdelivery.dto.DeliveryRequestResponse;
import com.localdelivery.dto.JobApplicationResponse;
import com.localdelivery.service.DeliveryRequestService;
import com.localdelivery.service.JobApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.security.Principal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Validated
public class JobApplicationController {
    private final JobApplicationService service;
    public JobApplicationController(JobApplicationService service) { this.service = service; }

    // /accept remains a compatibility alias, but can only request customer approval.
    @PostMapping({"/requests/{id}/applications", "/requests/{id}/accept"})
    public JobApplicationResponse apply(@PathVariable @Positive long id,
            @Valid @RequestBody AcceptRequestDto input, Principal principal) {
        long actor = Long.parseLong(principal.getName());
        DeliveryRequestService.checkClaim(input.helperId(), actor);
        return service.apply(id, actor);
    }
    @GetMapping("/requests/{id}/applications")
    public JobApplicationResponse.Page list(@PathVariable @Positive long id,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page, Principal principal) {
        return service.list(id, Long.parseLong(principal.getName()), page);
    }
    @GetMapping("/me/applications")
    public JobApplicationResponse.Page mine(@RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page, Principal principal) {
        return service.mine(Long.parseLong(principal.getName()), page);
    }
    @PostMapping("/requests/{id}/applications/{applicationId}/approve")
    public DeliveryRequestResponse approve(@PathVariable @Positive long id,
            @PathVariable @Positive long applicationId, Principal principal) {
        return service.approve(id, applicationId, Long.parseLong(principal.getName()));
    }
    @PostMapping("/requests/{id}/applications/{applicationId}/decline")
    public JobApplicationResponse decline(@PathVariable @Positive long id,
            @PathVariable @Positive long applicationId, Principal principal) {
        return service.decline(id, applicationId, Long.parseLong(principal.getName()));
    }
    @PostMapping("/requests/{id}/applications/{applicationId}/withdraw")
    public JobApplicationResponse withdraw(@PathVariable @Positive long id,
            @PathVariable @Positive long applicationId, Principal principal) {
        return service.withdraw(id, applicationId, Long.parseLong(principal.getName()));
    }
}
