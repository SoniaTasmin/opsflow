/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.api;

import com.opsflow.identity.application.UserSearchService;
import com.opsflow.platform.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Tag(name = "Users", description = "Provisioned users")
public class UserController {

    private final UserSearchService users;

    public UserController(UserSearchService users) {
        this.users = users;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            operationId = "searchUsers",
            summary = "Search provisioned users",
            description = "Matches username and display name, case-insensitively. Users appear here once they "
                    + "have authenticated at least once.")
    public PageResponse<UserResponse> search(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        UserSearchService.Result result = users.search(q, page, size);
        return PageResponse.of(
                result.content().stream().map(UserResponse::from).toList(), page, size, result.totalElements());
    }
}
