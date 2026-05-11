package com.maxjang.sleepstyle.sleepstyle;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

	@JsonProperty("Family")
	private String family;

	@JsonProperty("Model")
	private String model;

	@JsonProperty("Name")
	private String name;
}
