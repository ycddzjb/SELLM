package com.sellm.user;

import com.sellm.security.Role;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class UserRepository {

    private final AppUserMapper mapper;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserRepository(AppUserMapper mapper) {
        this.mapper = mapper;
    }

    // 兼容旧调用:不带 status 默认 ACTIVE
    public AppUser register(String username, String rawPassword, Role role, Long orgId) {
        return register(username, rawPassword, role, orgId, "ACTIVE");
    }

    public AppUser register(String username, String rawPassword, Role role, Long orgId, String status) {
        Map<String, Object> row = new HashMap<>();
        row.put("username", username);
        row.put("passwordHash", passwordEncoder.encode(rawPassword));
        row.put("role", role.name());
        row.put("orgId", orgId);
        row.put("status", status);
        mapper.insert(row);
        return new AppUser(((Number) row.get("id")).longValue(),
            username, (String) row.get("passwordHash"), role, orgId, status);
    }

    public AppUser findByUsername(String username) {
        return toAppUser(mapper.findByUsername(username));
    }

    public AppUser findById(Long id) {
        return toAppUser(mapper.findById(id));
    }

    public List<AppUser> listPendingByOrg(Long orgId) {
        List<AppUser> result = new ArrayList<>();
        for (Map<String, Object> row : mapper.findPendingByOrg(orgId)) {
            result.add(toAppUser(row));
        }
        return result;
    }

    public List<AppUser> listAll() {
        List<AppUser> result = new ArrayList<>();
        for (Map<String, Object> row : mapper.findAll()) {
            result.add(toAppUser(row));
        }
        return result;
    }

    public List<AppUser> listByOrg(Long orgId) {
        List<AppUser> result = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByOrg(orgId)) {
            result.add(toAppUser(row));
        }
        return result;
    }

    private AppUser toAppUser(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Long orgId = row.get("orgId") == null ? null : ((Number) row.get("orgId")).longValue();
        return new AppUser(((Number) row.get("id")).longValue(),
            (String) row.get("username"), (String) row.get("passwordHash"),
            Role.valueOf((String) row.get("role")), orgId, (String) row.get("status"));
    }

    public void updateRoleOrgStatus(Long id, Role role, Long orgId, String status) {
        mapper.updateRoleOrgStatus(id, role.name(), orgId, status);
    }

    public void updateStatus(Long id, String status) {
        mapper.updateStatus(id, status);
    }

    public void changePassword(Long userId, String rawPassword) {
        mapper.updatePassword(userId, passwordEncoder.encode(rawPassword));
    }

    public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}
