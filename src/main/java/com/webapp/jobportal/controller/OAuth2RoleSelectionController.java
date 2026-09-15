package com.webapp.jobportal.controller;

import com.webapp.jobportal.config.CustomOAuth2User;
import com.webapp.jobportal.entity.JobSeekerProfile;
import com.webapp.jobportal.entity.RecruiterProfile;
import com.webapp.jobportal.entity.Users;
import com.webapp.jobportal.entity.UsersType;
import com.webapp.jobportal.repository.JobSeekerProfileRepository;
import com.webapp.jobportal.repository.RecruiterProfileRepository;
import com.webapp.jobportal.repository.UsersRepository;
import com.webapp.jobportal.repository.UsersTypeRepository;
import com.webapp.jobportal.services.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/oauth2")
public class OAuth2RoleSelectionController {

    private final UsersRepository usersRepository;
    private final UsersTypeRepository usersTypeRepository;
    private final JobSeekerProfileRepository jobSeekerProfileRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final EmailService emailService;

    @Autowired
    public OAuth2RoleSelectionController(UsersRepository usersRepository,
                                         UsersTypeRepository usersTypeRepository,
                                         JobSeekerProfileRepository jobSeekerProfileRepository,
                                         RecruiterProfileRepository recruiterProfileRepository,
                                         EmailService emailService) {
        this.usersRepository = usersRepository;
        this.usersTypeRepository = usersTypeRepository;
        this.jobSeekerProfileRepository = jobSeekerProfileRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
        this.emailService = emailService;
    }

    private String getAuthenticatedEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomOAuth2User customOAuth2User) {
            return customOAuth2User.getEmail();
        } else if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        } else {
            return authentication.getName();
        }
    }

    @GetMapping("/choose-role")
    public String showChooseRolePage(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = getAuthenticatedEmail(authentication);

        if (email == null) {
            return "redirect:/login";
        }

        Optional<Users> userOpt = usersRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return "redirect:/login";
        }

        Users user = userOpt.get();

        // If user already selected a role, send them directly to dashboard
        if (user.getUserTypeId() != null) {
            return "redirect:/dashboard/";
        }

        String displayName = "User";
        String picture = null;
        if (authentication.getPrincipal() instanceof CustomOAuth2User customOAuth2User) {
            String givenName = customOAuth2User.getGivenName();
            if (givenName != null && !givenName.trim().isEmpty()) {
                displayName = givenName;
            } else if (customOAuth2User.getName() != null) {
                displayName = customOAuth2User.getName();
            }
            picture = customOAuth2User.getPicture();
        }

        model.addAttribute("displayName", displayName);
        model.addAttribute("email", email);
        model.addAttribute("picture", picture);

        return "oauth2-select-role";
    }

    @PostMapping("/choose-role")
    public String processRoleSelection(@RequestParam("userTypeId") Integer userTypeId,
                                       HttpServletRequest request,
                                       HttpServletResponse response,
                                       Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = getAuthenticatedEmail(authentication);

        if (email == null) {
            return "redirect:/login";
        }

        Optional<Users> userOpt = usersRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return "redirect:/login";
        }

        Users user = userOpt.get();

        // Only allow Client (1) or Freelancer (2)
        if (userTypeId == null || (userTypeId != 1 && userTypeId != 2)) {
            model.addAttribute("error", "Please select either Client or Freelancer.");
            return showChooseRolePage(model);
        }

        Optional<UsersType> usersTypeOpt = usersTypeRepository.findById(userTypeId);
        if (usersTypeOpt.isEmpty()) {
            model.addAttribute("error", "Invalid user type selected.");
            return showChooseRolePage(model);
        }

        UsersType usersType = usersTypeOpt.get();
        user.setUserTypeId(usersType);
        user = usersRepository.save(user);

        // Extract Google attributes
        String givenName = "";
        String familyName = "";
        String picture = null;

        if (authentication.getPrincipal() instanceof CustomOAuth2User customOAuth2User) {
            givenName = customOAuth2User.getGivenName();
            familyName = customOAuth2User.getFamilyName();
            picture = customOAuth2User.getPicture();
        }

        if (givenName == null || givenName.trim().isEmpty()) {
            givenName = "User";
        }
        if (familyName == null) {
            familyName = "";
        }

        // Initialize appropriate profile based on role
        if (userTypeId == 1) { // Client / Recruiter
            RecruiterProfile profile = recruiterProfileRepository.findById(user.getUserId())
                    .orElse(new RecruiterProfile(user));
            profile.setFirstName(givenName);
            profile.setLastName(familyName);
            if (picture != null && !picture.trim().isEmpty()) {
                profile.setProfilePhoto(picture);
            }
            recruiterProfileRepository.save(profile);
        } else if (userTypeId == 2) { // Freelancer / JobSeeker
            JobSeekerProfile profile = jobSeekerProfileRepository.findById(user.getUserId())
                    .orElse(new JobSeekerProfile(user));
            profile.setFirstName(givenName);
            profile.setLastName(familyName);
            if (picture != null && !picture.trim().isEmpty()) {
                profile.setProfilePhoto(picture);
            }
            jobSeekerProfileRepository.save(profile);
        }

        // Send Welcome Email
        emailService.sendWelcomeNotification(email, givenName);

        // Update SecurityContext with assigned authority
        String roleName = usersType.getUserTypeName();
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(roleName));

        UsernamePasswordAuthenticationToken newAuth = new UsernamePasswordAuthenticationToken(
                authentication.getPrincipal(),
                authentication.getCredentials(),
                authorities);

        SecurityContextHolder.getContext().setAuthentication(newAuth);
        HttpSessionSecurityContextRepository repo = new HttpSessionSecurityContextRepository();
        repo.saveContext(SecurityContextHolder.getContext(), request, response);

        return "redirect:/dashboard/";
    }
}
