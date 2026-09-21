/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.domain;

import com.opsflow.imports.ImportJobId;
import java.io.InputStream;

/**
 * Where uploaded files live. Two implementations exist today — local disk and an in-memory one
 * the batch tests use — which is what makes this a seam rather than a guess about the future.
 */
public interface ImportFileStore {

    /** Streams the upload to storage, hashing and counting lines in the same pass. */
    StoredFile store(ImportJobId jobId, InputStream content);

    InputStream open(String ref);

    void delete(String ref);
}
