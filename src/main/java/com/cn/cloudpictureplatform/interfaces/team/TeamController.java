package com.cn.cloudpictureplatform.interfaces.team;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.application.team.TeamCommandService;
import com.cn.cloudpictureplatform.application.team.TeamQueryService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.domain.team.TeamMemberStatus;
import com.cn.cloudpictureplatform.domain.team.TeamMemberEventType;
import com.cn.cloudpictureplatform.domain.team.TeamRole;
import com.cn.cloudpictureplatform.common.util.CsvUtil;
import com.cn.cloudpictureplatform.common.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.application.team.dto.TeamCreateRequest;
import com.cn.cloudpictureplatform.application.team.dto.TeamInviteRequest;
import com.cn.cloudpictureplatform.application.team.dto.TeamInviteSummaryResponse;
import com.cn.cloudpictureplatform.application.team.dto.TeamMemberEventResponse;
import com.cn.cloudpictureplatform.application.team.dto.TeamMemberResponse;
import com.cn.cloudpictureplatform.application.team.dto.TeamResponse;
import com.cn.cloudpictureplatform.application.team.dto.TeamRoleUpdateRequest;
import com.cn.cloudpictureplatform.application.team.dto.TeamSummaryResponse;
import com.cn.cloudpictureplatform.application.team.dto.TeamUpdateRequest;

