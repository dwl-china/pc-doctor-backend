package com.pcdoctor.controller;

import com.pcdoctor.common.Result;
import com.pcdoctor.dto.MessageRequest;
import com.pcdoctor.dto.MessageView;
import com.pcdoctor.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 预约留言接口（契约 §4.5）。
 */
@RestController
@RequestMapping("/api/appointments/{appointmentId}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public Result<List<MessageView>> list(@PathVariable Long appointmentId) {
        return Result.ok(messageService.list(appointmentId));
    }

    @PostMapping
    public Result<MessageView> post(@PathVariable Long appointmentId,
                                    @Valid @RequestBody MessageRequest request,
                                    Authentication authentication) {
        return Result.ok(messageService.post(appointmentId, request, authentication.getName()));
    }
}