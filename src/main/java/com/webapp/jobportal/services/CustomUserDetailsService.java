package com.webapp.jobportal.services;

import com.webapp.jobportal.entity.Users;
import com.webapp.jobportal.repository.UsersRepository;
import com.webapp.jobportal.util.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UsersRepository usersRepository;

    @Autowired
    public CustomUserDetailsService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String cleanEmail = username != null ? username.trim() : "";
        Users user = usersRepository.findByEmailIgnoreCase(cleanEmail)
                .or(() -> usersRepository.findByEmail(cleanEmail))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + cleanEmail));
        return new CustomUserDetails(user);
    }
}
