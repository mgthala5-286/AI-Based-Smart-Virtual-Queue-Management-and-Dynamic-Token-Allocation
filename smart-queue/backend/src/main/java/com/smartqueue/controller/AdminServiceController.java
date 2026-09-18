package com.smartqueue.controller;

import com.smartqueue.dto.CreateServiceRequest;
import com.smartqueue.dto.ServiceResponse;
import com.smartqueue.dto.UpdateServiceRequest;
import com.smartqueue.service.ServiceManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/services")
public class AdminServiceController {

    private final ServiceManagementService serviceManagementService;

    public AdminServiceController(ServiceManagementService serviceManagementService) {
        this.serviceManagementService = serviceManagementService;
    }

    @PostMapping
    public ResponseEntity<ServiceResponse> create(@Valid @RequestBody CreateServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceManagementService.create(request));
    }

    @GetMapping
    public List<ServiceResponse> getAll() {
        return serviceManagementService.getAll();
    }

    @PutMapping("/{id}")
    public ServiceResponse update(
            @PathVariable String id,
            @Valid @RequestBody UpdateServiceRequest request) {
        return serviceManagementService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ServiceResponse deactivate(@PathVariable String id) {
        return serviceManagementService.deactivate(id);
    }
}
