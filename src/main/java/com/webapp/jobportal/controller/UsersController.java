package com.webapp.jobportal.controller;

import com.webapp.jobportal.entity.Users;
import com.webapp.jobportal.entity.UsersType;
import com.webapp.jobportal.services.UsersService;
import com.webapp.jobportal.services.UsersTypeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

@Controller
public class UsersController {

    private final UsersTypeService usersTypeService;
    private final UsersService usersService;

    @Autowired
    public UsersController(UsersTypeService usersTypeService, UsersService usersService) {
        this.usersTypeService = usersTypeService;
        this.usersService = usersService;
    }

    @GetMapping("/register")
    public String register(@RequestParam(value = "type", required = false) String type, Model model) {
        List<UsersType> usersTypes = usersTypeService.getAll();
        model.addAttribute("getAllTypes", usersTypes);
        model.addAttribute("user", new Users());

        // Pre-select user type based on URL parameter
        if (type != null) {
            Integer selectedTypeId = null;
            if ("client".equals(type)) {
                selectedTypeId = 1; // Client type ID
            } else if ("freelancer".equals(type)) {
                selectedTypeId = 2; // Freelancer type ID
            }
            model.addAttribute("selectedType", selectedTypeId);
        }

        return "register";
    }

    @PostMapping("/register/new")
    public String userRegistration(@Valid Users users, @RequestParam("userTypeId") Integer userTypeId,
                                   HttpServletRequest request, Model model) {
        // Validate userTypeId to prevent admin role assignment
        if (userTypeId == null || userTypeId < 1 || userTypeId > 2) {
            model.addAttribute("error", "Invalid user type selected. Please select Client or Freelancer.");
            List<UsersType> usersTypes = usersTypeService.getAll();
            model.addAttribute("getAllTypes", usersTypes);
            model.addAttribute("user", new Users());
            return "register";
        }

        Optional<Users> optionalUsers = usersService.getUserByEmail(users.getEmail());
        if (optionalUsers.isPresent()) {
            model.addAttribute("error", "Email already registered, try to login or register with other email.");
            List<UsersType> usersTypes = usersTypeService.getAll();
            model.addAttribute("getAllTypes", usersTypes);
            model.addAttribute("user", new Users());
            return "register";
        }

        // Use a reference to the existing UsersType instead of creating a new one
        UsersType userType = usersTypeService.getById(userTypeId);
        users.setUserTypeId(userType);

        String appUrl = getAppUrl(request);
        usersService.addNew(users, appUrl);

        // Redirect to pending verification page with email
        String encodedEmail = "";
        try {
            encodedEmail = java.net.URLEncoder.encode(users.getEmail(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        return "redirect:/register/pending-verification?email=" + encodedEmail;
    }

    @GetMapping("/register/pending-verification")
    public String pendingVerification(@RequestParam(value = "email", required = false) String email, Model model) {
        model.addAttribute("email", email != null ? email : "");
        return "pending-verification";
    }

    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            @RequestParam(value = "disabled", required = false) String disabled,
            @RequestParam(value = "unverified", required = false) String unverified,
            @RequestParam(value = "email", required = false) String email,
            Model model) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            boolean hasAdmin = authentication.getAuthorities().stream().anyMatch(r -> r.getAuthority().equals("Admin"));
            boolean hasClient = authentication.getAuthorities().stream().anyMatch(r -> r.getAuthority().equals("Client"));
            boolean hasFreelancer = authentication.getAuthorities().stream().anyMatch(r -> r.getAuthority().equals("Freelancer"));
            if (hasAdmin) return "redirect:/admin/dashboard";
            if (hasClient) return "redirect:/client-dashboard/";
            if (hasFreelancer) return "redirect:/freelancer-dashboard/";
            return "redirect:/";
        }

        if (error != null) {
            model.addAttribute("loginError", true);
        }
        if (logout != null) {
            model.addAttribute("logoutSuccess", true);
        }
        if (disabled != null) {
            model.addAttribute("accountDisabled", true);
        }
        if (unverified != null) {
            model.addAttribute("accountUnverified", true);
            model.addAttribute("unverifiedEmail", email != null ? email : "");
        }
        return "login";
    }

    private String getAppUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();

        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isEmpty()) {
            scheme = forwardedProto;
        }

        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (forwardedHost != null && !forwardedHost.isEmpty()) {
            serverName = forwardedHost;
            if (serverName.contains(":")) {
                String[] parts = serverName.split(":");
                serverName = parts[0];
                try {
                    serverPort = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {}
            }
        }

        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);

        if ((scheme.equalsIgnoreCase("http") && serverPort != 80)
                || (scheme.equalsIgnoreCase("https") && serverPort != 443)) {
            url.append(":").append(serverPort);
        }

        return url.toString();
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }
        return "redirect:/";
    }
}
