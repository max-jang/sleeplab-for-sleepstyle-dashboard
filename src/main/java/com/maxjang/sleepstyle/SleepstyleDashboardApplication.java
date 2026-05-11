package com.maxjang.sleepstyle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SleepstyleDashboardApplication {

	/**
	 * 애플리케이션 시작 처리
	 *
	 * @param args 실행 인자
	 */
	public static void main(String[] args) {
		SpringApplication.run(SleepstyleDashboardApplication.class, args);
	}

}
