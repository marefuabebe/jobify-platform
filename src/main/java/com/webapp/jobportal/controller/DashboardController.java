package com.webapp.jobportal.controller;

import com.webapp.jobportal.entity.Users;
import com.webapp.jobportal.services.UsersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final UsersService usersService;

    @Autowired
    public DashboardController(UsersService usersService) {
        this.usersService = usersService;
    }

    @GetMapping({"/dashboard", "/dashboard/"})
    public String dashboard() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        // Check user roles and redirect accordingly
        boolean hasAdminRole = authentication.getAuthorities().stream()
                .anyMatch(r -> r.getAuthority().equals("Admin"));
        boolean hasClientRole = authentication.getAuthorities().stream()
                .anyMatch(r -> r.getAuthority().equals("Client"));
        boolean hasFreelancerRole = authentication.getAuthorities().stream()
                .anyMatch(r -> r.getAuthority().equals("Freelancer"));

        if (hasAdminRole) {
            return "redirect:/admin/dashboard";
        } else if (hasClientRole) {
            return "redirect:/client-dashboard/";
        } else if (hasFreelancerRole) {
            return "redirect:/freelancer-dashboard/";
        } else {
            return "redirect:/oauth2/choose-role";
        }
    }
}