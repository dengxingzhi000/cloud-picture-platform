package com.cn.cloudpictureplatform.application.album;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.domain.album.Album;
import com.cn.cloudpictureplatform.domain.album.AlbumPicture;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.infrastructure.persistence.AlbumPictureRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.AlbumRepository;
import com.cn.cloudpictureplatform.interfaces.album.dto.AlbumCreateRequest;
import com.cn.cloudpictureplatform.interfaces.album.dto.AlbumResponse;

@Service
@Transactional(readOnly = true)
public class AlbumService {
    private final AlbumRepository albumRepository;
    private final AlbumPictureRepository albumPictureRepository;

    public AlbumService(AlbumRepository albumRepository, AlbumPictureRepository albumPictureRepository) {
        this.albumRepository = albumRepository;
        this.albumPictureRepository = albumPictureRepository;
    }

    @Transactional
    public AlbumResponse createAlbum(UUID spaceId, AlbumCreateRequest request) {
        Album album = Album.builder()
                .spaceId(spaceId)
                .name(request.getName().trim())
                .description(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null)
                .visibility(request.getVisibility() != null ? request.getVisibility() : Visibility.PRIVATE)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();
        Album saved = albumRepository.save(album);
        return toResponse(saved, 0);
    }

    public List<AlbumResponse> listAlbums(UUID spaceId) {
        List<Album> albums = albumRepository.findBySpaceIdOrderBySortOrderAsc(spaceId);
        return albums.stream()
                .map(a -> toResponse(a, albumPictureRepository.countByAlbumId(a.getId())))
                .toList();
    }

    public AlbumResponse getAlbum(UUID albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "album not found"));
        return toResponse(album, albumPictureRepository.countByAlbumId(albumId));
    }

    @Transactional
    public AlbumResponse updateAlbum(UUID albumId, AlbumCreateRequest request) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "album not found"));
        if (StringUtils.hasText(request.getName())) {
            album.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            album.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        }
        if (request.getVisibility() != null) {
            album.setVisibility(request.getVisibility());
        }
        if (request.getSortOrder() != null) {
            album.setSortOrder(request.getSortOrder());
        }
        return toResponse(albumRepository.save(album), albumPictureRepository.countByAlbumId(albumId));
    }

    @Transactional
    public void deleteAlbum(UUID albumId) {
        if (!albumRepository.existsById(albumId)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "album not found");
        }
        albumRepository.deleteById(albumId);
    }

    @Transactional
    public void addPictures(UUID albumId, List<UUID> pictureIds) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "album not found"));
        int nextOrder = albumPictureRepository.countByAlbumId(albumId);
        for (UUID pictureId : pictureIds) {
            if (albumPictureRepository.findByAlbumIdAndPictureId(albumId, pictureId).isEmpty()) {
                albumPictureRepository.save(AlbumPicture.builder()
                        .albumId(albumId).pictureId(pictureId).sortOrder(nextOrder++).build());
            }
        }
    }

    @Transactional
    public void removePicture(UUID albumId, UUID pictureId) {
        albumPictureRepository.deleteByAlbumIdAndPictureId(albumId, pictureId);
    }

    @Transactional
    public void setCover(UUID albumId, UUID pictureId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "album not found"));
        album.setCoverPictureId(pictureId);
        albumRepository.save(album);
    }

    public List<UUID> listAlbumPictureIds(UUID albumId) {
        return albumPictureRepository.findByAlbumIdOrderBySortOrderAsc(albumId).stream()
                .map(AlbumPicture::getPictureId)
                .toList();
    }

    private AlbumResponse toResponse(Album album, int pictureCount) {
        return AlbumResponse.builder()
                .id(album.getId()).spaceId(album.getSpaceId()).name(album.getName())
                .description(album.getDescription()).coverPictureId(album.getCoverPictureId())
                .visibility(album.getVisibility()).sortOrder(album.getSortOrder())
                .pictureCount(pictureCount)
                .createdAt(album.getCreatedAt()).updatedAt(album.getUpdatedAt()).build();
    }
}
