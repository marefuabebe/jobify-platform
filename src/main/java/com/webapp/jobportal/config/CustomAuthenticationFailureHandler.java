package com.webapp.jobportal.config;

import com.webapp.jobportal.entity.Users;
import com.webapp.jobportal.repository.UsersRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(CustomAuthenticationFailureHandler.class);

    private final UsersRepository usersRepository;

    @Autowired
    public CustomAuthenticationFailureHandler(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {

        String username = request.getParameter("username");
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        String failureReason = exception.getMessage();

        logger.warn("Login Failed | User: {} | IP: {} | Reason: {} | UA: {}",
                username, ipAddress, failureReason, userAgent);

        boolean isDisabled = false;

        if (exception instanceof DisabledException) {
            isDisabled = true;
        } else if (exception.getCause() instanceof DisabledException) {
            isDisabled = true;
        } else if (exception.getMessage() != null && exception.getMessage().toLowerCase().contains("disabled")) {
            isDisabled = true;
        }

        if (isDisabled) {
            boolean isUnverified = false;
            try {
                if (username != null && !username.trim().isEmpty()) {
                    Optional<Users> userOpt = usersRepository.findByEmail(username.trim());
                    if (userOpt.isPresent()) {
                        Users user = userOpt.get();
                        // If user is inactive, distinguish unverified from banned
                        if (!user.isActive()) {
                            isUnverified = true;
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error inspecting user for verification status: {}", e.getMessage());
            }

            if (isUnverified) {
                logger.info("Redirecting unverified user {} to /login?unverified=true", username);
                String encodedEmail = URLEncoder.encode(username != null ? username.trim() : "", StandardCharsets.UTF_8);
                response.sendRedirect("/login?unverified=true&email=" + encodedEmail);
                return;
            }

            logger.info("Redirecting banned user {} to /login?disabled=true", username);
            response.sendRedirect("/login?disabled=true");
        } else {
            response.sendRedirect("/login?error=true");
        }
    }
}
