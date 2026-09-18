package com.smartqueue.controller;

import com.smartqueue.dto.QueueItemResponse;
import com.smartqueue.dto.QueuePositionResponse;
import com.smartqueue.dto.QueueSizeResponse;
import com.smartqueue.service.QueueService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/{serviceId}")
    public List<QueueItemResponse> getCurrentQueue(@PathVariable String serviceId) {
        return queueService.getCurrentQueue(serviceId);
    }

    @GetMapping("/{serviceId}/position/{tokenId}")
    public QueuePositionResponse getQueuePosition(
            @PathVariable String serviceId,
            @PathVariable String tokenId,
            Authentication authentication) {
        return queueService.getQueuePositionForRequester(
                serviceId,
                tokenId,
                authentication.getName(),
                isElevated(authentication));
    }

    @GetMapping("/{serviceId}/size")
    public QueueSizeResponse getQueueSize(@PathVariable String serviceId) {
        return queueService.getQueueSize(serviceId);
    }

    private boolean isElevated(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_STAFF")
                        || authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
