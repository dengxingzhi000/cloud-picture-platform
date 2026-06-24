package com.cn.cloudpictureplatform.interfaces.watermark;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.application.watermark.WatermarkService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.domain.watermark.ExportPreset;
import com.cn.cloudpictureplatform.domain.watermark.ExportTask;
import com.cn.cloudpictureplatform.domain.watermark.WatermarkConfig;
import com.cn.cloudpictureplatform.infrastructure.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.interfaces.watermark.dto.WatermarkConfigRequest;

@RestController
@RequestMapping("/api/v1")
public class WatermarkController {
    private final WatermarkService watermarkService;

    public WatermarkController(WatermarkService watermarkService) {
        this.watermarkService = watermarkService;
    }

    @GetMapping("/teams/{teamId}/watermark")
    public ApiResponse<WatermarkConfig> getWatermark(@PathVariable("teamId") UUID teamId) {
        return ApiResponse.ok(watermarkService.getWatermarkConfig(teamId));
    }

    @PutMapping("/teams/{teamId}/watermark")
    public ApiResponse<WatermarkConfig> updateWatermark(
            @PathVariable("teamId") UUID teamId,
            @RequestBody WatermarkConfigRequest request
    ) {
        return ApiResponse.ok(watermarkService.updateWatermarkConfig(teamId, request));
    }

    @GetMapping("/teams/{teamId}/export-presets")
    public ApiResponse<List<ExportPreset>> listPresets(@PathVariable("teamId") UUID teamId) {
        return ApiResponse.ok(watermarkService.listPresets(teamId));
    }

    @PostMapping("/teams/{teamId}/export-presets")
    public ApiResponse<ExportPreset> createPreset(
            @PathVariable("teamId") UUID teamId,
            @RequestBody ExportPreset preset
    ) {
        return ApiResponse.ok(watermarkService.createPreset(teamId, preset));
    }

    @DeleteMapping("/export-presets/{presetId}")
    public ApiResponse<Void> deletePreset(@PathVariable("presetId") UUID presetId) {
        watermarkService.deletePreset(presetId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/pictures/{pictureId}/export")
    public ApiResponse<ExportTask> createExport(
            @PathVariable("pictureId") UUID pictureId,
            @RequestBody(required = false) UUID presetId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(watermarkService.createExportTask(pictureId, presetId, principal.getId()));
    }

    @GetMapping("/export-tasks/{taskId}")
    public ApiResponse<ExportTask> getExportTask(@PathVariable("taskId") UUID taskId) {
        return ApiResponse.ok(watermarkService.getExportTask(taskId));
    }
}
