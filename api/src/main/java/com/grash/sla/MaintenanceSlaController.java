package com.grash.sla;

import com.grash.model.User;
import com.grash.model.enums.Priority;
import com.grash.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/maintenance-sla")
@PreAuthorize("hasRole('ROLE_CLIENT')")
@RequiredArgsConstructor
public class MaintenanceSlaController {
    private final MaintenanceSlaService service;
    public record PolicyInput(@Min(1) @Max(525600) int responseMinutes,@Min(1) @Max(525600) int repairMinutes,boolean enabled) {}
    public record Enrollment(@NotBlank @Size(min=5,max=500) String reason) {}
    @GetMapping("/policies")
    public List<MaintenanceSlaService.Policy> policies(@CurrentUser User user){return service.policies(user);}
    @PatchMapping("/policies/{priority}")
    public MaintenanceSlaService.Policy save(@PathVariable Priority priority,@Valid @RequestBody PolicyInput input,@CurrentUser User user) {
        return service.savePolicy(priority,input.responseMinutes(),input.repairMinutes(),input.enabled(),user);
    }
    @GetMapping("/cases")
    public MaintenanceSlaService.Board cases(@CurrentUser User user,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        return service.board(user,page,size);
    }
    @PostMapping("/cases/{id}/enroll")
    public Map<String,Boolean> enroll(@PathVariable long id,@Valid @RequestBody Enrollment input,@CurrentUser User user) {
        service.enroll(id,input.reason(),user);return Map.of("success",true);
    }
}
