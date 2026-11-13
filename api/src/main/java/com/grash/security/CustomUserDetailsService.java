package com.grash.security;

import com.grash.model.User;
import com.grash.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    @Lazy
    private UserService userService;

    @Override
    @Transactional(readOnly = true)
    public CustomUserDetail loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userService.whoami(username, false);
        // Authentication runs before MVC's open-in-view scope. Initialize the authorization
        // graph before this short transaction ends; never cache it between requests.
        org.hibernate.Hibernate.initialize(user.getRole());
        if (user.getRole() != null) {
            org.hibernate.Hibernate.initialize(user.getRole().getCreatePermissions());
            org.hibernate.Hibernate.initialize(user.getRole().getViewPermissions());
            org.hibernate.Hibernate.initialize(user.getRole().getViewOtherPermissions());
            org.hibernate.Hibernate.initialize(user.getRole().getEditOtherPermissions());
            org.hibernate.Hibernate.initialize(user.getRole().getDeleteOtherPermissions());
        }
        org.hibernate.Hibernate.initialize(user.getSuperAccountRelations());
        if (user.getCompany() != null && user.getCompany().getSubscription() != null) {
            org.hibernate.Hibernate.initialize(user.getCompany().getSubscription().getSubscriptionPlan().getFeatures());
        }
        return CustomUserDetail.builder()//
                .user(user)//
                .build();
    }

}
