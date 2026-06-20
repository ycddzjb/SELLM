package com.sellm.org;

import com.sellm.clazz.Clazz;
import com.sellm.clazz.ClazzRepository;
import com.sellm.clazz.dto.ClazzResponse;
import com.sellm.common.Result;
import com.sellm.org.dto.CreateOrgRequest;
import com.sellm.org.dto.OrgResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/orgs")
public class OrganizationController {

    private final OrganizationRepository repo;
    private final OrganizationAppService appService;
    private final ClazzRepository clazzRepository;

    public OrganizationController(OrganizationRepository repo, OrganizationAppService appService,
                                  ClazzRepository clazzRepository) {
        this.repo = repo;
        this.appService = appService;
        this.clazzRepository = clazzRepository;
    }

    /** 公开:注册选机构用(免登录)。返回 id+name+region 供下拉。 */
    @GetMapping("/public")
    public Result<List<OrgResponse>> publicList() {
        return Result.ok(toResponses());
    }

    /** 公开:某机构的班级列表(注册页选班级用,免登录;仅 id+name)。 */
    @GetMapping("/public/{orgId}/classes")
    public Result<List<ClazzResponse>> publicClasses(@PathVariable Long orgId) {
        List<ClazzResponse> out = new ArrayList<>();
        for (Clazz c : clazzRepository.listByOrg(orgId)) {
            out.add(new ClazzResponse(c.getId(), c.getName(), c.getOrgId(), c.getDisorderTypes()));
        }
        return Result.ok(out);
    }

    /** 超管:一体创建机构 + 该机构管理员(事务原子,端点级已限 SUPER_ADMIN)。 */
    @PostMapping
    public Result<Long> create(@RequestBody CreateOrgRequest req) {
        return Result.ok(appService.createWithManager(req));
    }

    /** 超管:看所有机构(端点级已限 SUPER_ADMIN)。 */
    @GetMapping
    public Result<List<OrgResponse>> listAll() {
        return Result.ok(toResponses());
    }

    private List<OrgResponse> toResponses() {
        return repo.listAll().stream()
            .map(o -> new OrgResponse(o.getId(), o.getName(), o.getRegion(),
                o.getDisorderTypes(), o.getProvince(), o.getCity()))
            .toList();
    }
}
