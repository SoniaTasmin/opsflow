/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.domain;

/** Why an import ended without completing. Reported to the user, so it says nothing internal. */
public enum ImportFailure {
    HEADER_MISMATCH("The file's header does not match the expected columns"),
    ROW_LIMIT_EXCEEDED("The file has more rows than an import may carry"),
    FILE_UNREADABLE("The file could not be read as CSV"),
    // The process died mid-import. Nothing is resumed: the applied rows stand and the
    // user re-uploads, which is safe because applying the same values converges.
    INTERRUPTED_BY_SHUTDOWN("The import was interrupted by an application shutdown"),
    PROCESSING_FAILED("The import stopped before it finished");

    private final String message;

    ImportFailure(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
