# SleepStyle Dashboard

![SleepStyle Dashboard screenshot](src/main/resources/static/image/screenshot.png)

A local dashboard for viewing Fisher & Paykel SleepStyle therapy data.

This project is built with Java 17, Spring Boot, Gradle, Thymeleaf, and Chart.js. It signs in through the official SleepStyle login flow and shows therapy metrics such as AHI, leak, humidity, usage hours, compliance, and Korea NHIS compliance-period progress.

## Features

- Login through the official SleepStyle flow
- No manually pasted bearer token
- Daily therapy summaries and trend charts
- Korea NHIS compliance-period helper card
- Light mode and dark mode
- English, Korean, Japanese, and Chinese UI
- Optional email-only remember feature
- Build output name: `sleepstyle-dashboard.jar`

## Requirements

- Java 17
- A valid SleepStyle account
- Network access to Fisher & Paykel SleepStyle services

## Run

```bash
./gradlew bootRun
```

Open:

[http://localhost:8080/auth/login](http://localhost:8080/auth/login)

## Build

```bash
./gradlew clean bootJar
```

Output:

```text
build/libs/sleepstyle-dashboard.jar
```

Run:

```bash
java -jar build/libs/sleepstyle-dashboard.jar
```

## Configuration

Most SleepStyle API values are discovered after login from official SleepStyle pages.

Optional:

```bash
export SLEEPSTYLE_UTC_OFFSET="9"
```

Application settings are in `src/main/resources/application.yml`. Translation files use Spring's standard `.properties` message bundle format.

## Korea NHIS Compliance Helper

The dashboard includes a helper card based on the public Korea NHIS CPAP compliance rule: during the first 90 days, compliance is calculated as passing when any consecutive 30-day window has at least 21 days with 4+ hours of use.

Source: [NHIS CPAP Q&A PDF](https://www.nhis.or.kr/static/html/wbma/c/wbmac0228_9.pdf)

This is only a helper calculation based on the data returned by SleepStyle. It is not an official NHIS decision.

## Privacy and Security

- Passwords are sent to the official SleepStyle login endpoint through this local Spring Boot app
- Passwords are not stored by this app
- SleepStyle session data is kept in the server-side HTTP session
- "Remember email" stores only the email address in the browser
- CSRF protection, no-store responses, SameSite session cookies, and basic browser security headers are enabled

This app is intended for local personal use. Do not expose it publicly without proper authentication, HTTPS, deployment hardening, logging policy, and dependency vulnerability scanning.

## Medical Data and Responsibility

This project is a personal dashboard viewer only. It is not a medical device, diagnostic tool, treatment guide, or replacement for professional medical care.

All medical and therapy information shown by this app comes from Fisher & Paykel SleepStyle services. This app does not measure, verify, correct, certify, or medically interpret that data.

This project is provided as-is. The author and maintainer are not responsible for data accuracy, missing data, API changes, service outages, medical interpretation, treatment decisions, device settings, or any consequence caused by using this dashboard.

Use of this app, interpretation of the data, and every decision made from it are entirely the user's responsibility. For medical questions or unexpected data, use the official SleepStyle service and contact a qualified healthcare professional.

## Affiliation

This is an independent project. It is not affiliated with, endorsed by, certified by, or supported by Fisher & Paykel Healthcare, Fisher & Paykel, F&P, SleepStyle, or NHIS.
