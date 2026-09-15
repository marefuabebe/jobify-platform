package com.webapp.jobportal.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.List;

@Controller
@RequestMapping("/info")
public class PublicInfoController {

    private final com.webapp.jobportal.services.JobPostActivityService jobPostActivityService;
    private final com.webapp.jobportal.services.JobSeekerProfileService jobSeekerProfileService;
    private final com.webapp.jobportal.services.RatingService ratingService;
    private final com.webapp.jobportal.repository.SkillsRepository skillsRepository;
    private final com.webapp.jobportal.services.UsersService usersService;

    @org.springframework.beans.factory.annotation.Autowired
    public PublicInfoController(com.webapp.jobportal.services.JobPostActivityService jobPostActivityService,
            com.webapp.jobportal.services.JobSeekerProfileService jobSeekerProfileService,
            com.webapp.jobportal.services.RatingService ratingService,
            com.webapp.jobportal.repository.SkillsRepository skillsRepository,
            com.webapp.jobportal.services.UsersService usersService) {
        this.jobPostActivityService = jobPostActivityService;
        this.jobSeekerProfileService = jobSeekerProfileService;
        this.ratingService = ratingService;
        this.skillsRepository = skillsRepository;
        this.usersService = usersService;
    }

    @org.springframework.web.bind.annotation.ModelAttribute
    public void addAttributes(Model model) {
        var currentUser = usersService.getCurrentUser();
        if (currentUser != null) {
            model.addAttribute("user", currentUser);
            model.addAttribute("isVerified", usersService.isUserFullyVerified(currentUser));
        }
    }

    @GetMapping("/find-work")
    public String findWorkInfo(Model model) {
        model.addAttribute("recentJobs", jobPostActivityService.getRecentJobs());
        return "find-work-info";
    }

    @GetMapping("/find-talent")
    public String findTalentInfo(Model model) {
        List<com.webapp.jobportal.entity.JobSeekerProfile> profiles = jobSeekerProfileService.getRecentFreelancers();
        List<com.webapp.jobportal.dto.FreelancerCardDTO> freelancers = new java.util.ArrayList<>();

        for (com.webapp.jobportal.entity.JobSeekerProfile profile : profiles) {
            if (Boolean.TRUE.equals(profile.getIsVerified())) {
                Double rating = ratingService.getAverageRating(profile);
                Long count = ratingService.getRatingCount(profile);
                freelancers.add(new com.webapp.jobportal.dto.FreelancerCardDTO(profile, rating, count));
            }
        }

        model.addAttribute("recentFreelancers", freelancers);

        // Add Real Stats
        model.addAttribute("verifiedFreelancersCount", jobSeekerProfileService.getTotalVerifiedFreelancers());
        model.addAttribute("skillsCount", skillsRepository.findDistinctSkillNames().size());
        model.addAttribute("successRate", ratingService.getGlobalSuccessRate());

        return "find-talent-info";
    }

    @GetMapping("/terms")
    public String terms() {
        return "terms";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    @GetMapping("/blog")
    public String blog() {
        return "blog";
    }

    @GetMapping("/blog/article")
    public String blogArticle(Model model) {
        model.addAttribute("activePage", "blog");
        return "blog-details";
    }

    @GetMapping("/news")
    public String news(Model model) {
        model.addAttribute("activePage", "news");
        return "news";
    }

    @GetMapping("/news/article")
    public String newsArticle(Model model) {
        model.addAttribute("activePage", "news");
        return "news-details";
    }

    @GetMapping("/contact")
    public String contact() {
        return "contact";
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("totalUsers", usersService.getTotalUsers());
        model.addAttribute("totalCountries", usersService.getTotalCountries());
        model.addAttribute("totalCompanies", usersService.getTotalClients());
        model.addAttribute("globalRating", ratingService.getGlobalSuccessRate());
        return "about";
    }
}
