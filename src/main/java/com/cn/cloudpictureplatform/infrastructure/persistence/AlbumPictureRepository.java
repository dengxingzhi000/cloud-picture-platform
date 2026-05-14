package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cn.cloudpictureplatform.domain.album.AlbumPicture;

public interface AlbumPictureRepository extends JpaRepository<AlbumPicture, UUID> {
    List<AlbumPicture> findByAlbumIdOrderBySortOrderAsc(UUID albumId);

    List<AlbumPicture> findByPictureId(UUID pictureId);

    Optional<AlbumPicture> findByAlbumIdAndPictureId(UUID albumId, UUID pictureId);

    int countByAlbumId(UUID albumId);

    void deleteByAlbumIdAndPictureId(UUID albumId, UUID pictureId);
}
