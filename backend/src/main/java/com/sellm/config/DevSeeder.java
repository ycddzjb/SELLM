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
        // 幂等:已存在则不重复创建
        AppUser existing = userRepository.findByUsername("admin");
        if (existing == null) {
            userRepository.register("admin", "admin123", Role.MANAGER, 1L);
        }
    }
}
