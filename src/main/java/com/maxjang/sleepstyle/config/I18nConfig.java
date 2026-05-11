package com.maxjang.sleepstyle.config;

import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

@Configuration
public class I18nConfig implements WebMvcConfigurer {

	/**
	 * 기본 영어 로케일 쿠키 해석기 생성
	 *
	 * @return 로케일 해석기
	 */
	@Bean
	public LocaleResolver localeResolver() {
		CookieLocaleResolver resolver = new CookieLocaleResolver("sleepstyle-locale");
		resolver.setDefaultLocale(Locale.ENGLISH);
		resolver.setDefaultLocaleFunction(request -> Locale.ENGLISH);
		return resolver;
	}

	/**
	 * lang 파라미터 기반 로케일 변경 인터셉터 생성
	 *
	 * @return 로케일 변경 인터셉터
	 */
	@Bean
	public LocaleChangeInterceptor localeChangeInterceptor() {
		LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
		interceptor.setParamName("lang");
		return interceptor;
	}

	/**
	 * MVC 인터셉터 등록
	 *
	 * @param registry 인터셉터 레지스트리
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(localeChangeInterceptor());
	}
}
