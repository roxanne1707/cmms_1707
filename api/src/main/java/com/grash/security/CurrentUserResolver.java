package com.grash.security;

import com.grash.model.User;
import com.grash.service.UserService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class CurrentUserResolver implements HandlerMethodArgumentResolver {

    private UserService userService;

    @Autowired
    @Lazy
    private FreshMaintenanceActor freshMaintenanceActor;

    @Autowired
    public void setDeps(@Lazy UserService userService
    ) {
        this.userService = userService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterAnnotation(CurrentUser.class) != null;
    }

    @Override
    public User resolveArgument(@NotNull MethodParameter parameter, ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest,
                                WebDataBinderFactory binderFactory) throws Exception {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        User identity = userService.whoami(request);
        if (request != null && FreshMaintenanceActor.applies(request.getMethod(), request.getRequestURI())) {
            return freshMaintenanceActor.load(identity);
        }
        return identity;
    }
}
