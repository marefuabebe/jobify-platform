package com.webapp.jobportal.controller;

import com.webapp.jobportal.entity.*;
import com.webapp.jobportal.services.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Controller
public class JobSeekerApplyController {

    private final JobPostActivityService jobPostActivityService;
    private final UsersService usersService;
    private final JobSeekerApplyService jobSeekerApplyService;
    private final JobSeekerSaveService jobSeekerSaveService;
    private final RecruiterProfileService recruiterProfileService;
    private final JobSeekerProfileService jobSeekerProfileService;
    private final EmailService emailService;
    private final NotificationService notificationService;

    @Autowired
    public JobSeekerApplyController(JobPostActivityService jobPostActivityService, UsersService usersService,
            JobSeekerApplyService jobSeekerApplyService, JobSeekerSaveService jobSeekerSaveService,
            RecruiterProfileService recruiterProfileService, JobSeekerProfileService jobSeekerProfileService,
            EmailService emailService, NotificationService notificationService) {
        this.jobPostActivityService = jobPostActivityService;
        this.usersService = usersService;
        this.jobSeekerApplyService = jobSeekerApplyService;
        this.jobSeekerSaveService = jobSeekerSaveService;
        this.recruiterProfileService = recruiterProfileService;
        this.jobSeekerProfileService = jobSeekerProfileService;
        this.emailService = emailService;
        this.notificationService = notificationService;
    }

