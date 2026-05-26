package com.clinic.config;

import com.clinic.entity.Admin;
import com.clinic.entity.AdminRole;
import com.clinic.entity.Doctor;
import com.clinic.repository.AdminRepository;
import com.clinic.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${seed.admin.username:narwar_veer}")
    private String adminUsername;

    @Value("${seed.admin.password:Narwar123}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (adminRepository.findByUsername(adminUsername).isPresent()) {
            return;
        }
        Doctor doctor = doctorRepository.findById(1L)
                .orElseGet(() -> doctorRepository.findAll().stream().findFirst().orElse(null));
        if (doctor == null) {
            log.warn("Skipping admin seeding because no doctor exists");
            return;
        }
        Admin admin = new Admin();
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(AdminRole.ROLE_ADMIN);
        admin.setDoctor(doctor);
        adminRepository.save(admin);
        log.info("Seeded default admin username={}", adminUsername);
    }
}