@Validated
@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {
    private final TeamQueryService teamQueryService;
    private final TeamCommandService teamCommandService;

    public TeamController(TeamQueryService teamQueryService, TeamCommandService teamCommandService) {
        this.teamQueryService = teamQueryService;
        this.teamCommandService = teamCommandService;
    }

    @PostMapping
    public ApiResponse<TeamResponse> createTeam(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody TeamCreateRequest request
    ) {
        return ApiResponse.ok(teamCommandService.createTeam(principal.getId(), request));
    }

    @GetMapping
    public ApiResponse<List<TeamSummaryResponse>> listMyTeams(
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(teamQueryService.listMyTeams(principal.getId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<TeamResponse> getTeamDetail(
            @PathVariable("id") UUID teamId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(teamQueryService.getTeamDetail(teamId, principal.getId()));
    }

    @PatchMapping("/{id}")
    public ApiResponse<TeamResponse> updateTeam(
            @PathVariable("id") UUID teamId,
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody TeamUpdateRequest request
    ) {
        return ApiResponse.ok(teamCommandService.updateTeam(teamId, principal.getId(), request));
    }

    @GetMapping("/invites")
    public ApiResponse<List<TeamInviteSummaryResponse>> listMyInvites(
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(teamQueryService.listMyInvites(principal.getId()));
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<TeamMemberResponse>> listMembers(
            @PathVariable("id") UUID teamId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(teamQueryService.listMembers(teamId, principal.getId()));
    }

    @GetMapping("/{id}/invites")
    public ApiResponse<List<TeamMemberResponse>> listTeamInvites(
            @PathVariable("id") UUID teamId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(teamQueryService.listTeamInvites(teamId, principal.getId()));
    }

    @GetMapping("/{id}/invites/history")
    public ApiResponse<PageResponse<TeamMemberResponse>> listInviteHistory(
            @PathVariable("id") UUID teamId,
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) TeamMemberStatus status,
            @RequestParam(required = false) TeamRole role,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant invitedAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant invitedBefore,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant joinedAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant joinedBefore,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir
    ) {
        return ApiResponse.ok(teamQueryService.listInviteHistory(
                teamId,
                principal.getId(),
                page,
                size,
                status,
                role,
                invitedAfter,
                invitedBefore,
                joinedAfter,
                joinedBefore,
                sortBy,
                sortDir
        ));
    }

    @GetMapping("/{id}/events")
    public ApiResponse<PageResponse<TeamMemberEventResponse>> listMemberEvents(
            @PathVariable("id") UUID teamId,
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) TeamMemberEventType type,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdBefore,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir
    ) {
        return ApiResponse.ok(teamQueryService.listMemberEvents(
                teamId,
                principal.getId(),
                page,
                size,
                type,
                userId,
                actorId,
                createdAfter,
                createdBefore,
                sortBy,
                sortDir
        ));
    }

    @GetMapping("/{id}/events/export")
    public ResponseEntity<String> exportMemberEvents(
            @PathVariable("id") UUID teamId,
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) TeamMemberEventType type,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdBefore,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(defaultValue = "1000") int limit
    ) {
        List<TeamMemberEventResponse> events = teamQueryService.exportMemberEvents(
                teamId,
                principal.getId(),
                type,
                userId,
                actorId,
                createdAfter,
                createdBefore,
                sortBy,
                sortDir,
                limit
        );
        String csv = toEventCsv(events);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"team_member_events.csv\"");
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    @GetMapping("/{id}/invites/cancellations")
    public ApiResponse<PageResponse<TeamMemberEventResponse>> listInviteCancelEvents(
            @PathVariable("id") UUID teamId,
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdBefore,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir
    ) {
        return ApiResponse.ok(teamQueryService.listInviteCancelEvents(
                teamId,
                principal.getId(),
                page,
                size,
                userId,
                actorId,
                createdAfter,
                createdBefore,
                sortBy,
                sortDir
        ));
    }

    @PostMapping("/{id}/invites")
    public ApiResponse<TeamMemberResponse> inviteMember(
            @PathVariable("id") UUID teamId,
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody TeamInviteRequest request
    ) {
        return ApiResponse.ok(teamCommandService.inviteMember(teamId, principal.getId(), request));
    }

    @PostMapping("/{id}/accept")
    public ApiResponse<TeamMemberResponse> acceptInvite(
            @PathVariable("id") UUID teamId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(teamCommandService.acceptInvite(teamId, principal.getId()));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Void> rejectInvite(
            @PathVariable("id") UUID teamId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        teamCommandService.rejectInvite(teamId, principal.getId());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}/invites/{userId}")
    public ApiResponse<TeamMemberResponse> cancelInvite(
            @PathVariable("id") UUID teamId,
            @PathVariable("userId") UUID userId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(teamCommandService.cancelInvite(teamId, principal.getId(), userId));
    }

    @PatchMapping("/{id}/members/{userId}/role")
    public ApiResponse<TeamMemberResponse> updateRole(
            @PathVariable("id") UUID teamId,
            @PathVariable("userId") UUID userId,
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody TeamRoleUpdateRequest request
    ) {
        return ApiResponse.ok(teamCommandService.updateRole(teamId, principal.getId(), userId, request));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<Void> removeMember(
            @PathVariable("id") UUID teamId,
            @PathVariable("userId") UUID userId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        teamCommandService.removeMember(teamId, principal.getId(), userId);
        return ApiResponse.ok(null);
    }

    private String toEventCsv(List<TeamMemberEventResponse> events) {
        StringBuilder builder = new StringBuilder();
        builder.append("id,teamId,type,role,userId,username,displayName,actorId,actorUsername,actorDisplayName,detail,createdAt\n");
        for (TeamMemberEventResponse event : events) {
            builder.append(CsvUtil.escapeCsv(event.getId()))
                    .append(',')
                    .append(CsvUtil.escapeCsv(event.getTeamId()))
                    .append(',')
                    .append(CsvUtil.escapeCsv(event.getType()))
                    .append(',')
                    .append(CsvUtil.escapeCsv(event.getRole()))
                    .append(',')
                    .append(CsvUtil.escapeCsv(event.getUserId()))
                    .append(',')
                    .append(CsvUtil.escapeCsv(event.getUsername()))
                    .append(',')
                    .append(CsvUtil.escapeCsv(event.getDisplayName()))
                    .append(',')
                    .append(CsvUtil.escapeCsv(event.getActorId()))
                    .append(',')
                    .append(CsvUtil.escapeCsv(event.getActorUsername()))
                    .append(',')
                    .append(CsvUtil.escapeCsv(event.getActorDisplayName()))
                    .append(',')
                    .append(CsvUtil.escapeCsv(event.getDetail()))
                    .append(',')
                    .append(CsvUtil.escapeCsv(event.getCreatedAt()))
                    .append('\n');
        }
        return builder.toString();
    }

}
