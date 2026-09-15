package com.webapp.jobportal.services;

import com.webapp.jobportal.entity.*;
import com.webapp.jobportal.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UsersService {

    @Value("${jobify.app.base-url:https://jobify-platform.onrender.com}")
    private String appBaseUrl;

    private final UsersRepository usersRepository;
    private final JobSeekerProfileRepository jobSeekerProfileRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JobPostActivityRepository jobPostActivityRepository;
    private final JobSeekerApplyRepository jobSeekerApplyRepository;
    private final JobSeekerSaveRepository jobSeekerSaveRepository;
    private final JobModerationLogRepository jobModerationLogRepository;
    private final NotificationRepository notificationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DisputeRepository disputeRepository;
    private final PaymentRepository paymentRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final RatingRepository ratingRepository;
    private final EmailService emailService;
    private final VerificationTokenRepository verificationTokenRepository;

    @Autowired
    public UsersService(UsersRepository usersRepository, JobSeekerProfileRepository jobSeekerProfileRepository,
            RecruiterProfileRepository recruiterProfileRepository, PasswordEncoder passwordEncoder,
            JobPostActivityRepository jobPostActivityRepository, JobSeekerApplyRepository jobSeekerApplyRepository,
            JobSeekerSaveRepository jobSeekerSaveRepository, JobModerationLogRepository jobModerationLogRepository,
            NotificationRepository notificationRepository, ChatMessageRepository chatMessageRepository,
            DisputeRepository disputeRepository, PaymentRepository paymentRepository,
            WithdrawalRepository withdrawalRepository, RatingRepository ratingRepository, EmailService emailService,
            VerificationTokenRepository verificationTokenRepository) {
        this.usersRepository = usersRepository;
        this.jobSeekerProfileRepository = jobSeekerProfileRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jobPostActivityRepository = jobPostActivityRepository;
        this.jobSeekerApplyRepository = jobSeekerApplyRepository;
        this.jobSeekerSaveRepository = jobSeekerSaveRepository;
        this.jobModerationLogRepository = jobModerationLogRepository;
        this.notificationRepository = notificationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.disputeRepository = disputeRepository;
        this.paymentRepository = paymentRepository;
        this.withdrawalRepository = withdrawalRepository;
        this.ratingRepository = ratingRepository;
        this.emailService = emailService;
        this.verificationTokenRepository = verificationTokenRepository;
    }

    public Users addNew(Users users) {
        return addNew(users, null);
    }

    public Users addNew(Users users, String appUrl) {
        users.setActive(false);
        // Admin users are auto-approved, others need admin approval
        int userTypeId = users.getUserTypeId().getUserTypeId();
        users.setApproved(userTypeId == 3); // Admin = 3
        users.setRegistrationDate(new Date(System.currentTimeMillis()));
        users.setPassword(passwordEncoder.encode(users.getPassword()));
        Users savedUser = usersRepository.save(users);

        // User type: 1=Client, 2=Freelancer, 3=Admin
        if (userTypeId == 1) {
            recruiterProfileRepository.save(new RecruiterProfile(savedUser));
        } else if (userTypeId == 2) {
            jobSeekerProfileRepository.save(new JobSeekerProfile(savedUser));
        }

        // Clean up any old tokens for this user first
        verificationTokenRepository.findByUser(savedUser).ifPresent(verificationTokenRepository::delete);

        // Generate Verification Token (24h validity)
        VerificationToken verificationToken = new VerificationToken(savedUser);
        verificationTokenRepository.save(verificationToken);

        // Build verification link dynamically using provided appUrl or fallback to configured base URL
        String baseUrl = (appUrl != null && !appUrl.trim().isEmpty()) ? appUrl.trim() : appBaseUrl;
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String verificationLink = baseUrl + "/verify-email?token=" + verificationToken.getToken();

        // Send Verification Email
        String name = "User";
        emailService.sendVerificationEmail(savedUser.getEmail(), name, verificationLink);

        return savedUser;
    }

    public void sendVerificationToken(Users user, String appUrl) {
        // Clean up existing token
        verificationTokenRepository.findByUser(user).ifPresent(verificationTokenRepository::delete);

        VerificationToken verificationToken = new VerificationToken(user);
        verificationTokenRepository.save(verificationToken);

        String baseUrl = (appUrl != null && !appUrl.trim().isEmpty()) ? appUrl.trim() : appBaseUrl;
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String verificationLink = baseUrl + "/verify-email?token=" + verificationToken.getToken();

        String name = getUserFullName(user);
        if (name == null || name.trim().isEmpty()) {
            name = "User";
        }

        emailService.sendVerificationEmail(user.getEmail(), name, verificationLink);
    }

    public Object getCurrentUserProfile() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            String username = authentication.getName();
            Users users = usersRepository.findByEmail(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Could not found " + "user"));
            int userId = users.getUserId();

            // Debug logging
            System.out.println("User ID: " + userId + ", User Type ID: " + users.getUserTypeId().getUserTypeId());
            authentication.getAuthorities()
                    .forEach(auth -> System.out.println("User authority: " + auth.getAuthority()));

            if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("Client"))) {
                RecruiterProfile recruiterProfile = recruiterProfileRepository.findById(userId)
                        .orElse(new RecruiterProfile());
                return recruiterProfile;
            } else if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("Freelancer"))) {
                JobSeekerProfile jobSeekerProfile = jobSeekerProfileRepository.findById(userId)
                        .orElse(new JobSeekerProfile());
                return jobSeekerProfile;
            }
        }

        return null;
    }

    public Users getCurrentUser() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            String username = authentication.getName();
            Users user = usersRepository.findByEmail(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Could not found " + "user"));
            return user;
        }

        return null;
    }

    public Users findByEmail(String currentUsername) {
        return usersRepository.findByEmail(currentUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not " +
                        "found"));
    }

    public Optional<Users> getUserByEmail(String email) {
        return usersRepository.findByEmail(email);
    }

    public List<Users> getAllUsers() {
        return usersRepository.findAll();
    }

    public List<Users> getPendingUsers() {
        return usersRepository.findAll().stream()
                .filter(user -> !user.isApproved())
                .collect(java.util.stream.Collectors.toList());
    }

    public List<Users> getPendingUsersForVerification() {
        return usersRepository.findAll().stream()
                .filter(user -> !user.isApproved()) // Allow inactive/banned users to show up so they can be unbanned
                .filter(user -> user.getUserTypeId() != null
                        && (user.getUserTypeId().getUserTypeId() == 1 || user.getUserTypeId().getUserTypeId() == 2))
                .filter(user -> {
                    // Filter out rejected profiles
                    if (user.getUserTypeId().getUserTypeId() == 1) { // Client
                        Optional<RecruiterProfile> p = recruiterProfileRepository.findById(user.getUserId());
                        return p.isPresent() && !"rejected".equalsIgnoreCase(p.get().getDocumentStatus());
                    } else if (user.getUserTypeId().getUserTypeId() == 2) { // Freelancer
                        Optional<JobSeekerProfile> p = jobSeekerProfileRepository.findById(user.getUserId());
                        return p.isPresent() && !"rejected".equalsIgnoreCase(p.get().getDocumentStatus());
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    public Users approveUser(int userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setApproved(true);
        return usersRepository.save(user);
    }

    public Users banUser(int userId, String reason) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        Users savedUser = usersRepository.save(user); // Save first

        // Send email
        String userName = getUserFullName(user);
        emailService.sendBanNotification(user.getEmail(), userName, reason);

        return savedUser;
    }

    public Users unbanUser(int userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(true);
        Users savedUser = usersRepository.save(user); // Save first

        // Send email
        String userName = getUserFullName(user);
        emailService.sendUnbanNotification(user.getEmail(), userName);

        return savedUser;
    }

    public Users rejectUser(int userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setApproved(false);
        user.setActive(false);
        return usersRepository.save(user);
    }

    @Transactional
    public void deleteUser(int userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Common deletions for all user types (Messages, Notifications, Disputes,
        // Payments, Withdrawals)
        // Note: Repository methods for deleting by User might need to be added if not
        // present,
        // or we fetch and delete. For simplicity in this generated code, we'll iterate
        // or assume
        // cascade setup in DB, BUT since we want to be sure, we should better fetch and
        // delete where possible.
        // However, standard JPA/Hibernate CascadeType.ALL on Entities would handle this
        // automatically if configured.
        // Looking at entities, many don't have Cascade defined on the @OneToMany side
        // (which is often missing).
        // So we manually clean up to be safe and thorough.

        // 1. Chat Messages
        // We need to delete messages where user is sender OR receiver
        // (Assuming we want to wipe them out completely. Alternatively, we could
        // anonymize them)
        // A simple approach:
        // chatMessageRepository.deleteBySenderIdOrReceiverId(user, user); // (Requires
        // Repo method)
        // Manual way:
        List<ChatMessage> messages = chatMessageRepository.findAll().stream() // Not efficient but safe for now given
                                                                              // repo limits
                .filter(m -> m.getSenderId().getUserId() == userId || m.getReceiverId().getUserId() == userId)
                .collect(Collectors.toList());
        chatMessageRepository.deleteAll(messages);

        // 2. Notifications
        List<Notification> notifications = notificationRepository.findAll().stream()
                .filter(n -> n.getUserId().getUserId() == userId)
                .collect(Collectors.toList());
        notificationRepository.deleteAll(notifications);

        // 3. Disputes (Reporter or Against)
        List<Dispute> disputes = disputeRepository.findAll().stream()
                .filter(d -> d.getReporter().getUserId() == userId
                        || (d.getAgainst() != null && d.getAgainst().getUserId() == userId))
                .collect(Collectors.toList());
        disputeRepository.deleteAll(disputes);

        // 4. Payments (Payer or Payee)
        List<Payment> payments = paymentRepository.findAll().stream()
                .filter(p -> p.getPayer().getUserId() == userId || p.getPayee().getUserId() == userId)
                .collect(Collectors.toList());
        paymentRepository.deleteAll(payments);

        // 5. Withdrawals
        List<Withdrawal> withdrawals = withdrawalRepository.findAll().stream()
                .filter(w -> w.getUser().getUserId() == userId)
                .collect(Collectors.toList());
        withdrawalRepository.deleteAll(withdrawals);

        // Specific deletions based on user type
        if (user.getUserTypeId().getUserTypeId() == 1) { // Client / Recruiter
            RecruiterProfile profile = recruiterProfileRepository.findById(userId).orElse(null);

            // Delete Jobs posted by Recruiter
            // Jobs link to Applications, Saves, Logs, etc. We must cascade those.
            List<JobPostActivity> jobs = jobPostActivityRepository.getRecruiterJobs(userId).stream()
                    .map(dto -> jobPostActivityRepository.findById(dto.getJob_post_id()).orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());

            for (JobPostActivity job : jobs) {
                // Delete Applications for this job
                List<JobSeekerApply> applications = jobSeekerApplyRepository.findByJob(job);
                jobSeekerApplyRepository.deleteAll(applications);

                // Delete Saves for this job
                List<JobSeekerSave> saves = jobSeekerSaveRepository.findByJob(job);
                jobSeekerSaveRepository.deleteAll(saves);

                // Delete Moderation Logs for this job
                List<JobModerationLog> logs = jobModerationLogRepository.findByJobOrderByTimestampDesc(job);
                jobModerationLogRepository.deleteAll(logs);

                // Delete the Job itself
                jobPostActivityRepository.delete(job);
            }

            // Delete Ratings (as client)
            List<Rating> ratings = ratingRepository.findAll().stream()
                    .filter(r -> r.getClient().getUserId() == userId)
                    .collect(Collectors.toList());
            ratingRepository.deleteAll(ratings);

            // Delete Profile
            if (profile != null) {
                recruiterProfileRepository.delete(profile);
            }

        } else if (user.getUserTypeId().getUserTypeId() == 2) { // Freelancer
            JobSeekerProfile profile = jobSeekerProfileRepository.findById(userId).orElse(null);

            if (profile != null) {
                // Delete Applications made by freelancer
                List<JobSeekerApply> applications = jobSeekerApplyRepository.findByUserId(profile);
                jobSeekerApplyRepository.deleteAll(applications);

                // Delete Saved Jobs
                List<JobSeekerSave> saves = jobSeekerSaveRepository.findByUserId(profile);
                jobSeekerSaveRepository.deleteAll(saves);

                // Delete Ratings (as freelancer)
                List<Rating> ratings = ratingRepository.findAll().stream()
                        .filter(r -> r.getFreelancer().getUserAccountId() == userId)
                        .collect(Collectors.toList());
                ratingRepository.deleteAll(ratings);

                // Delete Profile
                jobSeekerProfileRepository.delete(profile);
            }
        }

        // Delete Moderation Logs where user was the ACTION BY (e.g. if they were a
        // moderator? or if logic changes)
        // Typically regular users don't create logs, but good to check if we tracked
        // "reported by" etc.
        // Assuming current logic only tracks admin actions, but if `actionBy` FK
        // exists:
        List<JobModerationLog> logsByAction = jobModerationLogRepository.findAll().stream()
                .filter(l -> l.getActionBy() != null && l.getActionBy().getUserId() == userId)
                .collect(Collectors.toList());
        jobModerationLogRepository.deleteAll(logsByAction);

        // Finally, delete the User
        usersRepository.delete(user);
    }

    public long getTotalUsers() {
        return usersRepository.count();
    }

    public long getTotalClients() {
        return usersRepository.findAll().stream()
                .filter(user -> user.getUserTypeId().getUserTypeId() == 1)
                .count();
    }

    public long getTotalFreelancers() {
        return usersRepository.findAll().stream()
                .filter(user -> user.getUserTypeId().getUserTypeId() == 2)
                .count();
    }

    public int getTotalCountries() {
        java.util.Set<String> uniqueCountries = new java.util.HashSet<>();

        List<String> freelancerCountries = jobSeekerProfileRepository.findDistinctCountries();
        if (freelancerCountries != null)
            uniqueCountries.addAll(freelancerCountries);

        List<String> recruiterCountries = recruiterProfileRepository.findDistinctCountries();
        if (recruiterCountries != null)
            uniqueCountries.addAll(recruiterCountries);

        return uniqueCountries.size();
    }

    public Users getUserById(int userId) {
        return usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public boolean isUserFullyVerified(Users user) {
        // if (!user.isApproved())
        // return false;

        if (user.getUserTypeId().getUserTypeId() == 1) { // Client
            RecruiterProfile profile = recruiterProfileRepository.findById(user.getUserId()).orElse(null);
            return profile != null && Boolean.TRUE.equals(profile.getIsVerified());
        } else if (user.getUserTypeId().getUserTypeId() == 2) { // Freelancer
            JobSeekerProfile profile = jobSeekerProfileRepository.findById(user.getUserId()).orElse(null);
            return profile != null && Boolean.TRUE.equals(profile.getIsVerified());
        }

        return user.getUserTypeId().getUserTypeId() == 3; // Admin is always verified
    }

    public void changePassword(Users user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        usersRepository.save(user);
    }

    public boolean verifyPassword(Users user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    public void save(Users user) {
        usersRepository.save(user);
    }

    public String getUserFullName(Users user) {
        if (user == null)
            return "Unknown";

        String firstName = "";
        String lastName = "";

        if (user.getUserTypeId().getUserTypeId() == 1) { // Client
            RecruiterProfile profile = recruiterProfileRepository.findById(user.getUserId()).orElse(null);
            if (profile != null) {
                firstName = profile.getFirstName();
                lastName = profile.getLastName();
            }
        } else if (user.getUserTypeId().getUserTypeId() == 2) { // Freelancer
            JobSeekerProfile profile = jobSeekerProfileRepository.findById(user.getUserId()).orElse(null);
            if (profile != null) {
                firstName = profile.getFirstName();
                lastName = profile.getLastName();
            }
        }

        if (firstName != null && !firstName.isEmpty()) {
            return firstName + " " + (lastName != null ? lastName : "");
        }

        return user.getEmail(); // Fallback to email
    }

    public String getUserProfileImagePath(Users user) {
        if (user == null)
            return null;

        if (user.getUserTypeId().getUserTypeId() == 1) { // Client
            RecruiterProfile profile = recruiterProfileRepository.findById(user.getUserId()).orElse(null);
            return (profile != null) ? profile.getPhotosImagePath() : null;
        } else if (user.getUserTypeId().getUserTypeId() == 2) { // Freelancer
            JobSeekerProfile profile = jobSeekerProfileRepository.findById(user.getUserId()).orElse(null);
            return (profile != null) ? profile.getPhotosImagePath() : null;
        }
        return null; // Admins or unknown types
    }
}
