package com.maxjang.sleepstyle.sleepstyle;

import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SleepMetric {

	@JsonAlias("Date")
	private LocalDate date;

	@JsonAlias("StartTime")
	private Instant startTime;

	@JsonAlias("EndTime")
	private Instant endTime;

	@JsonAlias("AverageLeak")
	private double averageLeak;

	@JsonAlias("Humidity")
	private double humidity;

	@JsonAlias("AveAhi")
	private double aveAhi;

	@JsonAlias("TotalHours")
	private double totalHours;

	@JsonAlias("IsHighLeak")
	private boolean highLeak;

	@JsonAlias("IsLowCompliedTime")
	private boolean lowCompliedTime;
}
