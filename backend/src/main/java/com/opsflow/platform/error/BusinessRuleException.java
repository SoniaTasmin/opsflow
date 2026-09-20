/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

import java.util.Map;

/**
 * The request is well formed but a business rule rejects it: retrying it unchanged will always
 * fail. Maps to 422.
 *
 * <p>Carries its own {@link ErrorCode} because business rules are module-specific — the codes
 * are contributed by the modules that own the rules rather than enumerated centrally here.
 */
public final class BusinessRuleException extends DomainException {

    private final String ruleCode;

    public BusinessRuleException(String ruleCode, String message) {
        this(ruleCode, message, Map.of());
    }

    public BusinessRuleException(String ruleCode, String message, Map<String, Object> context) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message, context);
        this.ruleCode = ruleCode;
    }

    /**
     * The violated rule, in {@code SCREAMING_SNAKE_CASE}. This is what a client switches on and
     * what is reported as {@code code}, in place of the generic enum constant.
     */
    public String ruleCode() {
        return ruleCode;
    }
}
