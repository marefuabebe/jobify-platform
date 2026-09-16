package com.webapp.jobportal.controller;

import com.webapp.jobportal.entity.PasswordResetToken;
import com.webapp.jobportal.entity.Users;
import com.webapp.jobportal.repository.PasswordResetTokenRepository;
import com.webapp.jobportal.repository.UsersRepository;
import com.webapp.jobportal.services.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Date;
import java.util.Optional;

@Controller
public class PasswordResetController {

    private final UsersRepository usersRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public PasswordResetController(UsersRepository usersRepository,
                                   PasswordResetTokenRepository tokenRepository,
                                   EmailService emailService,
                                   PasswordEncoder passwordEncoder) {
        this.usersRepository = usersRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam("email") String email, HttpServletRequest request, Model model) {
        String cleanEmail = email != null ? email.trim() : "";
        Optional<Users> userOpt = usersRepository.findByEmailIgnoreCase(cleanEmail)
                .or(() -> usersRepository.findByEmail(cleanEmail));
        
        if (userOpt.isEmpty()) {
            model.addAttribute("error", "We could not find an account with that email address.");
            return "forgot-password";
        }

        Users user = userOpt.get();

        // Check if token already exists and delete it
        Optional<PasswordResetToken> existingToken = tokenRepository.findByUser(user);
        existingToken.ifPresent(tokenRepository::delete);

        // Create new token
        PasswordResetToken myToken = new PasswordResetToken(user);
        tokenRepository.save(myToken);

        // Generate reset link
        String appUrl = getAppUrl(request);
        String resetLink = appUrl + "/reset-password?token=" + myToken.getToken();

        // Send email
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);

        model.addAttribute("success", "A password reset link has been sent to your email.");
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam("token") String token, Model model) {
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByToken(token);

        if (tokenOpt.isEmpty()) {
            model.addAttribute("error", "Invalid password reset token.");
            return "reset-password";
        }

        PasswordResetToken resetToken = tokenOpt.get();

        if (resetToken.getExpiryDate().before(new Date())) {
            model.addAttribute("error", "Password reset token has expired.");
            return "reset-password";
        }

        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam("token") String token,
                                       @RequestParam("password") String password,
                                       @RequestParam("confirmPassword") String confirmPassword,
                                       Model model) {
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            model.addAttribute("token", token);
            return "reset-password";
        }

        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByToken(token);

        if (tokenOpt.isEmpty()) {
            model.addAttribute("error", "Invalid password reset token.");
            return "reset-password";
        }

        PasswordResetToken resetToken = tokenOpt.get();

        if (resetToken.getExpiryDate().before(new Date())) {
            model.addAttribute("error", "Password reset token has expired.");
            return "reset-password";
        }

        Users user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(password));
        usersRepository.save(user);

        tokenRepository.delete(resetToken);

        model.addAttribute("success", "Your password has been successfully reset.");
        return "reset-password";
    }

    private String getAppUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        
        // Handle proxy headers if deployed behind a load balancer
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null) {
            scheme = forwardedProto;
        }
        
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (forwardedHost != null) {
            serverName = forwardedHost;
            // X-Forwarded-Host usually includes the port if it's non-standard
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

        if ((scheme.equals("http") && serverPort != 80) || (scheme.equals("https") && serverPort != 443)) {
            url.append(":").append(serverPort);
        }

        return url.toString();
    }
}
