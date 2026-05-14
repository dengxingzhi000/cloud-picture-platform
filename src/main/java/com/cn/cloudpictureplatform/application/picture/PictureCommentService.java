package com.cn.cloudpictureplatform.application.picture;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.domain.picture.PictureComment;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureCommentRepository;
import com.cn.cloudpictureplatform.interfaces.picture.dto.CommentCreateRequest;
import com.cn.cloudpictureplatform.interfaces.picture.dto.CommentResponse;

@Service
@Transactional(readOnly = true)
public class PictureCommentService {
    private final PictureCommentRepository pictureCommentRepository;
    private final AppUserRepository appUserRepository;

    public PictureCommentService(
            PictureCommentRepository pictureCommentRepository,
            AppUserRepository appUserRepository
    ) {
        this.pictureCommentRepository = pictureCommentRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public CommentResponse createComment(UUID pictureId, UUID authorId, CommentCreateRequest request) {
        PictureComment comment = PictureComment.builder()
                .pictureId(pictureId)
                .authorId(authorId)
                .content(request.getContent().trim())
                .parentId(request.getParentId())
                .x(request.getX())
                .y(request.getY())
                .resolved(false)
                .build();
        PictureComment saved = pictureCommentRepository.save(comment);
        return toResponse(saved, 0);
    }

    public List<CommentResponse> listComments(UUID pictureId) {
        List<PictureComment> topLevel = pictureCommentRepository.findByPictureIdOrderByCreatedAtAsc(pictureId)
                .stream().filter(c -> c.getParentId() == null).toList();
        List<UUID> authorIds = topLevel.stream().map(PictureComment::getAuthorId).distinct().toList();
        Map<UUID, AppUser> userMap = appUserRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        return topLevel.stream()
                .map(c -> toResponse(c, pictureCommentRepository.countByParentId(c.getId()),
                        userMap.get(c.getAuthorId())))
                .toList();
    }

    @Transactional
    public CommentResponse updateComment(UUID commentId, UUID userId, String content) {
        PictureComment comment = pictureCommentRepository.findById(commentId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "comment not found"));
        if (!userId.equals(comment.getAuthorId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "not the comment author");
        }
        comment.setContent(content.trim());
        return toResponse(pictureCommentRepository.save(comment),
                pictureCommentRepository.countByParentId(commentId));
    }

    @Transactional
    public void deleteComment(UUID commentId, UUID userId) {
        PictureComment comment = pictureCommentRepository.findById(commentId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "comment not found"));
        if (!userId.equals(comment.getAuthorId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "not the comment author");
        }
        pictureCommentRepository.delete(comment);
    }

    @Transactional
    public void resolveComment(UUID commentId, UUID userId) {
        PictureComment comment = pictureCommentRepository.findById(commentId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "comment not found"));
        if (!userId.equals(comment.getAuthorId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "not the comment author");
        }
        comment.setResolved(!comment.isResolved());
        pictureCommentRepository.save(comment);
    }

    private CommentResponse toResponse(PictureComment c, int replyCount) {
        return toResponse(c, replyCount, null);
    }

    private CommentResponse toResponse(PictureComment c, int replyCount, AppUser author) {
        return CommentResponse.builder()
                .id(c.getId()).pictureId(c.getPictureId()).authorId(c.getAuthorId())
                .authorUsername(author == null ? null : author.getUsername())
                .authorDisplayName(author == null ? null : author.getDisplayName())
                .content(c.getContent()).parentId(c.getParentId())
                .x(c.getX()).y(c.getY()).resolved(c.isResolved())
                .replyCount(replyCount)
                .createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt()).build();
    }
}
