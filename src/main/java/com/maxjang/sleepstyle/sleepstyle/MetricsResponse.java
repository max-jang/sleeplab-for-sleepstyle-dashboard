package com.maxjang.sleepstyle.sleepstyle;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MetricsResponse {

	private List<SleepMetric> metrics;
	private SleepSummary summary;
	private String source;
	private Instant refreshedAt;
}
