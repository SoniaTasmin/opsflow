/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.application;

import com.opsflow.organization.OrganizationId;
import com.opsflow.reporting.PeriodId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * What an idempotency key is compared against.
 *
 * <p>The file alone does not identify the operation: the same CSV sent against two periods is two
 * different imports that happen to share bytes. Covering the whole logical request is what stops
 * the second one silently returning the first one's job.
 */
final class RequestFingerprint {

    private RequestFingerprint() {}

    static String of(String fileChecksum, PeriodId reportingPeriodId, OrganizationId organizationId) {
        String material = String.join(
                "|",
                fileChecksum,
                reportingPeriodId.toString(),
                organizationId == null ? "ALL" : organizationId.toString());
        return hash(material);
    }

    private static String hash(String material) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }
}
