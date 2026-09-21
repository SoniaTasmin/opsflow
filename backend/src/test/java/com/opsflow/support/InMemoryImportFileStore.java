/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.support;

import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.domain.ImportFileStore;
import com.opsflow.imports.domain.StoredFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The second implementation of {@link ImportFileStore}, and the reason it is a port at all:
 * integration tests exercise the whole import path without a filesystem to clean up between runs.
 */
public class InMemoryImportFileStore implements ImportFileStore {

    private final Map<String, byte[]> files = new ConcurrentHashMap<>();

    @Override
    public StoredFile store(ImportJobId jobId, InputStream content) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (content) {
            content.transferTo(buffer);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        byte[] bytes = buffer.toByteArray();
        String ref = jobId.value() + ".csv";
        files.put(ref, bytes);

        long newlines = 0;
        for (byte value : bytes) {
            if (value == '\n') {
                newlines++;
            }
        }
        long lines = bytes.length == 0 ? 0 : newlines + (bytes[bytes.length - 1] == '\n' ? 0 : 1);
        return new StoredFile(ref, sha256(bytes), bytes.length, (int) Math.max(0, lines - 1));
    }

    @Override
    public InputStream open(String ref) {
        byte[] bytes = files.get(ref);
        if (bytes == null) {
            throw new IllegalStateException("No stored file " + ref);
        }
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public void delete(String ref) {
        files.remove(ref);
    }

    public void clear() {
        files.clear();
    }

    /** How many files are held, so a test can prove a losing request cleaned up after itself. */
    public int size() {
        return files.size();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
