package com.webapp.jobportal.controller;

import com.webapp.jobportal.entity.Notification;
import com.webapp.jobportal.entity.Users;
import com.webapp.jobportal.services.NotificationService;
import com.webapp.jobportal.services.UsersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UsersService usersService;

    @Autowired
    public NotificationController(NotificationService notificationService, UsersService usersService) {
        this.notificationService = notificationService;
        this.usersService = usersService;
    }

    @GetMapping({ "", "/" })
    public String notifications(Model model) {
        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        List<Notification> notifications = notificationService.getUserNotifications(currentUser);
        model.addAttribute("notifications", notifications);
        model.addAttribute("unreadCount", notificationService.getUnreadCount(currentUser));
        return "notifications/list";
    }

    @GetMapping("/unread")
    @ResponseBody
    public Map<String, Object> getUnreadNotifications() {
        Users currentUser = usersService.getCurrentUser();
        Map<String, Object> response = new HashMap<>();

        if (currentUser != null) {
            response.put("notifications", notificationService.getUnreadNotifications(currentUser));
            response.put("count", notificationService.getUnreadCount(currentUser));
        } else {
            response.put("notifications", List.of());
            response.put("count", 0);
        }

        return response;
    }

    @GetMapping("/recent")
    @ResponseBody
    public Map<String, Object> getRecentNotifications() {
        Users currentUser = usersService.getCurrentUser();
        Map<String, Object> response = new HashMap<>();

        if (currentUser != null) {
            response.put("notifications", notificationService.getRecentNotifications(currentUser, 5));
            response.put("unreadCount", notificationService.getUnreadCount(currentUser));
        } else {
            response.put("notifications", List.of());
            response.put("unreadCount", 0);
        }

        return response;
    }

    @GetMapping("/count")
    @ResponseBody
    public Map<String, Long> getUnreadCount() {
        Users currentUser = usersService.getCurrentUser();
        Map<String, Long> response = new HashMap<>();
        long count = 0;

        if (currentUser != null) {
            count = notificationService.getUnreadCount(currentUser);
        }

        response.put("count", count);
        return response;
    }

    @PostMapping("/{id}/read")
    @ResponseBody
    public ResponseEntity<?> markAsRead(@PathVariable Integer id) {
        try {
            notificationService.markAsRead(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error marking notification as read");
        }
    }

    @PostMapping("/read-all")
    @ResponseBody
    public ResponseEntity<?> markAllAsRead() {
        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.badRequest().body("User not authenticated");
        }

        try {
            notificationService.markAllAsRead(currentUser);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error marking all notifications as read");
        }
    }

    @GetMapping("/read/{id}")
    public String markAsReadAndRedirect(@PathVariable Integer id) {
        try {
            // Retrieve notification first to get redirection details
            com.webapp.jobportal.entity.Notification notification = notificationService.getOne(id);
            if (notification == null) {
                return "redirect:/dashboard/";
            }

            // Mark as read
            notificationService.markAsRead(id);

            // Determine redirect URL based on type
            if (notification.getRelatedId() != null) {
                String type = notification.getType();
                if ("JOB".equals(type) || "APPLICATION".equals(type)) {
                    return "redirect:/job-details-apply/" + notification.getRelatedId();
                } else if ("MESSAGE".equals(type)) {
                    return "redirect:/chat/"; // Ideally redirect to specific chat if possible
                } else if ("PAYMENT".equals(type)) {
                    return "redirect:/client-dashboard/payments";
                } else if ("VERIFICATION".equals(type)) {
                    Users notifUser = notification.getUserId();
                    if (notifUser != null && notifUser.getUserTypeId() != null) {
                        int roleId = notifUser.getUserTypeId().getUserTypeId();
                        if (roleId == 3) {
                            return "redirect:/admin/users/pending";
                        } else if (roleId == 2) {
                            return "redirect:/job-seeker-profile/#verification-section";
                        } else if (roleId == 1) {
                            return "redirect:/recruiter-profile/";
                        }
                    }
                    return "redirect:/job-seeker-profile/#verification-section";
                }
            }

            // Default redirects based on user type
            Users user = notification.getUserId();
            if (user != null && user.getUserTypeId().getUserTypeId() == 1) { // Recruiter
                return "redirect:/client-dashboard/";
            } else if (user != null && user.getUserTypeId().getUserTypeId() == 2) { // Freelancer
                return "redirect:/freelancer-dashboard/";
            }

            return "redirect:/dashboard/";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/dashboard/";
        }
    }

    @GetMapping("/mark-all-read")
    public String markAllReadAndRedirect() {
        Users currentUser = usersService.getCurrentUser();
        if (currentUser != null) {
            notificationService.markAllAsRead(currentUser);

            if (currentUser.getUserTypeId().getUserTypeId() == 1) {
                return "redirect:/client-dashboard/";
            } else if (currentUser.getUserTypeId().getUserTypeId() == 2) {
                return "redirect:/freelancer-dashboard/";
            }
        }
        return "redirect:/dashboard/";
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteNotification(@PathVariable Integer id) {
        try {
            notificationService.deleteNotification(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error deleting notification");
        }
    }
}
