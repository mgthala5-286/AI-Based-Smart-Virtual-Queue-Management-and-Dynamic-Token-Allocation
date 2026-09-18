package com.smartqueue.service;

import com.smartqueue.dto.CreateTokenRequest;
import com.smartqueue.dto.TokenResponse;
import com.smartqueue.exception.ForbiddenOperationException;
import com.smartqueue.exception.InvalidRequestException;
import com.smartqueue.exception.ResourceNotFoundException;
import com.smartqueue.model.QueueEvent;
import com.smartqueue.model.Service;
import com.smartqueue.model.Token;
import com.smartqueue.model.TokenStatus;
import com.smartqueue.model.User;
import com.smartqueue.model.UserRole;
import com.smartqueue.repository.QueueEventRepository;
import com.smartqueue.repository.ServiceRepository;
import com.smartqueue.repository.TokenRepository;
import com.smartqueue.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@org.springframework.stereotype.Service
public class TokenService {

    private static final String TOKEN_CREATED = "TOKEN_CREATED";
    private static final String TOKEN_CANCELLED = "TOKEN_CANCELLED";
    private static final Pattern TOKEN_NUMBER_PATTERN = Pattern.compile("^(.*?)(\\d+)$");

    private final TokenRepository tokenRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final QueueEventRepository queueEventRepository;
    private final DynamicTokenAllocationService dynamicTokenAllocationService;

    public TokenService(
            TokenRepository tokenRepository,
            ServiceRepository serviceRepository,
            UserRepository userRepository,
            QueueEventRepository queueEventRepository,
            DynamicTokenAllocationService dynamicTokenAllocationService) {
        this.tokenRepository = tokenRepository;
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
        this.queueEventRepository = queueEventRepository;
        this.dynamicTokenAllocationService = dynamicTokenAllocationService;
    }

    /**
     * Creates a WAITING token and immediately lets the dynamic allocation
     * service refresh the queue position and basic waiting time.
     */
    public synchronized TokenResponse createToken(CreateTokenRequest request, String userId) {
        User user = requireUser(userId);
        if (user.getRole() != UserRole.USER) {
            throw new ForbiddenOperationException("Only USER accounts can create tokens");
        }

        Service service = requireActiveService(request.getServiceId());
        LocalDateTime createdAt = LocalDateTime.now();
        Token token = Token.builder()
                .id(UUID.randomUUID().toString())
                .tokenNumber(nextTokenNumber(service))
                .userId(userId)
                .serviceId(service.getId())
                .status(TokenStatus.WAITING)
                .createdAt(createdAt)
                .queuePosition(0)
                .estimatedWaitTime(0)
                .build();

        Token savedToken = tokenRepository.save(token);
        queueEventRepository.save(QueueEvent.builder()
                .id(UUID.randomUUID().toString())
                .tokenId(savedToken.getId())
                .eventType(TOKEN_CREATED)
                .oldPosition(0)
                .newPosition(0)
                .timestamp(createdAt)
                .build());

        dynamicTokenAllocationService.recalculateAfterTokenChange(service.getId());
        return toResponse(tokenRepository.findById(savedToken.getId()).orElse(savedToken));
    }

    public TokenResponse getToken(String id, String requesterId, boolean elevatedRequester) {
        User requester = requireUser(requesterId);
        Token token = findToken(id);
        ensureOwnerOrElevated(token, requester, elevatedRequester);
        return toResponse(token);
    }

    public List<TokenResponse> getUserTokens(
            String userId,
            String requesterId,
            boolean elevatedRequester) {
        User requester = requireUser(requesterId);
        User requestedUser = requireUser(userId);
        if (!elevatedRequester && !requester.getId().equals(requestedUser.getId())) {
            throw new ForbiddenOperationException("Users can only access their own tokens");
        }

        return tokenRepository.readAll().stream()
                .filter(token -> requestedUser.getId().equals(token.getUserId()))
                .map(this::toResponse)
                .toList();
    }

