package io.github.danielnguyen327.ledgersync;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	/** An encryption key for tests only (the bytes 0 to 31). Real keys live in backend/.env, never in Git. */
	static final String TEST_TOKEN_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}

	@Bean
	DynamicPropertyRegistrar testSecrets() {
		return registry -> {
			registry.add("ledgersync.token-encryption-key", () -> TEST_TOKEN_KEY);
			registry.add("ledgersync.sync.cron", () -> "-"); // no automatic syncs during tests; tests start them
		};
	}

}
