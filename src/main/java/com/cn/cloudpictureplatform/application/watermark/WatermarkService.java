package com.cn.cloudpictureplatform.application.watermark;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.domain.watermark.ExportPreset;
import com.cn.cloudpictureplatform.domain.watermark.ExportTask;
import com.cn.cloudpictureplatform.domain.watermark.WatermarkConfig;
import com.cn.cloudpictureplatform.infrastructure.persistence.ExportPresetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.ExportTaskRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.WatermarkConfigRepository;
import com.cn.cloudpictureplatform.interfaces.watermark.dto.WatermarkConfigRequest;

@Service
@Transactional(readOnly = true)
public class WatermarkService {
    private final WatermarkConfigRepository watermarkConfigRepository;
    private final ExportPresetRepository exportPresetRepository;
    private final ExportTaskRepository exportTaskRepository;

    public WatermarkService(
            WatermarkConfigRepository watermarkConfigRepository,
            ExportPresetRepository exportPresetRepository,
            ExportTaskRepository exportTaskRepository
    ) {
        this.watermarkConfigRepository = watermarkConfigRepository;
        this.exportPresetRepository = exportPresetRepository;
        this.exportTaskRepository = exportTaskRepository;
    }

    // Watermark config

    public WatermarkConfig getWatermarkConfig(UUID teamId) {
        return watermarkConfigRepository.findByTeamId(teamId)
                .orElse(null);
    }

    @Transactional
    public WatermarkConfig updateWatermarkConfig(UUID teamId, WatermarkConfigRequest request) {
        WatermarkConfig config = watermarkConfigRepository.findByTeamId(teamId)
                .orElse(WatermarkConfig.builder().teamId(teamId).build());
        if (request.getEnabled() != null) config.setEnabled(request.getEnabled());
        if (request.getType() != null) config.setType(request.getType());
        if (request.getText() != null) config.setText(request.getText());
        if (request.getImageStorageKey() != null) config.setImageStorageKey(request.getImageStorageKey());
        if (request.getOpacity() != null) config.setOpacity(request.getOpacity());
        if (request.getPosition() != null) config.setPosition(request.getPosition());
        return watermarkConfigRepository.save(config);
    }

    // Export presets

    public List<ExportPreset> listPresets(UUID teamId) {
        return exportPresetRepository.findByTeamIdOrderByNameAsc(teamId);
    }

    @Transactional
    public ExportPreset createPreset(UUID teamId, ExportPreset preset) {
        preset.setTeamId(teamId);
        return exportPresetRepository.save(preset);
    }

    @Transactional
    public void deletePreset(UUID presetId) {
        exportPresetRepository.deleteById(presetId);
    }

    // Export tasks

    public ExportTask getExportTask(UUID taskId) {
        return exportTaskRepository.findById(taskId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "export task not found"));
    }

    @Transactional
    public ExportTask createExportTask(UUID pictureId, UUID presetId, UUID userId) {
        ExportTask task = ExportTask.builder()
                .pictureId(pictureId).presetId(presetId).userId(userId)
                .status("PENDING").build();
        return exportTaskRepository.save(task);
    }
}
