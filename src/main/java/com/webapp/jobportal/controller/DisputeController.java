package com.webapp.jobportal.controller;

import com.webapp.jobportal.entity.*;
import com.webapp.jobportal.services.*;
import com.webapp.jobportal.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Date;
import java.util.List;

@Controller
@RequestMapping("/dispute")
public class DisputeController {

    private final DisputeService disputeService;
    private final JobPostActivityService jobPostActivityService;
    private final UsersService usersService;
    private final PaymentRepository paymentRepository;
    private final JobSeekerApplyService jobSeekerApplyService; // To update application status
    private final NotificationService notificationService;
    private final EmailService emailService;

    @Autowired
    public DisputeController(DisputeService disputeService, JobPostActivityService jobPostActivityService,
            UsersService usersService, PaymentRepository paymentRepository,
            JobSeekerApplyService jobSeekerApplyService,
            NotificationService notificationService,
            EmailService emailService) {
        this.disputeService = disputeService;
        this.jobPostActivityService = jobPostActivityService;
        this.usersService = usersService;
        this.paymentRepository = paymentRepository;
        this.jobSeekerApplyService = jobSeekerApplyService;
        this.notificationService = notificationService;
        this.emailService = emailService;
    }

    // 1. Submit Work (Freelancer)
    @PostMapping("/submit-work")
    public String submitWork(@RequestParam("jobId") Integer jobId, RedirectAttributes redirectAttributes) {
        Users currentUser = usersService.getCurrentUser();
        // (Add validation: Is Freelancer? Is Hired?)

        JobPostActivity job = jobPostActivityService.getOne(jobId);
        List<JobSeekerApply> applications = jobSeekerApplyService.getJobCandidates(job);
        // Find application for this freelancer
        JobSeekerApply app = applications.stream()
                .filter(a -> a.getUserId().getUserAccountId().equals(currentUser.getUserId()))
                .findFirst().orElse(null);

        if (app != null) {
            jobSeekerApplyService.updateApplicationStatus(app.getId(), "WORK_SUBMITTED");
        }

        redirectAttributes.addFlashAttribute("success", "Work submitted for review!");
        return "redirect:/freelancer-dashboard/projects";
    }

    // 2. Approve Work (Client)
    @PostMapping("/approve-work")
    public String approveWork(@RequestParam("jobId") Integer jobId, RedirectAttributes redirectAttributes) {
        JobPostActivity job = jobPostActivityService.getOne(jobId);

        // 1. Release Escrow
        List<Payment> payments = paymentRepository.findByJob(job);
        for (Payment p : payments) {
            if ("ESCROW_HELD".equals(p.getStatus())) {
                p.setStatus("COMPLETED");
                paymentRepository.save(p);
            }
        }

        // 2. Mark App as Completed
        List<JobSeekerApply> applications = jobSeekerApplyService.getJobCandidates(job);
        JobSeekerApply app = applications.stream()
                .filter(a -> "WORK_SUBMITTED".equals(a.getApplicationStatus())
                        || "HIRED".equals(a.getApplicationStatus()))
                .findFirst().orElse(null);

        if (app != null) {
            jobSeekerApplyService.updateApplicationStatus(app.getId(), "COMPLETED");
        }

        redirectAttributes.addFlashAttribute("success", "Work approved! Payment released to freelancer.");
        return "redirect:/dashboard/";
    }

    // 3. Create Dispute
    @PostMapping("/create")
    public String createDispute(@RequestParam("jobId") Integer jobId,
            @RequestParam("description") String description,
            @RequestParam("type") String type,
            @RequestParam("againstId") Integer againstId,
            RedirectAttributes redirectAttributes) {
        Users currentUser = usersService.getCurrentUser();
        JobPostActivity job = jobPostActivityService.getOne(jobId);
        Users againstUser = usersService.getUserById(againstId);

        Dispute dispute = new Dispute();
        dispute.setReporter(currentUser);
        dispute.setAgainst(againstUser);
        dispute.setJob(job);
        dispute.setDescription(description);
        dispute.setType(type);
        dispute.setStatus("PENDING");
        dispute.setPostedDate(new Date());

        Dispute savedDispute = disputeService.createDispute(dispute);

        // Notify Admin via In-App Notification and Email
        try {
            notificationService.createAdminDisputeNotification(savedDispute);
            String clientEmail = (currentUser.getUserTypeId() != null && currentUser.getUserTypeId().getUserTypeId() == 1)
                    ? currentUser.getEmail()
                    : againstUser.getEmail();
            String freelancerEmail = (currentUser.getUserTypeId() != null && currentUser.getUserTypeId().getUserTypeId() == 2)
                    ? currentUser.getEmail()
                    : againstUser.getEmail();
            emailService.sendDisputeNotification("marefu933@gmail.com", job.getJobTitle(), clientEmail, freelancerEmail, description);
        } catch (Exception e) {
            System.err.println("Failed to send dispute notifications: " + e.getMessage());
        }

        // Freeze Funds
        List<Payment> payments = paymentRepository.findByJob(job);
        for (Payment p : payments) {
            if ("ESCROW_HELD".equals(p.getStatus())) {
                p.setStatus("DISPUTED");
                paymentRepository.save(p);
            }
        }

        redirectAttributes.addFlashAttribute("success", "Dispute submitted. Funds are frozen pending review.");
        return "redirect:/dashboard/";
    }
}
