package com.cn.cloudpictureplatform.interfaces.admin;

import java.time.Instant;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
        return ApiResponse.ok(new SearchReindexResponse(queued, Instant.now()));
    }
}
