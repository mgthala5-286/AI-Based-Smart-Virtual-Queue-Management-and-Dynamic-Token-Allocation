package com.smartqueue.controller;

import com.smartqueue.dto.CounterResponse;
import com.smartqueue.dto.ServiceResponse;
import com.smartqueue.service.CounterManagementService;
import com.smartqueue.service.ServiceManagementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceManagementService serviceManagementService;
    private final CounterManagementService counterManagementService;

    public ServiceController(
            ServiceManagementService serviceManagementService,
            CounterManagementService counterManagementService) {
        this.serviceManagementService = serviceManagementService;
        this.counterManagementService = counterManagementService;
    }

    @GetMapping
    public List<ServiceResponse> getActiveServices() {
        return serviceManagementService.getActive();
    }

    @GetMapping("/{serviceId}/counters")
    public List<CounterResponse> getActiveCounters(@PathVariable String serviceId) {
        return counterManagementService.getActiveForService(serviceId);
    }
}
