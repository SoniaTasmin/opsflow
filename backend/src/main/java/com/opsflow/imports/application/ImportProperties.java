/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param storageDirectory where uploaded files are kept. Local disk, one replica — see ADR-0009
 * @param maxRows the largest file an import may carry, in data rows
 * @param chunkSize rows per batch chunk, and therefore per transaction
 * @param maxErrors the cap on recorded row errors; beyond it the file needs fixing, not reading
 * @param concurrency how many imports may run at once
 */
@ConfigurationProperties(prefix = "opsflow.imports")
public record ImportProperties(String storageDirectory, int maxRows, int chunkSize, int maxErrors, int concurrency) {}
