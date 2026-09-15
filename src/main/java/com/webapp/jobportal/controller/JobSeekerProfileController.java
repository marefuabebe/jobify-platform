package com.webapp.jobportal.controller;

import com.webapp.jobportal.entity.JobSeekerProfile;
import com.webapp.jobportal.entity.Skills;
import com.webapp.jobportal.entity.Users;
import com.webapp.jobportal.repository.UsersRepository;
import com.webapp.jobportal.services.JobSeekerProfileService;
import com.webapp.jobportal.services.RatingService;
import com.webapp.jobportal.services.EmailService;
import com.webapp.jobportal.services.NotificationService;
import com.webapp.jobportal.util.FileDownloadUtil;
import com.webapp.jobportal.util.FileUploadUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.validation.Valid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Controller
@RequestMapping("/job-seeker-profile")
public class JobSeekerProfileController {

    private JobSeekerProfileService jobSeekerProfileService;
    private UsersRepository usersRepository;
    private RatingService ratingService;
    private EmailService emailService;
    private NotificationService notificationService;
    private com.webapp.jobportal.services.RecruiterProfileService recruiterProfileService;
    private com.webapp.jobportal.services.JobPostActivityService jobPostActivityService;
    private com.webapp.jobportal.services.CloudinaryService cloudinaryService;

    @Autowired
    public JobSeekerProfileController(JobSeekerProfileService jobSeekerProfileService,
            UsersRepository usersRepository,
            RatingService ratingService,
            EmailService emailService,
            NotificationService notificationService,
            com.webapp.jobportal.services.JobPostActivityService jobPostActivityService,
            com.webapp.jobportal.services.RecruiterProfileService recruiterProfileService,
            com.webapp.jobportal.services.CloudinaryService cloudinaryService) {
        this.jobSeekerProfileService = jobSeekerProfileService;
        this.usersRepository = usersRepository;
        this.ratingService = ratingService;
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.jobPostActivityService = jobPostActivityService;
        this.recruiterProfileService = recruiterProfileService;
        this.cloudinaryService = cloudinaryService;
    }

    @GetMapping("/")
    public String jobSeekerProfile(Model model) {
        JobSeekerProfile jobSeekerProfile = new JobSeekerProfile();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<Skills> skills = new ArrayList<>();

        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            try {
                Users user = usersRepository.findByEmail(authentication.getName())
                        .orElseThrow(() -> new UsernameNotFoundException("User not found."));
                Optional<JobSeekerProfile> seekerProfile = jobSeekerProfileService.getOne(user.getUserId());
                if (seekerProfile.isPresent()) {
                    jobSeekerProfile = seekerProfile.get();
                    if (jobSeekerProfile.getIsVerified() == null) {
                        jobSeekerProfile.setIsVerified(false);
                    }
                    if (user.isApproved()) {
                        jobSeekerProfile.setIsVerified(jobSeekerProfile.getIsVerified());
                    } else {
                        jobSeekerProfile.setIsVerified(false);
                    }
                    model.addAttribute("user", jobSeekerProfile);
                    // Ensure skills list is initialized
                    if (jobSeekerProfile.getSkills() != null && !jobSeekerProfile.getSkills().isEmpty()) {
                        skills = jobSeekerProfile.getSkills();
                    } else {
                        skills.add(new Skills());
                        jobSeekerProfile.setSkills(skills);
                    }
                } else {
                    // Initialize a new profile for user without existing profile
                    jobSeekerProfile.setUserId(user);
                    model.addAttribute("user", jobSeekerProfile);
                    jobSeekerProfile.setUserAccountId(user.getUserId());
                    skills.add(new Skills());
                    jobSeekerProfile.setSkills(skills);
                }
            } catch (Exception e) {
                // If there's an error retrieving the profile, initialize with default values
                System.err.println("Error retrieving job seeker profile: " + e.getMessage());
                e.printStackTrace();
                skills.add(new Skills());
                jobSeekerProfile.setSkills(skills);
            }
        } else {
            // For unauthenticated users, redirect to login
            return "redirect:/login";
        }

