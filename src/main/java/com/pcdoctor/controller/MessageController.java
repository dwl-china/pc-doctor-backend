package com.pcdoctor.controller;

import com.pcdoctor.common.Result;
import com.pcdoctor.dto.MessageRequest;
import com.pcdoctor.dto.MessageView;
import com.pcdoctor.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/appointments/{appointmentId}/messages")
    public Result<List<MessageView>> list(@PathVariable Long appointmentId) {
        return Result.ok(messageService.list(appointmentId));
    }

    @PostMapping("/appointments/{appointmentId}/messages")
    public Result<MessageView> post(@PathVariable Long appointmentId,
                                    @Valid @RequestBody MessageRequest request,
                                    Authentication authentication) {
        return Result.ok(messageService.post(appointmentId, request, authentication.getName()));
    }

    /** 删除留言（走查建议 #3）：本人可删自己的；电医/管理员可删任意违法留言 */
    @DeleteMapping("/messages/{messageId}")
    public Result<Void> delete(@PathVariable Long messageId,
                               Authentication authentication) {
        messageService.delete(messageId, authentication.getName(), isPrivileged(authentication));
        return Result.ok();
    }

    private boolean isPrivileged(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_DOCTOR".equals(a.getAuthority()));
    }
}
