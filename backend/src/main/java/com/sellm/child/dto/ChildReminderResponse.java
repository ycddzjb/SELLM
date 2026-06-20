package com.sellm.child.dto;

/** 到期提醒条目:某儿童的复评 / IEP 到期临近(或已逾期)。 */
public class ChildReminderResponse {
    private final Long childId;
    private final String name;
    private final String reminderType; // REASSESS 复评 / IEP_DUE IEP到期
    private final String dueDate;      // ISO yyyy-MM-dd
    private final int daysLeft;        // 距今天数,负=已逾期
    private final boolean overdue;

    public ChildReminderResponse(Long childId, String name, String reminderType,
                                String dueDate, int daysLeft, boolean overdue) {
        this.childId = childId;
        this.name = name;
        this.reminderType = reminderType;
        this.dueDate = dueDate;
        this.daysLeft = daysLeft;
        this.overdue = overdue;
    }

    public Long getChildId() { return childId; }
    public String getName() { return name; }
    public String getReminderType() { return reminderType; }
    public String getDueDate() { return dueDate; }
    public int getDaysLeft() { return daysLeft; }
    public boolean isOverdue() { return overdue; }
}