        // Ensure all profile properties have default values to prevent template errors
        if (jobSeekerProfile.getFirstName() == null)
            jobSeekerProfile.setFirstName("");
        if (jobSeekerProfile.getLastName() == null)
            jobSeekerProfile.setLastName("");
        if (jobSeekerProfile.getCountry() == null)
            jobSeekerProfile.setCountry("");
        if (jobSeekerProfile.getCity() == null)
            jobSeekerProfile.setCity("");
        if (jobSeekerProfile.getState() == null)
            jobSeekerProfile.setState("");
        if (jobSeekerProfile.getWorkAuthorization() == null)
            jobSeekerProfile.setWorkAuthorization("");
        if (jobSeekerProfile.getEmploymentType() == null)
            jobSeekerProfile.setEmploymentType("");
        if (jobSeekerProfile.getProfilePhoto() == null)
            jobSeekerProfile.setProfilePhoto("");
        if (jobSeekerProfile.getVerificationDocument() == null)
            jobSeekerProfile.setVerificationDocument("");
        if (jobSeekerProfile.getResume() == null)
            jobSeekerProfile.setResume("");
        if (jobSeekerProfile.getDocumentStatus() == null)
            jobSeekerProfile.setDocumentStatus("");
        if (jobSeekerProfile.getIsVerified() == null)
            jobSeekerProfile.setIsVerified(false);

        model.addAttribute("skills", skills);
        model.addAttribute("profile", jobSeekerProfile);

        // Add rating attributes to prevent template errors
        model.addAttribute("averageRating", 0.0);
        model.addAttribute("ratingCount", 0L);

