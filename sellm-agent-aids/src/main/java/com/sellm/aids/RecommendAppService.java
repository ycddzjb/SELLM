package com.sellm.aids;

import org.springframework.stereotype.Service;
import java.util.List;

/** 教具推荐:按障碍类型查库,纯本地、不出网、不脱敏。 */
@Service
public class RecommendAppService {

    private final TeachingAidRepository repo;
    public RecommendAppService(TeachingAidRepository repo) { this.repo = repo; }

    /** disorderType 可空 → 返回全部;无匹配 → 空列表。 */
    public List<TeachingAid> recommend(String disorderType) {
        return repo.findByDisorderType(disorderType);
    }
}
