package com.cn.cloudpictureplatform.interfaces.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import com.cn.cloudpictureplatform.application.picture.PictureService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.infrastructure.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.interfaces.admin.dto.AdminPictureSummary;
import com.cn.cloudpictureplatform.interfaces.admin.dto.ModerationRecordResponse;
import com.cn.cloudpictureplatform.interfaces.admin.dto.ReviewRequest;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureResponse;

@Validated
@RestController
@RequestMapping("/api/admin/pictures")
public class AdminPictureController {
    private final PictureService pictureService;

    public AdminPictureController(PictureService pictureService) {
        this.pictureService = pictureService;
    }

    @GetMapping("/pending")
    public ApiResponse<PageResponse<AdminPictureSummary>> listPending(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(pictureService.listPending(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminPictureSummary> getDetail(@PathVariable("id") UUID pictureId) {
        return ApiResponse.ok(pictureService.getAdminPicture(pictureId));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<PictureResponse> review(
            @PathVariable("id") UUID pictureId,
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody ReviewRequest request
    ) {
        return ApiResponse.ok(pictureService.review(
                pictureId,
                principal.getId(),
                request.getStatus(),
                request.getReason()
        ));
    }

    @GetMapping("/{id}/reviews")
    public ApiResponse<PageResponse<ModerationRecordResponse>> listReviews(
            @PathVariable("id") UUID pictureId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) UUID reviewerId,
            @RequestParam(required = false) ReviewStatus fromStatus,
            @RequestParam(required = false) ReviewStatus toStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant reviewedAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant reviewedBefore,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir
    ) {
        return ApiResponse.ok(pictureService.listModerationHistory(
                pictureId,
                page,
                size,
                reviewerId,
                fromStatus,
                toStatus,
                reviewedAfter,
                reviewedBefore,
                sortBy,
                sortDir
        ));
    }

    @GetMapping("/reviews")
    public ApiResponse<PageResponse<ModerationRecordResponse>> searchReviews(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) UUID pictureId,
            @RequestParam(required = false) UUID reviewerId,
            @RequestParam(required = false) ReviewStatus fromStatus,
            @RequestParam(required = false) ReviewStatus toStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant reviewedAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant reviewedBefore,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir
    ) {
        return ApiResponse.ok(pictureService.listModerationHistory(
                pictureId,
                page,
                size,
                reviewerId,
                fromStatus,
                toStatus,
                reviewedAfter,
                reviewedBefore,
                sortBy,
                sortDir
        ));
    }

    @GetMapping(value = "/reviews/export", produces = "text/csv")
    public ResponseEntity<String> exportReviews(
            @RequestParam(required = false) UUID pictureId,
            @RequestParam(required = false) UUID reviewerId,
            @RequestParam(required = false) ReviewStatus fromStatus,
            @RequestParam(required = false) ReviewStatus toStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant reviewedAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant reviewedBefore,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(defaultValue = "1000") @Min(1) @Max(10000) int limit
    ) {
        List<ModerationRecordResponse> records = pictureService.exportModerationHistory(
                pictureId,
                reviewerId,
                fromStatus,
                toStatus,
                reviewedAfter,
                reviewedBefore,
                sortBy,
                sortDir,
                limit
        );
        String csv = toCsv(records);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"moderation_reviews.csv\"");
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    private String toCsv(List<ModerationRecordResponse> records) {
        StringBuilder builder = new StringBuilder();
        builder.append("id,pictureId,reviewerId,reviewerUsername,reviewerDisplayName,fromStatus,toStatus,reason,reviewedAt\n");
        for (ModerationRecordResponse record : records) {
            builder.append(escapeCsv(record.getId()))
                    .append(',')
                    .append(escapeCsv(record.getPictureId()))
                    .append(',')
                    .append(escapeCsv(record.getReviewerId()))
                    .append(',')
                    .append(escapeCsv(record.getReviewerUsername()))
                    .append(',')
                    .append(escapeCsv(record.getReviewerDisplayName()))
                    .append(',')
                    .append(escapeCsv(record.getFromStatus()))
                    .append(',')
                    .append(escapeCsv(record.getToStatus()))
                    .append(',')
                    .append(escapeCsv(record.getReason()))
                    .append(',')
                    .append(escapeCsv(record.getReviewedAt()))
                    .append('\n');
        }
        return builder.toString();
    }

    private String escapeCsv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        boolean needsEscaping = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (needsEscaping) {
            text = text.replace("\"", "\"\"");
            return "\"" + text + "\"";
        }
        return text;
    }
}