        return "job-seeker-profile";
    }

    @PostMapping("/addNew")
    public String addNew(@Valid JobSeekerProfile jobSeekerProfile,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "pdf", required = false) MultipartFile pdf,
            @RequestParam(value = "verificationDoc", required = false) MultipartFile verificationDoc,
            @RequestParam(value = "educationDocFile", required = false) MultipartFile educationDoc,
            @RequestParam(value = "certificationsDocFile", required = false) MultipartFile certificationsDoc,
            @RequestParam(value = "removeProfilePhoto", defaultValue = "false") boolean removeProfilePhoto,
            @RequestParam(value = "removeVerificationDoc", defaultValue = "false") boolean removeVerificationDoc,
            @RequestParam(value = "removeResume", defaultValue = "false") boolean removeResume,
            @RequestParam(value = "removeEducationDoc", defaultValue = "false") boolean removeEducationDoc,
            @RequestParam(value = "removeCertificationsDoc", defaultValue = "false") boolean removeCertificationsDoc,
            @RequestParam(value = "redirect", required = false) String redirectTarget,
            Model model, RedirectAttributes redirectAttributes) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof AnonymousAuthenticationToken) {
            return "redirect:/login";
        }

        Users user = usersRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));

        // Get existing profile to preserve unchanged data
        Optional<JobSeekerProfile> existingProfileOpt = jobSeekerProfileService.getOne(user.getUserId());
        JobSeekerProfile existingProfile;

        if (existingProfileOpt.isPresent()) {
            existingProfile = existingProfileOpt.get();
            // Update existing profile with new data
            existingProfile.setFirstName(jobSeekerProfile.getFirstName());
            existingProfile.setLastName(jobSeekerProfile.getLastName());
            existingProfile.setCountry(jobSeekerProfile.getCountry());
            existingProfile.setCity(jobSeekerProfile.getCity());
            existingProfile.setState(jobSeekerProfile.getState());
            existingProfile.setWorkAuthorization(jobSeekerProfile.getWorkAuthorization());
            existingProfile.setEmploymentType(jobSeekerProfile.getEmploymentType());
            existingProfile.setExperienceLevel(jobSeekerProfile.getExperienceLevel());
            existingProfile.setTagline(jobSeekerProfile.getTagline());
            existingProfile.setBio(jobSeekerProfile.getBio());
            existingProfile.setTitle(jobSeekerProfile.getTitle());
            existingProfile.setHourlyRate(jobSeekerProfile.getHourlyRate());
            existingProfile.setLanguages(jobSeekerProfile.getLanguages());
            existingProfile.setEducation(jobSeekerProfile.getEducation());
            existingProfile.setCertifications(jobSeekerProfile.getCertifications());
            existingProfile.setAvailabilityStatus(jobSeekerProfile.getAvailabilityStatus());
            existingProfile.setHoursPerWeek(jobSeekerProfile.getHoursPerWeek());

            // Note: Docs are handled via file upload logic below
            jobSeekerProfile = existingProfile;
        } else {
            // Set user relationship for new profile
            jobSeekerProfile.setUserId(user);
            jobSeekerProfile.setUserAccountId(user.getUserId());
            if (jobSeekerProfile.getSkills() != null) {
                for (Skills skill : jobSeekerProfile.getSkills()) {
                    skill.setJobSeekerProfile(jobSeekerProfile);
                }
            }
        }

        String uploadDir = "photos/candidate/" + jobSeekerProfile.getUserAccountId();
        String cloudinaryFolder = "jobportal/candidate/" + jobSeekerProfile.getUserAccountId();

        // Handle image upload
        if (image != null && !image.isEmpty()) {
            try {
                String imageUrl = cloudinaryService.uploadImage(image, cloudinaryFolder);
                jobSeekerProfile.setProfilePhoto(imageUrl);
                try {
                    String imageName = StringUtils.cleanPath(Objects.requireNonNull(image.getOriginalFilename()));
                    FileUploadUtil.saveFile(uploadDir, imageName, image);
                } catch (Exception ignored) {}
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", true);
                return "redirect:/job-seeker-profile/";
            }
        } else if (removeProfilePhoto) {
            // Remove existing image if requested
            if (jobSeekerProfile.getProfilePhoto() != null && !jobSeekerProfile.getProfilePhoto().isEmpty()) {
                jobSeekerProfile.setProfilePhoto(null);
            }
        }

        // Handle verification document upload
        if (verificationDoc != null && !verificationDoc.isEmpty()) {
            System.out.println("Controller: Received verificationDoc: " + verificationDoc.getOriginalFilename()
                    + ", size: " + verificationDoc.getSize());
            try {
                String docUrl = cloudinaryService.uploadDocument(verificationDoc, cloudinaryFolder);
                jobSeekerProfile.setVerificationDocument(docUrl);
                jobSeekerProfile.setDocumentStatus("UNDER_REVIEW");
                try {
                    String verificationDocName = StringUtils
                            .cleanPath(Objects.requireNonNull(verificationDoc.getOriginalFilename()));
                    FileUploadUtil.saveFile(uploadDir, verificationDocName, verificationDoc);
                } catch (Exception ignored) {}
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", true);
                return "redirect:/job-seeker-profile/";
            }
        } else if (removeVerificationDoc) {
            // Remove existing verification doc if requested
            if (jobSeekerProfile.getVerificationDocument() != null
                    && !jobSeekerProfile.getVerificationDocument().isEmpty()) {
                jobSeekerProfile.setVerificationDocument(null);
            }
        }

        // Handle resume upload
        if (pdf != null && !pdf.isEmpty()) {
            try {
                String resumeUrl = cloudinaryService.uploadDocument(pdf, cloudinaryFolder);
                jobSeekerProfile.setResume(resumeUrl);
                try {
                    String resumeName = StringUtils.cleanPath(Objects.requireNonNull(pdf.getOriginalFilename()));
                    FileUploadUtil.saveFile(uploadDir, resumeName, pdf);
                } catch (Exception ignored) {}
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", true);
                return "redirect:/job-seeker-profile/";
            }
        } else if (removeResume) {
            // Remove existing resume if requested
            if (jobSeekerProfile.getResume() != null && !jobSeekerProfile.getResume().isEmpty()) {
                jobSeekerProfile.setResume(null);
            }
        }

        // Handle education document upload
        if (educationDoc != null && !educationDoc.isEmpty()) {
            try {
                String eduUrl = cloudinaryService.uploadDocument(educationDoc, cloudinaryFolder);
                jobSeekerProfile.setEducationDoc(eduUrl);
                try {
                    String educationDocName = StringUtils.cleanPath(Objects.requireNonNull(educationDoc.getOriginalFilename()));
                    FileUploadUtil.saveFile(uploadDir, educationDocName, educationDoc);
                } catch (Exception ignored) {}
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", true);
                return "redirect:/job-seeker-profile/";
            }
        } else if (removeEducationDoc) {
            if (jobSeekerProfile.getEducationDoc() != null && !jobSeekerProfile.getEducationDoc().isEmpty()) {
                jobSeekerProfile.setEducationDoc(null);
            }
        }

        // Handle certifications document upload
        if (certificationsDoc != null && !certificationsDoc.isEmpty()) {
            try {
                String certUrl = cloudinaryService.uploadDocument(certificationsDoc, cloudinaryFolder);
                jobSeekerProfile.setCertificationDoc(certUrl);
                try {
                    String certificationsDocName = StringUtils
                            .cleanPath(Objects.requireNonNull(certificationsDoc.getOriginalFilename()));
                    FileUploadUtil.saveFile(uploadDir, certificationsDocName, certificationsDoc);
                } catch (Exception ignored) {}
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", true);
                return "redirect:/job-seeker-profile/";
            }
        } else if (removeCertificationsDoc) {
            if (jobSeekerProfile.getCertificationDoc() != null && !jobSeekerProfile.getCertificationDoc().isEmpty()) {
                jobSeekerProfile.setCertificationDoc(null);
            }
        }

        try {
            jobSeekerProfileService.addNew(jobSeekerProfile);
            redirectAttributes.addFlashAttribute("success", "Profile updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", true);
            return "redirect:/job-seeker-profile/";
        } catch (Throwable e) {
            e.printStackTrace();
            System.err.println("CRITICAL ERROR IN addNew: " + e.getMessage());
            redirectAttributes.addFlashAttribute("error", true);
            return "redirect:/job-seeker-profile/";
        }

        // Send admin notification if verification document was uploaded
        if (verificationDoc != null && !verificationDoc.isEmpty()) {
            redirectAttributes.addFlashAttribute("verificationSuccess",
                    "Verification documents submitted successfully!");
            try {
                emailService.sendAdminVerificationNotification(jobSeekerProfile.getUserId().getEmail(), "Freelancer");
                notificationService.createAdminNotification(jobSeekerProfile.getUserId().getEmail(), "Freelancer");
            } catch (Exception e) {
                System.err.println("Failed to send admin notification: " + e.getMessage());
            }
        }

        // Determine the appropriate redirect URL based on user type or redirect target
        if ("skills".equals(redirectTarget)) {
            return "redirect:/job-seeker-profile/skills";
        }

        if (user.getUserTypeId().getUserTypeId() == 2) { // Freelancer user type ID
            return "redirect:/freelancer-dashboard/";
        } else {
            return "redirect:/dashboard/";
        }
    }

    @GetMapping("/view/{id}")
    public String candidateProfile(@PathVariable("id") int id, Model model) {
        Optional<JobSeekerProfile> seekerProfile = jobSeekerProfileService.getOneWithSkills(id);
        if (seekerProfile.isPresent()) {
            JobSeekerProfile profile = seekerProfile.get();
            model.addAttribute("profile", profile);

            // Handle skills safely
            List<Skills> skills = profile.getSkills();
            if (skills == null || skills.isEmpty()) {
                skills = new ArrayList<>();
            }
            model.addAttribute("skills", skills);

            // Add rating information
            Double avgRating = ratingService.getAverageRating(profile);
            Long ratingCount = ratingService.getRatingCount(profile);
            model.addAttribute("averageRating", avgRating != null ? avgRating : 0.0);
            model.addAttribute("ratingCount", ratingCount != null ? ratingCount : 0L);

            // Fetch Current User for Navbar and Actions
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (!(authentication instanceof AnonymousAuthenticationToken)) {
                Optional<Users> userOptional = usersRepository.findByEmail(authentication.getName());
                if (userOptional.isPresent()) {
                    Users currentUser = userOptional.get();

                    // Pass the correct profile object as "user" for the header
                    if (currentUser.getUserTypeId().getUserTypeId() == 1) {
                        // Recruiter
                        Optional<com.webapp.jobportal.entity.RecruiterProfile> recruiterProfileOpt = recruiterProfileService
                                .getOne(currentUser.getUserId());
                        if (recruiterProfileOpt.isPresent()) {
                            model.addAttribute("user", recruiterProfileOpt.get());

                            // Fetch active jobs for invite
                            List<com.webapp.jobportal.entity.JobPostActivity> allJobs = jobPostActivityService.getAll();
                            List<com.webapp.jobportal.entity.JobPostActivity> myJobs = allJobs.stream()
                                    .filter(job -> job.getPostedById() != null
                                            && job.getPostedById().getUserId() == currentUser.getUserId())
                                    .collect(java.util.stream.Collectors.toList());
                            model.addAttribute("recruiterJobs", myJobs);
                        } else {
                            // Fallback if profile missing
                            model.addAttribute("user", currentUser);
                        }

                    } else if (currentUser.getUserTypeId().getUserTypeId() == 2) {
                        // Freelancer (Viewer is also a freelancer viewing another freelancer)
                        Optional<JobSeekerProfile> viewerProfileOpt = jobSeekerProfileService
                                .getOne(currentUser.getUserId());
                        if (viewerProfileOpt.isPresent()) {
                            model.addAttribute("user", viewerProfileOpt.get());
                        } else {
                            model.addAttribute("user", currentUser);
                        }
                    } else {
                        // Admin or other
                        model.addAttribute("user", currentUser);
                    }
                }
            }
        } else {
            // Handle case where profile is not found
            return "redirect:/error?message=Profile not found";
        }
        return "job-seeker-profile-view";
    }

    @GetMapping("/downloadResume")
    public ResponseEntity<?> downloadResume(@RequestParam(value = "fileName") String fileName,
            @RequestParam(value = "userID") String userId) {

        if (fileName != null && (fileName.startsWith("http://") || fileName.startsWith("https://"))) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(java.net.URI.create(fileName))
                    .build();
        }

        FileDownloadUtil downloadUtil = new FileDownloadUtil();
        Resource resource = null;

        try {
            resource = downloadUtil.getFileAsResourse("photos/candidate/" + userId, fileName);
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }

        if (resource == null) {
            return new ResponseEntity<>("File not found", HttpStatus.NOT_FOUND);
        }

        String contentType = "application/octet-stream";
        String headerValue = "attachment; filename=\"" + resource.getFilename() + "\"";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
                .body(resource);

    }

    @GetMapping("/skills")
    public String showSkillsPage(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof AnonymousAuthenticationToken) {
            return "redirect:/login";
        }

        Users user = usersRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));

        Optional<JobSeekerProfile> seekerProfile = jobSeekerProfileService.getOne(user.getUserId());

        if (seekerProfile.isPresent()) {
            JobSeekerProfile profile = seekerProfile.get();
            model.addAttribute("profile", profile);
            model.addAttribute("skills", profile.getSkills());
            model.addAttribute("newSkill", new Skills());
            model.addAttribute("user", profile); // For header
        } else {
            return "redirect:/job-seeker-profile/";
        }

        return "job-seeker-skills";
    }

    @PostMapping("/skills/add")
    public String addSkill(Skills skill, RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof AnonymousAuthenticationToken) {
            return "redirect:/login";
        }

        Users user = usersRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));

        Optional<JobSeekerProfile> seekerProfileOpt = jobSeekerProfileService.getOne(user.getUserId());

        if (seekerProfileOpt.isPresent()) {
            JobSeekerProfile profile = seekerProfileOpt.get();
            skill.setJobSeekerProfile(profile);
            profile.getSkills().add(skill);
            jobSeekerProfileService.addNew(profile);
            redirectAttributes.addFlashAttribute("success", "Skill added successfully!");
        }

        return "redirect:/job-seeker-profile/skills";
    }

    @GetMapping("/skills/delete/{id}")
    public String deleteSkill(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof AnonymousAuthenticationToken) {
            return "redirect:/login";
        }

        Users user = usersRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));

        Optional<JobSeekerProfile> seekerProfileOpt = jobSeekerProfileService.getOne(user.getUserId());

        if (seekerProfileOpt.isPresent()) {
            JobSeekerProfile profile = seekerProfileOpt.get();
            // Find and remove the skill to ensure ownership
            profile.getSkills().removeIf(s -> s.getId() != null && s.getId() == id);
            jobSeekerProfileService.addNew(profile);
            redirectAttributes.addFlashAttribute("success", "Skill removed successfully!");
        }

        return "redirect:/job-seeker-profile/skills";
    }
}
