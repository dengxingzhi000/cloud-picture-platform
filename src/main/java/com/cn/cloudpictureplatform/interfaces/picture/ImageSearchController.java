package com.cn.cloudpictureplatform.interfaces.picture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.cn.cloudpictureplatform.application.search.HybridSearchService;
import com.cn.cloudpictureplatform.application.shared.dto.PictureSummary;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.common.web.PageResponse;

@RestController
@RequestMapping("/api/search")
public class ImageSearchController {
    private final HybridSearchService hybridSearchService;

    public ImageSearchController(HybridSearchService hybridSearchService) {
        this.hybridSearchService = hybridSearchService;
    }

    @GetMapping("/semantic")
    public ApiResponse<PageResponse<PictureSummary>> semanticSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(hybridSearchService.search(query, page, size));
    }
}
