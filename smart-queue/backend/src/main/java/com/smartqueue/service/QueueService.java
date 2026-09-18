package com.smartqueue.service;

import com.smartqueue.dto.QueueItemResponse;
import com.smartqueue.dto.QueuePositionResponse;
import com.smartqueue.dto.QueueSizeResponse;
import com.smartqueue.exception.ForbiddenOperationException;
import com.smartqueue.exception.InvalidRequestException;
import com.smartqueue.exception.ResourceNotFoundException;
import com.smartqueue.model.Token;
import com.smartqueue.model.User;
import com.smartqueue.repository.TokenRepository;
import com.smartqueue.repository.UserRepository;

import java.util.List;

@org.springframework.stereotype.Service
public class QueueService {

    private final DynamicTokenAllocationService dynamicTokenAllocationService;
    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;

    public QueueService(
            DynamicTokenAllocationService dynamicTokenAllocationService,
            TokenRepository tokenRepository,
            UserRepository userRepository) {
        this.dynamicTokenAllocationService = dynamicTokenAllocationService;
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
    }

    public List<QueueItemResponse> getCurrentQueue(String serviceId) {
        return dynamicTokenAllocationService.allocateTokens(serviceId);
    }

    public QueuePositionResponse getQueuePosition(String tokenId) {
        Token token = findToken(tokenId);
        dynamicTokenAllocationService.recalculateAfterTokenChange(token.getServiceId());
        return toQueuePosition(findToken(tokenId));
    }

    public QueuePositionResponse getQueuePositionForRequester(
            String serviceId,
            String tokenId,
            String requesterId,
            boolean elevatedRequester) {
        User requester = requireUser(requesterId);
        Token token = findToken(tokenId);
        validateTokenBelongsToService(token, serviceId);
        if (!elevatedRequester && !requester.getId().equals(token.getUserId())) {
            throw new ForbiddenOperationException(
                    "Users can only access detailed position information for their own token");
        }
        dynamicTokenAllocationService.recalculateAfterTokenChange(serviceId);
        return toQueuePosition(findToken(tokenId));
    }

    public QueueSizeResponse getQueueSize(String serviceId) {
        dynamicTokenAllocationService.recalculateAfterTokenChange(serviceId);
        return new QueueSizeResponse(
                serviceId,
                dynamicTokenAllocationService.getActiveQueueSize(serviceId));
    }

    public void recalculateQueue(String serviceId) {
        dynamicTokenAllocationService.recalculateAfterTokenChange(serviceId);
    }

    public double calculateBasicWaitingTime(String tokenId) {
        return dynamicTokenAllocationService.calculateBasicWaitingTime(tokenId);
    }

    private Token findToken(String tokenId) {
        return tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found: " + tokenId));
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user not found: " + userId));
    }

    private void validateTokenBelongsToService(Token token, String serviceId) {
        if (!serviceId.equals(token.getServiceId())) {
            throw new InvalidRequestException("Token does not belong to the requested service");
        }
    }

    private QueuePositionResponse toQueuePosition(Token token) {
        int peopleAhead = token.getQueuePosition() > 0
                ? token.getQueuePosition() - 1
                : 0;
        return new QueuePositionResponse(
                token.getTokenNumber(),
                token.getQueuePosition(),
                peopleAhead,
                token.getStatus(),
                token.getEstimatedWaitTime());
    }
}
