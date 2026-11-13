package com.grash.service;
import com.grash.model.Subscription;
import com.grash.repository.SubscriptionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
@Service
@RequiredArgsConstructor
public class SubscriptionExpiryCommand {
    private final SubscriptionRepository subscriptions;
    private final SubscriptionService service;
    private final EntityManager em;
    @Transactional public void expire(long id) {
        Subscription value=subscriptions.findById(id).orElse(null);if(value==null) return;
        em.lock(value,LockModeType.PESSIMISTIC_WRITE);em.refresh(value);
        if(value.getPaddleSubscriptionId()!=null || value.getEndsOn()==null || value.getEndsOn().toInstant().isAfter(Instant.now()) || "FREE".equals(value.getSubscriptionPlan().getCode())) return;
        service.resetToFreePlan(value);
    }
}
