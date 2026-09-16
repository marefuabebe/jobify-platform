package com.webapp.jobportal.config;

import com.webapp.jobportal.entity.Users;
import com.webapp.jobportal.entity.UsersType;
import com.webapp.jobportal.repository.UsersRepository;
import com.webapp.jobportal.repository.UsersTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;
import java.util.Optional;

@Configuration
public class DatabaseSeeder {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseSeeder.class);

    @Bean
    public CommandLineRunner seedDatabase(UsersTypeRepository usersTypeRepository,
                                          UsersRepository usersRepository,
                                          PasswordEncoder passwordEncoder,
                                          JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                // 1. Seed UsersType (1: Client, 2: Freelancer, 3: Admin)
                try {
                    jdbcTemplate.execute(
                        "INSERT INTO users_type (user_type_id, user_type_name) VALUES " +
                        "(1, 'Client'), (2, 'Freelancer'), (3, 'Admin') " +
                        "ON DUPLICATE KEY UPDATE user_type_name = VALUES(user_type_name)"
                    );
                    logger.info("UsersType seeded/verified successfully via JdbcTemplate.");
                } catch (Exception sqlEx) {
                    logger.warn("JdbcTemplate seed notice: {}. Attempting fallback...", sqlEx.getMessage());
                    if (usersTypeRepository.count() == 0) {
                        UsersType clientType = new UsersType();
                        clientType.setUserTypeName("Client");
                        usersTypeRepository.save(clientType);

                        UsersType freelancerType = new UsersType();
                        freelancerType.setUserTypeName("Freelancer");
                        usersTypeRepository.save(freelancerType);

                        UsersType adminType = new UsersType();
                        adminType.setUserTypeName("Admin");
                        usersTypeRepository.save(adminType);
                        logger.info("UsersType fallback seeding completed.");
                    }
                }

                // 2. Seed Default Admin User if none exists
                Optional<Users> adminUserOpt = usersRepository.findByEmail("marefu933@gmail.com");
                if (adminUserOpt.isEmpty()) {
                    logger.info("Seeding default Admin account (marefu933@gmail.com)...");
                    Users adminUser = new Users();
                    adminUser.setEmail("marefu933@gmail.com");
                    adminUser.setPassword(passwordEncoder.encode("marefu@@3854"));
                    adminUser.setActive(true);
                    adminUser.setApproved(true);
                    adminUser.setRegistrationDate(new Date());

                    // Dynamically find the Admin type
                    UsersType adminType = usersTypeRepository.findAll().stream()
                            .filter(t -> "Admin".equalsIgnoreCase(t.getUserTypeName()))
                            .findFirst()
                            .or(() -> usersTypeRepository.findById(3))
                            .orElse(null);

                    if (adminType != null) {
                        adminUser.setUserTypeId(adminType);
                        usersRepository.save(adminUser);
                        logger.info("Default Admin account seeded successfully.");
                    } else {
                        logger.warn("Could not seed default Admin account: Admin UsersType not found.");
                    }
                }
            } catch (Throwable t) {
                // Defensive safeguard: database seeding issues should never crash Spring Boot startup
                logger.error("Non-fatal notice during database seeding: {}", t.getMessage(), t);
            }
        };
    }
}

