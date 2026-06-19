package com.sellm.user;

import com.sellm.security.Role;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import java.util.HashMap;
import java.util.Map;

@Repository
public class UserRepository {

    private final AppUserMapper mapper;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserRepository(AppUserMapper mapper) {
        this.mapper = mapper;
    }

    public AppUser register(String username, String rawPassword, Role role, Long orgId) {
        Map<String, Object> row = new HashMap<>();
        row.put("username", username);
        row.put("passwordHash", passwordEncoder.encode(rawPassword));
        row.put("role", role.name());
        row.put("orgId", orgId);
        mapper.insert(row);
        return new AppUser(((Number) row.get("id")).longValue(),
            username, (String) row.get("passwordHash"), role, orgId);
    }

    public AppUser findByUsername(String username) {
        Map<String, Object> row = mapper.findByUsername(username);
        if (row == null) {
            return null;
        }
        Long orgId = row.get("orgId") == null ? null : ((Number) row.get("orgId")).longValue();
        return new AppUser(((Number) row.get("id")).longValue(),
            (String) row.get("username"), (String) row.get("passwordHash"),
            Role.valueOf((String) row.get("role")), orgId);
    }

    public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}
