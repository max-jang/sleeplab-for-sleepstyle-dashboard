package com.maxjang.sleepstyle.sleepstyle;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

@Component
public class SleepStyleClient {

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private final ObjectMapper objectMapper;
	private final SleepStyleProperties properties;

	/**
	 * SleepStyle 지표 클라이언트 생성
	 *
	 * @param objectMapper JSON 매퍼
	 * @param properties SleepStyle 설정
	 */
	public SleepStyleClient(ObjectMapper objectMapper, SleepStyleProperties properties) {
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	/**
	 * 지표 조회 가능 여부 확인
	 *
	 * @param session SleepStyle 세션
	 * @return 조회 가능 여부
	 */
	public boolean canFetch(SleepStyleSession session) {
		return hasMetricIdentifiers(session)
				&& hasCredentials(session);
	}

	/**
	 * 로그인 자격 보유 여부 확인
	 *
	 * @param session SleepStyle 세션
	 * @return 자격 보유 여부
	 */
	public boolean hasCredentials(SleepStyleSession session) {
		return session != null && session.hasCredentials();
	}

	/**
	 * 지표 API 식별자 보유 여부 확인
	 *
	 * @param session SleepStyle 세션
	 * @return 지표 API 식별자 보유 여부
	 */
	public boolean hasMetricIdentifiers(SleepStyleSession session) {
		return hasText(metricId(session))
				&& hasText(serialNumber(session))
				&& hasText(patientKnowledge(session));
	}

	/**
	 * 누락된 지표 API 설정 설명 생성
	 *
	 * @param session SleepStyle 세션
	 * @return 누락 설정 설명
	 */
	public String missingMetricSettings(SleepStyleSession session) {
		StringBuilder missing = new StringBuilder();
		if (!hasText(metricId(session))) {
			missing.append("metricId");
		}
		if (!hasText(serialNumber(session))) {
			if (missing.length() > 0) {
				missing.append(", ");
			}
			missing.append("serialNumber");
		}
		if (!hasText(patientKnowledge(session))) {
			if (missing.length() > 0) {
				missing.append(", ");
			}
			missing.append("patientKnowledge");
		}
		return missing.toString();
	}

	/**
	 * SleepStyle 일별 지표 조회
	 *
	 * @param since 시작일
	 * @param endDate 종료일
	 * @param session SleepStyle 세션
	 * @return 일별 지표 목록
	 */
	public List<SleepMetric> fetchMetrics(LocalDate since, LocalDate endDate, SleepStyleSession session) {
		if (!canFetch(session)) {
			throw new IllegalStateException("SleepStyle API settings are incomplete.");
		}

		URI uri = URI.create("%s/data/metric/%s?serialNumber=%s&utcoffset=%s&since=%s&endDate=%s".formatted(
				trimTrailingSlash(properties.getBaseUrl()),
				encode(metricId(session)),
				encode(serialNumber(session)),
				properties.getUtcOffset(),
				since,
				endDate));

		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(20))
				.header("accept", "*/*")
				.header("content-type", "application/json")
				.header("origin", properties.getOrigin())
				.header("referer", properties.getOrigin() + "/")
				.GET();

		String accessToken = session == null ? null : session.getAccessToken();
		String clientId = session == null ? null : session.getClientId();
		if (hasText(accessToken)) {
			requestBuilder.header("authorization", "bearer " + accessToken);
		}
		if (hasText(clientId)) {
			requestBuilder.header("fphcare-clientid", clientId);
		}
		String patientKnowledge = patientKnowledge(session);
		if (hasText(patientKnowledge)) {
			requestBuilder.header("fphcare-patientknowledge", patientKnowledge);
		}
		if (session != null && hasText(session.getCookieHeader())) {
			requestBuilder.header("cookie", session.getCookieHeader());
		}

		HttpRequest request = requestBuilder.build();

		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException("SleepStyle API returned HTTP " + response.statusCode());
			}
			return Arrays.asList(objectMapper.readValue(response.body(), SleepMetric[].class));
		}
		catch (IOException ex) {
			throw new IllegalStateException("SleepStyle API response could not be parsed.", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("SleepStyle API request was interrupted.", ex);
		}
	}

	/**
	 * 지표 ID 선택
	 *
	 * @param session SleepStyle 세션
	 * @return 지표 ID
	 */
	private String metricId(SleepStyleSession session) {
		return hasText(session == null ? null : session.getMetricId()) ? session.getMetricId() : properties.getMetricId();
	}

	/**
	 * 기기 serial number 선택
	 *
	 * @param session SleepStyle 세션
	 * @return 기기 serial number
	 */
	private String serialNumber(SleepStyleSession session) {
		return hasText(session == null ? null : session.getSerialNumber()) ? session.getSerialNumber() : properties.getSerialNumber();
	}

	/**
	 * patient knowledge 값 선택
	 *
	 * @param session SleepStyle 세션
	 * @return patient knowledge 값
	 */
	private String patientKnowledge(SleepStyleSession session) {
		return hasText(session == null ? null : session.getPatientKnowledge()) ? session.getPatientKnowledge() : properties.getPatientKnowledge();
	}

	/**
	 * 끝 슬래시 제거
	 *
	 * @param value 대상 문자열
	 * @return 정규화 문자열
	 */
	private static String trimTrailingSlash(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	/**
	 * URL 파라미터 인코딩
	 *
	 * @param value 원본 값
	 * @return 인코딩 값
	 */
	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	/**
	 * 문자열 존재 여부 확인
	 *
	 * @param value 대상 문자열
	 * @return 존재 여부
	 */
	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
