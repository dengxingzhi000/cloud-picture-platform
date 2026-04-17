package com.cn.cloudpictureplatform.interfaces.admin;

import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.application.search.SearchMaintenanceService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.interfaces.admin.dto.SearchReindexResponse;

@RestController
@RequestMapping("/api/admin/search")
public class SearchAdminController {

    private final SearchMaintenanceService searchMaintenanceService;

    public SearchAdminController(SearchMaintenanceService searchMaintenanceService) {
        this.searchMaintenanceService = searchMaintenanceService;
    }

    @PostMapping("/reindex")
    public ApiResponse<SearchReindexResponse> reindexAll() {
        int queued = searchMaintenanceService.enqueueAllPictures();
        return ApiResponse.ok(new SearchReindexResponse("all", queued, null, Instant.now()));
    }

    @PostMapping("/pictures/{pictureId}/reindex")
    public ApiResponse<SearchReindexResponse> reindexPicture(@PathVariable("pictureId") UUID pictureId) {
        boolean queued = searchMaintenanceService.enqueuePicture(pictureId);
        if (!queued) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "picture not found");
        }
        return ApiResponse.ok(new SearchReindexResponse("picture", 1, pictureId, Instant.now()));
    }
}
