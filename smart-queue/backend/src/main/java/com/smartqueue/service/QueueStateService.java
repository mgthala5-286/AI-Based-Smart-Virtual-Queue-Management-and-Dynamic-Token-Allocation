package com.smartqueue.service;

import com.smartqueue.exception.ResourceNotFoundException;
import com.smartqueue.model.Counter;
import com.smartqueue.model.QueueState;
import com.smartqueue.repository.CounterRepository;
import com.smartqueue.repository.ServiceRepository;

import java.time.LocalDateTime;

@org.springframework.stereotype.Service
public class QueueStateService {

    private final DynamicTokenAllocationService dynamicTokenAllocationService;
    private final ServiceRepository serviceRepository;
    private final CounterRepository counterRepository;

    public QueueStateService(
            DynamicTokenAllocationService dynamicTokenAllocationService,
            ServiceRepository serviceRepository,
            CounterRepository counterRepository) {
        this.dynamicTokenAllocationService = dynamicTokenAllocationService;
        this.serviceRepository = serviceRepository;
        this.counterRepository = counterRepository;
    }

    public QueueState getCurrentState(String serviceId) {
        serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId));
        dynamicTokenAllocationService.recalculateWaitingTimes(serviceId);
        int activeCounters = (int) counterRepository.readAll().stream()
                .filter(Counter::isActive)
                .filter(counter -> serviceId.equals(counter.getServiceId()))
                .count();

        return QueueState.builder()
                .serviceId(serviceId)
                .queueSize(dynamicTokenAllocationService.getActiveQueueSize(serviceId))
                .activeCounters(activeCounters)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
