package com.webapp.jobportal.controller;

import com.webapp.jobportal.services.JobPostActivityService;
import com.webapp.jobportal.services.UsersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.ResponseEntity;

import com.webapp.jobportal.services.TestimonialsService; // Import Service

@Controller
public class HomeController {

    private final UsersService usersService;
    private final JobPostActivityService jobPostActivityService;
    private final TestimonialsService testimonialsService; // Inject Service

    @Autowired
    public HomeController(UsersService usersService, JobPostActivityService jobPostActivityService,
            TestimonialsService testimonialsService) {
        this.usersService = usersService;
        this.jobPostActivityService = jobPostActivityService;
        this.testimonialsService = testimonialsService;
    }

    @GetMapping("/")
    public String home(org.springframework.ui.Model model) {
        var currentUser = usersService.getCurrentUser();
        if (currentUser != null) {
            model.addAttribute("user", currentUser);
            model.addAttribute("isVerified", usersService.isUserFullyVerified(currentUser));
        }

        // Add dynamic stats and features for landing page
        model.addAttribute("totalFreelancers", usersService.getTotalFreelancers());
        model.addAttribute("totalJobs", jobPostActivityService.getTotalJobs());
        model.addAttribute("recentJobs", jobPostActivityService.getRecentJobs());
        model.addAttribute("trustedCompanies", jobPostActivityService.getTrustedCompanies());
        model.addAttribute("testimonials", testimonialsService.getAllTestimonials()); // Add Data

        return "index";
    }

    @GetMapping("/find-talent-home")
    public String findTalentFromHome() {
        var currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/info/find-talent";
        }
        return "redirect:/find-talent/";
    }

    @GetMapping("/find-work-home")
    public String findWorkFromHome() {
        var currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/info/find-work";
        }
        return "redirect:/find-work/";
    }

    @GetMapping("/proposals")
    public String proposals() {
        var currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        // Redirect based on user type
        if (currentUser.getUserTypeId().getUserTypeId() == 2) {
            // Freelancer
            return "redirect:/freelancer-dashboard/proposals";
        } else if (currentUser.getUserTypeId().getUserTypeId() == 3) {
            // Client
            return "redirect:/client-dashboard/proposals";
        } else {
            return "redirect:/dashboard/";
        }
    }

    @GetMapping("/projects")
    public String projects() {
        var currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        // Redirect based on user type
        if (currentUser.getUserTypeId().getUserTypeId() == 2) {
            // Freelancer
            return "redirect:/freelancer-dashboard/projects";
        } else if (currentUser.getUserTypeId().getUserTypeId() == 3) {
            // Client
            return "redirect:/client-dashboard/projects";
        } else {
            return "redirect:/dashboard/";
        }
    }

    @GetMapping("/earnings")
    public String earnings() {
        var currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        // Redirect based on user type - only freelancers have earnings
        if (currentUser.getUserTypeId().getUserTypeId() == 2) {
            // Freelancer
            return "redirect:/freelancer-dashboard/earnings";
        } else {
            return "redirect:/dashboard/";
        }
    }

    @GetMapping("/health")
    @ResponseBody
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("UP");
    }
}
