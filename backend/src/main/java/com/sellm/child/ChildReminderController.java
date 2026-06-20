package com.sellm.child;

import com.sellm.child.dto.ChildReminderResponse;
import com.sellm.common.Result;
import com.sellm.security.AccessGuard;
import com.sellm.security.AuthPrincipal;
import com.sellm.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 到期提醒:距今 30 天内(含已逾期)需复评 / IEP 到期的儿童。
 * 行级权限复用 AccessGuard(老师/管理员限本机构、家长限自己孩子),只读。
 */
@RestController
@RequestMapping("/api/children/reminders")
public class ChildReminderController {

    private static final int REMIND_WITHIN_DAYS = 30;

    private final ChildRepository repository;
    private final CurrentUser currentUser;
    private final AccessGuard accessGuard;

    public ChildReminderController(ChildRepository repository, CurrentUser currentUser, AccessGuard accessGuard) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public Result<List<ChildReminderResponse>> reminders() {
        AuthPrincipal me = currentUser.require();
        LocalDate today = LocalDate.now();
        List<ChildReminderResponse> out = new ArrayList<>();
        for (Child c : repository.findAll()) {
            if (!accessGuard.canAccess(me, c)) {
                continue;   // 行级:无权的跳过
            }
            addIfDue(out, c, c.getReassessDate(), "REASSESS", today);
            addIfDue(out, c, c.getIepDueDate(), "IEP_DUE", today);
        }
        out.sort(Comparator.comparingInt(ChildReminderResponse::getDaysLeft)); // 逾期最久 / 最紧急在前
        return Result.ok(out);
    }

    /** 日期非空且距今 ≤ 30 天(含逾期)则纳入;非法日期串跳过。 */
    private void addIfDue(List<ChildReminderResponse> out, Child c, String isoDate,
                          String type, LocalDate today) {
        if (isoDate == null || isoDate.isBlank()) {
            return;
        }
        LocalDate due;
        try {
            due = LocalDate.parse(isoDate.trim());
        } catch (Exception e) {
            return; // 非法日期串,跳过不报错
        }
        long daysLeft = ChronoUnit.DAYS.between(today, due);
        if (daysLeft <= REMIND_WITHIN_DAYS) {
            out.add(new ChildReminderResponse(c.getId(), c.getName(), type,
                isoDate, (int) daysLeft, daysLeft < 0));
        }
    }
}
