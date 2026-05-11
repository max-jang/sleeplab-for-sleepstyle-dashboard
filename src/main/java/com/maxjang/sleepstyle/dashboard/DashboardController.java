package com.maxjang.sleepstyle.dashboard;

import java.time.LocalDate;

import com.maxjang.sleepstyle.config.SecurityTokens;
import com.maxjang.sleepstyle.sleepstyle.LoginException;
import com.maxjang.sleepstyle.sleepstyle.MetricsResponse;
import com.maxjang.sleepstyle.sleepstyle.ProductInfo;
import com.maxjang.sleepstyle.sleepstyle.SleepStyleClient;
import com.maxjang.sleepstyle.sleepstyle.SleepStyleLoginService;
import com.maxjang.sleepstyle.sleepstyle.SleepMetricService;
import com.maxjang.sleepstyle.sleepstyle.SleepStyleProductClient;
import com.maxjang.sleepstyle.sleepstyle.SleepStyleSession;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class DashboardController {

	private final SleepMetricService sleepMetricService;
	private final SleepStyleClient sleepStyleClient;
	private final SleepStyleLoginService loginService;
	private final SleepStyleProductClient productClient;

	/**
	 * 대시보드 컨트롤러 의존성 주입
	 *
	 * @param sleepMetricService 수면 지표 서비스
	 * @param sleepStyleClient SleepStyle 지표 클라이언트
	 * @param loginService SleepStyle 로그인 서비스
	 * @param productClient SleepStyle 제품 클라이언트
	 */
	public DashboardController(
			SleepMetricService sleepMetricService,
			SleepStyleClient sleepStyleClient,
			SleepStyleLoginService loginService,
			SleepStyleProductClient productClient) {
		this.sleepMetricService = sleepMetricService;
		this.sleepStyleClient = sleepStyleClient;
		this.loginService = loginService;
		this.productClient = productClient;
	}

	/**
	 * 화면 CSRF 토큰 제공
	 *
	 * @param session HTTP 세션
	 * @return CSRF 토큰
	 */
	@ModelAttribute("csrfToken")
	public String csrfToken(HttpSession session) {
		return SecurityTokens.csrfToken(session);
	}

	/**
	 * 대시보드 화면 진입 처리
	 *
	 * @param model 화면 모델
	 * @param session HTTP 세션
	 * @return 뷰 이름
	 */
	@GetMapping("/")
	public String dashboard(Model model, HttpSession session) {
		SleepStyleSession sleepStyleSession = sleepStyleSession(session);
		if (!sleepStyleClient.hasCredentials(sleepStyleSession)) {
			return "redirect:/auth/login";
		}
		LocalDate today = LocalDate.now();
		model.addAttribute("defaultSince", today.minusDays(14));
		model.addAttribute("defaultEndDate", today);
		model.addAttribute("loginEmail", sleepStyleSession == null ? "" : sleepStyleSession.displayEmail());
		return "dashboard";
	}

	/**
	 * 로그인 화면 진입 처리
	 *
	 * @param model 화면 모델
	 * @return 뷰 이름
	 */
	@GetMapping("/auth/login")
	public String login(Model model) {
		if (!model.containsAttribute("loginForm")) {
			model.addAttribute("loginForm", new LoginForm("", ""));
		}
		return "login";
	}

	/**
	 * SleepStyle 로그인 요청 처리
	 *
	 * @param loginForm 로그인 폼
	 * @param request HTTP 요청
	 * @param redirectAttributes 리다이렉트 속성
	 * @return 리다이렉트 경로
	 */
	@PostMapping("/auth/login")
	public String login(
			@ModelAttribute LoginForm loginForm,
			HttpServletRequest request,
			RedirectAttributes redirectAttributes) {
		try {
			SleepStyleSession sleepStyleSession = loginService.login(loginForm.getEmail(), loginForm.getPassword());
			HttpSession previousSession = request.getSession(false);
			if (previousSession != null) {
				previousSession.invalidate();
			}
			HttpSession session = request.getSession(true);
			SecurityTokens.rotateCsrfToken(session);
			session.setAttribute(SleepStyleSession.SESSION_ATTRIBUTE, sleepStyleSession);
			return "redirect:/";
		}
		catch (LoginException ex) {
			redirectAttributes.addFlashAttribute("loginError", ex.getMessage());
			redirectAttributes.addFlashAttribute("loginForm", new LoginForm(loginForm.getEmail(), ""));
			return "redirect:/auth/login";
		}
	}

	/**
	 * SleepStyle 세션 로그아웃 처리
	 *
	 * @param session HTTP 세션
	 * @return 리다이렉트 경로
	 */
	@PostMapping("/auth/logout")
	public String logout(HttpSession session) {
		session.invalidate();
		return "redirect:/auth/login";
	}

	/**
	 * 일별 치료 지표 조회 처리
	 *
	 * @param since 시작일
	 * @param endDate 종료일
	 * @param session HTTP 세션
	 * @return 지표 응답
	 */
	@ResponseBody
	@GetMapping("/api/metrics")
	public MetricsResponse metrics(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			HttpSession session) {
		SleepStyleSession sleepStyleSession = sleepStyleSession(session);
		if (!sleepStyleClient.hasCredentials(sleepStyleSession)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SleepStyle login is required.");
		}
		if (!sleepStyleClient.canFetch(sleepStyleSession)) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"SleepStyle metric settings are incomplete: " + sleepStyleClient.missingMetricSettings(sleepStyleSession));
		}
		LocalDate effectiveEndDate = endDate == null ? LocalDate.now() : endDate;
		LocalDate effectiveSince = since == null ? effectiveEndDate.minusDays(14) : since;
		return sleepMetricService.getMetrics(effectiveSince, effectiveEndDate, sleepStyleSession);
	}

	/**
	 * SleepStyle 제품 정보 조회 처리
	 *
	 * @param session HTTP 세션
	 * @return 제품 정보
	 */
	@ResponseBody
	@GetMapping("/api/product")
	public ProductInfo product(HttpSession session) {
		SleepStyleSession sleepStyleSession = sleepStyleSession(session);
		if (!sleepStyleClient.hasCredentials(sleepStyleSession)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SleepStyle login is required.");
		}
		return productClient.fetchProduct(sleepStyleSession);
	}

	/**
	 * 인증 상태 조회 처리
	 *
	 * @param session HTTP 세션
	 * @return 인증 상태
	 */
	@ResponseBody
	@GetMapping("/api/auth/status")
	public AuthStatus authStatus(HttpSession session) {
		SleepStyleSession sleepStyleSession = sleepStyleSession(session);
		return new AuthStatus(
				sleepStyleClient.hasCredentials(sleepStyleSession),
				sleepStyleClient.canFetch(sleepStyleSession),
				sleepStyleSession == null ? "" : sleepStyleSession.displayEmail());
	}

	/**
	 * API 상태 예외 응답 생성
	 *
	 * @param ex 상태 예외
	 * @return API 오류 응답
	 */
	@ResponseBody
	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiError> apiError(ResponseStatusException ex) {
		return ResponseEntity
				.status(ex.getStatusCode())
				.body(new ApiError(ex.getStatusCode().value(), ex.getReason()));
	}

	/**
	 * HTTP 세션의 SleepStyle 세션 추출
	 *
	 * @param session HTTP 세션
	 * @return SleepStyle 세션
	 */
	private SleepStyleSession sleepStyleSession(HttpSession session) {
		Object value = session.getAttribute(SleepStyleSession.SESSION_ATTRIBUTE);
		return value instanceof SleepStyleSession sleepStyleSession ? sleepStyleSession : null;
	}
}
