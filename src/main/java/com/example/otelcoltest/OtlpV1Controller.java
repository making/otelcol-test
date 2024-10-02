package com.example.otelcoltest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.opentelemetry.proto.logs.v1.LogsData;
import io.opentelemetry.proto.metrics.v1.MetricsData;
import io.opentelemetry.proto.trace.v1.TracesData;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OtlpV1Controller {

	List<TracesData> tracesData = new CopyOnWriteArrayList<>();

	List<MetricsData> metricsData = new CopyOnWriteArrayList<>();

	List<LogsData> logsData = new CopyOnWriteArrayList<>();

	void reset() {
		this.tracesData.clear();
		this.metricsData.clear();
		this.logsData.clear();
	}

	@PostMapping(path = "/v1/traces", consumes = MediaType.APPLICATION_PROTOBUF_VALUE)
	public void traces(@RequestBody TracesData data) {
		this.tracesData.add(data);
	}

	@PostMapping(path = "/v1/metrics", consumes = MediaType.APPLICATION_PROTOBUF_VALUE)
	public void metrics(@RequestBody MetricsData data) {
		this.metricsData.add(data);
	}

	@PostMapping(path = "/v1/logs", consumes = MediaType.APPLICATION_PROTOBUF_VALUE)
	public void logs(@RequestBody LogsData data) {
		this.logsData.add(data);
	}

}
