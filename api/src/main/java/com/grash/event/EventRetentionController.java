package com.grash.event;

import com.grash.model.User;
import com.grash.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/reliable-events/retention")
@RequiredArgsConstructor
@ConditionalOnProperty(name="cmms.events.enabled",havingValue="true")
@PreAuthorize("hasRole('ROLE_CLIENT')")
public class EventRetentionController {
    private final EventReplayService access;
    private final EventRetentionService retention;
    public record RunRequest(@NotNull Boolean dryRun,@NotBlank @Size(min=5,max=500) String reason) {}
    @GetMapping public Map<String,Object> status(@CurrentUser User caller) {
        User user=access.authorize(caller);return retention.status(user.getCompany().getId());
    }
    @PostMapping("/runs") public EventRetentionService.BatchResult run(@CurrentUser User caller,@Valid @RequestBody RunRequest request) {
        User user=access.authorize(caller);
        return retention.run(user.getCompany().getId(),user.getId(),request.dryRun(),request.reason());
    }
}
