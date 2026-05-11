package com.maxjang.sleepstyle.sleepstyle;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SleepSummary {

	private int days;
	private double averageAhi;
	private double averageLeak;
	private double averageHours;
	private double complianceRate;
	private int highLeakDays;
	private int lowUsageDays;

	/**
	 * 지표 목록 기반 요약 생성
	 *
	 * @param metrics 지표 목록
	 * @return 수면 요약
	 */
	public static SleepSummary from(Iterable<SleepMetric> metrics) {
		int days = 0;
		int compliantDays = 0;
		int highLeakDays = 0;
		int lowUsageDays = 0;
		double ahi = 0;
		double leak = 0;
		double hours = 0;

		for (SleepMetric metric : metrics) {
			days++;
			ahi += metric.getAveAhi();
			leak += metric.getAverageLeak();
			hours += metric.getTotalHours();
			if (metric.getTotalHours() >= 4.0 && !metric.isLowCompliedTime()) {
				compliantDays++;
			}
			if (metric.isHighLeak()) {
				highLeakDays++;
			}
			if (metric.isLowCompliedTime()) {
				lowUsageDays++;
			}
		}

		if (days == 0) {
			return new SleepSummary(0, 0, 0, 0, 0, 0, 0);
		}

		return new SleepSummary(
				days,
				round1(ahi / days),
				round1(leak / days),
				round1(hours / days),
				round1(compliantDays * 100.0 / days),
				highLeakDays,
				lowUsageDays);
	}

	/**
	 * 소수점 한 자리 반올림
	 *
	 * @param value 원본 값
	 * @return 반올림 값
	 */
	private static double round1(double value) {
		return Math.round(value * 10.0) / 10.0;
	}
}
