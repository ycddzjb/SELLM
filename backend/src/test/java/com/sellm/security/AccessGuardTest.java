package com.sellm.security;

import com.sellm.child.Child;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AccessGuard 单元测试 — 行级权限判定
 */
public class AccessGuardTest {

    private AccessGuard accessGuard;

    @BeforeEach
    public void setup() {
        accessGuard = new AccessGuard();
    }

    // ===== SUPER_ADMIN 跨机构访问 =====

    @Test
    public void testSuperAdminAccessDifferentOrgChild() {
        // SUPER_ADMIN 访问不同机构的 child → 返回 true
        AuthPrincipal superAdmin = new AuthPrincipal(1L, "admin", Role.SUPER_ADMIN, null);
        Child child = new Child(100L, "Tom", "ASD", 99L, null);

        assertTrue(accessGuard.canAccess(superAdmin, child));
    }

    @Test
    public void testSuperAdminAccessSameOrgChild() {
        // SUPER_ADMIN 访问同机构的 child(虽然跨机构不是要求) → 返回 true
        AuthPrincipal superAdmin = new AuthPrincipal(1L, "admin", Role.SUPER_ADMIN, 1L);
        Child child = new Child(100L, "Tom", "ASD", 1L, null);

        assertTrue(accessGuard.canAccess(superAdmin, child));
    }

    @Test
    public void testSuperAdminAccessNullChild() {
        // SUPER_ADMIN 访问 null child → 返回 false(资源不存在,谁都拒)
        AuthPrincipal superAdmin = new AuthPrincipal(1L, "admin", Role.SUPER_ADMIN, null);

        assertFalse(accessGuard.canAccess(superAdmin, null));
    }

    // ===== MANAGER 同机构/跨机构 =====

    @Test
    public void testManagerAccessSameOrgChild() {
        // MANAGER 访问本机构 child → 返回 true
        AuthPrincipal manager = new AuthPrincipal(2L, "mgr1", Role.MANAGER, 1L);
        Child child = new Child(101L, "Alice", "ADHD", 1L, null);

        assertTrue(accessGuard.canAccess(manager, child));
    }

    @Test
    public void testManagerAccessDifferentOrgChild() {
        // MANAGER 访问不同机构 child → 返回 false
        AuthPrincipal manager = new AuthPrincipal(2L, "mgr1", Role.MANAGER, 1L);
        Child child = new Child(102L, "Bob", "ADHD", 99L, null);

        assertFalse(accessGuard.canAccess(manager, child));
    }

    @Test
    public void testManagerAccessNullChild() {
        // MANAGER 访问 null child → 返回 false
        AuthPrincipal manager = new AuthPrincipal(2L, "mgr1", Role.MANAGER, 1L);

        assertFalse(accessGuard.canAccess(manager, null));
    }

    // ===== TEACHER 同机构/跨机构 =====

    @Test
    public void testTeacherAccessSameOrgChild() {
        // TEACHER 访问本机构 child → 返回 true
        AuthPrincipal teacher = new AuthPrincipal(3L, "teacher1", Role.TEACHER, 2L);
        Child child = new Child(103L, "Charlie", "ASD", 2L, null);

        assertTrue(accessGuard.canAccess(teacher, child));
    }

    @Test
    public void testTeacherAccessDifferentOrgChild() {
        // TEACHER 访问不同机构 child → 返回 false
        AuthPrincipal teacher = new AuthPrincipal(3L, "teacher1", Role.TEACHER, 2L);
        Child child = new Child(104L, "Dave", "ASD", 99L, null);

        assertFalse(accessGuard.canAccess(teacher, child));
    }

    @Test
    public void testTeacherAccessNullChild() {
        // TEACHER 访问 null child → 返回 false
        AuthPrincipal teacher = new AuthPrincipal(3L, "teacher1", Role.TEACHER, 2L);

        assertFalse(accessGuard.canAccess(teacher, null));
    }

    // ===== PARENT 自己监护/他人孩子 =====

    @Test
    public void testParentAccessOwnChild() {
        // PARENT 访问自己监护的孩子(userId == guardianUserId) → 返回 true
        AuthPrincipal parent = new AuthPrincipal(4L, "parent1", Role.PARENT, 1L);
        Child child = new Child(105L, "Eve", "ASD", 1L, 4L);  // guardianUserId = 4L

        assertTrue(accessGuard.canAccess(parent, child));
    }

    @Test
    public void testParentAccessOtherChild() {
        // PARENT 访问他人的孩子(userId != guardianUserId) → 返回 false
        AuthPrincipal parent = new AuthPrincipal(4L, "parent1", Role.PARENT, 1L);
        Child child = new Child(106L, "Frank", "ADHD", 1L, 999L);  // guardianUserId = 999L

        assertFalse(accessGuard.canAccess(parent, child));
    }

    @Test
    public void testParentAccessNullChild() {
        // PARENT 访问 null child → 返回 false
        AuthPrincipal parent = new AuthPrincipal(4L, "parent1", Role.PARENT, 1L);

        assertFalse(accessGuard.canAccess(parent, null));
    }

    // ===== Edge Cases =====

    @Test
    public void testManagerWithNullOrgIdAccessChild() {
        // MANAGER 的 orgId 为 null,访问任何 child → 返回 false(机构不明确,拒绝)
        AuthPrincipal manager = new AuthPrincipal(2L, "mgr_no_org", Role.MANAGER, null);
        Child child = new Child(107L, "Grace", "ASD", 1L, null);

        assertFalse(accessGuard.canAccess(manager, child));
    }

    @Test
    public void testParentWithNullUserIdAccessChild() {
        // PARENT 的 userId 为 null(不合理但防御),访问 child → 返回 false
        AuthPrincipal parent = new AuthPrincipal(null, "parent_no_id", Role.PARENT, 1L);
        Child child = new Child(108L, "Henry", "ASD", 1L, 4L);

        assertFalse(accessGuard.canAccess(parent, child));
    }

    @Test
    public void testCheckChildAccessThrowsForDenied() {
        // checkChildAccess 对无权返回应抛出异常
        AuthPrincipal teacher = new AuthPrincipal(3L, "teacher1", Role.TEACHER, 1L);
        Child child = new Child(109L, "Ivy", "ADHD", 99L, null);

        assertThrows(com.sellm.common.BusinessException.class, () -> {
            accessGuard.checkChildAccess(teacher, child);
        });
    }

    @Test
    public void testCheckChildAccessPassesForAllowed() {
        // checkChildAccess 对有权返回应不抛异常
        AuthPrincipal teacher = new AuthPrincipal(3L, "teacher1", Role.TEACHER, 1L);
        Child child = new Child(110L, "Jack", "ASD", 1L, null);

        // 不抛异常 = 测试通过
        assertDoesNotThrow(() -> {
            accessGuard.checkChildAccess(teacher, child);
        });
    }
}
