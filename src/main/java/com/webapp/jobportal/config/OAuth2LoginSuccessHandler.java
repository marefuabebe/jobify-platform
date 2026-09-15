package com.webapp.jobportal.config;

import com.webapp.jobportal.entity.Users;
import com.webapp.jobportal.entity.UsersType;
import com.webapp.jobportal.repository.UsersRepository;
import com.webapp.jobportal.repository.UsersTypeRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.util.Date;
import java.util.Optional;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private UsersTypeRepository usersTypeRepository;

    @Autowired
    private com.webapp.jobportal.repository.JobSeekerProfileRepository jobSeekerProfileRepository;

    @Autowired
    private com.webapp.jobportal.repository.RecruiterProfileRepository recruiterProfileRepository;

    @Autowired
    private com.webapp.jobportal.services.EmailService emailService;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        try {
            CustomOAuth2User oauth2User = (CustomOAuth2User) authentication.getPrincipal();
            String email = oauth2User.getEmail();
            String givenName = oauth2User.getGivenName();
            String familyName = oauth2User.getFamilyName();
            String picture = oauth2User.getPicture();

            Optional<Users> userOptional = usersRepository.findByEmail(email);
            Users user;

            if (userOptional.isEmpty()) {
                // Register new user without a role yet
                Users newUser = new Users();
                newUser.setEmail(email);
                newUser.setPassword(java.util.UUID.randomUUID().toString());
                newUser.setActive(true);
                newUser.setApproved(true);
                newUser.setRegistrationDate(new Date());
                newUser.setUserTypeId(null); // Pending role selection

                user = usersRepository.save(newUser);

                // Temporary authority for role selection page
                List<org.springframework.security.core.GrantedAuthority> authorities = new java.util.ArrayList<>();
                authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PENDING_ROLE"));

                org.springframework.security.authentication.UsernamePasswordAuthenticationToken newAuth =
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                authentication.getPrincipal(),
                                authentication.getCredentials(),
                                authorities);

                SecurityContextHolder.getContext().setAuthentication(newAuth);
                org.springframework.security.web.context.SecurityContextRepository securityContextRepository =
                        new org.springframework.security.web.context.HttpSessionSecurityContextRepository();
                securityContextRepository.saveContext(SecurityContextHolder.getContext(), request, response);

                response.sendRedirect("/oauth2/choose-role");
                return;
            } else {
                user = userOptional.get();
                if (!user.isActive()) {
                    SecurityContextHolder.clearContext();
                    if (request.getSession(false) != null) {
                        request.getSession(false).invalidate();
                    }
                    response.sendRedirect("/login?disabled=true");
                    return;
                }

                // If user has not chosen a role yet
                if (user.getUserTypeId() == null) {
                    List<org.springframework.security.core.GrantedAuthority> authorities = new java.util.ArrayList<>();
                    authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PENDING_ROLE"));

                    org.springframework.security.authentication.UsernamePasswordAuthenticationToken newAuth =
                            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                    authentication.getPrincipal(),
                                    authentication.getCredentials(),
                                    authorities);

                    SecurityContextHolder.getContext().setAuthentication(newAuth);
                    org.springframework.security.web.context.SecurityContextRepository securityContextRepository =
                            new org.springframework.security.web.context.HttpSessionSecurityContextRepository();
                    securityContextRepository.saveContext(SecurityContextHolder.getContext(), request, response);

                    response.sendRedirect("/oauth2/choose-role");
                    return;
                }

                // Self-healing for existing users if firstName was saved as email or blank
                if (user.getUserTypeId().getUserTypeId() == 2) {
                    jobSeekerProfileRepository.findById(user.getUserId()).ifPresent(p -> {
                        boolean updated = false;
                        if (p.getFirstName() == null || p.getFirstName().trim().isEmpty() || p.getFirstName().equalsIgnoreCase(email)) {
                            p.setFirstName(givenName);
                            if (p.getLastName() == null || p.getLastName().trim().isEmpty()) {
                                p.setLastName(familyName);
                            }
                            updated = true;
                        }
                        if ((p.getProfilePhoto() == null || p.getProfilePhoto().trim().isEmpty()) && picture != null) {
                            p.setProfilePhoto(picture);
                            updated = true;
                        }
                        if (updated) {
                            jobSeekerProfileRepository.save(p);
                        }
                    });
                } else if (user.getUserTypeId().getUserTypeId() == 1) {
                    recruiterProfileRepository.findById(user.getUserId()).ifPresent(p -> {
                        boolean updated = false;
                        if (p.getFirstName() == null || p.getFirstName().trim().isEmpty() || p.getFirstName().equalsIgnoreCase(email)) {
                            p.setFirstName(givenName);
                            if (p.getLastName() == null || p.getLastName().trim().isEmpty()) {
                                p.setLastName(familyName);
                            }
                            updated = true;
                        }
                        if ((p.getProfilePhoto() == null || p.getProfilePhoto().trim().isEmpty()) && picture != null) {
                            p.setProfilePhoto(picture);
                            updated = true;
                        }
                        if (updated) {
                            recruiterProfileRepository.save(p);
                        }
                    });
                }
            }

            // --- 2. Inject Authorities into SecurityContext ---
            String roleName = user.getUserTypeId().getUserTypeName();
            List<org.springframework.security.core.GrantedAuthority> authorities = new java.util.ArrayList<>();
            authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(roleName));

            org.springframework.security.authentication.UsernamePasswordAuthenticationToken newAuth =
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            authentication.getPrincipal(),
                            authentication.getCredentials(),
                            authorities);

            SecurityContextHolder.getContext().setAuthentication(newAuth);

            org.springframework.security.web.context.SecurityContextRepository securityContextRepository =
                    new org.springframework.security.web.context.HttpSessionSecurityContextRepository();
            securityContextRepository.saveContext(SecurityContextHolder.getContext(), request, response);

            response.sendRedirect("/dashboard/");

        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect("/login?error");
        }
    }
}
