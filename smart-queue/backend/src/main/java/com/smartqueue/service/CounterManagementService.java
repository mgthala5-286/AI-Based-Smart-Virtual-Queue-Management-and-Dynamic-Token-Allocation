package com.smartqueue.service;

import com.smartqueue.dto.CounterResponse;
import com.smartqueue.dto.CreateCounterRequest;
import com.smartqueue.dto.UpdateCounterRequest;
import com.smartqueue.exception.DuplicateResourceException;
import com.smartqueue.exception.InvalidRequestException;
import com.smartqueue.exception.ResourceNotFoundException;
import com.smartqueue.model.Counter;
import com.smartqueue.model.Service;
import com.smartqueue.repository.CounterRepository;
import com.smartqueue.repository.ServiceRepository;

import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Service
public class CounterManagementService {

    private final CounterRepository counterRepository;
    private final ServiceRepository serviceRepository;
    private final DynamicTokenAllocationService dynamicTokenAllocationService;

    public CounterManagementService(
            CounterRepository counterRepository,
            ServiceRepository serviceRepository,
            DynamicTokenAllocationService dynamicTokenAllocationService) {
        this.counterRepository = counterRepository;
        this.serviceRepository = serviceRepository;
        this.dynamicTokenAllocationService = dynamicTokenAllocationService;
    }

    public CounterResponse create(CreateCounterRequest request) {
        Service service = requireActiveService(request.getServiceId());
        ensureCounterNumberIsAvailable(request.getCounterNumber(), service.getId(), null);

        Counter counter = Counter.builder()
                .id(UUID.randomUUID().toString())
                .counterNumber(request.getCounterNumber())
                .serviceId(service.getId())
                .active(true)
                .build();

        Counter savedCounter = counterRepository.save(counter);
        dynamicTokenAllocationService.recalculateAfterCounterChange(service.getId());
        return toResponse(savedCounter);
    }

    public List<CounterResponse> getAll() {
        return counterRepository.readAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<CounterResponse> getActiveForService(String serviceId) {
        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId));

        if (!service.isActive()) {
            return List.of();
        }

        return counterRepository.readAll().stream()
                .filter(Counter::isActive)
                .filter(counter -> service.getId().equals(counter.getServiceId()))
                .map(this::toResponse)
                .toList();
    }

    public CounterResponse update(String id, UpdateCounterRequest request) {
        Counter counter = findCounter(id);
        Service service = requireActiveService(request.getServiceId());
        ensureCounterNumberIsAvailable(request.getCounterNumber(), service.getId(), id);

        String previousServiceId = counter.getServiceId();
        counter.setCounterNumber(request.getCounterNumber());
        counter.setServiceId(service.getId());
        counter.setActive(request.getActive());

        Counter updatedCounter = counterRepository.update(counter);
        dynamicTokenAllocationService.recalculateAfterCounterChange(previousServiceId);
        if (!previousServiceId.equals(updatedCounter.getServiceId())) {
            dynamicTokenAllocationService.recalculateAfterCounterChange(updatedCounter.getServiceId());
        }
        return toResponse(updatedCounter);
    }

    public CounterResponse deactivate(String id) {
        return setActive(id, false);
    }

    public CounterResponse activate(String id) {
        return setActive(id, true);
    }

    private CounterResponse setActive(String id, boolean active) {
        Counter counter = findCounter(id);
        counter.setActive(active);
        Counter updatedCounter = counterRepository.update(counter);
        dynamicTokenAllocationService.recalculateAfterCounterChange(updatedCounter.getServiceId());
        return toResponse(updatedCounter);
    }

    private Counter findCounter(String id) {
        return counterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found: " + id));
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

    private void ensureCounterNumberIsAvailable(int counterNumber, String serviceId, String ignoredId) {
        if (counterNumber <= 0) {
            throw new InvalidRequestException("counterNumber must be greater than 0");
        }

        boolean duplicate = counterRepository.readAll().stream()
                .anyMatch(counter -> counterNumber == counter.getCounterNumber()
                        && serviceId.equals(counter.getServiceId())
                        && !counter.getId().equals(ignoredId));
        if (duplicate) {
            throw new DuplicateResourceException(
                    "Counter number " + counterNumber + " is already used for this service");
        }
    }

    private CounterResponse toResponse(Counter counter) {
        return new CounterResponse(
                counter.getId(),
                counter.getCounterNumber(),
                counter.getServiceId(),
                counter.isActive());
    }
}
