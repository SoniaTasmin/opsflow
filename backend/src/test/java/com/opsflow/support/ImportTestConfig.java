/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.support;

import com.opsflow.imports.domain.ImportFileStore;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class ImportTestConfig {

    @Bean
    @Primary
    ImportFileStore inMemoryImportFileStore() {
        return new InMemoryImportFileStore();
    }
}
