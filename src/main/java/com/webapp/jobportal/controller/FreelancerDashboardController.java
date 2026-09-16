package com.webapp.jobportal.controller;

import com.webapp.jobportal.entity.*;
import com.webapp.jobportal.services.*;
import com.webapp.jobportal.repository.WithdrawalRepository;
import com.webapp.jobportal.repository.PaymentRepository;
import com.webapp.jobportal.repository.ContractRepository;
import com.webapp.jobportal.repository.MilestoneRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/freelancer-dashboard")
public class FreelancerDashboardController {

    private final UsersService usersService;
    private final JobPostActivityService jobPostActivityService;
    private final JobSeekerApplyService jobSeekerApplyService;
    private final JobSeekerSaveService jobSeekerSaveService;
    private final ChatService chatService;
    private final RecruiterProfileService recruiterProfileService;
    private final WithdrawalRepository withdrawalRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final PaymentRepository paymentRepository;
    private final StripeService stripeService;
    private final ContractRepository contractRepository;
    private final MilestoneRepository milestoneRepository;

    @Autowired
    public FreelancerDashboardController(UsersService usersService,
            JobPostActivityService jobPostActivityService,
            JobSeekerApplyService jobSeekerApplyService,
            JobSeekerSaveService jobSeekerSaveService,
            ChatService chatService,
            RecruiterProfileService recruiterProfileService,
            WithdrawalRepository withdrawalRepository,
            NotificationService notificationService,
            EmailService emailService,
            PaymentRepository paymentRepository,
            StripeService stripeService,
            ContractRepository contractRepository,
            MilestoneRepository milestoneRepository) {
        this.usersService = usersService;
        this.jobPostActivityService = jobPostActivityService;
        this.jobSeekerApplyService = jobSeekerApplyService;
        this.jobSeekerSaveService = jobSeekerSaveService;
        this.chatService = chatService;
        this.recruiterProfileService = recruiterProfileService;
        this.withdrawalRepository = withdrawalRepository;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.paymentRepository = paymentRepository;
        this.stripeService = stripeService;
        this.contractRepository = contractRepository;
        this.milestoneRepository = milestoneRepository;
    }