    @GetMapping("job-details-apply/{jobId}")
    public String display(@PathVariable("jobId") int jobId, Model model) {
        JobPostActivity jobDetails = jobPostActivityService.getOne(jobId);
        List<JobSeekerApply> jobSeekerApplyList = jobSeekerApplyService.getJobCandidates(jobDetails);
        List<JobSeekerSave> jobSeekerSaveList = jobSeekerSaveService.getJobCandidates(jobDetails);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("Client")) ||
                    authentication.getAuthorities().contains(new SimpleGrantedAuthority("Recruiter"))) {
                RecruiterProfile user = recruiterProfileService.getCurrentRecruiterProfile();
                Users currentUser = usersService.getCurrentUser();
                if (user != null) {
                    model.addAttribute("applyList", jobSeekerApplyList);
                    model.addAttribute("isVerified", Boolean.TRUE.equals(user.getIsVerified()) &&
                            currentUser != null && currentUser.isApproved());
                }
            } else {
                JobSeekerProfile user = jobSeekerProfileService.getCurrentSeekerProfile();
                Users currentUser = usersService.getCurrentUser();
                if (user != null) {
                    boolean exists = false;
                    boolean saved = false;
                    for (JobSeekerApply jobSeekerApply : jobSeekerApplyList) {
                        if (jobSeekerApply.getUserId().getUserAccountId() == user.getUserAccountId()) {
                            exists = true;
                            break;
                        }
                    }
                    for (JobSeekerSave jobSeekerSave : jobSeekerSaveList) {
                        if (jobSeekerSave.getUserId().getUserAccountId() == user.getUserAccountId()) {
                            saved = true;
                            break;
                        }
                    }
                    model.addAttribute("alreadyApplied", exists);
                    model.addAttribute("alreadySaved", saved);
                    model.addAttribute("isFreelancerVerified", Boolean.TRUE.equals(user.getIsVerified()) &&
                            currentUser != null && currentUser.isApproved());
                }
            }
        }

        JobSeekerApply jobSeekerApply = new JobSeekerApply();
        model.addAttribute("applyJob", jobSeekerApply);

        model.addAttribute("jobDetails", jobDetails);
        model.addAttribute("user", usersService.getCurrentUserProfile());
        return "job-details";
    }

    @GetMapping("job-details/apply/{jobId}")
    public String displayApplyGet(@PathVariable("jobId") int jobId) {
        return "redirect:/job-details-apply/" + jobId;
    }

    @PostMapping("job-details/apply/{jobId}")
    public String apply(@PathVariable("jobId") int jobId, JobSeekerApply jobSeekerApply,
            RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            String currentUsername = authentication.getName();
            Users user = usersService.findByEmail(currentUsername);
            Optional<JobSeekerProfile> seekerProfile = jobSeekerProfileService.getOne(user.getUserId());
            JobPostActivity jobPostActivity = jobPostActivityService.getOne(jobId);

            // Check if freelancer is verified - must be approved AND have verification
            // document
            if (seekerProfile.isPresent() && user.getUserTypeId().getUserTypeId() == 2) {
                if (!user.isApproved()) {
                    redirectAttributes.addFlashAttribute("error",
                            "Your account must be approved by an admin before applying for jobs.");
                    return "redirect:/job-details-apply/" + jobId;
                }

                if (!Boolean.TRUE.equals(seekerProfile.get().getIsVerified()) ||
                        seekerProfile.get().getVerificationDocument() == null) {
                    redirectAttributes.addFlashAttribute("error",
                            "Identity verification required: Please upload your government-issued ID and wait for admin verification before applying for jobs.");
                    return "redirect:/job-details-apply/" + jobId;
                }
            }

            if (seekerProfile.isPresent() && jobPostActivity != null) {
                // Do NOT create a new object; usage the one with form data
                jobSeekerApply.setUserId(seekerProfile.get());
                jobSeekerApply.setJob(jobPostActivity);
                jobSeekerApply.setApplyDate(new Date());
                jobSeekerApply.setApplicationStatus("PENDING");
            } else {
                throw new RuntimeException("User not found");
            }

            // Check for duplicate application
            if (jobSeekerApplyService.hasApplied(seekerProfile.get(), jobPostActivity)) {
                redirectAttributes.addFlashAttribute("error", "You have already applied for this job.");
                return "redirect:/job-details-apply/" + jobId;
            }

            jobSeekerApplyService.addNew(jobSeekerApply);

            // Send email notifications
            if (jobPostActivity.getPostedById() != null && jobPostActivity.getPostedById().getEmail() != null) {
                String freelancerName = (seekerProfile.get().getFirstName() != null ? seekerProfile.get().getFirstName()
                        : "") +
                        " " + (seekerProfile.get().getLastName() != null ? seekerProfile.get().getLastName() : "");
                emailService.sendNewApplicationNotification(
                        jobPostActivity.getPostedById().getEmail(),
                        freelancerName.trim().isEmpty() ? "A Freelancer" : freelancerName.trim(),
                        jobPostActivity.getJobTitle());

                // Create in-app notification for client
                notificationService.createNotification(
                        jobPostActivity.getPostedById(),
                        "New Application Received",
                        "You have received a new application for: " + jobPostActivity.getJobTitle(),
                        "APPLICATION",
                        jobSeekerApply.getId());
            }
            if (seekerProfile.get().getUserId() != null && seekerProfile.get().getUserId().getEmail() != null) {
                String clientName = jobPostActivity.getPostedById() != null &&
                        jobPostActivity.getPostedById().getEmail() != null ? "The Client" : "The Client";
                emailService.sendApplicationNotification(
                        seekerProfile.get().getUserId().getEmail(),
                        jobPostActivity.getJobTitle(),
                        clientName);
            }

            redirectAttributes.addFlashAttribute("success", "Application submitted successfully!");
            return "redirect:/job-details-apply/" + jobId;
        }

        return "redirect:/freelancer-dashboard/";
    }

    @PostMapping("/applications/{id}/status")
    public String updateApplicationStatus(@PathVariable("id") int id,
            @RequestParam("status") String status,
            RedirectAttributes redirectAttributes) {
        try {
            Users currentUser = usersService.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            // Check if user is trying to hire and verify they are a verified client
            if ("HIRED".equals(status) && currentUser.getUserTypeId().getUserTypeId() == 1) {
                if (!currentUser.isApproved()) {
                    redirectAttributes.addFlashAttribute("error",
                            "Your account must be approved by an admin before hiring freelancers.");
                    return "redirect:/job-details-apply/" + jobSeekerApplyService.getById(id).getJob().getJobPostId();
                }

                RecruiterProfile clientProfile = recruiterProfileService.getCurrentRecruiterProfile();
                if (clientProfile == null || !Boolean.TRUE.equals(clientProfile.getIsVerified())) {
                    redirectAttributes.addFlashAttribute("error",
                            "Identity verification required: Please upload your government-issued ID and wait for admin verification before hiring freelancers.");
                    return "redirect:/job-details-apply/" + jobSeekerApplyService.getById(id).getJob().getJobPostId();
                }
            }

            JobSeekerApply application = jobSeekerApplyService.updateApplicationStatus(id, status);

            // Send email notification to freelancer
            if (application.getUserId() != null && application.getUserId().getUserId() != null &&
                    application.getJob() != null) {
                String freelancerEmail = application.getUserId().getUserId().getEmail();
                if (freelancerEmail != null) {
                    emailService.sendApplicationStatusUpdate(
                            freelancerEmail,
                            application.getJob().getJobTitle(),
                            status);

                    // Create in-app notification for freelancer
                    notificationService.createNotification(
                            application.getUserId().getUserId(),
                            "Application Status Updated",
                            "Your application for '" + application.getJob().getJobTitle() + "' has been updated to: "
                                    + status,
                            "APPLICATION",
                            application.getId());
                }
            }

            redirectAttributes.addFlashAttribute("success", "Application status updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating status: " + e.getMessage());
        }
        return "redirect:/job-details-apply/" + jobSeekerApplyService.getById(id).getJob().getJobPostId();
    }

    @GetMapping("/dashboard/edit-proposal/{id}")
    public String editProposal(@PathVariable("id") int id, Model model) {
        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        JobSeekerApply application = jobSeekerApplyService.getById(id);
        if (application == null) {
            return "redirect:/freelancer-dashboard/proposals";
        }

        // Verify ownership and status
        Object profileObj = usersService.getCurrentUserProfile();
        if (!(profileObj instanceof JobSeekerProfile)) {
            return "redirect:/freelancer-dashboard/proposals";
        }
        JobSeekerProfile profile = (JobSeekerProfile) profileObj;

        if (application.getUserId().getUserAccountId() != profile.getUserAccountId()) {
            return "redirect:/freelancer-dashboard/proposals";
        }

        if (!"PENDING".equals(application.getApplicationStatus())) {
            return "redirect:/freelancer-dashboard/proposals";
        }

        model.addAttribute("applyJob", application);
        model.addAttribute("user", profile);
        return "edit-proposal";
    }

    @PostMapping("/dashboard/edit-proposal/{id}")
    public String updateProposal(@PathVariable("id") int id, JobSeekerApply updatedApplication,
            RedirectAttributes redirectAttributes) {
        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        JobSeekerApply existingApplication = jobSeekerApplyService.getById(id);
        if (existingApplication == null) {
            return "redirect:/freelancer-dashboard/proposals";
        }

        // Verify ownership and status
        Object profileObj = usersService.getCurrentUserProfile();
        if (!(profileObj instanceof JobSeekerProfile)) {
            return "redirect:/freelancer-dashboard/proposals";
        }
        JobSeekerProfile profile = (JobSeekerProfile) profileObj;

        if (existingApplication.getUserId().getUserAccountId() != profile.getUserAccountId()) {
            return "redirect:/freelancer-dashboard/proposals";
        }

        if (!"PENDING".equals(existingApplication.getApplicationStatus())) {
            redirectAttributes.addFlashAttribute("error", "Cannot edit proposal that is not pending.");
            return "redirect:/freelancer-dashboard/proposals";
        }

        existingApplication.setCoverLetter(updatedApplication.getCoverLetter());
        existingApplication.setProposedRate(updatedApplication.getProposedRate());
        existingApplication.setExpectedCompletionTime(updatedApplication.getExpectedCompletionTime());
        existingApplication.setUnderstanding(updatedApplication.getUnderstanding());
        existingApplication.setRelevantSkills(updatedApplication.getRelevantSkills());
        existingApplication.setApplyDate(new Date()); // Update apply date to show it was modified

        jobSeekerApplyService.addNew(existingApplication); // Save changes

        redirectAttributes.addFlashAttribute("success", "Proposal updated successfully!");
        return "redirect:/freelancer-dashboard/proposals";
    }
}
