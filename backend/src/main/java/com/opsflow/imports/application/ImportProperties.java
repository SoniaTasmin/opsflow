/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param storageDirectory local disk, one replica (ADR-0009)
 * @param chunkSize rows per chunk, and therefore per transaction
 * @param maxErrors beyond this the file needs fixing, not reading
 */
@ConfigurationProperties(prefix = "opsflow.imports")
public record ImportProperties(String storageDirectory, int maxRows, int chunkSize, int maxErrors, int concurrency) {}
