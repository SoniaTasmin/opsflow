/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SubmissionJpaRepository extends JpaRepository<SubmissionEntity, UUID> {}
