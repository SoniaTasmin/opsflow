/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.infrastructure.batch;

class HeaderMismatchException extends RuntimeException {

    HeaderMismatchException() {
        super("The file's header does not match the expected columns");
    }
}
