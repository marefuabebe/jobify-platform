package com.webapp.jobportal.controller;

import com.webapp.jobportal.entity.Users;
import com.webapp.jobportal.services.JobSeekerProfileService;
import com.webapp.jobportal.services.RecruiterProfileService;
import com.webapp.jobportal.services.UsersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final UsersService usersService;
    private final RecruiterProfileService recruiterProfileService;
    private final JobSeekerProfileService jobSeekerProfileService;
    private final com.webapp.jobportal.services.NotificationService notificationService;

    @Autowired
    public GlobalControllerAdvice(UsersService usersService, RecruiterProfileService recruiterProfileService,
            JobSeekerProfileService jobSeekerProfileService,
            com.webapp.jobportal.services.NotificationService notificationService) {
        this.usersService = usersService;
        this.recruiterProfileService = recruiterProfileService;
        this.jobSeekerProfileService = jobSeekerProfileService;
        this.notificationService = notificationService;
    }

    @ModelAttribute
    public void addDefaultsToModel(Model model) {
        model.addAttribute("unreadNotificationsCount", 0L);
        model.addAttribute("notifications", java.util.Collections.emptyList());
        model.addAttribute("unreadMessagesCount", 0);
        model.addAttribute("isVerified", false);
        model.addAttribute("documentStatus", "");
    }

    @ModelAttribute
    public void addUserToModel(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken)) {
            String currentUsername = authentication.getName();
            // Get the base User
            Users user = usersService.getUserByEmail(currentUsername).orElse(null);

            if (user != null) {
                // If it's a Recruiter or JobSeeker, put their PROFILE in the 'user' attribute
                // because templates expect 'user.firstName', 'user.isVerified', etc.
                if (user.getUserTypeId() != null) {
                    if (user.getUserTypeId().getUserTypeId() == 1) { // Recruiter
                        recruiterProfileService.getOne(user.getUserId()).ifPresent(profile -> {
                            if (profile.getUserId() == null) {
                                profile.setUserId(user);
                            }
                            if (profile.getUserAccountId() <= 0) {
                                profile.setUserAccountId(user.getUserId());
                            }
                            boolean verified = Boolean.TRUE.equals(profile.getIsVerified()) && user.isApproved();
                            profile.setIsVerified(verified);
                            model.addAttribute("user", profile);
                            model.addAttribute("userType", "Recruiter");
                            model.addAttribute("isVerified", verified);
                            model.addAttribute("documentStatus", profile.getDocumentStatus() != null ? profile.getDocumentStatus() : "");
                        });
                    } else if (user.getUserTypeId().getUserTypeId() == 2) { // JobSeeker
                        jobSeekerProfileService.getOne(user.getUserId()).ifPresent(profile -> {
                            if (profile.getUserId() == null) {
                                profile.setUserId(user);
                            }
                            if (profile.getUserAccountId() == null) {
                                profile.setUserAccountId(user.getUserId());
                            }
                            boolean verified = Boolean.TRUE.equals(profile.getIsVerified()) && user.isApproved();
                            profile.setIsVerified(verified);
                            model.addAttribute("user", profile);
                            model.addAttribute("userType", "JobSeeker");
                            model.addAttribute("isVerified", verified);
                            model.addAttribute("documentStatus", profile.getDocumentStatus() != null ? profile.getDocumentStatus() : "");
                        });
                    } else {
                        // Admin or other: just put base user
                        model.addAttribute("user", user);
                        model.addAttribute("userType", "Admin");
                        model.addAttribute("isVerified", true);
                    }
                } else {
                    // Pending role selection
                    model.addAttribute("user", user);
                    model.addAttribute("userType", "Pending");
                }

                // Also add 'baseUser' if templates need the raw Users entity (e.g. for id or
                // email if profile missing it)
                model.addAttribute("baseUser", user);

                // Add 'username' method attribute which seems to be used
                model.addAttribute("username", currentUsername);

                // Add Notifications globally with defensive try-catch
                try {
                    java.util.List<com.webapp.jobportal.entity.Notification> notifications = notificationService
                            .getRecentNotifications(user, 10);
                    long unreadCount = notificationService.getUnreadCount(user);

                    model.addAttribute("notifications", notifications != null ? notifications : java.util.Collections.emptyList());
                    model.addAttribute("unreadNotificationsCount", unreadCount);
                } catch (Exception e) {
                    model.addAttribute("notifications", java.util.Collections.emptyList());
                    model.addAttribute("unreadNotificationsCount", 0L);
                }
            }
        }
    }
}
