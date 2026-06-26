package com.sellm.org;

import com.sellm.clazz.Clazz;
import com.sellm.clazz.ClazzRepository;
import com.sellm.clazz.dto.ClazzResponse;
import com.sellm.common.Result;
import com.sellm.org.dto.CreateOrgRequest;
import com.sellm.org.dto.OrgResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    /** 超管:编辑机构信息(端点级限 SUPER_ADMIN)。 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CreateOrgRequest req) {
        appService.update(id, req);
        return Result.ok(null);
    }

    /** 超管:软删机构(机构下有用户/儿童则拦截;端点级限 SUPER_ADMIN)。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        appService.delete(id);
        return Result.ok(null);
    }

    /** 超管:批量创建机构(含机构管理员),逐条容错,返回成功数+失败明细。 */
    @PostMapping("/batch")
    public Result<OrganizationAppService.BatchResult> batchCreate(@RequestBody List<CreateOrgRequest> list) {
        return Result.ok(appService.batchCreate(list));
    }

    private List<OrgResponse> toResponses() {
        return repo.listAll().stream()
            .map(o -> new OrgResponse(o.getId(), o.getName(), o.getRegion(),
                o.getDisorderTypes(), o.getProvince(), o.getCity()))
            .toList();
    }
}
