package com.cn.cloudpictureplatform.interfaces.album;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.application.album.AlbumService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.interfaces.album.dto.AlbumCreateRequest;
import com.cn.cloudpictureplatform.interfaces.album.dto.AlbumResponse;

@RestController
@RequestMapping("/api/albums")
public class AlbumController {
    private final AlbumService albumService;

    public AlbumController(AlbumService albumService) {
        this.albumService = albumService;
    }

    @PostMapping
    public ApiResponse<AlbumResponse> createAlbum(
            @RequestParam UUID spaceId,
            @Valid @RequestBody AlbumCreateRequest request
    ) {
        return ApiResponse.ok(albumService.createAlbum(spaceId, request));
    }

    @GetMapping
    public ApiResponse<List<AlbumResponse>> listAlbums(@RequestParam UUID spaceId) {
        return ApiResponse.ok(albumService.listAlbums(spaceId));
    }

    @GetMapping("/{id}")
    public ApiResponse<AlbumResponse> getAlbum(@PathVariable("id") UUID albumId) {
        return ApiResponse.ok(albumService.getAlbum(albumId));
    }

    @PatchMapping("/{id}")
    public ApiResponse<AlbumResponse> updateAlbum(
            @PathVariable("id") UUID albumId,
            @Valid @RequestBody AlbumCreateRequest request
    ) {
        return ApiResponse.ok(albumService.updateAlbum(albumId, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAlbum(@PathVariable("id") UUID albumId) {
        albumService.deleteAlbum(albumId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/pictures")
    public ApiResponse<Void> addPictures(
            @PathVariable("id") UUID albumId,
            @RequestBody List<UUID> pictureIds
    ) {
        albumService.addPictures(albumId, pictureIds);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/pictures")
    public ApiResponse<List<UUID>> listAlbumPictures(@PathVariable("id") UUID albumId) {
        return ApiResponse.ok(albumService.listAlbumPictureIds(albumId));
    }

    @DeleteMapping("/{id}/pictures/{pictureId}")
    public ApiResponse<Void> removePicture(
            @PathVariable("id") UUID albumId,
            @PathVariable("pictureId") UUID pictureId
    ) {
        albumService.removePicture(albumId, pictureId);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{id}/cover")
    public ApiResponse<Void> setCover(
            @PathVariable("id") UUID albumId,
            @RequestBody UUID pictureId
    ) {
        albumService.setCover(albumId, pictureId);
        return ApiResponse.ok(null);
    }
}
