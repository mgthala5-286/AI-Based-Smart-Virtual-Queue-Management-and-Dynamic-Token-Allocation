package com.smartqueue.service;

import com.smartqueue.dto.MlWaitingTimeRequest;
import com.smartqueue.dto.QueueItemResponse;
import com.smartqueue.exception.ResourceNotFoundException;
import com.smartqueue.model.Counter;
import com.smartqueue.model.Prediction;
import com.smartqueue.model.QueueEvent;
import com.smartqueue.model.Service;
import com.smartqueue.model.Token;
import com.smartqueue.model.TokenStatus;
import com.smartqueue.repository.CounterRepository;
import com.smartqueue.repository.PredictionRepository;
import com.smartqueue.repository.QueueEventRepository;
import com.smartqueue.repository.ServiceRepository;
import com.smartqueue.repository.TokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

@org.springframework.stereotype.Service
public class DynamicTokenAllocationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicTokenAllocationService.class);
    private static final Set<TokenStatus> ACTIVE_QUEUE_STATUSES =
            EnumSet.of(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.SERVING);
    private static final String POSITION_CHANGED = "POSITION_CHANGED";
    private static final String ML_SOURCE = "ML";
    private static final String FALLBACK_SOURCE = "FALLBACK";

    private final TokenRepository tokenRepository;
    private final ServiceRepository serviceRepository;
    private final CounterRepository counterRepository;
    private final QueueEventRepository queueEventRepository;
    private final PredictionRepository predictionRepository;
    private final DynamicTokenAllocationStrategy allocationStrategy;
    private final MlWaitingTimeService mlWaitingTimeService;

    public DynamicTokenAllocationService(
            TokenRepository tokenRepository,
            ServiceRepository serviceRepository,
            CounterRepository counterRepository,
            QueueEventRepository queueEventRepository,
            PredictionRepository predictionRepository,
            DynamicTokenAllocationStrategy allocationStrategy,
            MlWaitingTimeService mlWaitingTimeService) {
        this.tokenRepository = tokenRepository;
        this.serviceRepository = serviceRepository;
        this.counterRepository = counterRepository;
        this.queueEventRepository = queueEventRepository;
        this.predictionRepository = predictionRepository;
        this.allocationStrategy = allocationStrategy;
        this.mlWaitingTimeService = mlWaitingTimeService;
    }

    public synchronized List<QueueItemResponse> allocateTokens(String serviceId) {
        recalculate(serviceId);
        return currentQueue(serviceId);
    }

    public synchronized List<QueueItemResponse> recalculateAfterTokenChange(String serviceId) {
        recalculate(serviceId);
        return currentQueue(serviceId);
    }

    public synchronized List<QueueItemResponse> recalculateAfterCounterChange(String serviceId) {
        recalculate(serviceId);
        return currentQueue(serviceId);
    }

    public synchronized List<QueueItemResponse> recalculateWaitingTimes(String serviceId) {
        recalculate(serviceId);
        return currentQueue(serviceId);
    }

    public synchronized List<Token> getActiveTokens(String serviceId) {
        requireService(serviceId);
        return tokenRepository.readAll().stream()
                .filter(token -> serviceId.equals(token.getServiceId()))
                .filter(token -> ACTIVE_QUEUE_STATUSES.contains(token.getStatus()))
                .sorted(Comparator.comparing(Token::getQueuePosition)
                        .thenComparing(Token::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public synchronized int getActiveQueueSize(String serviceId) {
        return getActiveTokens(serviceId).size();
    }

    public synchronized double calculateBasicWaitingTime(String tokenId) {
        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found: " + tokenId));
        recalculate(token.getServiceId());
        return tokenRepository.findById(tokenId).orElse(token).getEstimatedWaitTime();
    }

    private void recalculate(String serviceId) {
        Service service = requireService(serviceId);
        List<Token> allTokens = tokenRepository.readAll();
        List<Token> activeTokens = allTokens.stream()
                .filter(token -> serviceId.equals(token.getServiceId()))
                .filter(token -> ACTIVE_QUEUE_STATUSES.contains(token.getStatus()))
                .toList();
        int activeCounters = countActiveCounters(serviceId);
        List<Token> allocatedTokens = allocationStrategy.allocate(
                activeTokens, service, activeCounters);
        int queueSize = allocatedTokens.size();
        int hourOfDay = LocalDateTime.now().getHour();

        for (int index = 0; index < allocatedTokens.size(); index++) {
            Token token = allocatedTokens.get(index);
            int oldPosition = tokenRepository.findById(token.getId())
                    .map(Token::getQueuePosition)
                    .orElse(0);
            double fallbackWaitingTime = calculateFallbackWaitingTime(
                    index,
                    service,
                    activeCounters);
            MlWaitingTimeRequest request = new MlWaitingTimeRequest(
                    queueSize,
                    index,
                    activeCounters,
                    service.getAverageServiceTime(),
                    hourOfDay);
            OptionalDouble mlPrediction = mlWaitingTimeService.predict(
                    request,
                    token.getTokenNumber());
            String source = mlPrediction.isPresent() ? ML_SOURCE : FALLBACK_SOURCE;
            double waitingTime = mlPrediction.orElse(fallbackWaitingTime);

            token.setEstimatedWaitTime(waitingTime);
            tokenRepository.update(token);
            recordPositionChange(token, oldPosition, token.getQueuePosition());
            recordPrediction(token, request, waitingTime, source);
        }

        for (Token token : allTokens) {
            if (serviceId.equals(token.getServiceId())
                    && !ACTIVE_QUEUE_STATUSES.contains(token.getStatus())
                    && (token.getQueuePosition() != 0 || token.getEstimatedWaitTime() != 0)) {
                int oldPosition = token.getQueuePosition();
                token.setQueuePosition(0);
                token.setEstimatedWaitTime(0);
                tokenRepository.update(token);
                recordPositionChange(token, oldPosition, 0);
            }
        }
    }

    private double calculateFallbackWaitingTime(
            int peopleAhead,
            Service service,
            int activeCounters) {
        if (activeCounters == 0) {
            return 0;
        }
        return (double) peopleAhead * service.getAverageServiceTime() / activeCounters;
    }

    private void recordPrediction(
            Token token,
            MlWaitingTimeRequest request,
            double waitingTime,
            String source) {
        LocalDateTime predictionTime = LocalDateTime.now();
        Prediction latest = predictionRepository.findLatestByTokenId(token.getId()).orElse(null);
        if (samePrediction(latest, request, waitingTime, source)) {
            return;
        }

        predictionRepository.save(Prediction.builder()
                .id(UUID.randomUUID().toString())
                .tokenId(token.getId())
                .queueSize(request.queueSize())
                .tokensAhead(request.tokensAhead())
                .activeCounters(request.activeCounters())
                .averageServiceTime(request.averageServiceTime())
                .hourOfDay(request.hourOfDay())
                .predictedWaitingTime(waitingTime)
                .predictionTime(predictionTime)
                .source(source)
                .build());
        LOGGER.debug("Stored {} waiting-time prediction for token {}", source, token.getTokenNumber());
    }

    private boolean samePrediction(
            Prediction latest,
            MlWaitingTimeRequest request,
            double waitingTime,
            String source) {
        return latest != null
                && latest.getQueueSize() == request.queueSize()
                && latest.getTokensAhead() == request.tokensAhead()
                && latest.getActiveCounters() == request.activeCounters()
                && Double.compare(latest.getAverageServiceTime(), request.averageServiceTime()) == 0
                && latest.getHourOfDay() == request.hourOfDay()
                && Double.compare(latest.getPredictedWaitingTime(), waitingTime) == 0
                && source.equals(latest.getSource());
    }

    private List<QueueItemResponse> currentQueue(String serviceId) {
        return getActiveTokens(serviceId).stream()
                .map(token -> new QueueItemResponse(
                        token.getTokenNumber(),
                        token.getQueuePosition(),
                        token.getStatus(),
                        token.getEstimatedWaitTime()))
                .toList();
    }

    private Service requireService(String serviceId) {
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId));
    }

    private int countActiveCounters(String serviceId) {
        return (int) counterRepository.readAll().stream()
                .filter(Counter::isActive)
                .filter(counter -> serviceId.equals(counter.getServiceId()))
                .count();
    }

    private void recordPositionChange(Token token, int oldPosition, int newPosition) {
        if (oldPosition == newPosition) {
            return;
        }
        queueEventRepository.save(QueueEvent.builder()
                .id(UUID.randomUUID().toString())
                .tokenId(token.getId())
                .eventType(POSITION_CHANGED)
                .oldPosition(oldPosition)
                .newPosition(newPosition)
                .timestamp(LocalDateTime.now())
                .build());
    }
}
