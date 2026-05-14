package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cn.cloudpictureplatform.domain.album.Album;

public interface AlbumRepository extends JpaRepository<Album, UUID> {
    List<Album> findBySpaceIdOrderBySortOrderAsc(UUID spaceId);
}
