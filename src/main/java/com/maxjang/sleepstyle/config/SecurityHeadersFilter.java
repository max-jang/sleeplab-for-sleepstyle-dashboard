package com.maxjang.sleepstyle.config;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

	private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

	/**
	 * 보안 헤더와 CSRF 검증 처리
	 *
	 * @param request HTTP 요청
	 * @param response HTTP 응답
	 * @param filterChain 필터 체인
	 * @throws ServletException 서블릿 예외
	 * @throws IOException 입출력 예외
	 */
	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		applyHeaders(response);
		if (isStateChanging(request) && !isValidCsrfToken(request)) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token.");
			return;
		}
		filterChain.doFilter(request, response);
	}

	/**
	 * 기본 브라우저 보안 헤더 설정
	 *
	 * @param response HTTP 응답
	 */
	private void applyHeaders(HttpServletResponse response) {
		response.setHeader("Cache-Control", "no-store");
		response.setHeader("Pragma", "no-cache");
		response.setHeader("X-Content-Type-Options", "nosniff");
		response.setHeader("X-Frame-Options", "DENY");
		response.setHeader("Referrer-Policy", "no-referrer");
		response.setHeader(
				"Content-Security-Policy",
				"default-src 'self'; script-src 'self' blob:; script-src-elem 'self' blob:; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'");
	}

	/**
	 * 상태 변경 요청 여부 확인
	 *
	 * @param request HTTP 요청
	 * @return 상태 변경 요청 여부
	 */
	private boolean isStateChanging(HttpServletRequest request) {
		return !SAFE_METHODS.contains(request.getMethod());
	}

	/**
	 * CSRF 토큰 유효성 확인
	 *
	 * @param request HTTP 요청
	 * @return 유효성 여부
	 */
	private boolean isValidCsrfToken(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null) {
			return false;
		}
		Object expected = session.getAttribute(SecurityTokens.CSRF_SESSION_ATTRIBUTE);
		String actual = request.getParameter("_csrf");
		if (actual == null || actual.isBlank()) {
			actual = request.getHeader("X-CSRF-Token");
		}
		return expected instanceof String token && token.equals(actual);
	}
}
