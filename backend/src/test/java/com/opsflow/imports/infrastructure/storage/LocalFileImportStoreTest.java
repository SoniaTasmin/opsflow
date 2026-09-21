/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.application.ImportProperties;
import com.opsflow.imports.domain.ImportFileStore;
import com.opsflow.imports.domain.StoredFile;
import com.opsflow.platform.error.BusinessRuleException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileImportStoreTest {

    private static final String CSV =
            """
            organization_code,metric_code,value,note
            ALPHA,HEADCOUNT,42,
            BETA,HEADCOUNT,7,busy quarter
            """;

    @TempDir
    Path directory;

    private ImportFileStore store() {
        return new LocalFileImportStore(new ImportProperties(directory.toString(), 100_000, 500, 5_000, 1));
    }

    @Test
    void storesTheFileAndReportsWhatItContains() throws IOException {
        ImportJobId jobId = ImportJobId.generate(Clock.systemUTC());

        StoredFile stored = store().store(jobId, new ByteArrayInputStream(CSV.getBytes(StandardCharsets.UTF_8)));

        assertThat(stored.sizeBytes()).isEqualTo(CSV.getBytes(StandardCharsets.UTF_8).length);
        assertThat(stored.checksum()).isEqualTo(sha256(CSV));
        // Three lines, one of which is the header.
        assertThat(stored.lineCount()).isEqualTo(2);
        assertThat(Files.readString(directory.resolve(stored.ref()))).isEqualTo(CSV);
    }

    @Test
    void namesTheStoredFileAfterTheJobRatherThanTheUpload() {
        ImportJobId jobId = ImportJobId.generate(Clock.systemUTC());

        StoredFile stored = store().store(jobId, new ByteArrayInputStream(CSV.getBytes(StandardCharsets.UTF_8)));

        assertThat(stored.ref()).isEqualTo(jobId.value() + ".csv");
    }

    @Test
    void countsAFinalLineThatHasNoNewline() {
        String withoutTrailingNewline = "organization_code,metric_code,value,note\nALPHA,HEADCOUNT,42,";

        StoredFile stored = store().store(
                        ImportJobId.generate(Clock.systemUTC()),
                        new ByteArrayInputStream(withoutTrailingNewline.getBytes(StandardCharsets.UTF_8)));

        assertThat(stored.lineCount()).isEqualTo(1);
    }

    @Test
    void readsBackWhatItStored() throws IOException {
        ImportFileStore store = store();
        StoredFile stored = store.store(
                ImportJobId.generate(Clock.systemUTC()),
                new ByteArrayInputStream(CSV.getBytes(StandardCharsets.UTF_8)));

        try (InputStream in = store.open(stored.ref())) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(CSV);
        }
    }

    @Test
    void refusesAReferenceThatWouldEscapeTheDirectory() {
        assertThatThrownBy(() -> store().open("../../etc/passwd")).isInstanceOf(BusinessRuleException.class);
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
