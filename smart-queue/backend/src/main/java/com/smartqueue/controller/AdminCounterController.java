package com.smartqueue.controller;

import com.smartqueue.dto.CounterResponse;
import com.smartqueue.dto.CreateCounterRequest;
import com.smartqueue.dto.UpdateCounterRequest;
import com.smartqueue.service.CounterManagementService;
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
@RequestMapping("/api/admin/counters")
public class AdminCounterController {

    private final CounterManagementService counterManagementService;

    public AdminCounterController(CounterManagementService counterManagementService) {
        this.counterManagementService = counterManagementService;
    }

    @PostMapping
    public ResponseEntity<CounterResponse> create(@Valid @RequestBody CreateCounterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(counterManagementService.create(request));
    }

    @GetMapping
    public List<CounterResponse> getAll() {
        return counterManagementService.getAll();
    }

    @PutMapping("/{id}")
    public CounterResponse update(
            @PathVariable String id,
            @Valid @RequestBody UpdateCounterRequest request) {
        return counterManagementService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public CounterResponse deactivate(@PathVariable String id) {
        return counterManagementService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    public CounterResponse activate(@PathVariable String id) {
        return counterManagementService.activate(id);
    }

    @PostMapping("/{id}/deactivate")
    public CounterResponse deactivateExplicitly(@PathVariable String id) {
        return counterManagementService.deactivate(id);
    }
}
