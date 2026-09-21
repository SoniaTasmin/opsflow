/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission;

import java.util.List;
import java.util.Optional;

public record ApplyValuesResult(
        SubmissionId submissionId, int appliedCount, List<RejectedValue> rejected, GroupRejection groupRejection) {

    public ApplyValuesResult {
        rejected = List.copyOf(rejected);
    }

    public static ApplyValuesResult rejectedGroup(String code, String message) {
        return new ApplyValuesResult(null, 0, List.of(), new GroupRejection(code, message));
    }

    public Optional<GroupRejection> rejection() {
        return Optional.ofNullable(groupRejection);
    }
}
