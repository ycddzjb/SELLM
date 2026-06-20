package com.sellm.org;

import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.org.dto.CreateOrgRequest;
import com.sellm.security.Role;
import com.sellm.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationAppService {

    private final OrganizationRepository orgRepository;
    private final UserRepository userRepository;

    public OrganizationAppService(OrganizationRepository orgRepository, UserRepository userRepository) {
        this.orgRepository = orgRepository;
        this.userRepository = userRepository;
    }

    /**
     * 超管一体创建:机构 + 该机构 MANAGER 账号。
     * 事务原子:机构与管理员同成败(任一失败整体回滚),避免建了机构却没建成管理员的半成品。
     */
    @Transactional
    public Long createWithManager(CreateOrgRequest req) {
        if (req.getManagerUsername() == null || req.getManagerUsername().isBlank()
                || req.getManagerPassword() == null || req.getManagerPassword().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "请填写机构管理员账号密码");
        }
        if (userRepository.findByUsername(req.getManagerUsername()) != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "管理员用户名已存在");
        }
        // save 内已校验 disorderTypes(Task 2)
        Organization org = orgRepository.save(new Organization(
            null, req.getName(), req.getRegion(),
            req.getDisorderTypes(), req.getProvince(), req.getCity()));
        // 一体创建该机构 MANAGER(orgId 指向新机构,ACTIVE)
        userRepository.register(req.getManagerUsername(), req.getManagerPassword(),
            Role.MANAGER, org.getId(), "ACTIVE");
        return org.getId();
    }
}
