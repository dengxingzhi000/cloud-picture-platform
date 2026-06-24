package com.cn.cloudpictureplatform.application.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.domain.user.UserStatus;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.UserRoleRepository;
import com.cn.cloudpictureplatform.application.shared.dto.AdminUserSummary;

@Service
public class AdminUserService {

    private final AppUserRepository appUserRepository;
    private final UserRoleRepository userRoleRepository;

    public AdminUserService(
            AppUserRepository appUserRepository,
            UserRoleRepository userRoleRepository
    ) {
        this.appUserRepository = appUserRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional(readOnly = true)
    public Page<AdminUserSummary> listUsers(String keyword, String status, int page, int size) {
        Specification<AppUser> spec = buildSpec(keyword, status);
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AppUser> result = appUserRepository.findAll(spec, pageable);

        List<UUID> userIds = result.getContent().stream().map(AppUser::getId).toList();
        Map<UUID, Set<String>> rolesMap = buildRolesMap(userIds);

        List<AdminUserSummary> items = result.getContent().stream()
                .map(user -> new AdminUserSummary(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getDisplayName(),
                        user.getAvatarUrl(),
                        user.getStatus().name(),
                        rolesMap.getOrDefault(user.getId(), Set.of()),
                        user.getCreatedAt()
                ))
                .toList();

        return new PageImpl<>(items, pageable, result.getTotalElements());
    }

    private Map<UUID, Set<String>> buildRolesMap(List<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return userRoleRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.groupingBy(
                        ur -> ur.getUserId(),
                        Collectors.mapping(ur -> ur.getRole().getName(), Collectors.toSet())
                ));
    }

    private Specification<AppUser> buildSpec(String keyword, String status) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                Predicate usernameLike = builder.like(builder.lower(root.get("username")), pattern);
                Predicate emailLike = builder.like(builder.lower(root.get("email")), pattern);
                Predicate displayNameLike = builder.like(builder.lower(root.get("displayName")), pattern);
                predicates.add(builder.or(usernameLike, emailLike, displayNameLike));
            }
            if (StringUtils.hasText(status)) {
                try {
                    UserStatus userStatus = UserStatus.valueOf(status.toUpperCase());
                    predicates.add(builder.equal(root.get("status"), userStatus));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
