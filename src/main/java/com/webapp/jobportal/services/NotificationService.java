package com.webapp.jobportal.services;

import com.webapp.jobportal.entity.Notification;
import com.webapp.jobportal.entity.Users;
import com.webapp.jobportal.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.webapp.jobportal.repository.UsersRepository usersRepository;

    @Autowired
    public NotificationService(NotificationRepository notificationRepository,
            SimpMessagingTemplate messagingTemplate,
            @org.springframework.context.annotation.Lazy com.webapp.jobportal.repository.UsersRepository usersRepository) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.usersRepository = usersRepository;
    }

    public Notification createNotification(Users user, String title, String message, String type, Integer relatedId) {
        Notification notification = new Notification();
        notification.setUserId(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setRelatedId(relatedId);
        notification.setIsRead(false);
        notification.setCreatedAt(new Date());

        Notification saved = notificationRepository.save(notification);

        // Send real-time notification to user
        messagingTemplate.convertAndSendToUser(
                String.valueOf(user.getUserId()),
                "/queue/notifications",
                saved);

        return saved;
    }

    public List<Notification> getUserNotifications(Users user) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user);
    }

    public List<Notification> getUnreadNotifications(Users user) {
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(user);
    }

    public long getUnreadCount(Users user) {
        return notificationRepository.countByUserIdAndIsReadFalse(user);
    }

    public List<Notification> getRecentNotifications(Users user, int limit) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user).stream()
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
    }

    public void markAsRead(Integer notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    public void markAllAsRead(Users user) {
        List<Notification> notifications = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(user);
        notifications.forEach(notification -> {
            notification.setIsRead(true);
            notificationRepository.save(notification);
        });
    }

    public void deleteNotification(Integer notificationId) {
        notificationRepository.deleteById(notificationId);
    }

    public void createAdminNotification(String title, String message, String type, Integer relatedId) {
        List<Users> admins = usersRepository.findAllAdmins();
        if (admins == null || admins.isEmpty()) {
            usersRepository.findByEmail("marefu933@gmail.com").ifPresent(admin -> {
                createNotification(admin, title, message, type, relatedId);
            });
            return;
        }

        for (Users admin : admins) {
            createNotification(admin, title, message, type, relatedId);
        }
    }

    public void createAdminNotification(String userEmail, String userType) {
        createAdminVerificationNotification(userEmail, userType, null);
    }

    public void createAdminVerificationNotification(String userEmail, String userType, Integer userId) {
        String title = "New " + userType + " Verification Required";
        String message = "User " + userEmail + " has uploaded verification documents and is awaiting your review.";
        createAdminNotification(title, message, "VERIFICATION", userId);
    }

    public void createAdminDisputeNotification(com.webapp.jobportal.entity.Dispute dispute) {
        String title = "New Dispute Filed: #" + dispute.getId();
        String reporterEmail = dispute.getReporter() != null ? dispute.getReporter().getEmail() : "User";
        String jobTitle = dispute.getJob() != null ? dispute.getJob().getJobTitle() : "Job";
        String message = "A dispute was filed by " + reporterEmail + " regarding '" + jobTitle + "': " + dispute.getDescription();
        createAdminNotification(title, message, "DISPUTE", dispute.getId());
    }

    public Notification getOne(Integer id) {
        return notificationRepository.findById(id).orElse(null);
    }
}
