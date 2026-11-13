package com.grash.event;

import com.grash.dto.SuccessResponse;
import com.grash.model.User;
import com.grash.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/reliable-events")
@RequiredArgsConstructor
@ConditionalOnProperty(name="cmms.events.enabled",havingValue="true")
@PreAuthorize("hasRole('ROLE_CLIENT')")
public class EventAdministrationController {
    private final EventReplayService replay;
    public record ReplayRequest(@NotBlank @Size(min=5,max=500) String reason) {}
    @GetMapping("/failures") public Map<String,Object> failures(@CurrentUser User user) { return replay.failures(user); }
    @PostMapping("/{eventId}/replay") public SuccessResponse replay(@PathVariable UUID eventId,@RequestBody @Valid ReplayRequest request,@CurrentUser User user) {
        replay.replayEvent(user,eventId,request.reason()); return new SuccessResponse(true,"Event replay scheduled");
    }
    @PostMapping("/deliveries/{deliveryId}/retry") public SuccessResponse retry(@PathVariable UUID deliveryId,@RequestBody @Valid ReplayRequest request,@CurrentUser User user) {
        replay.retryDelivery(user,deliveryId,request.reason()); return new SuccessResponse(true,"Delivery retry scheduled");
    }
}