    @GetMapping("/")
    public String freelancerDashboard(Model model,
            @RequestParam(value = "job", required = false) String job,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "partTime", required = false) String partTime,
            @RequestParam(value = "fullTime", required = false) String fullTime,
            @RequestParam(value = "freelance", required = false) String freelance,
            @RequestParam(value = "remoteOnly", required = false) String remoteOnly,
            @RequestParam(value = "officeOnly", required = false) String officeOnly,
            @RequestParam(value = "partialRemote", required = false) String partialRemote,
            @RequestParam(value = "today", required = false) boolean today,
            @RequestParam(value = "days7", required = false) boolean days7,
            @RequestParam(value = "days30", required = false) boolean days30,
            @RequestParam(value = "category", required = false) String category) {
        return freelancerDashboardRedirect(model, job, location, partTime, fullTime, freelance, remoteOnly, officeOnly,
                partialRemote, today, days7, days30, category);
    }

    @GetMapping("")
    public String freelancerDashboardRedirect(Model model,
            @RequestParam(value = "job", required = false) String job,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "partTime", required = false) String partTime,
            @RequestParam(value = "fullTime", required = false) String fullTime,
            @RequestParam(value = "freelance", required = false) String freelance,
            @RequestParam(value = "remoteOnly", required = false) String remoteOnly,
            @RequestParam(value = "officeOnly", required = false) String officeOnly,
            @RequestParam(value = "partialRemote", required = false) String partialRemote,
            @RequestParam(value = "today", required = false) boolean today,
            @RequestParam(value = "days7", required = false) boolean days7,
            @RequestParam(value = "days30", required = false) boolean days30,
            @RequestParam(value = "category", required = false) String category) {

        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        // Verify user is a Freelancer
        if (currentUser.getUserTypeId().getUserTypeId() != 2) {
            return "redirect:/dashboard/";
        }

        JobSeekerProfile freelancerProfile = (JobSeekerProfile) usersService.getCurrentUserProfile();
        if (freelancerProfile == null) {
            return "redirect:/job-seeker-profile/";
        }

        // Get applied jobs (proposals)
        List<JobSeekerApply> appliedJobs = jobSeekerApplyService.getCandidatesJobs(freelancerProfile);
        appliedJobs.forEach(app -> populateRecruiterInfo(app.getJob()));

        // Get ongoing projects (jobs with HIRED status)
        List<JobSeekerApply> ongoingProjects = appliedJobs.stream()
                .filter(application -> "HIRED".equals(application.getApplicationStatus()))
                .collect(Collectors.toList());

        // Get saved jobs
        List<JobSeekerSave> savedJobs = jobSeekerSaveService.getCandidatesJob(freelancerProfile);
        savedJobs.forEach(save -> populateRecruiterInfo(save.getJob()));

        // Get unread messages
        List<ChatMessage> unreadMessages = chatService.getUnreadMessages(currentUser);

        // Search for jobs
        model.addAttribute("partTime", Objects.equals(partTime, "Part-Time"));
        model.addAttribute("fullTime", Objects.equals(fullTime, "Full-Time"));
        model.addAttribute("freelance", Objects.equals(freelance, "Freelance"));
        model.addAttribute("remoteOnly", Objects.equals(remoteOnly, "Remote-Only"));
        model.addAttribute("officeOnly", Objects.equals(officeOnly, "Office-Only"));
        model.addAttribute("partialRemote", Objects.equals(partialRemote, "Partial-Remote"));
        model.addAttribute("today", today);
        model.addAttribute("days7", days7);
        model.addAttribute("days30", days30);
        model.addAttribute("job", job);
        model.addAttribute("location", location);
        model.addAttribute("category", category);

        LocalDate searchDate = null;
        List<JobPostActivity> jobPost = null;
        boolean dateSearchFlag = true;
        boolean remote = true;
        boolean type = true;

        if (days30) {
            searchDate = LocalDate.now().minusDays(30);
        } else if (days7) {
            searchDate = LocalDate.now().minusDays(7);
        } else if (today) {
            searchDate = LocalDate.now();
        } else {
            dateSearchFlag = false;
        }

        if (partTime == null && fullTime == null && freelance == null) {
            partTime = "Part-Time";
            fullTime = "Full-Time";
            freelance = "Freelance";
            remote = false;
        }

        if (officeOnly == null && remoteOnly == null && partialRemote == null) {
            officeOnly = "Office-Only";
            remoteOnly = "Remote-Only";
            partialRemote = "Partial-Remote";
            type = false;
        }

        if (!dateSearchFlag && !remote && !type && !StringUtils.hasText(job) && !StringUtils.hasText(location)
                && !StringUtils.hasText(category)) {
            jobPost = jobPostActivityService.getAll();
        } else {
            jobPost = jobPostActivityService.search(job, location, Arrays.asList(partTime, fullTime, freelance),
                    Arrays.asList(remoteOnly, officeOnly, partialRemote), searchDate, category);
        }

        // Sort and Limit to 6 recent jobs
        if (jobPost != null) {
            jobPost.sort(Comparator.comparing(JobPostActivity::getPostedDate,
                    Comparator.nullsLast(Comparator.reverseOrder())));
            jobPost = jobPost.stream().limit(6).collect(Collectors.toList());
        } else {
            jobPost = new java.util.ArrayList<>();
        }

        jobPost.forEach(this::populateRecruiterInfo);

        // Mark which jobs are applied/saved
        for (JobPostActivity jobActivity : jobPost) {
            boolean exist = false;
            boolean saved = false;

            // Set basic status
            jobActivity.setUserApplicationStatus("Open");

            for (JobSeekerApply jobSeekerApply : appliedJobs) {
                if (Objects.equals(jobActivity.getJobPostId(), jobSeekerApply.getJob().getJobPostId())) {
                    jobActivity.setIsActive(true);
                    exist = true;
                    // Set detailed status
                    String status = jobSeekerApply.getApplicationStatus();
                    if ("HIRED".equals(status)) {
                        jobActivity.setUserApplicationStatus("In Progress");
                    } else {
                        jobActivity.setUserApplicationStatus("Applied");
                    }
                    break;
                }
            }
            for (JobSeekerSave jobSeekerSave : savedJobs) {
                if (Objects.equals(jobActivity.getJobPostId(), jobSeekerSave.getJob().getJobPostId())) {
                    jobActivity.setIsSaved(true);
                    saved = true;
                    break;
                }
            }
            if (!exist) {
                jobActivity.setIsActive(false);
            }
            if (!saved) {
                jobActivity.setIsSaved(false);
            }
        }

        boolean isVerified = Boolean.TRUE.equals(freelancerProfile.getIsVerified()) && currentUser.isApproved();
        String documentStatus = freelancerProfile.getDocumentStatus() != null ? freelancerProfile.getDocumentStatus() : "";

        // Dispatch verification reminder if unverified and no reminder sent yet
        if (!isVerified) {
            checkAndSendVerificationNotification(currentUser);
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("user", freelancerProfile);
        model.addAttribute("jobPost", jobPost);
        model.addAttribute("appliedJobs", appliedJobs);
        model.addAttribute("ongoingProjects", ongoingProjects);
        model.addAttribute("savedJobs", savedJobs);
        model.addAttribute("unreadMessagesCount", unreadMessages.size());
        model.addAttribute("isVerified", isVerified);
        model.addAttribute("documentStatus", documentStatus);

        return "freelancer-dashboard";
    }

    private void populateRecruiterInfo(JobPostActivity jobActivity) {
        if (jobActivity == null || jobActivity.getPostedById() == null)
            return;
        RecruiterProfile recruiter = recruiterProfileService.getOne(jobActivity.getPostedById().getUserId())
                .orElse(null);
        if (recruiter != null) {
            jobActivity.setRecruiterFirstName(recruiter.getFirstName());
            jobActivity.setRecruiterLastName(recruiter.getLastName());
            jobActivity.setRecruiterCity(recruiter.getCity());
            jobActivity.setRecruiterCountry(recruiter.getCountry());
            jobActivity.setRecruiterCompany(recruiter.getCompany() != null ? recruiter.getCompany() : "");
            jobActivity.setRecruiterProfilePhoto(recruiter.getPhotosImagePath());
            jobActivity.setPostedByVerified(
                    Boolean.TRUE.equals(recruiter.getIsVerified()) && jobActivity.getPostedById().isApproved());
        }
    }

    @GetMapping("/proposals")
    public String viewProposals(Model model) {
        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        if (currentUser.getUserTypeId().getUserTypeId() != 2) {
            return "redirect:/dashboard/";
        }

        JobSeekerProfile freelancerProfile = (JobSeekerProfile) usersService.getCurrentUserProfile();
        if (freelancerProfile == null) {
            return "redirect:/job-seeker-profile/";
        }

        List<JobSeekerApply> appliedJobs = jobSeekerApplyService.getCandidatesJobs(freelancerProfile);
        appliedJobs.forEach(app -> populateRecruiterInfo(app.getJob()));

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("user", freelancerProfile);
        model.addAttribute("proposals", appliedJobs);
        model.addAttribute("isVerified",
                Boolean.TRUE.equals(freelancerProfile.getIsVerified()) && currentUser.isApproved());
        model.addAttribute("documentStatus", freelancerProfile.getDocumentStatus() != null ? freelancerProfile.getDocumentStatus() : "");
        model.addAttribute("backUrl", "/freelancer-dashboard/");

        return "freelancer-proposals";
    }

    @GetMapping("/projects")
    public String viewProjects(Model model) {
        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        if (currentUser.getUserTypeId().getUserTypeId() != 2) {
            return "redirect:/dashboard/";
        }

        JobSeekerProfile freelancerProfile = (JobSeekerProfile) usersService.getCurrentUserProfile();
        if (freelancerProfile == null) {
            return "redirect:/job-seeker-profile/";
        }

        List<JobSeekerApply> appliedJobs = jobSeekerApplyService.getCandidatesJobs(freelancerProfile);
        List<JobSeekerApply> ongoingProjects = appliedJobs.stream()
                .filter(application -> "HIRED".equals(application.getApplicationStatus()))
                .collect(Collectors.toList());

        ongoingProjects.forEach(app -> populateRecruiterInfo(app.getJob()));

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("user", freelancerProfile);
        model.addAttribute("ongoingProjects", ongoingProjects);
        model.addAttribute("isVerified",
                Boolean.TRUE.equals(freelancerProfile.getIsVerified()) && currentUser.isApproved());
        model.addAttribute("documentStatus", freelancerProfile.getDocumentStatus() != null ? freelancerProfile.getDocumentStatus() : "");

        return "freelancer-projects";
    }

    @GetMapping("/earnings")
    public String viewEarnings(Model model) {
        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        if (currentUser.getUserTypeId().getUserTypeId() != 2) {
            return "redirect:/dashboard/";
        }

        JobSeekerProfile freelancerProfile = (JobSeekerProfile) usersService.getCurrentUserProfile();
        if (freelancerProfile == null) {
            return "redirect:/job-seeker-profile/";
        }

        List<JobSeekerApply> appliedJobs = jobSeekerApplyService.getCandidatesJobs(freelancerProfile);
        List<JobSeekerApply> ongoingProjects = appliedJobs.stream()
                .filter(application -> "HIRED".equals(application.getApplicationStatus()))
                .collect(Collectors.toList());

        ongoingProjects.forEach(app -> populateRecruiterInfo(app.getJob()));

        double totalProjectValue = ongoingProjects.stream()
                .mapToDouble(project -> project.getProposedRate() != null ? project.getProposedRate() : 0.0)
                .sum();

        // Calculate withdrawals
        List<Withdrawal> withdrawals = withdrawalRepository.findByUser(currentUser);

        // Check which projects are paid AND calculate remaining limit
        java.util.Map<Integer, Boolean> projectPaymentStatus = new java.util.HashMap<>();
        java.util.Map<Integer, Double> projectRemainingBalance = new java.util.HashMap<>();
        java.util.Map<Integer, Double> withdrawableBalanceMap = new java.util.HashMap<>();

        double availableBalance = 0.0;
        double pendingClearance = 0.0;

        for (JobSeekerApply project : ongoingProjects) {
            JobPostActivity job = project.getJob();
            Double rate = project.getProposedRate() != null ? project.getProposedRate() : 0.0;

            // Check payments for this job
            List<Payment> payments = paymentRepository.findByJob(job);

            // 1. Released funds (Approved by client - COMPLETED status)
            double releasedForJob = payments.stream()
                    .filter(p -> "COMPLETED".equals(p.getStatus()))
                    .mapToDouble(Payment::getAmount)
                    .sum();

            // 2. Escrowed funds (Funded but not approved - ESCROW_HELD or PENDING)
            double heldInEscrow = payments.stream()
                    .filter(p -> "ESCROW_HELD".equals(p.getStatus()) || "PENDING".equals(p.getStatus()))
                    .mapToDouble(Payment::getAmount)
                    .sum();

            // Set payment status flag for UI badges (true if funded or released)
            projectPaymentStatus.put(job.getJobPostId(), (releasedForJob > 0 || heldInEscrow > 0));

            // Check how much freelancer already withdrew
            List<Withdrawal> jobWithdrawals = withdrawalRepository.findByJob(job);
            double withdrawnForJob = jobWithdrawals.stream().mapToDouble(Withdrawal::getAmount).sum();

            // Amount available for withdrawal right now
            double withdrawable = Math.max(0, releasedForJob - withdrawnForJob);
            // Total amount still remaining on the contract (Budget - Withdrawn)
            double remainingOnContract = Math.max(0, rate - withdrawnForJob);

            projectRemainingBalance.put(job.getJobPostId(), remainingOnContract);
            withdrawableBalanceMap.put(job.getJobPostId(), withdrawable);

            availableBalance += withdrawable;
            pendingClearance += heldInEscrow;
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("user", freelancerProfile);
        model.addAttribute("ongoingProjects", ongoingProjects);
        model.addAttribute("totalProjectValue", totalProjectValue);
        model.addAttribute("availableBalance", availableBalance);
        model.addAttribute("pendingClearance", pendingClearance);
        model.addAttribute("withdrawals", withdrawals);
        model.addAttribute("projectPaymentStatus", projectPaymentStatus);
        model.addAttribute("projectRemainingBalance", projectRemainingBalance);
        model.addAttribute("withdrawableBalance", withdrawableBalanceMap);
        model.addAttribute("isVerified",
                Boolean.TRUE.equals(freelancerProfile.getIsVerified()) && currentUser.isApproved());
        model.addAttribute("documentStatus", freelancerProfile.getDocumentStatus() != null ? freelancerProfile.getDocumentStatus() : "");

        return "freelancer-earnings";
    }

    @PostMapping("/process-withdrawal")
    public String processWithdrawal(@RequestParam("projectId") Integer projectId,
            @RequestParam(value = "amount", required = false, defaultValue = "0.0") Double amount,
            @RequestParam("method") String method,
            RedirectAttributes redirectAttributes) {

        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null)
            return "redirect:/login";

        if (amount == null || amount <= 0) {
            redirectAttributes.addFlashAttribute("error", "Invalid withdrawal amount.");
            return "redirect:/freelancer-dashboard/earnings";
        }

        // Verify Stripe Account Connection
        if (currentUser.getStripeAccountId() == null || currentUser.getStripeAccountId().isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "Please connect your Stripe account before withdrawing funds.");
            return "redirect:/freelancer-dashboard/earnings";
        }

        JobPostActivity job = jobPostActivityService.getOne(projectId);

        // Validation: Verify this freelancer is hired for this job
        JobSeekerProfile profile = (JobSeekerProfile) usersService.getCurrentUserProfile();
        List<JobSeekerApply> applications = jobSeekerApplyService.getCandidatesJobs(profile);
        boolean isHired = applications.stream()
                .anyMatch(app -> app.getJob().getJobPostId().equals(projectId)
                        && "HIRED".equals(app.getApplicationStatus()));

        if (!isHired) {
            redirectAttributes.addFlashAttribute("error", "Invalid withdrawal request.");
            return "redirect:/freelancer-dashboard/earnings";
        }

        // Validation: Verify Payment Exists
        List<Payment> payments = paymentRepository.findByJob(job);
        if (payments.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "The client has not released the payment for this project yet.");
            return "redirect:/freelancer-dashboard/earnings";
        }

        // Validation: Limit Check
        // Find proposed rate
        JobSeekerApply application = applications.stream()
                .filter(app -> app.getJob().getJobPostId().equals(projectId))
                .findFirst().orElse(null);
        Double rate = (application != null && application.getProposedRate() != null) ? application.getProposedRate()
                : 0.0;

        List<Withdrawal> existingWithdrawals = withdrawalRepository.findByJob(job);
        double alreadyWithdrawn = existingWithdrawals.stream().mapToDouble(Withdrawal::getAmount).sum();

        if (alreadyWithdrawn + amount > rate) {
            redirectAttributes.addFlashAttribute("error",
                    "Insufficient funds. You have already withdrawn $" + alreadyWithdrawn + " of $" + rate);
            return "redirect:/freelancer-dashboard/earnings";
        }

        try {
            // EXECUTE TRANSFER
            stripeService.transferFunds(currentUser, amount);

            // Create Withdrawal Record
            Withdrawal withdrawal = new Withdrawal(currentUser, job, amount, "COMPLETED", method);
            withdrawalRepository.save(withdrawal);

            // Mark milestones as WITHDRAWN and complete contract if fully paid
            contractRepository.findByJobApplication_Job_JobPostId(job.getJobPostId()).ifPresent(contract -> {
                // All available balance milestones are now withdrawn
                contract.getMilestones().stream()
                        .filter(m -> "AVAILABLE_BALANCE".equals(m.getStatus()) || "APPROVED".equals(m.getStatus())
                                || "RELEASED".equals(m.getStatus()))
                        .forEach(m -> {
                            m.setStatus("WITHDRAWN");
                            milestoneRepository.save(m);
                        });

                // Check if the entire contract value is withdrawn
                double totalWithdrawnNow = alreadyWithdrawn + amount;
                if (totalWithdrawnNow >= contract.getTotalAmount() - 0.01) { // 0.01 for floating point safety
                    contract.setStatus("COMPLETED");
                    contractRepository.save(contract);
                }
            });

            // Notify
            notificationService.createNotification(
                    currentUser,
                    "Withdrawal Successful",
                    "You have successfully withdrawn $" + String.format("%.2f", amount) + " via " + method,
                    "PAYMENT",
                    withdrawal.getId());

            // Send Email
            emailService.sendWithdrawalNotification(currentUser.getEmail(), amount, method);

            redirectAttributes.addFlashAttribute("success",
                    "Withdrawal processed successfully! Funds transferred to your Stripe account.");

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Transfer failed: " + e.getMessage());
        }

        return "redirect:/freelancer-dashboard/earnings";
    }

    @PostMapping("/create-stripe-account")
    public String createStripeAccount(RedirectAttributes redirectAttributes) {
        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            String accountId = currentUser.getStripeAccountId();

            // 1. Create Express Account if not exists
            if (accountId == null || accountId.isEmpty()) {
                com.stripe.model.Account account = stripeService.createExpressAccount(currentUser);
                accountId = account.getId();
                currentUser.setStripeAccountId(accountId);
                usersService.save(currentUser);
            }

            // 2. Create Account Link
            com.stripe.model.AccountLink accountLink = stripeService.createAccountLink(accountId);

            return "redirect:" + accountLink.getUrl();

        } catch (Exception e) {
            e.printStackTrace();
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("signed up for Connect")) {
                errorMsg = "Stripe Connect is not enabled on this Stripe account. Please enable Connect at https://dashboard.stripe.com/connect to allow freelancer payouts.";
            }
            redirectAttributes.addFlashAttribute("error", "Failed to connect Stripe: " + errorMsg);
            return "redirect:/freelancer-dashboard/earnings";
        }
    }

    @GetMapping("/stripe-return")
    public String stripeReturn(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("success", "Stripe account connected successfully!");
        return "redirect:/freelancer-dashboard/earnings";
    }

    @GetMapping("/stripe-refresh")
    public String stripeRefresh() {
        return "redirect:/freelancer-dashboard/create-stripe-account";
    }

    private void checkAndSendVerificationNotification(Users user) {
        try {
            List<Notification> userNotifications = notificationService.getUserNotifications(user);
            boolean alreadyNotified = userNotifications != null && userNotifications.stream()
                    .anyMatch(n -> "VERIFICATION".equalsIgnoreCase(n.getType()) || 
                            (n.getTitle() != null && n.getTitle().contains("Verify Your Identity")));
            if (!alreadyNotified) {
                notificationService.createVerificationReminderNotification(user);
            }
        } catch (Exception e) {
            System.err.println("Could not check/send verification notification: " + e.getMessage());
        }
    }
}
