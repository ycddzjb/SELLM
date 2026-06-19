package com.sellm.org;

import com.sellm.common.Result;
import com.sellm.org.dto.CreateOrgRequest;
import com.sellm.org.dto.OrgResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orgs")
public class OrganizationController {

    private final OrganizationRepository repo;

    public OrganizationController(OrganizationRepository repo) {
        this.repo = repo;
    }

    /** 公开:注册选机构用(免登录)。返回 id+name+region 供下拉。 */
    @GetMapping("/public")
    public Result<List<OrgResponse>> publicList() {
        return Result.ok(toResponses());
    }

    /** 超管:建机构(端点级已限 SUPER_ADMIN)。 */
    @PostMapping
    public Result<Long> create(@RequestBody CreateOrgRequest req) {
        Organization saved = repo.save(new Organization(null, req.getName(), req.getRegion()));
        return Result.ok(saved.getId());
    }

    /** 超管:看所有机构(端点级已限 SUPER_ADMIN)。 */
    @GetMapping
    public Result<List<OrgResponse>> listAll() {
        return Result.ok(toResponses());
    }

    private List<OrgResponse> toResponses() {
        return repo.listAll().stream()
            .map(o -> new OrgResponse(o.getId(), o.getName(), o.getRegion()))
            .toList();
    }
}
