package com.maxjang.sleepstyle.sleepstyle;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

@Component
public class SleepStyleProductClient {

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private final ObjectMapper objectMapper;
	private final SleepStyleProperties properties;

	/**
	 * SleepStyle 제품 클라이언트 생성
	 *
	 * @param objectMapper JSON 매퍼
	 * @param properties SleepStyle 설정
	 */
	public SleepStyleProductClient(ObjectMapper objectMapper, SleepStyleProperties properties) {
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	/**
	 * SleepStyle 제품 정보 조회
	 *
	 * @param session SleepStyle 세션
	 * @return 제품 정보
	 */
	public ProductInfo fetchProduct(SleepStyleSession session) {
		if (session == null || !session.hasCredentials()) {
			throw new IllegalStateException("SleepStyle login is required.");
		}

		URI uri = URI.create("%s/umbraco/api/productapi/getcpap?family=%s&model=%s&culture=%s".formatted(
				trimTrailingSlash(properties.getWebUrl()),
				encode(valueOrDefault(properties.getProductFamily(), "SleepStyle")),
				encode(valueOrDefault(properties.getProductModel(), "Auto")),
				encode(valueOrDefault(properties.getProductCulture(), "en-US"))));

		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(20))
				.header("accept", "*/*")
				.header("content-type", "application/json")
				.header("referer", trimTrailingSlash(properties.getWebUrl()) + "/MyCpap")
				.GET();

		if (hasText(session.getAccessToken())) {
			requestBuilder.header("authorization", "bearer " + session.getAccessToken());
		}
		if (hasText(session.getClientId())) {
			requestBuilder.header("fphcare-clientid", session.getClientId());
		}
		if (hasText(session.getCookieHeader())) {
			requestBuilder.header("cookie", session.getCookieHeader());
		}

		try {
			HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException("SleepStyle product API returned HTTP " + response.statusCode());
			}
			ProductResponse product = objectMapper.readValue(response.body(), ProductResponse.class);
			String modelName = modelName(product);
			return new ProductInfo(modelName, null);
		}
		catch (IOException ex) {
			throw new IllegalStateException("SleepStyle product API response could not be parsed.", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("SleepStyle product API request was interrupted.", ex);
		}
	}

	/**
	 * 표시용 모델명 생성
	 *
	 * @param product 제품 API 응답
	 * @return 표시용 모델명
	 */
	private String modelName(ProductResponse product) {
		String name = firstText(product.getName(), "SleepStyle");
		String model = firstText(product.getModel(), properties.getProductModel());
		if (!hasText(model)) {
			return name;
		}
		return name.contains(model) ? name : name + " " + model;
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
	 * 기본값 보정
	 *
	 * @param value 원본 값
	 * @param fallback 기본값
	 * @return 보정 값
	 */
	private static String valueOrDefault(String value, String fallback) {
		return hasText(value) ? value : fallback;
	}

	/**
	 * 첫 문자열 선택
	 *
	 * @param primary 우선 값
	 * @param fallback 대체 값
	 * @return 선택 값
	 */
	private static String firstText(String primary, String fallback) {
		return hasText(primary) ? primary : fallback;
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
