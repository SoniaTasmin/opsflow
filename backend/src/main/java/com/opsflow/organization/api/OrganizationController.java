/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.api;

import com.opsflow.identity.Actor;
import com.opsflow.identity.CurrentActorProvider;
import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationCode;
import com.opsflow.organization.OrganizationId;
import com.opsflow.organization.api.dto.CreateOrganizationRequest;
import com.opsflow.organization.api.dto.MemberResponse;
import com.opsflow.organization.api.dto.OrganizationResponse;
import com.opsflow.organization.api.dto.UpdateOrganizationRequest;
import com.opsflow.organization.application.OrganizationCommandService;
import com.opsflow.organization.application.OrganizationQueryService;
import com.opsflow.organization.domain.OrganizationPage;
import com.opsflow.organization.domain.OrganizationStatus;
import com.opsflow.platform.web.PageResponse;
import com.opsflow.platform.web.VersionedRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/organizations", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Tag(name = "Organizations", description = "Organizations and their membership")
public class OrganizationController {

    private final OrganizationCommandService commands;
    private final OrganizationQueryService queries;
    private final CurrentActorProvider currentActor;

    public OrganizationController(
            OrganizationCommandService commands, OrganizationQueryService queries, CurrentActorProvider currentActor) {
        this.commands = commands;
        this.queries = queries;
        this.currentActor = currentActor;
    }

    @GetMapping
    @Operation(
            operationId = "listOrganizations",
            summary = "List organizations",
            description = "Administrators and reviewers see every organization; everyone else sees only "
                    + "the organizations they belong to.")
    public PageResponse<OrganizationResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) OrganizationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        Actor actor = currentActor.requireActor();
        OrganizationPage result = queries.search(actor, q, status, page, size);

        return PageResponse.of(
                result.content().stream().map(OrganizationResponse::from).toList(), page, size, result.totalElements());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getOrganization", summary = "Get one organization")
    public OrganizationResponse get(@PathVariable UUID id) {
        return OrganizationResponse.from(queries.requireVisible(currentActor.requireActor(), OrganizationId.of(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "createOrganization", summary = "Create an organization")
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody CreateOrganizationRequest request) {
        OrganizationResponse created =
                OrganizationResponse.from(commands.create(new OrganizationCode(request.code()), request.name()));
        return ResponseEntity.created(URI.create("/api/v1/organizations/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            operationId = "renameOrganization",
            summary = "Rename an organization",
            description = "The code cannot be changed.")
    public OrganizationResponse rename(@PathVariable UUID id, @Valid @RequestBody UpdateOrganizationRequest request) {
        return OrganizationResponse.from(commands.rename(OrganizationId.of(id), request.name(), request.version()));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            operationId = "archiveOrganization",
            summary = "Archive an organization",
            description = "Stops future participation and leaves history intact. There is no delete.")
    public OrganizationResponse archive(@PathVariable UUID id, @Valid @RequestBody VersionedRequest request) {
        return OrganizationResponse.from(commands.archive(OrganizationId.of(id), request.version()));
    }

    @GetMapping("/{id}/members")
    @Operation(operationId = "listMembers", summary = "List the members of an organization")
    public List<MemberResponse> members(@PathVariable UUID id) {
        return queries.membersOf(currentActor.requireActor(), OrganizationId.of(id)).stream()
                .map(MemberResponse::from)
                .toList();
    }

    @PutMapping("/{id}/members/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            operationId = "addMember",
            summary = "Add a member",
            description = "Idempotent. Returns 201 when the membership was created, 204 when it already existed.")
    public ResponseEntity<Void> addMember(@PathVariable UUID id, @PathVariable UUID userId) {
        boolean created = commands.addMember(OrganizationId.of(id), UserId.of(userId));
        return created
                ? ResponseEntity.status(201).build()
                : ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            operationId = "removeMember",
            summary = "Remove a member",
            description = "Idempotent, and permitted on an archived organization.")
    public ResponseEntity<Void> removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        commands.removeMember(OrganizationId.of(id), UserId.of(userId));
        return ResponseEntity.noContent().build();
    }
}
