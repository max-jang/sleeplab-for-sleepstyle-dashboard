package com.maxjang.sleepstyle.sleepstyle;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class SleepStyleLoginService {

	private static final Pattern UFPRT_PATTERN = Pattern.compile(
			"name=['\"]ufprt['\"][^>]*value=['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
	private static final Pattern APP_ID_PATTERN = Pattern.compile(
			"window\\.fph\\.config\\.appId\\s*=\\s*['\"]([^'\"]+)['\"]");
	private static final List<Pattern> ACCESS_TOKEN_PATTERNS = List.of(
			Pattern.compile("access[_-]?token['\"]?\\s*[:=]\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE),
			Pattern.compile("authorization['\"]?\\s*[:=]\\s*['\"]bearer\\s+([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE),
			Pattern.compile("bearer\\s+([A-Za-z0-9._~+\\-/=]+)", Pattern.CASE_INSENSITIVE));
	private static final List<Pattern> METRIC_ID_PATTERNS = List.of(
			Pattern.compile("\"Patient\"\\s*:\\s*\\{\\s*\"Id\"\\s*:\\s*\"([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\"", Pattern.CASE_INSENSITIVE),
			Pattern.compile("\"Id\"\\s*:\\s*\"([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\"\\s*,\\s*\"FirstName\"", Pattern.CASE_INSENSITIVE),
			Pattern.compile("/data/metric/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})", Pattern.CASE_INSENSITIVE),
			Pattern.compile("\\\\/data\\\\/metric\\\\/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})", Pattern.CASE_INSENSITIVE),
			Pattern.compile("metricId['\"]?\\s*[:=]\\s*['\"]([0-9a-fA-F-]{36})['\"]", Pattern.CASE_INSENSITIVE),
			Pattern.compile("patientId['\"]?\\s*[:=]\\s*['\"]([0-9a-fA-F-]{36})['\"]", Pattern.CASE_INSENSITIVE));
	private static final List<Pattern> SERIAL_NUMBER_PATTERNS = List.of(
			Pattern.compile("serialNumber['\"]?\\s*[:=]\\s*['\"]([^'\"&\\s]+)['\"]", Pattern.CASE_INSENSITIVE),
			Pattern.compile("serialNumber=([^&'\"\\s]+)", Pattern.CASE_INSENSITIVE));
	private static final List<Pattern> PATIENT_KNOWLEDGE_PATTERNS = List.of(
			Pattern.compile("fphcare-patientknowledge['\"]?\\s*[:=]\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE),
			Pattern.compile("patientKnowledge['\"]?\\s*[:=]\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE),
			Pattern.compile("patientknowledge['\"]?\\s*[:=]\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE),
			Pattern.compile("dateOfBirth['\"]?\\s*[:=]\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE),
			Pattern.compile("birthDate['\"]?\\s*[:=]\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE),
			Pattern.compile("name=['\"](?:DateOfBirth|BirthDate|DOB)['\"][^>]*value=['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE),
			Pattern.compile("value=['\"]([^'\"]+)['\"][^>]*name=['\"](?:DateOfBirth|BirthDate|DOB)['\"]", Pattern.CASE_INSENSITIVE));

	private final SleepStyleProperties properties;

	/**
	 * SleepStyle 로그인 서비스 생성
	 *
	 * @param properties SleepStyle 설정
	 */
	public SleepStyleLoginService(SleepStyleProperties properties) {
		this.properties = properties;
	}

	/**
	 * SleepStyle 공식 로그인 처리
	 *
	 * @param email 이메일
	 * @param password 비밀번호
	 * @return SleepStyle 세션
	 */
	public SleepStyleSession login(String email, String password) {
		CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		HttpClient client = HttpClient.newBuilder()
				.cookieHandler(cookieManager)
				.connectTimeout(Duration.ofSeconds(12))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();

		try {
			URI loginUri = URI.create(trimTrailingSlash(properties.getWebUrl()) + "/login?ReturnUrl=%2fMyCpap");
			HttpResponse<String> loginPage = client.send(
					HttpRequest.newBuilder(loginUri)
							.timeout(Duration.ofSeconds(20))
							.header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
							.GET()
							.build(),
					HttpResponse.BodyHandlers.ofString());

			if (loginPage.statusCode() < 200 || loginPage.statusCode() >= 300) {
				throw new LoginException("SleepStyle login page returned HTTP " + loginPage.statusCode());
			}

			String ufprt = extract(loginPage.body(), UFPRT_PATTERN)
					.orElseThrow(() -> new LoginException("Could not find SleepStyle login token."));
			String clientId = extract(loginPage.body(), APP_ID_PATTERN)
					.orElseThrow(() -> new LoginException("Could not find SleepStyle client id."));

			String boundary = "----SleepStyleDashboard" + UUID.randomUUID().toString().replace("-", "");
			HttpRequest request = HttpRequest.newBuilder(loginUri)
					.timeout(Duration.ofSeconds(25))
					.header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
					.header("content-type", "multipart/form-data; boundary=" + boundary)
					.header("origin", properties.getWebUrl())
					.header("referer", loginUri.toString())
					.POST(HttpRequest.BodyPublishers.ofString(multipartBody(boundary, email, password, ufprt)))
					.build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 400) {
				throw new LoginException("SleepStyle login returned HTTP " + response.statusCode());
			}
			if (isLoginPage(response.uri(), response.body())) {
				throw new LoginException("SleepStyle login did not succeed. Check the email, password, and verification requirements.");
			}

			String discoveryBody = response.body()
					+ "\n" + fetchAuthenticatedPage(client, "/MyData")
					+ "\n" + fetchAuthenticatedPage(client, "/MyCpap")
					+ "\n" + fetchAuthenticatedPage(client, "/profile");
			String cookieHeader = cookieHeader(cookieManager);
			String accessToken = extractAccessToken(discoveryBody)
					.or(() -> accessTokenCookie(cookieManager))
					.orElse(null);
			if (!SleepStyleSession.hasText(cookieHeader) && !SleepStyleSession.hasText(accessToken)) {
				throw new LoginException("SleepStyle login succeeded, but no reusable cookie or token was found.");
			}

			return new SleepStyleSession(
					email,
					cookieHeader,
					accessToken,
					clientId,
					metricId(discoveryBody),
					serialNumber(discoveryBody),
					patientKnowledge(discoveryBody),
					Instant.now());
		}
		catch (IOException ex) {
			throw new LoginException("Could not reach SleepStyle login.", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new LoginException("SleepStyle login was interrupted.", ex);
		}
	}

	/**
	 * 인증 필요 화면 조회
	 *
	 * @param client HTTP 클라이언트
	 * @param path 화면 경로
	 * @return 화면 본문
	 * @throws IOException 통신 실패
	 * @throws InterruptedException 인터럽트
	 */
	private String fetchAuthenticatedPage(HttpClient client, String path) throws IOException, InterruptedException {
		URI uri = URI.create(trimTrailingSlash(properties.getWebUrl()) + path);
		HttpResponse<String> response = client.send(
				HttpRequest.newBuilder(uri)
						.timeout(Duration.ofSeconds(20))
						.header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
						.header("referer", properties.getWebUrl() + "/")
						.GET()
						.build(),
				HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 400 || isLoginPage(response.uri(), response.body())) {
			return "";
		}
		return response.body();
	}

	/**
	 * 로그인 화면 응답 여부 확인
	 *
	 * @param uri 응답 URI
	 * @param body 응답 본문
	 * @return 로그인 화면 여부
	 */
	private boolean isLoginPage(URI uri, String body) {
		String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase();
		return path.endsWith("/login") && body != null && body.contains("name=\"Password\"");
	}

	/**
	 * 응답 본문 access token 추출
	 *
	 * @param body 응답 본문
	 * @return access token
	 */
	private static Optional<String> extractAccessToken(String body) {
		for (Pattern pattern : ACCESS_TOKEN_PATTERNS) {
			Optional<String> value = extract(body, pattern);
			if (value.isPresent()) {
				return value;
			}
		}
		return Optional.empty();
	}

	/**
	 * 지표 ID 추출
	 *
	 * @param body 응답 본문
	 * @return 지표 ID
	 */
	private String metricId(String body) {
		return extractFirst(body, METRIC_ID_PATTERNS).orElse(properties.getMetricId());
	}

	/**
	 * 기기 serial number 추출
	 *
	 * @param body 응답 본문
	 * @return 기기 serial number
	 */
	private String serialNumber(String body) {
		return extractFirst(body, SERIAL_NUMBER_PATTERNS).orElse(properties.getSerialNumber());
	}

	/**
	 * patient knowledge 추출
	 *
	 * @param body 응답 본문
	 * @return patient knowledge
	 */
	private String patientKnowledge(String body) {
		return extractFirst(body, PATIENT_KNOWLEDGE_PATTERNS)
				.map(this::normalizeDate)
				.orElse(properties.getPatientKnowledge());
	}

	/**
	 * 날짜 문자열 정규화
	 *
	 * @param value 원본 날짜 문자열
	 * @return ISO 날짜 문자열
	 */
	private String normalizeDate(String value) {
		if (!SleepStyleSession.hasText(value)) {
			return value;
		}
		String trimmed = value.trim();
		if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}T.*")) {
			return trimmed.substring(0, 10);
		}
		List<DateTimeFormatter> formatters = List.of(
				DateTimeFormatter.ISO_LOCAL_DATE,
				DateTimeFormatter.ofPattern("M/d/yyyy"),
				DateTimeFormatter.ofPattern("M-d-yyyy"),
				DateTimeFormatter.ofPattern("d/M/yyyy"),
				DateTimeFormatter.ofPattern("d-M-yyyy"));
		for (DateTimeFormatter formatter : formatters) {
			try {
				return LocalDate.parse(trimmed, formatter).toString();
			}
			catch (DateTimeParseException ignored) {
				// 다음 날짜 형식 확인
			}
		}
		return trimmed;
	}

	/**
	 * 여러 정규식 기반 첫 값 추출
	 *
	 * @param body 대상 본문
	 * @param patterns 추출 패턴 목록
	 * @return 첫 추출 값
	 */
	private static Optional<String> extractFirst(String body, List<Pattern> patterns) {
		for (Pattern pattern : patterns) {
			Optional<String> value = extract(body, pattern);
			if (value.isPresent()) {
				return value;
			}
		}
		return Optional.empty();
	}

	/**
	 * 쿠키 기반 access token 추출
	 *
	 * @param cookieManager 쿠키 매니저
	 * @return access token
	 */
	private static Optional<String> accessTokenCookie(CookieManager cookieManager) {
		for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
			if ("access-token".equalsIgnoreCase(cookie.getName())) {
				return Optional.of(stripBearer(cookie.getValue()));
			}
		}
		return Optional.empty();
	}

	/**
	 * bearer 접두어 제거
	 *
	 * @param value 토큰 값
	 * @return 정규화 토큰 값
	 */
	private static String stripBearer(String value) {
		if (value == null) {
			return null;
		}
		return value.regionMatches(true, 0, "bearer ", 0, 7) ? value.substring(7) : value;
	}

	/**
	 * 정규식 기반 값 추출
	 *
	 * @param body 대상 본문
	 * @param pattern 추출 패턴
	 * @return 추출 값
	 */
	private static Optional<String> extract(String body, Pattern pattern) {
		if (body == null) {
			return Optional.empty();
		}
		Matcher matcher = pattern.matcher(body);
		return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
	}

	/**
	 * 요청용 Cookie 헤더 생성
	 *
	 * @param cookieManager 쿠키 매니저
	 * @return Cookie 헤더 값
	 */
	private static String cookieHeader(CookieManager cookieManager) {
		StringBuilder builder = new StringBuilder();
		for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
			if (builder.length() > 0) {
				builder.append("; ");
			}
			builder.append(cookie.getName()).append('=').append(cookie.getValue());
		}
		return builder.toString();
	}

	/**
	 * 로그인 multipart 본문 생성
	 *
	 * @param boundary multipart boundary
	 * @param email 이메일
	 * @param password 비밀번호
	 * @param ufprt 로그인 토큰
	 * @return multipart 본문
	 */
	private static String multipartBody(String boundary, String email, String password, String ufprt) {
		return part(boundary, "Email", email)
				+ part(boundary, "Password", password)
				+ part(boundary, "ufprt", ufprt)
				+ "--" + boundary + "--\r\n";
	}

	/**
	 * multipart 파트 생성
	 *
	 * @param boundary multipart boundary
	 * @param name 파트 이름
	 * @param value 파트 값
	 * @return multipart 파트
	 */
	private static String part(String boundary, String name, String value) {
		return "--" + boundary + "\r\n"
				+ "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
				+ value + "\r\n";
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
}
