/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.domain;

import com.opsflow.imports.ImportJobId;
import java.io.InputStream;

/** Two implementations exist today — local disk and an in-memory one the tests use. */
public interface ImportFileStore {

    StoredFile store(ImportJobId jobId, InputStream content);

    InputStream open(String ref);

    void delete(String ref);
}
