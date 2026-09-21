/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.application;

import com.opsflow.identity.UserSummary;
import com.opsflow.submission.domain.StatusTransition;
import java.util.Optional;

public record TransitionEntry(StatusTransition transition, Optional<UserSummary> actor) {}
