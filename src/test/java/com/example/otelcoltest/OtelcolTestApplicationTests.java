package com.example.otelcoltest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.LogsData;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.ScopeLogs;
import io.opentelemetry.proto.resource.v1.Resource;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.zalando.logbook.spring.LogbookClientHttpRequestInterceptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Testcontainers
class OtelcolTestApplicationTests {

	private static final String COLLECTOR_IMAGE = "ghcr.io/open-telemetry/opentelemetry-collector-releases/opentelemetry-collector-contrib:0.110.0";

	private static final int COLLECTOR_OTLP_HTTP_PORT = 4318;

	private static final int COLLECTOR_HEALTH_CHECK_PORT = 13133;

	private static final int port = getFreePort();

	static {
		// https://java.testcontainers.org/features/networking/#exposing-host-ports-to-the-container
		org.testcontainers.Testcontainers.exposeHostPorts(port);
	}

	@Container
	private static GenericContainer<?> collector = new GenericContainer<>(DockerImageName.parse(COLLECTOR_IMAGE))
			.withEnv("OTLP_EXPORTER_ENDPOINT", "http://host.testcontainers.internal:" + port)
			.withClasspathResourceMapping("otel-config.yaml", "/otel-config.yaml", BindMode.READ_ONLY)
			.withCommand("--config", "/otel-config.yaml")
			.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("otel-collector")))
			.withExposedPorts(COLLECTOR_OTLP_HTTP_PORT, COLLECTOR_HEALTH_CHECK_PORT)
			.waitingFor(Wait.forHttp("/").forPort(COLLECTOR_HEALTH_CHECK_PORT));

	@Autowired
	OtlpV1Controller otlpV1Controller;

	RestClient restClient;

	@DynamicPropertySource
	static void updateProperties(DynamicPropertyRegistry registry) {
		registry.add("server.port", () -> port);
	}

	@BeforeEach
	void beforeEach(@Autowired RestClient.Builder restClientBuilder,
			@Autowired LogbookClientHttpRequestInterceptor logbookClientHttpRequestInterceptor) {
		this.restClient = restClientBuilder
				.baseUrl("http://localhost:" + collector.getMappedPort(COLLECTOR_OTLP_HTTP_PORT))
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_PROTOBUF_VALUE)
				.requestInterceptor(logbookClientHttpRequestInterceptor)
				.build();
		this.otlpV1Controller.reset();
	}

	@Test
	void contextLoads() {
		String message = "2016-07-11T23:56:42.000+00:00 INFO [MySecretApp.com.Transaction.Manager]:Starting transaction for session -464410bf-37bf-475a-afc0-498e0199f008";
		ResponseEntity<Void> response = this.restClient.post()
				.uri("/v1/logs")
				// https://logz.io/blog/logstash-grok/
				.body(just(message))
				.retrieve()
				.toBodilessEntity();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		Awaitility.waitAtMost(Duration.ofSeconds(1)).untilAsserted(() -> {
			assertThat(this.otlpV1Controller.logsData).hasSize(1);
			assertThat(this.otlpV1Controller.logsData.get(0)).isEqualTo(LogsData.newBuilder()
					.addResourceLogs(ResourceLogs.newBuilder()
							.setResource(Resource.newBuilder())
							.addScopeLogs(ScopeLogs.newBuilder()
									.setScope(InstrumentationScope.newBuilder())
									.addLogRecords(LogRecord.newBuilder()
											.setSeverityText("INFO")
											.setTimeUnixNano(toNano("2016-07-11T23:56:42.000+00:00"))
											.setObservedTimeUnixNano(toNano("2016-07-11T23:56:42.000+00:00"))
											.addAttributes(KeyValue.newBuilder()
													.setKey("class")
													.setValue(AnyValue.newBuilder().setStringValue("MySecretApp.com.Transaction.Manager")))
											.setBody(AnyValue.newBuilder()
													.setStringValue(
															"Starting transaction for session -464410bf-37bf-475a-afc0-498e0199f008")))))
					.build());
		});
	}

	long toNano(String timestamp) {
		Instant instant = Instant.parse(timestamp);
		return instant.toEpochMilli() * 1_000_000 + instant.getNano();
	}

	static LogsData just(String message) {
		return LogsData.newBuilder()
				.addResourceLogs(ResourceLogs.newBuilder()
						.addScopeLogs(ScopeLogs.newBuilder()
								.addLogRecords(LogRecord.newBuilder().setBody(AnyValue.newBuilder().setStringValue(message)))))
				.build();
	}

	static int getFreePort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
		catch (IOException e) {
			throw new UncheckedIOException("Failed to find a free port", e);
		}
	}

}
