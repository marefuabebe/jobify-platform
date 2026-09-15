package com.webapp.jobportal.controller;

import com.webapp.jobportal.entity.Users;
import com.webapp.jobportal.entity.VerificationToken;
import com.webapp.jobportal.repository.UsersRepository;
import com.webapp.jobportal.repository.VerificationTokenRepository;
import com.webapp.jobportal.services.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Date;
import java.util.Optional;

@Controller
public class VerificationController {

    private final VerificationTokenRepository tokenRepository;
    private final UsersRepository usersRepository;
    private final EmailService emailService;

    @Autowired
    public VerificationController(VerificationTokenRepository tokenRepository,
                                  UsersRepository usersRepository,
                                  EmailService emailService) {
        this.tokenRepository = tokenRepository;
        this.usersRepository = usersRepository;
        this.emailService = emailService;
    }

    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam("token") String token, Model model) {
        Optional<VerificationToken> tokenOpt = tokenRepository.findByToken(token);

        if (tokenOpt.isEmpty()) {
            model.addAttribute("error", "Invalid or missing verification token.");
            return "verify-email";
        }

        VerificationToken verificationToken = tokenOpt.get();

        if (verificationToken.getExpiryDate().before(new Date())) {
            model.addAttribute("error", "This verification link has expired.");
            return "verify-email";
        }

        Users user = verificationToken.getUser();
        
        // If already active, just tell them
        if (user.isActive()) {
            model.addAttribute("success", "Your account is already verified! You can now log in.");
            tokenRepository.delete(verificationToken);
            return "verify-email";
        }

        // Activate the user
        user.setActive(true);
        usersRepository.save(user);

        // Delete the token so it can't be used again
        tokenRepository.delete(verificationToken);

        // Send the Welcome Email now that they are verified
        String userName = "User";
        // Attempt to get name if available (simplified)
        // If profile was populated earlier we could fetch it, but let's default to "User" as before
        emailService.sendWelcomeNotification(user.getEmail(), userName);

        model.addAttribute("success", "Your email has been successfully verified! You can now log in and access all features.");
        return "verify-email";
    }
}
