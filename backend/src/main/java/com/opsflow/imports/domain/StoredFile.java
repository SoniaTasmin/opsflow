/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.domain;

/**
 * @param ref server-generated storage name; the client's filename never forms part of it
 * @param checksum SHA-256 of the bytes, computed while streaming
 * @param lineCount data lines, header excluded
 */
public record StoredFile(String ref, String checksum, long sizeBytes, int lineCount) {}
