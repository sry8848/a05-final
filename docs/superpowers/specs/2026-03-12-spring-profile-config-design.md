# Spring Profile Config Design

## Background

The backend currently mixes shared configuration, local development overrides, and test-only concerns. This makes it hard for a beginner to tell:

- which configuration file is used when running the app in IntelliJ IDEA
- which configuration file is only for automated tests
- which services are optional at startup versus required only when specific features are exercised

The immediate bug was caused by `application-local.yml` excluding Redis auto-configuration while production code still required `StringRedisTemplate`.

## Goals

- Separate shared defaults from local development overrides and test-only overrides.
- Remove `spring.profiles.active` from shared configuration.
- Keep Redis auto-configuration available in `local`.
- Clarify IntelliJ IDEA usage so local app startup uses `local` explicitly and tests continue using `test`.
- Replace the existing IDEA guide with a beginner-focused explanation of profiles and startup flow.

## Non-Goals

- No broad business-logic refactor.
- No large-scale test environment redesign.
- No dependency installation or infrastructure provisioning changes.

## Configuration Design

### `application.yml`

This file is the shared baseline.

- Contains common defaults used by all environments.
- Must not set `spring.profiles.active`.
- Keeps existing shared application settings, ports, feature flags, AI defaults, Redis defaults, and upload paths.

### `application-local.yml`

This file is for manual local development runs in IntelliJ IDEA.

- Contains only local overrides.
- May override datasource credentials and logging.
- May exclude RabbitMQ auto-configuration for easier local startup.
- Must not exclude Redis auto-configuration.
- Is selected explicitly through IDEA run configuration with `Active profiles=local`.

### `application-test.yml`

This file is for automated tests only.

- Remains under `src/test/resources`.
- Keeps test-safe overrides such as disabling RabbitMQ and speech features.
- Is selected by test code via `@ActiveProfiles("test")`.
- Documentation must explicitly state that it is not the profile for manually starting the application.

## IDEA Usage Design

The replacement guide should teach one simple rule:

- Run the application manually with `local`.
- Run automated tests with `test`.

The guide should show:

- where to set `Active profiles=local` in IntelliJ IDEA
- that `application.yml` no longer hardcodes a profile
- what it means when the app starts successfully but a middleware-backed feature still fails later due to a missing service

## Testing Strategy

Add minimal regression coverage for configuration responsibilities:

- a test asserting shared config no longer hardcodes `spring.profiles.active`
- a test asserting `application-local.yml` does not exclude Redis auto-configuration

Verification should include:

- targeted Maven test execution
- one real startup check using the `local` profile

## Risks And Mitigations

- Risk: removing hardcoded `local` profile could confuse existing developers.
  Mitigation: replace the IDEA guide with explicit run configuration steps.

- Risk: local startup may still fail when external services are unavailable.
  Mitigation: document which startup failures come from missing MySQL versus feature-time Redis failures versus disabled RabbitMQ integration.

