# Spring Profile Config Cleanup Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separate shared, local-development, and test configuration responsibilities; remove the hardcoded active profile; and replace the IDEA guide with clear beginner-facing instructions.

**Architecture:** Keep `application.yml` as the shared baseline, keep `application-local.yml` as explicit IDE local-run overrides, and keep `application-test.yml` as test-only overrides. Add small regression tests that lock down the new responsibilities and verify the real startup path with the `local` profile.

**Tech Stack:** Spring Boot 3.2, JUnit 5, Maven, YAML config, Markdown docs

---

## Chunk 1: Lock Down Config Responsibilities

### Task 1: Add a failing test for shared config not hardcoding an active profile

**Files:**
- Create: `backend/src/test/java/com/a05/aiinterview/ApplicationProfileConfigTest.java`
- Test: `backend/src/test/java/com/a05/aiinterview/ApplicationProfileConfigTest.java`

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run `mvn -Dtest=ApplicationProfileConfigTest test` and verify it fails because `application.yml` still contains `spring.profiles.active`**
- [ ] **Step 3: Implement the minimal config change later by removing the hardcoded active profile**
- [ ] **Step 4: Re-run `mvn -Dtest=ApplicationProfileConfigTest test` and verify it passes**

### Task 2: Keep the existing regression around Redis auto-configuration in local

**Files:**
- Modify: `backend/src/test/java/com/a05/aiinterview/LocalProfileRedisConfigTest.java`
- Test: `backend/src/test/java/com/a05/aiinterview/LocalProfileRedisConfigTest.java`

- [ ] **Step 1: Confirm the test targets `application-local.yml` content directly**
- [ ] **Step 2: Run `mvn -Dtest=LocalProfileRedisConfigTest test` and verify it passes after the config cleanup**

## Chunk 2: Refactor Spring Config Files

### Task 3: Clean up shared config

**Files:**
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: Remove `spring.profiles.active` from shared config**
- [ ] **Step 2: Keep only shared defaults and comments that apply across environments**
- [ ] **Step 3: Avoid moving test-only or machine-specific instructions into the shared file**

### Task 4: Keep local-only overrides in the local profile

**Files:**
- Modify: `backend/src/main/resources/application-local.yml`

- [ ] **Step 1: Keep local datasource overrides and local logging choices**
- [ ] **Step 2: Keep RabbitMQ excluded if needed for local startup**
- [ ] **Step 3: Ensure Redis auto-configuration is not excluded**
- [ ] **Step 4: Add comments that explain this file is selected explicitly in IDEA**

### Task 5: Clarify test-only config

**Files:**
- Modify: `backend/src/test/resources/application-test.yml`

- [ ] **Step 1: Keep it focused on test-only overrides**
- [ ] **Step 2: Disable integrations that should not be required for routine test runs**
- [ ] **Step 3: Add comments that this file is for automated tests, not manual app startup**

## Chunk 3: Replace The IDEA Guide

### Task 6: Rewrite the developer guide for beginners

**Files:**
- Modify: `docs/idea-dev-guide.md`

- [ ] **Step 1: Replace the existing content entirely**
- [ ] **Step 2: Explain the purpose of `application.yml`, `application-local.yml`, and `application-test.yml`**
- [ ] **Step 3: Show exact IntelliJ IDEA run configuration steps with `Active profiles=local`**
- [ ] **Step 4: Explain the difference between startup-time failures and feature-time middleware failures**
- [ ] **Step 5: Keep the guide practical and beginner-readable**

## Chunk 4: Verification

### Task 7: Run targeted regression tests

**Files:**
- Test: `backend/src/test/java/com/a05/aiinterview/ApplicationProfileConfigTest.java`
- Test: `backend/src/test/java/com/a05/aiinterview/LocalProfileRedisConfigTest.java`

- [ ] **Step 1: Run `mvn -Dtest=ApplicationProfileConfigTest,LocalProfileRedisConfigTest test`**
- [ ] **Step 2: Confirm both tests pass with zero failures**

### Task 8: Verify real local startup behavior

**Files:**
- Modify: none

- [ ] **Step 1: Run `mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.jvmArguments=-Dspring.main.web-application-type=none`**
- [ ] **Step 2: Confirm startup reaches `Started AiInterviewApplication` without the previous `StringRedisTemplate` bean error**
- [ ] **Step 3: Stop the process after verification**
