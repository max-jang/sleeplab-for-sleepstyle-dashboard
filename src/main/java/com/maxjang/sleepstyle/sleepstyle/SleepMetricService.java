package com.maxjang.sleepstyle.sleepstyle;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class SleepMetricService {

	private final SleepStyleClient client;

	/**
	 * 수면 지표 서비스 생성
	 *
	 * @param client SleepStyle 지표 클라이언트
	 */
	public SleepMetricService(SleepStyleClient client) {
		this.client = client;
	}

	/**
	 * SleepStyle API 기반 지표 조회
	 *
	 * @param since 시작일
	 * @param endDate 종료일
	 * @param session SleepStyle 세션
	 * @return 지표 응답
	 */
	public MetricsResponse getMetrics(LocalDate since, LocalDate endDate, SleepStyleSession session) {
		List<SleepMetric> metrics = sorted(client.fetchMetrics(since, endDate, session));
		return new MetricsResponse(metrics, SleepSummary.from(metrics), "api", Instant.now());
	}

	/**
	 * 날짜 오름차순 지표 정렬
	 *
	 * @param metrics 지표 목록
	 * @return 정렬 지표 목록
	 */
	private static List<SleepMetric> sorted(List<SleepMetric> metrics) {
		return metrics.stream()
				.sorted(Comparator.comparing(SleepMetric::getDate))
				.toList();
	}
}
