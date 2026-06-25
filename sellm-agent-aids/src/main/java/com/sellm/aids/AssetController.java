package com.sellm.aids;

import com.sellm.agentcommon.UnauthorizedException;
import com.sellm.aids.dto.AssetResponse;
import com.sellm.aids.dto.GenerateAssetRequest;
import com.sellm.aids.dto.SubmitResponse;
import com.sellm.aids.dto.TaskStatusResponse;
import com.sellm.common.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 文生素材端点(长任务异步:POST 202 / GET tasks / GET assets)。X-User-Id 鉴权 + 行级权限。 */
@RestController
@RequestMapping("/api/aids")
public class AssetController {

    private final AssetAppService appService;
    public AssetController(AssetAppService appService) { this.appService = appService; }

    /** 提交生成任务 → 202 Accepted + taskId。 */
    @PostMapping("/assets")
    public ResponseEntity<Result<SubmitResponse>> submit(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody GenerateAssetRequest req) {
        requireUser(userId);
        Long taskId = appService.submit(userId, req);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Result.ok(new SubmitResponse(taskId)));
    }

    /** 轮询任务状态。 */
    @GetMapping("/tasks/{taskId}")
    public Result<TaskStatusResponse> pollTask(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable("taskId") Long taskId) {
        requireUser(userId);
        return Result.ok(appService.pollTask(userId, taskId));
    }

    /** 查看素材产物(行级权限)。 */
    @GetMapping("/assets/{id}")
    public Result<AssetResponse> get(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable("id") Long id) {
        requireUser(userId);
        return Result.ok(appService.get(userId, id));
    }

    /** 取产物原始字节(行级权限);小程序图片预览/下载。 */
    @GetMapping("/assets/{id}/raw")
    public ResponseEntity<byte[]> raw(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable("id") Long id) {
        requireUser(userId);
        AssetAppService.RawAsset raw = appService.getRaw(userId, id);
        return ResponseEntity.ok()
            .header("Content-Type", raw.mimeType())
            .body(raw.bytes());
    }

    /** 我的素材列表。 */
    @GetMapping("/assets")
    public Result<List<AssetResponse>> mine(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUser(userId);
        return Result.ok(appService.listMine(userId));
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new UnauthorizedException("缺少 X-User-Id");
    }
}
