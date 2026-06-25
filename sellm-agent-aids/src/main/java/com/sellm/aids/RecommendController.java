package com.sellm.aids;

import com.sellm.agentcommon.UnauthorizedException;
import com.sellm.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 教具推荐端点。X-User-Id 鉴权(仅校验登录,推荐结果与具体用户无关)。 */
@RestController
@RequestMapping("/api/aids/recommendations")
public class RecommendController {

    private final RecommendAppService appService;
    public RecommendController(RecommendAppService appService) { this.appService = appService; }

    @GetMapping
    public Result<List<TeachingAid>> recommend(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(value = "disorderType", required = false) String disorderType) {
        if (userId == null) throw new UnauthorizedException("缺少 X-User-Id");
        return Result.ok(appService.recommend(disorderType));
    }
}
