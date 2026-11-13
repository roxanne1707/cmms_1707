package com.grash.controller;

import com.grash.model.User;
import com.grash.security.CurrentUser;
import com.grash.service.RequestLinkCommandService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/request-links")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_CLIENT')")
public class RequestLinkController {
    private final RequestLinkCommandService commands;
    public record Link(@Positive long workOrderId,@NotBlank @Size(min=5,max=500) String reason) {}
    @GetMapping("/pending")
    public RequestLinkCommandService.Page pending(@CurrentUser User user,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        return commands.pending(user,page,size);
    }
    @GetMapping("/{id}/candidates")
    public RequestLinkCommandService.Page candidates(@PathVariable long id,@CurrentUser User user,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        return commands.candidates(id,user,page,size);
    }
    @GetMapping("/{id}/audit")
    public List<Map<String,Object>> audit(@PathVariable long id,@CurrentUser User user){return commands.audit(id,user);}
    @PostMapping("/{id}")
    public RequestLinkCommandService.Result link(@PathVariable long id,@Valid @RequestBody Link body,@CurrentUser User user) {
        return commands.link(id,body.workOrderId(),body.reason(),user);
    }
}
