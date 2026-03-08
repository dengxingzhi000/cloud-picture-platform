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
import com.cn.cloudpictureplatform.application.team.TeamService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.domain.team.TeamMemberStatus;
import com.cn.cloudpictureplatform.domain.team.TeamMemberEventType;
import com.cn.cloudpictureplatform.domain.team.TeamRole;
import com.cn.cloudpictureplatform.infrastructure.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.interfaces.team.dto.TeamCreateRequest;
import com.cn.cloudpictureplatform.interfaces.team.dto.TeamInviteRequest;
import com.cn.cloudpictureplatform.interfaces.team.dto.TeamInviteSummaryResponse;
import com.cn.cloudpictureplatform.interfaces.team.dto.TeamMemberEventResponse;
import com.cn.cloudpictureplatform.interfaces.team.dto.TeamMemberResponse;
import com.cn.cloudpictureplatform.interfaces.team.dto.TeamResponse;
import com.cn.cloudpictureplatform.interfaces.team.dto.TeamRoleUpdateRequest;
import com.cn.cloudpictureplatform.interfaces.team.dto.TeamSummaryResponse;
import com.cn.cloudpictureplatform.interfaces.team.dto.TeamUpdateRequest;

@Validated
@RestController
@RequestMapping("/api/teams")
public class TeamController {
    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    public ApiResponse<TeamResponse> createTeam(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody TeamCreateRequest request
    ) {
        return ApiResponse.ok(teamService.createTeam(principal.getId(), request));
    }

    @GetMapping
    public ApiResponse<List<TeamSummaryResponse>> listMyTeams(
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(teamService.listMyTeams(principal.getId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<TeamResponse> getTeamDetail(
            @PathVariable("id") UUID teamId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(teamService.getTeamDetail(teamId, principal.getId()));
    }

    @PatchMapping("/{id}")
    public ApiResponse<TeamResponse> updateTeam(
            @PathVariable("id") UUID teamId,
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody TeamUpdateRequest request
    ) {
        return ApiResponse.ok(teamService.updateTeam(teamId, principal.getId(), request));
    }

    @GetMapping("/invites")
    public ApiResponse<List<TeamInviteSummaryResponse>> listMyInvites(
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(teamService.listMyInvites(principal.getId()));
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<TeamMemberResponse>> listMembers(
            @PathVariable("id") UUID teamId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(teamService.listMembers(teamId, principal.getId()));
    }

    @GetMapping("/{id}/invites")
    public ApiResponse<List<TeamMemberResponse>> listTeamInvites(
            @PathVariable("id") UUID teamId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(teamService.listTeamInvites(teamId, principal.getId()));
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
        return ApiResponse.ok(teamService.listInviteHistory(
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
        return ApiResponse.ok(teamService.listMemberEvents(
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
        List<TeamMemberEventResponse> events = teamService.exportMemberEvents(
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
        return ApiResponse.ok(teamService.listInviteCancelEvents(
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
        return ApiResponse.ok(teamService.inviteMember(teamId, principal.getId(), request));
    }

    @PostMapping("/{id}/accept")
    public ApiResponse<TeamMemberResponse> acceptInvite(
            @PathVariable("id") UUID teamId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(teamService.acceptInvite(teamId, principal.getId()));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Void> rejectInvite(
            @PathVariable("id") UUID teamId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        teamService.rejectInvite(teamId, principal.getId());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}/invites/{userId}")
    public ApiResponse<TeamMemberResponse> cancelInvite(
            @PathVariable("id") UUID teamId,
            @PathVariable("userId") UUID userId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(teamService.cancelInvite(teamId, principal.getId(), userId));
    }

    @PatchMapping("/{id}/members/{userId}/role")
    public ApiResponse<TeamMemberResponse> updateRole(
            @PathVariable("id") UUID teamId,
            @PathVariable("userId") UUID userId,
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody TeamRoleUpdateRequest request
    ) {
        return ApiResponse.ok(teamService.updateRole(teamId, principal.getId(), userId, request));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<Void> removeMember(
            @PathVariable("id") UUID teamId,
            @PathVariable("userId") UUID userId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        teamService.removeMember(teamId, principal.getId(), userId);
        return ApiResponse.ok(null);
    }

    private String toEventCsv(List<TeamMemberEventResponse> events) {
        StringBuilder builder = new StringBuilder();
        builder.append("id,teamId,type,role,userId,username,displayName,actorId,actorUsername,actorDisplayName,detail,createdAt\n");
        for (TeamMemberEventResponse event : events) {
            builder.append(escapeCsv(event.getId()))
                    .append(',')
                    .append(escapeCsv(event.getTeamId()))
                    .append(',')
                    .append(escapeCsv(event.getType()))
                    .append(',')
                    .append(escapeCsv(event.getRole()))
                    .append(',')
                    .append(escapeCsv(event.getUserId()))
                    .append(',')
                    .append(escapeCsv(event.getUsername()))
                    .append(',')
                    .append(escapeCsv(event.getDisplayName()))
                    .append(',')
                    .append(escapeCsv(event.getActorId()))
                    .append(',')
                    .append(escapeCsv(event.getActorUsername()))
                    .append(',')
                    .append(escapeCsv(event.getActorDisplayName()))
                    .append(',')
                    .append(escapeCsv(event.getDetail()))
                    .append(',')
                    .append(escapeCsv(event.getCreatedAt()))
                    .append('\n');
        }
        return builder.toString();
    }

    private String escapeCsv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        boolean needsEscaping = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (needsEscaping) {
            text = text.replace("\"", "\"\"");
            return "\"" + text + "\"";
        }
        return text;
    }
}
