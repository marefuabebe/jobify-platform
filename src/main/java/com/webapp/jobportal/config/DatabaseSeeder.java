package com.webapp.jobportal.config;

import com.webapp.jobportal.entity.Users;
import com.webapp.jobportal.entity.UsersType;
import com.webapp.jobportal.repository.UsersRepository;
import com.webapp.jobportal.repository.UsersTypeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;
import java.util.Optional;

@Configuration
public class DatabaseSeeder {

    @Bean
    public CommandLineRunner seedDatabase(UsersTypeRepository usersTypeRepository,
                                          UsersRepository usersRepository,
                                          PasswordEncoder passwordEncoder) {
        return args -> {
            // Seed UsersType if empty
            if (usersTypeRepository.count() == 0) {
                System.out.println("Seeding UsersType...");
                UsersType clientType = new UsersType();
                clientType.setUserTypeId(1);
                clientType.setUserTypeName("Client");
                usersTypeRepository.save(clientType);

                UsersType freelancerType = new UsersType();
                freelancerType.setUserTypeId(2);
                freelancerType.setUserTypeName("Freelancer");
                usersTypeRepository.save(freelancerType);

                UsersType adminType = new UsersType();
                adminType.setUserTypeId(3);
                adminType.setUserTypeName("Admin");
                usersTypeRepository.save(adminType);
            }

            // Seed Admin User if none exists
            Optional<Users> adminUserOpt = usersRepository.findByEmail("marefu933@gmail.com");
            if (adminUserOpt.isEmpty()) {
                System.out.println("Seeding default Admin account...");
                Users adminUser = new Users();
                adminUser.setEmail("marefu933@gmail.com");
                // Set default secure password for admin
                adminUser.setPassword(passwordEncoder.encode("marefu@@3854"));
                adminUser.setActive(true);
                adminUser.setApproved(true);
                adminUser.setRegistrationDate(new Date());

                UsersType adminType = usersTypeRepository.findById(3).orElseThrow();
                adminUser.setUserTypeId(adminType);

                usersRepository.save(adminUser);
            }
        };
    }
}
