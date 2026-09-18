package com.smartqueue.service;

import com.smartqueue.dto.CreateServiceRequest;
import com.smartqueue.dto.ServiceResponse;
import com.smartqueue.dto.UpdateServiceRequest;
import com.smartqueue.exception.InvalidRequestException;
import com.smartqueue.exception.ResourceNotFoundException;
import com.smartqueue.model.Service;
import com.smartqueue.repository.ServiceRepository;

import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Service
public class ServiceManagementService {

    private final ServiceRepository serviceRepository;

    public ServiceManagementService(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    public ServiceResponse create(CreateServiceRequest request) {
        validateServiceValues(request.getName(), request.getAverageServiceTime());

        Service service = Service.builder()
                .id(UUID.randomUUID().toString())
                .name(request.getName().trim())
                .description(request.getDescription())
                .averageServiceTime(request.getAverageServiceTime())
                .active(true)
                .build();

        return toResponse(serviceRepository.save(service));
    }

    public List<ServiceResponse> getAll() {
        return serviceRepository.readAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ServiceResponse> getActive() {
        return serviceRepository.readAll().stream()
                .filter(Service::isActive)
                .map(this::toResponse)
                .toList();
    }

    public ServiceResponse update(String id, UpdateServiceRequest request) {
        Service service = findService(id);
        validateServiceValues(request.getName(), request.getAverageServiceTime());

        service.setName(request.getName().trim());
        service.setDescription(request.getDescription());
        service.setAverageServiceTime(request.getAverageServiceTime());
        service.setActive(request.getActive());

        return toResponse(serviceRepository.update(service));
    }

    public ServiceResponse deactivate(String id) {
        Service service = findService(id);
        service.setActive(false);
        return toResponse(serviceRepository.update(service));
    }

    private Service findService(String id) {
        return serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + id));
    }

    private void validateServiceValues(String name, int averageServiceTime) {
        if (name == null || name.isBlank() || name.trim().length() < 2) {
            throw new InvalidRequestException("Service name must contain at least 2 characters");
        }
        if (averageServiceTime <= 0) {
            throw new InvalidRequestException("averageServiceTime must be greater than 0");
        }
    }

    private ServiceResponse toResponse(Service service) {
        return new ServiceResponse(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getAverageServiceTime(),
                service.isActive());
    }
}
