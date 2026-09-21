/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.infrastructure.storage;

import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.application.ImportProperties;
import com.opsflow.imports.domain.ImportFileStore;
import com.opsflow.imports.domain.StoredFile;
import com.opsflow.platform.error.BusinessRuleException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class LocalFileImportStore implements ImportFileStore {

    private static final int BUFFER = 8192;

    private final Path root;

    LocalFileImportStore(ImportProperties properties) {
        this.root = Path.of(properties.storageDirectory()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * One pass over the stream produces the file, its checksum and its row count: the content is
     * never held in memory, and reading it three times would be three times the I/O.
     */
    @Override
    public StoredFile store(ImportJobId jobId, InputStream content) {
        Path target = resolve(ref(jobId));
        MessageDigest digest = sha256();
        long bytes = 0;
        long newlines = 0;
        int lastByte = -1;

        try (DigestInputStream in = new DigestInputStream(content, digest);
                OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[BUFFER];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                bytes += read;
                for (int i = 0; i < read; i++) {
                    if (buffer[i] == '\n') {
                        newlines++;
                    }
                }
                lastByte = buffer[read - 1];
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        long lines = bytes == 0 ? 0 : newlines + (lastByte == '\n' ? 0 : 1);
        // The first line is the header, so it is not a row anyone can import.
        int dataLines = (int) Math.max(0, lines - 1);
        return new StoredFile(ref(jobId), HexFormat.of().formatHex(digest.digest()), bytes, dataLines);
    }

    @Override
    public InputStream open(String ref) {
        try {
            return Files.newInputStream(resolve(ref));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void delete(String ref) {
        try {
            Files.deleteIfExists(resolve(ref));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String ref(ImportJobId jobId) {
        return jobId.value() + ".csv";
    }

    /**
     * Storage names are generated from the job id, never from anything a client sent. This also
     * refuses a reference that would escape the directory, so a stored name can never be used to
     * read or write elsewhere.
     */
    private Path resolve(String ref) {
        Path candidate = root.resolve(ref).normalize();
        if (!candidate.startsWith(root)) {
            throw new BusinessRuleException(
                    "INVALID_STORAGE_REFERENCE", "Invalid storage reference", Map.of("ref", ref));
        }
        return candidate;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }
}
