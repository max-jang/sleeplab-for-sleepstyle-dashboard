package com.maxjang.sleepstyle.sleepstyle;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "sleepstyle.api")
public class SleepStyleProperties {

	private String baseUrl;
	private String webUrl;
	private String metricId;
	private String serialNumber;
	private String productFamily;
	private String productModel;
	private String productCulture;
	private Integer utcOffset;
	private String patientKnowledge;
	private String origin;

	/**
	 * 문자열 존재 여부 확인
	 *
	 * @param value 대상 문자열
	 * @return 존재 여부
	 */
	public static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
