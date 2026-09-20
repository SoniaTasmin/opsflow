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

/**
 * Marks a test that needs the full application context and a real PostgreSQL.
 *
 * <p>A composed annotation rather than an abstract base class: test classes stay free to extend
 * whatever they need, and there is no inherited behaviour to reason about — consistent with the
 * project's rule against base classes that exist only to share configuration.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest
@Import(PostgresContainerConfig.class)
@ActiveProfiles("test")
public @interface IntegrationTest {}
