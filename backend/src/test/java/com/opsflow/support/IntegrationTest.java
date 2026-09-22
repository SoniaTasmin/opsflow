/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** A composed annotation rather than a base class, so tests inherit no behaviour to reason about. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest
@Import(PostgresContainerConfig.class)
@ActiveProfiles("test")
public @interface IntegrationTest {}
