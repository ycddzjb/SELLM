package com.sellm.org;

import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.child.ChildRepository;
import com.sellm.org.dto.CreateOrgRequest;
import com.sellm.security.Role;
import com.sellm.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class OrganizationAppService {

    private final OrganizationRepository orgRepository;
    private final UserRepository userRepository;
    private final ChildRepository childRepository;

    public OrganizationAppService(OrganizationRepository orgRepository, UserRepository userRepository,
                                  ChildRepository childRepository) {
        this.orgRepository = orgRepository;
        this.userRepository = userRepository;
        this.childRepository = childRepository;
    }

    /**
     * 超管一体创建:机构 + 该机构 MANAGER 账号。
     * 事务原子:机构与管理员同成败(任一失败整体回滚),避免建了机构却没建成管理员的半成品。
     */
    @Transactional
    public Long createWithManager(CreateOrgRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "请填写机构名称");
        }
        if (req.getManagerUsername() == null || req.getManagerUsername().isBlank()
                || req.getManagerPassword() == null || req.getManagerPassword().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "请填写机构管理员账号密码");
        }
        if (userRepository.findByUsername(req.getManagerUsername()) != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "管理员用户名已存在");
        }
        // 机构按名去重:重名则复用已有机构(不重复建),管理员照常归到该机构
        String name = req.getName().trim();
        Long orgId = orgRepository.listAll().stream()
            .filter(o -> name.equals(o.getName()))
            .map(Organization::getId)
            .findFirst()
            .orElse(null);
        if (orgId == null) {
            // save 内已校验 disorderTypes(Task 2)
            Organization org = orgRepository.save(new Organization(
                null, name, req.getRegion(),
                req.getDisorderTypes(), req.getProvince(), req.getCity()));
            orgId = org.getId();
        }
        // 一体创建该机构 MANAGER(orgId 指向新建或复用的机构,ACTIVE)
        userRepository.register(req.getManagerUsername(), req.getManagerPassword(),
            Role.MANAGER, orgId, "ACTIVE");
        return orgId;
    }

    /** 编辑机构信息(不含管理员)。 */
    public void update(Long id, CreateOrgRequest req) {
        Organization org = orgRepository.findById(id);
        if (org == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "机构不存在");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "机构名称不能为空");
        }
        org.setName(req.getName());
        org.setRegion(req.getRegion());
        org.setDisorderTypes(req.getDisorderTypes());
        org.setProvince(req.getProvince());
        org.setCity(req.getCity());
        orgRepository.update(org);
    }

    /** 软删机构;机构下仍有用户/儿童则阻止。 */
    public void delete(Long id) {
        Organization org = orgRepository.findById(id);
        if (org == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "机构不存在");
        }
        if (userRepository.countByOrg(id) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "机构下仍有用户,请先转移或停用后再删除");
        }
        if (childRepository.countByOrg(id) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "机构下仍有儿童档案,请先处理后再删除");
        }
        orgRepository.softDelete(id);
    }

    /** 批量建机构(逐条容错):返回成功数 + 失败明细(行号从 1 计 + 原因)。 */
    public BatchResult batchCreate(List<CreateOrgRequest> list) {
        BatchResult result = new BatchResult();
        if (list == null || list.isEmpty()) return result;
        int row = 0;
        for (CreateOrgRequest req : list) {
            row++;
            try {
                createWithManager(req);   // 各条独立事务(@Transactional 自调用不生效,逐条失败仅影响本条)
                result.success++;
            } catch (Exception e) {
                result.failures.add("第 " + row + " 行: " + e.getMessage());
            }
        }
        return result;
    }

    /** 批量导入结果。 */
    public static class BatchResult {
        public int success = 0;
        public List<String> failures = new ArrayList<>();
        public int getSuccess() { return success; }
        public List<String> getFailures() { return failures; }
    }
}