    public TokenResponse cancelToken(String id, String requesterId, boolean elevatedRequester) {
        User requester = requireUser(requesterId);
        Token token = findToken(id);
        ensureOwnerOrElevated(token, requester, elevatedRequester);

        if (token.getStatus() == TokenStatus.CANCELLED) {
            return toResponse(token);
        }

        int oldPosition = token.getQueuePosition();
        token.setStatus(TokenStatus.CANCELLED);
        Token updatedToken = tokenRepository.update(token);

        queueEventRepository.save(QueueEvent.builder()
                .id(UUID.randomUUID().toString())
                .tokenId(updatedToken.getId())
                .eventType(TOKEN_CANCELLED)
                .oldPosition(oldPosition)
                .newPosition(updatedToken.getQueuePosition())
                .timestamp(LocalDateTime.now())
                .build());

        dynamicTokenAllocationService.recalculateAfterTokenChange(updatedToken.getServiceId());
        return toResponse(tokenRepository.findById(updatedToken.getId()).orElse(updatedToken));
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user not found: " + userId));
    }

    private Service requireActiveService(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            throw new InvalidRequestException("serviceId is required");
        }

        Service service = serviceRepository.findById(serviceId.trim())
                .orElseThrow(() -> new InvalidRequestException(
                        "serviceId must refer to an existing active service"));
        if (!service.isActive()) {
            throw new InvalidRequestException(
                    "serviceId must refer to an existing active service");
        }
        return service;
    }

    private Token findToken(String id) {
        return tokenRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found: " + id));
    }

    private void ensureOwnerOrElevated(Token token, User requester, boolean elevatedRequester) {
        if (!elevatedRequester && !requester.getId().equals(token.getUserId())) {
            throw new ForbiddenOperationException(
                    "Only the token owner, STAFF, or ADMIN can access this token");
        }
    }

    private String nextTokenNumber(Service service) {
        String prefix = tokenPrefix(service.getName());
        int nextNumber = tokenRepository.readAll().stream()
                .filter(token -> service.getId().equals(token.getServiceId()))
                .filter(token -> token.getStatus() != TokenStatus.CANCELLED)
                .map(Token::getTokenNumber)
                .mapToInt(tokenNumber -> numericPart(tokenNumber, prefix))
                .max()
                .orElse(0) + 1;

        String tokenNumber = formatTokenNumber(prefix, nextNumber);
        while (hasActiveTokenNumber(service.getId(), tokenNumber)) {
            nextNumber++;
            tokenNumber = formatTokenNumber(prefix, nextNumber);
        }
        return tokenNumber;
    }

    private boolean hasActiveTokenNumber(String serviceId, String tokenNumber) {
        return tokenRepository.readAll().stream()
                .anyMatch(token -> serviceId.equals(token.getServiceId())
                        && tokenNumber.equals(token.getTokenNumber())
                        && token.getStatus() != TokenStatus.CANCELLED);
    }

    private int numericPart(String tokenNumber, String prefix) {
        if (tokenNumber == null || !tokenNumber.startsWith(prefix)) {
            return 0;
        }

        Matcher matcher = TOKEN_NUMBER_PATTERN.matcher(tokenNumber);
        if (!matcher.matches() || !prefix.equals(matcher.group(1))) {
            return 0;
        }

        try {
            return Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String formatTokenNumber(String prefix, int number) {
        return prefix + String.format(Locale.ROOT, "%03d", number);
    }

    private String tokenPrefix(String serviceName) {
        if (serviceName != null) {
            for (char character : serviceName.trim().toCharArray()) {
                if (Character.isLetterOrDigit(character)) {
                    return String.valueOf(Character.toUpperCase(character));
                }
            }
        }
        return "S";
    }

    private TokenResponse toResponse(Token token) {
        return new TokenResponse(
                token.getId(),
                token.getTokenNumber(),
                token.getServiceId(),
                token.getStatus(),
                token.getCreatedAt(),
                token.getQueuePosition(),
                token.getEstimatedWaitTime());
    }
}
