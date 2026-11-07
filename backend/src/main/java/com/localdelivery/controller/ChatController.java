package com.localdelivery.controller;

import com.localdelivery.dto.ChatDtos.*;
import com.localdelivery.service.ChatService;
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
public class ChatController {
    private final ChatService service;
    public ChatController(ChatService service) { this.service = service; }

    @PostMapping("/requests/{id}/conversation")
    public Conversation start(@PathVariable @Positive long id, Principal principal) {
        return service.start(id, Long.parseLong(principal.getName()));
    }
    @GetMapping("/conversations")
    public ConversationPage list(@RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(required = false) @Positive Long requestId, Principal principal) {
        return service.list(Long.parseLong(principal.getName()), page, requestId);
    }
    @GetMapping("/conversations/{id}")
    public Conversation get(@PathVariable @Positive long id, Principal principal) {
        return service.get(id, Long.parseLong(principal.getName()));
    }
    @GetMapping("/conversations/{id}/messages")
    public MessagePage messages(@PathVariable @Positive long id,
            @RequestParam(required = false) @Positive Long before,
            @RequestParam(required = false) @Min(0) Long after, Principal principal) {
        return service.messages(id, Long.parseLong(principal.getName()), before, after);
    }
    @PostMapping("/conversations/{id}/messages")
    public Message send(@PathVariable @Positive long id, @Valid @RequestBody SendMessage input, Principal principal) {
        return service.send(id, Long.parseLong(principal.getName()), input);
    }
}
