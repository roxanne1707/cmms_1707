package com.grash.controller;

import com.grash.exception.RestExceptionHandlerController;
import com.grash.repository.ApiKeyRepository;
import com.grash.security.ClientIpResolver;
import com.grash.service.LicenseService;
import com.grash.service.RateLimiterService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Import(RestExceptionHandlerController.class)
@EnableMethodSecurity
public class AbstractControllerTest {
    @MockitoBean
    protected com.grash.security.FreshMaintenanceActor freshMaintenanceActor;

    @org.junit.jupiter.api.BeforeEach
    void defaultFreshMaintenanceActor() {
        org.mockito.Mockito.lenient().when(freshMaintenanceActor.load(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @MockitoBean
    private ApiKeyRepository apiKeyRepository;
    @MockitoBean
    private LicenseService licenseService;
    @MockitoBean
    protected RateLimiterService rateLimiterService;
    @MockitoBean
    private ClientIpResolver clientIpResolver;
}
