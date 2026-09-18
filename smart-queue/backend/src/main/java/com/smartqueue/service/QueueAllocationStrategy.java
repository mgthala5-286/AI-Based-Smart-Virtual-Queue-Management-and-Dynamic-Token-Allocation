package com.smartqueue.service;

import com.smartqueue.model.Service;
import com.smartqueue.model.Token;

import java.util.List;

public interface QueueAllocationStrategy {

    List<Token> allocate(List<Token> activeTokens, Service service, int activeCounters);
}
