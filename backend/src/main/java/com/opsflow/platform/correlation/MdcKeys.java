/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.correlation;

/** Keys placed into the SLF4J {@code MDC} and emitted as fields by structured logging. */
public final class MdcKeys {

    public static final String CORRELATION_ID = "correlationId";

    private MdcKeys() {}
}
