package com.maxjang.sleepstyle.config;

import java.security.SecureRandom;
import java.util.Base64;

import jakarta.servlet.http.HttpSession;

public final class SecurityTokens {

	public static final String CSRF_SESSION_ATTRIBUTE = "csrfToken";

	private static final SecureRandom RANDOM = new SecureRandom();

	private SecurityTokens() {
	}

	/**
	 * 세션 CSRF 토큰 확보
	 *
	 * @param session HTTP 세션
	 * @return CSRF 토큰
	 */
	public static String csrfToken(HttpSession session) {
		Object value = session.getAttribute(CSRF_SESSION_ATTRIBUTE);
		if (value instanceof String token && !token.isBlank()) {
			return token;
		}
		String token = newToken();
		session.setAttribute(CSRF_SESSION_ATTRIBUTE, token);
		return token;
	}

	/**
	 * CSRF 토큰 재발급
	 *
	 * @param session HTTP 세션
	 * @return CSRF 토큰
	 */
	public static String rotateCsrfToken(HttpSession session) {
		String token = newToken();
		session.setAttribute(CSRF_SESSION_ATTRIBUTE, token);
		return token;
	}

	/**
	 * 새 보안 토큰 생성
	 *
	 * @return 보안 토큰
	 */
	private static String newToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
