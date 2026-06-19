package com.sellm.config;

import com.sellm.security.Role;
import com.sellm.user.AppUser;
import com.sellm.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("dev")
@Component
public class DevSeeder implements CommandLineRunner {

    private final UserRepository userRepository;

    public DevSeeder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        // 幂等:admin 不存在则建为超管;已存在但非超管则纠正为 SUPER_ADMIN(无机构,ACTIVE)
        AppUser existing = userRepository.findByUsername("admin");
        if (existing == null) {
            userRepository.register("admin", "admin123", Role.SUPER_ADMIN, null, "ACTIVE");
        } else if (existing.getRole() != Role.SUPER_ADMIN
                || existing.getOrgId() != null
                || !"ACTIVE".equals(existing.getStatus())) {
            userRepository.updateRoleOrgStatus(existing.getId(), Role.SUPER_ADMIN, null, "ACTIVE");
        }
    }
}
