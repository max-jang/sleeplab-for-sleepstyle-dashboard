package com.maxjang.sleepstyle.sleepstyle;

public class LoginException extends RuntimeException {

	/**
	 * 로그인 예외 생성
	 *
	 * @param message 예외 메시지
	 */
	public LoginException(String message) {
		super(message);
	}

	/**
	 * 원인 포함 로그인 예외 생성
	 *
	 * @param message 예외 메시지
	 * @param cause 원인 예외
	 */
	public LoginException(String message, Throwable cause) {
		super(message, cause);
	}
}
