package com.ricestoremanagement.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ricestoremanagement.dto.chat.ChatMessageRequest;
import com.ricestoremanagement.dto.chat.ChatMessageResponse;
import com.ricestoremanagement.model.enums.OrderSource;
import com.ricestoremanagement.service.ChatbotOrderFlowService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/chat")
public class WebChatController {
    private final ChatbotOrderFlowService chatbotOrderFlowService;

    public WebChatController(ChatbotOrderFlowService chatbotOrderFlowService) {
        this.chatbotOrderFlowService = chatbotOrderFlowService;
    }

    @PostMapping("/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(@Valid @RequestBody ChatMessageRequest request) {
        String sessionId = isNotBlank(request.getSessionId())
                ? request.getSessionId().trim()
                : UUID.randomUUID().toString();

        ChatMessageResponse response = chatbotOrderFlowService.handleMessage(
                "web",
                sessionId,
                "WebChat:" + sessionId,
                OrderSource.WEB,
                request.getMessage());

        return ResponseEntity.ok(response);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
