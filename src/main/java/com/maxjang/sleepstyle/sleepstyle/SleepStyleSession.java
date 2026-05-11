package com.maxjang.sleepstyle.sleepstyle;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SleepStyleSession implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public static final String SESSION_ATTRIBUTE = "sleepStyleSession";

	private String email;
	private String cookieHeader;
	private String accessToken;
	private String clientId;
	private String metricId;
	private String serialNumber;
	private String patientKnowledge;
	private Instant authenticatedAt;

	/**
	 * API 자격 보유 여부 확인
	 *
	 * @return 자격 보유 여부
	 */
	public boolean hasCredentials() {
		return hasText(accessToken) || hasText(cookieHeader);
	}

	/**
	 * 지표 API 식별자 보유 여부 확인
	 *
	 * @return 지표 API 식별자 보유 여부
	 */
	public boolean hasMetricIdentifiers() {
		return hasText(metricId) && hasText(serialNumber) && hasText(patientKnowledge);
	}

	/**
	 * 마스킹 이메일 반환
	 *
	 * @return 표시용 이메일
	 */
	public String displayEmail() {
		if (!hasText(email)) {
			return "";
		}
		int at = email.indexOf('@');
		if (at <= 1) {
			return email;
		}
		return email.charAt(0) + "***" + email.substring(at);
	}

	/**
	 * 문자열 존재 여부 확인
	 *
	 * @param value 대상 문자열
	 * @return 존재 여부
	 */
	static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
