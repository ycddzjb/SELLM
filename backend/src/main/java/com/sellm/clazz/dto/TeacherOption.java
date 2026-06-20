package com.sellm.clazz.dto;

/** 公开:班级下老师选项(注册页选审核老师用),仅暴露 id + username,无敏感字段。 */
public class TeacherOption {
    private Long userId;
    private String username;

    public TeacherOption(Long userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
}
