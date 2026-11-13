package com.grash.job;
import com.grash.service.SubscriptionExpiryCommand;
import lombok.RequiredArgsConstructor;
import org.quartz.*;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
@DisallowConcurrentExecution
public class SubscriptionEndJob implements Job {
    private final SubscriptionExpiryCommand command;
    @Override public void execute(JobExecutionContext context) { command.expire(context.getMergedJobDataMap().getLong("subscriptionId")); }
}
