/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

import java.util.Map;

/** A well-formed request a rule refuses: 422, with a module-supplied {@code ruleCode}. */
public final class BusinessRuleException extends DomainException {

    private final String ruleCode;

    public BusinessRuleException(String ruleCode, String message) {
        this(ruleCode, message, Map.of());
    }

    public BusinessRuleException(String ruleCode, String message, Map<String, Object> context) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message, context);
        this.ruleCode = ruleCode;
    }

    /** Reported as {@code code} in place of the enum constant; clients switch on it. */
    public String ruleCode() {
        return ruleCode;
    }
}
