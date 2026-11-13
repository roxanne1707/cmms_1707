package com.grash.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/** Isolated lab scrape credentials are separate from user JWTs and never enabled in production. */
@Configuration @Profile("maintenance-lab")
public class LabObservabilitySecurity {
    @Bean @Order(0)
    SecurityFilterChain labMetricsChain(HttpSecurity http,@Value("${cmms.lab.metrics-password:}") String password) throws Exception {
        http.securityMatcher("/actuator/**").csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if(password.isBlank()) return http.authorizeHttpRequests(a->a.anyRequest().denyAll()).build();
        var users=new InMemoryUserDetailsManager(User.withUsername("cmms-metrics").password("{noop}"+password).roles("METRICS").build());
        http.authenticationManager(new ProviderManager(new DaoAuthenticationProvider(users)))
                .httpBasic(Customizer.withDefaults()).authorizeHttpRequests(a->a.anyRequest().hasRole("METRICS"));
        return http.build();
    }
}
