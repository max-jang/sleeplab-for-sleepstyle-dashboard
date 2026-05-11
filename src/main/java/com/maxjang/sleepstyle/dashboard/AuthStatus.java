package com.maxjang.sleepstyle.dashboard;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthStatus {

	private boolean authenticated;
	private boolean apiReady;
	private String email;
}
