package com.smartqueue.service;

import com.smartqueue.model.Service;
import com.smartqueue.model.Token;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class FifoAllocationStrategy implements QueueAllocationStrategy {

    @Override
    public List<Token> allocate(List<Token> activeTokens, Service service, int activeCounters) {
        List<Token> sortedTokens = new ArrayList<>(activeTokens);
        sortedTokens.sort(Comparator
                .comparing(Token::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Token::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        for (int index = 0; index < sortedTokens.size(); index++) {
            Token token = sortedTokens.get(index);
            token.setQueuePosition(index + 1);
            token.setEstimatedWaitTime(calculateWaitingTime(index, service, activeCounters));
        }
        return sortedTokens;
    }

    private double calculateWaitingTime(int peopleAhead, Service service, int activeCounters) {
        if (activeCounters == 0) {
            return 0;
        }
        return (double) peopleAhead * service.getAverageServiceTime() / activeCounters;
    }
}
