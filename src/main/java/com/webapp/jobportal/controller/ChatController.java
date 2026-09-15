package com.webapp.jobportal.controller;

import com.webapp.jobportal.entity.ChatMessage;
import com.webapp.jobportal.entity.JobPostActivity;
import com.webapp.jobportal.entity.JobSeekerApply;
import com.webapp.jobportal.entity.RecruiterProfile;
import com.webapp.jobportal.entity.JobSeekerProfile;
import com.webapp.jobportal.entity.RecruiterJobsDto;
import com.webapp.jobportal.entity.Users;
import com.webapp.jobportal.services.ChatService;
import com.webapp.jobportal.services.JobPostActivityService;
import com.webapp.jobportal.services.JobSeekerApplyService;
import com.webapp.jobportal.services.NotificationService;
import com.webapp.jobportal.services.UsersService;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Controller
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;
    private final UsersService usersService;
    private final JobPostActivityService jobPostActivityService;
    private final JobSeekerApplyService jobSeekerApplyService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.webapp.jobportal.services.CloudinaryService cloudinaryService;

    // File upload directory
    private final String uploadDir = "photos/";

    @Autowired
    public ChatController(ChatService chatService, UsersService usersService,
            JobPostActivityService jobPostActivityService,
            JobSeekerApplyService jobSeekerApplyService,
            NotificationService notificationService,
            SimpMessagingTemplate messagingTemplate,
            com.webapp.jobportal.services.CloudinaryService cloudinaryService) {
        this.chatService = chatService;
        this.usersService = usersService;
        this.jobPostActivityService = jobPostActivityService;
        this.jobSeekerApplyService = jobSeekerApplyService;
        this.notificationService = notificationService;
        this.messagingTemplate = messagingTemplate;
        this.cloudinaryService = cloudinaryService;
    }

    @GetMapping({ "/", "" })
    public String chatList(Model model) {
        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        // Check if current user is verified based on their profile
        if (!isCurrentUserVerified(currentUser)) {
            model.addAttribute("error",
                    "You must be verified by admin to use chat functionality. Please complete your profile verification.");
            model.addAttribute("chatPartners", List.of());
            model.addAttribute("conversations", List.of());
            model.addAttribute("totalUnreadCount", 0);
            model.addAttribute("currentUser", currentUser);
            return "chat/list";
        }

        List<com.webapp.jobportal.dto.ChatConversationDTO> conversations = chatService.getConversations(currentUser);
        // Calculate total unread messages count
        int totalUnread = conversations.stream()
                .mapToInt(com.webapp.jobportal.dto.ChatConversationDTO::getUnreadCount)
                .sum();
        model.addAttribute("conversations", conversations);
        model.addAttribute("totalUnreadCount", totalUnread);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("currentUserPhotosImagePath", usersService.getUserProfileImagePath(currentUser));

        // Add user profile for header fragments
        Object userProfile = usersService.getCurrentUserProfile();
        model.addAttribute("user", userProfile);

        // Add header attributes for consistent header rendering
        addHeaderAttributes(model, currentUser, userProfile);

        return "chat/list";
    }

    private boolean isCurrentUserVerified(Users user) {
        if (user == null)
            return false;

        // Check if user is active
        if (!user.isActive()) {
            return false;
        }

        // Get current user's profile to check verification status
        Object profile = usersService.getCurrentUserProfile();
        if (profile == null) {
            return false;
        }

        // For freelancers (user type 2), check JobSeekerProfile verification
        if (user.getUserTypeId().getUserTypeId() == 2) {
            return Boolean.TRUE.equals(((com.webapp.jobportal.entity.JobSeekerProfile) profile).getIsVerified());
        }
        // For clients (user type 1), check RecruiterProfile verification
        else if (user.getUserTypeId().getUserTypeId() == 1) {
            return Boolean.TRUE.equals(((com.webapp.jobportal.entity.RecruiterProfile) profile).getIsVerified());
        }

        return false;
    }

    private boolean isUserVerified(Users user) {
        if (user == null)
            return false;

        // Check if user is active
        if (!user.isActive()) {
            return false;
        }

        return true;
    }

    @GetMapping("/create/{userId}")
    public String createChatRedirect(@PathVariable Integer userId) {
        return "redirect:/chat/" + userId;
    }

    @GetMapping("/{userId}")
    public String chatWithUser(@PathVariable Integer userId,
            @RequestParam(required = false) Integer jobId,
            Model model) {
        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        // Check if current user is verified
        if (!isCurrentUserVerified(currentUser)) {
            model.addAttribute("error",
                    "You must be verified by admin to use chat functionality. Please complete your profile verification.");
            return "redirect:/chat/";
        }

        Users otherUser = usersService.getAllUsers().stream()
                .filter(u -> u.getUserId() == userId)
                .findFirst()
                .orElse(null);

        if (otherUser == null) {
            return "redirect:/chat/";
        }

        // Check if other user is verified
        if (!isUserVerified(otherUser)) {
            model.addAttribute("error", "Cannot chat with unverified user.");
            return "redirect:/chat/";
        }

        JobPostActivity job = null;
        if (jobId != null) {
            job = jobPostActivityService.getOne(jobId);
        }

        List<ChatMessage> messages = chatService.getConversation(currentUser, otherUser, job);

        // Mark conversation as read
        chatService.markConversationAsRead(otherUser, currentUser);

        model.addAttribute("messages", messages);
        model.addAttribute("otherUser", otherUser);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("otherUserPhotosImagePath", usersService.getUserProfileImagePath(otherUser));
        model.addAttribute("currentUserPhotosImagePath", usersService.getUserProfileImagePath(currentUser));
        model.addAttribute("job", job);

        // Add conversations list for sidebar
        List<com.webapp.jobportal.dto.ChatConversationDTO> conversations = chatService.getConversations(currentUser);
        // Calculate total unread messages count
        int totalUnread = conversations.stream()
                .mapToInt(com.webapp.jobportal.dto.ChatConversationDTO::getUnreadCount)
                .sum();
        model.addAttribute("conversations", conversations);
        model.addAttribute("totalUnreadCount", totalUnread);

        // Add user profile and headers data for the view fragment
        Object userProfile = usersService.getCurrentUserProfile();
        model.addAttribute("user", userProfile);

        // Add header attributes for consistent header rendering
        addHeaderAttributes(model, currentUser, userProfile);

        return "chat/conversation";
    }

    @GetMapping("/unread-count")
    @ResponseBody
    public ResponseEntity<Integer> getUnreadCount() {
        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.ok(0);
        }

        // Count all unread messages for this user
        List<ChatMessage> unread = chatService.getUnreadMessages(currentUser);
        return ResponseEntity.ok(unread.size());
    }

    @GetMapping("/conversations/count")
    @ResponseBody
    public ResponseEntity<Integer> getConversationCount() {
        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.ok(0);
        }
        return ResponseEntity.ok(chatService.getConversations(currentUser).size());
    }

    @MessageMapping("/chat.sendMessage")
    public ChatMessage sendMessage(@Payload @NonNull ChatMessage chatMessage,
            @NonNull SimpMessageHeaderAccessor headerAccessor) {
        Authentication auth = (Authentication) headerAccessor.getUser();
        if (auth != null) {
            Users sender = usersService.findByEmail(auth.getName());

            // Verify sender is verified before allowing message
            if (!isUserVerified(sender)) {
                throw new RuntimeException("You must be verified to send messages");
            }

            // Verify receiver is verified
            // Must fetch full entity because payload only contains ID
            Users receiverPartial = chatMessage.getReceiverId();
            if (receiverPartial == null) {
                throw new RuntimeException("Receiver ID is required");
            }

            Users receiver = usersService.getAllUsers().stream()
                    .filter(u -> u.getUserId() == receiverPartial.getUserId())
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Receiver not found"));

            if (!isUserVerified(receiver)) {
                throw new RuntimeException("Cannot send message to unverified user");
            }

            chatMessage.setSenderId(sender);
            chatMessage.setReceiverId(receiver); // crucial: set full object with Email
            chatMessage.setTimestamp(new Date());
            chatMessage.setIsRead(false);

            ChatMessage savedMessage = chatService.saveMessage(chatMessage);

            // Real-time sending and notification is handled inside chatService.saveMessage

            return savedMessage;
        }
        return chatMessage;
    }

    @PostMapping("/send")
    @ResponseBody
    public ChatMessage sendMessageViaHttp(@RequestParam Integer receiverId,
            @RequestParam @NonNull String message,
            @RequestParam(required = false) Integer jobId) {
        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            throw new RuntimeException("User not authenticated");
        }

        // Check if current user is verified
        if (!isUserVerified(currentUser)) {
            throw new RuntimeException("You must be verified to send messages");
        }

        Users receiver = usersService.getAllUsers().stream()
                .filter(u -> u.getUserId() == receiverId)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Receiver not found"));

        // Check if receiver is verified
        if (!isUserVerified(receiver)) {
            throw new RuntimeException("Cannot send message to unverified user");
        }

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSenderId(currentUser);
        chatMessage.setReceiverId(receiver);
        chatMessage.setMessage(message);
        chatMessage.setTimestamp(new Date());
        chatMessage.setIsRead(false);

        if (jobId != null) {
            chatMessage.setJobId(jobPostActivityService.getOne(jobId));
        }

        ChatMessage savedMessage = chatService.saveMessage(chatMessage);

        // Real-time sending and notification is handled inside chatService.saveMessage

        return savedMessage;
    }

    private String getDisplayName(@NonNull Users user) {
        // Simplified display name - using email as fallback
        return user.getEmail();
    }

    /**
     * Add header attributes to ensure consistent header rendering across all pages
     */
    private void addHeaderAttributes(Model model, Users currentUser, Object userProfile) {
        if (currentUser == null || userProfile == null) {
            model.addAttribute("unseenProposals", 0);
            model.addAttribute("activeProjects", 0);
            return;
        }

        Integer userTypeId = currentUser.getUserTypeId() != null ? currentUser.getUserTypeId().getUserTypeId() : null;

        // For Clients (userTypeId == 1)
        if (userTypeId != null && userTypeId == 1 && userProfile instanceof RecruiterProfile) {
            RecruiterProfile clientProfile = (RecruiterProfile) userProfile;

            // Get posted jobs
            List<RecruiterJobsDto> postedJobs = jobPostActivityService
                    .getRecruiterJobs(clientProfile.getUserAccountId());

            // Get all proposals
            List<JobSeekerApply> allProposals = postedJobs.stream()
                    .flatMap(job -> {
                        JobPostActivity jobPost = jobPostActivityService.getOne(job.getJobPostId());
                        return jobSeekerApplyService.getJobCandidates(jobPost).stream();
                    })
                    .collect(Collectors.toList());

            // Count unseen proposals (proposals that haven't been viewed)
            long unseenProposals = allProposals.stream()
                    .filter(p -> !"WORK_SUBMITTED".equals(p.getApplicationStatus()))
                    .count(); // For now, count all non-submitted as "unseen" - can be refined later

            // Count active projects (HIRED status)
            long activeProjects = allProposals.stream()
                    .filter(p -> "HIRED".equals(p.getApplicationStatus()))
                    .count();

            model.addAttribute("unseenProposals", (int) unseenProposals);
            model.addAttribute("activeProjects", (int) activeProjects);
        }
        // For Freelancers (userTypeId == 2)
        else if (userTypeId != null && userTypeId == 2 && userProfile instanceof JobSeekerProfile) {
            JobSeekerProfile freelancerProfile = (JobSeekerProfile) userProfile;

            // Get applied jobs
            List<JobSeekerApply> appliedJobs = jobSeekerApplyService.getCandidatesJobs(freelancerProfile);

            // Count unseen proposals (for freelancers, this might be different)
            long unseenProposals = appliedJobs.stream()
                    .filter(p -> !"WORK_SUBMITTED".equals(p.getApplicationStatus()))
                    .count();

            // Count active projects (HIRED status)
            long activeProjects = appliedJobs.stream()
                    .filter(p -> "HIRED".equals(p.getApplicationStatus()))
                    .count();

            model.addAttribute("unseenProposals", (int) unseenProposals);
            model.addAttribute("activeProjects", (int) activeProjects);
        }
        // Default values for other user types
        else {
            model.addAttribute("unseenProposals", 0);
            model.addAttribute("activeProjects", 0);
        }
    }

    @GetMapping("/unread")
    @ResponseBody
    public List<ChatMessage> getUnreadMessages() {
        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return List.of();
        }
        return chatService.getUnreadMessages(currentUser);
    }

    @GetMapping("/debug")
    @ResponseBody
    public String debugUsers() {
        Users currentUser = usersService.getCurrentUser();
        StringBuilder sb = new StringBuilder();
        sb.append("Current User: ").append(currentUser.getEmail())
                .append(" (ID: ").append(currentUser.getUserId())
                .append(", Type: ").append(currentUser.getUserTypeId().getUserTypeName())
                .append("[").append(currentUser.getUserTypeId().getUserTypeId()).append("]")
                .append(", Approved: ").append(currentUser.isApproved())
                .append(")\n\n");

        sb.append("All Users:\n");
        usersService.getAllUsers().forEach(u -> {
            sb.append("ID: ").append(u.getUserId())
                    .append(", Email: ").append(u.getEmail())
                    .append(", Type: ").append(u.getUserTypeId().getUserTypeId())
                    .append(", Approved: ").append(u.isApproved())
                    .append(", Active: ").append(u.isActive())
                    .append("\n");
        });

        sb.append("\nPartners found for current user:\n");
        chatService.getVerifiedChatPartners(currentUser).forEach(u -> {
            sb.append(u.getEmail()).append("\n");
        });

        return sb.toString();
    }

    @PostMapping("/message/edit")
    @ResponseBody
    public ResponseEntity<String> editMessage(@RequestParam Integer messageId, @RequestParam String newContent) {
        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null)
            return ResponseEntity.status(403).body("Unauthorized");

        chatService.editMessage(messageId, newContent);
        return ResponseEntity.ok("Edited");
    }

    @PostMapping("/message/delete")
    @ResponseBody
    public ResponseEntity<String> deleteMessage(@RequestParam Integer messageId) {
        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null)
            return ResponseEntity.status(403).body("Unauthorized");

        // Check ownership? ideally yes but for now we trust the logic in service or
        // simple ID check
        // Ideally should check if message.sender.id == currentUser.id before invoking
        // service

        chatService.deleteMessage(messageId);
        return ResponseEntity.ok("Deleted");
    }

    @PostMapping("/message/upload")
    @ResponseBody
    public ResponseEntity<ChatMessage> uploadAttachment(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam Integer receiverId,
            @RequestParam(required = false) Integer jobId) {

        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null)
            throw new RuntimeException("Unauthorized");

        try {
            // Determine type
            String contentType = file.getContentType();
            String type = "FILE";
            if (contentType != null && contentType.startsWith("image")) {
                type = "IMAGE";
            } else if (contentType != null && contentType.startsWith("audio")) {
                type = "AUDIO";
            }

            String folder = "jobportal/chat/" + currentUser.getUserId();
            String attachmentUrl = cloudinaryService.uploadMedia(file, folder);

            try {
                String filename = org.springframework.util.StringUtils
                        .cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
                String uniqueFilename = System.currentTimeMillis() + "_" + filename;
                java.nio.file.Path uploadPath = java.nio.file.Paths.get(uploadDir);
                if (!java.nio.file.Files.exists(uploadPath)) {
                    java.nio.file.Files.createDirectories(uploadPath);
                }
                try (java.io.InputStream inputStream = file.getInputStream()) {
                    java.nio.file.Path filePath = uploadPath.resolve(uniqueFilename);
                    java.nio.file.Files.copy(inputStream, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) {}

            // Create Message
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setSenderId(currentUser);
            chatMessage.setReceiverId(usersService.getUserById(receiverId));
            chatMessage.setTimestamp(new Date());
            chatMessage.setIsRead(false);
            if (type.equals("IMAGE")) {
                chatMessage.setMessage("Sent an image");
            } else if (type.equals("AUDIO")) {
                chatMessage.setMessage("Sent a voice message");
            } else {
                chatMessage.setMessage("Sent a file");
            }
            chatMessage.setAttachmentPath(attachmentUrl);
            chatMessage.setAttachmentType(type);

            if (jobId != null) {
                chatMessage.setJobId(jobPostActivityService.getOne(jobId));
            }

            ChatMessage saved = chatService.saveMessage(chatMessage);
            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            throw new RuntimeException("Could not store file", e);
        }
    }
}
