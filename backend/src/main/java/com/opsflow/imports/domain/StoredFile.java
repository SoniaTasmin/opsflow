/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.domain;

/**
 * @param ref server-generated; the client's filename never forms part of it
 * @param lineCount data lines, header excluded
 */
public record StoredFile(String ref, String checksum, long sizeBytes, int lineCount) {}
