package com.webapp.jobportal.controller;

import com.webapp.jobportal.entity.Users;
import com.webapp.jobportal.entity.VerificationToken;
import com.webapp.jobportal.repository.UsersRepository;
import com.webapp.jobportal.repository.VerificationTokenRepository;
import com.webapp.jobportal.services.EmailService;
import com.webapp.jobportal.services.UsersService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Date;
import java.util.Optional;

@Controller
public class VerificationController {

    private final VerificationTokenRepository tokenRepository;
    private final UsersRepository usersRepository;
    private final EmailService emailService;
    private final UsersService usersService;

    @Autowired
    public VerificationController(VerificationTokenRepository tokenRepository,
                                  UsersRepository usersRepository,
                                  EmailService emailService,
                                  UsersService usersService) {
        this.tokenRepository = tokenRepository;
        this.usersRepository = usersRepository;
        this.emailService = emailService;
        this.usersService = usersService;
    }

    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam(value = "token", required = false) String token, Model model) {
        if (token == null || token.trim().isEmpty()) {
            model.addAttribute("error", "Verification token is missing. Please check your link or request a new one.");
            model.addAttribute("canResend", true);
            return "verify-email";
        }

        Optional<VerificationToken> tokenOpt = tokenRepository.findByToken(token.trim());

        if (tokenOpt.isEmpty()) {
            model.addAttribute("error", "Invalid or used verification token. If your link has expired, you can request a new one below.");
            model.addAttribute("canResend", true);
            return "verify-email";
        }

        VerificationToken verificationToken = tokenOpt.get();

        if (verificationToken.getExpiryDate().before(new Date())) {
            String userEmail = verificationToken.getUser() != null ? verificationToken.getUser().getEmail() : null;
            model.addAttribute("error", "This verification link has expired (links are valid for 24 hours). Please request a new link.");
            model.addAttribute("canResend", true);
            model.addAttribute("email", userEmail);
            tokenRepository.delete(verificationToken);
            return "verify-email";
        }

        Users user = verificationToken.getUser();

        // If already active, inform them
        if (user.isActive()) {
            model.addAttribute("success", "Your account is already verified! You can now log in.");
            tokenRepository.delete(verificationToken);
            return "verify-email";
        }

        // Activate and approve the user
        user.setActive(true);
        user.setApproved(true);
        usersRepository.save(user);

        // Delete the token so it cannot be reused
        tokenRepository.delete(verificationToken);

        // Send the Welcome Email now that they are verified
        String userName = "User";
        try {
            userName = usersService.getUserFullName(user);
        } catch (Exception ignored) {}
        emailService.sendWelcomeNotification(user.getEmail(), userName);

        model.addAttribute("success", "Your email has been successfully verified! You can now log in and access all features.");
        return "verify-email";
    }

    @GetMapping("/resend-verification")
    public String showResendVerificationPage(@RequestParam(value = "email", required = false) String email, Model model) {
        if (email != null) {
            model.addAttribute("email", email.trim());
        }
        return "resend-verification";
    }

    @PostMapping("/resend-verification")
    public String handleResendVerification(@RequestParam("email") String email, HttpServletRequest request, Model model) {
        if (email == null || email.trim().isEmpty()) {
            model.addAttribute("error", "Please provide a valid email address.");
            return "resend-verification";
        }

        String sanitizedEmail = email.trim();
        Optional<Users> userOpt = usersRepository.findByEmailIgnoreCase(sanitizedEmail)
                .or(() -> usersRepository.findByEmail(sanitizedEmail));

        if (userOpt.isEmpty()) {
            model.addAttribute("error", "We could not find an account with the email: " + sanitizedEmail);
            model.addAttribute("email", sanitizedEmail);
            return "resend-verification";
        }

        Users user = userOpt.get();

        if (user.isActive()) {
            model.addAttribute("success", "Your account is already verified! You can proceed to sign in.");
            model.addAttribute("alreadyActive", true);
            return "resend-verification";
        }

        // Generate new token and send verification email
        String appUrl = getAppUrl(request);
        usersService.sendVerificationToken(user, appUrl);

        model.addAttribute("success", "A new verification email has been sent to " + sanitizedEmail + ". Please check your inbox and spam folder (valid for 24 hours).");
        model.addAttribute("sent", true);
        model.addAttribute("email", sanitizedEmail);
        return "resend-verification";
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
}
