package com.cn.cloudpictureplatform.application.watermark;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.storage.StorageResult;
import com.cn.cloudpictureplatform.domain.storage.StorageService;
import com.cn.cloudpictureplatform.domain.watermark.ExportPreset;
import com.cn.cloudpictureplatform.domain.watermark.ExportTask;
import com.cn.cloudpictureplatform.domain.watermark.WatermarkConfig;
import com.cn.cloudpictureplatform.infrastructure.persistence.ExportPresetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.ExportTaskRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.WatermarkConfigRepository;

@Service
public class ExportProcessingService {
    private static final Logger log = LoggerFactory.getLogger(ExportProcessingService.class);
    private static final int BATCH_SIZE = 5;

    private final ExportTaskRepository exportTaskRepository;
    private final ExportPresetRepository exportPresetRepository;
    private final PictureAssetRepository pictureAssetRepository;
    private final WatermarkConfigRepository watermarkConfigRepository;
    private final StorageService storageService;

    public ExportProcessingService(
            ExportTaskRepository exportTaskRepository,
            ExportPresetRepository exportPresetRepository,
            PictureAssetRepository pictureAssetRepository,
            WatermarkConfigRepository watermarkConfigRepository,
            StorageService storageService
    ) {
        this.exportTaskRepository = exportTaskRepository;
        this.exportPresetRepository = exportPresetRepository;
        this.pictureAssetRepository = pictureAssetRepository;
        this.watermarkConfigRepository = watermarkConfigRepository;
        this.storageService = storageService;
    }

    @Scheduled(fixedDelay = 30_000)
    public void processPendingExports() {
        List<ExportTask> pending = exportTaskRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        if (pending.isEmpty()) {
            return;
        }
        int count = Math.min(pending.size(), BATCH_SIZE);
        for (int i = 0; i < count; i++) {
            processTask(pending.get(i));
        }
    }

    private void processTask(ExportTask task) {
        try {
            PictureAsset picture = pictureAssetRepository.findById(task.getPictureId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "picture not found"));

            if (task.getPresetId() == null) {
                markFailed(task, "presetId is required");
                return;
            }

            ExportPreset preset = exportPresetRepository.findById(task.getPresetId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "preset not found"));

            task.setStatus("PROCESSING");
            exportTaskRepository.save(task);

            BufferedImage original;
            try (InputStream in = storageService.retrieve(picture.getStorageKey())) {
                original = ImageIO.read(in);
            }
            if (original == null) {
                markFailed(task, "failed to read image: " + picture.getStorageKey());
                return;
            }

            BufferedImage processed = processImage(original, preset, picture.getSpaceId());

            String formatName = preset.getFormat().toLowerCase();
            if ("jpeg".equals(formatName)) {
                formatName = "jpg";
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(processed, formatName, baos);
            byte[] imageBytes = baos.toByteArray();

            String exportKey = "exports/" + task.getId() + "_" + task.getPictureId() + "." + formatName;
            StorageResult result = storageService.store(imageBytes, exportKey, "image/" + formatName);

            task.setStatus("COMPLETED");
            task.setResultUrl(result.getUrl());
            task.setCompletedAt(Instant.now());
            exportTaskRepository.save(task);
            log.info("Export completed: taskId={} pictureId={} url={}", task.getId(), task.getPictureId(), result.getUrl());
        } catch (Exception e) {
            log.error("Export failed: taskId={} pictureId={}", task.getId(), task.getPictureId(), e);
            markFailed(task, e.getMessage());
        }
    }

    private BufferedImage processImage(BufferedImage original, ExportPreset preset, UUID spaceId) {
        int targetW = preset.getTargetWidth();
        int targetH = preset.getTargetHeight();

        double scale = Math.min(
                (double) targetW / original.getWidth(),
                (double) targetH / original.getHeight()
        );
        int scaledW = (int) (original.getWidth() * scale);
        int scaledH = (int) (original.getHeight() * scale);

        BufferedImage resized = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setColor(java.awt.Color.WHITE);
        g2d.fillRect(0, 0, targetW, targetH);

        int x = (targetW - scaledW) / 2;
        int y = (targetH - scaledH) / 2;
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(original, x, y, scaledW, scaledH, null);

        if (preset.isWatermarkEnabled()) {
            WatermarkConfig wm = watermarkConfigRepository.findByTeamId(spaceId).orElse(null);
            if (wm != null && wm.isEnabled()) {
                applyWatermark(g2d, wm, targetW, targetH);
            }
        }

        g2d.dispose();
        return resized;
    }

    private void applyWatermark(Graphics2D g2d, WatermarkConfig wm, int imageW, int imageH) {
        g2d.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER,
                (float) wm.getOpacity()));

        if ("TEXT".equals(wm.getType()) && wm.getText() != null && !wm.getText().isEmpty()) {
            g2d.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, Math.max(imageW, imageH) / 20));
            g2d.setColor(new java.awt.Color(255, 255, 255));
            java.awt.FontMetrics fm = g2d.getFontMetrics();
            int textW = fm.stringWidth(wm.getText());
            int textH = fm.getHeight();

            int[] pos = resolvePosition(wm.getPosition(), imageW, imageH, textW, textH);
            g2d.drawString(wm.getText(), pos[0], pos[1] + textH);
        }
        g2d.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 1.0f));
    }

    private int[] resolvePosition(String position, int imageW, int imageH, int elementW, int elementH) {
        int margin = 20;
        return switch (position) {
            case "CENTER" -> new int[]{ (imageW - elementW) / 2, (imageH - elementH) / 2 };
            case "TOP_LEFT" -> new int[]{ margin, margin };
            case "TOP_RIGHT" -> new int[]{ imageW - elementW - margin, margin };
            case "BOTTOM_LEFT" -> new int[]{ margin, imageH - elementH - margin };
            default -> new int[]{ imageW - elementW - margin, imageH - elementH - margin };
        };
    }

    private void markFailed(ExportTask task, String error) {
        task.setStatus("FAILED");
        task.setErrorMessage(error);
        task.setCompletedAt(Instant.now());
        exportTaskRepository.save(task);
    }
}
