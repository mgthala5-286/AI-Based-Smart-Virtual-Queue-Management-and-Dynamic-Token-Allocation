package com.smartqueue.service;

import com.smartqueue.model.Service;
import com.smartqueue.model.Token;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DynamicTokenAllocationStrategy implements QueueAllocationStrategy {

    private final FifoAllocationStrategy fifoAllocationStrategy;

    public DynamicTokenAllocationStrategy(FifoAllocationStrategy fifoAllocationStrategy) {
        this.fifoAllocationStrategy = fifoAllocationStrategy;
    }

    @Override
    public List<Token> allocate(List<Token> activeTokens, Service service, int activeCounters) {
        // Phase 6 dynamically reapplies the current capacity while preserving FIFO.
        return fifoAllocationStrategy.allocate(activeTokens, service, activeCounters);
    }
}
