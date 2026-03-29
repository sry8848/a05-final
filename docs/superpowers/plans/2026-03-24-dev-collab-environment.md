# Development Collaboration Environment Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a minimal GitHub-friendly collaboration setup with Dockerized dependencies, a configurable frontend API base, and clear onboarding docs.

**Architecture:** Keep application code local for development, move infrastructure dependencies into Docker Compose, and centralize frontend backend-URL handling in one helper. Preserve current default behavior so existing local workflows continue to work unchanged.

**Tech Stack:** Vue 3, Vite 5, Node built-in test runner, Spring Boot 3, MySQL 8, Redis 7, RabbitMQ 3, Docker Compose

---

### Task 1: Add the failing frontend regression test

**Files:**
- Create: `frontend/tests/api-base.test.js`

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run `node --test tests/api-base.test.js` in `frontend/` and confirm failure**
- [ ] **Step 3: Implement the shared API base helper**
- [ ] **Step 4: Re-run the helper test and confirm pass**

### Task 2: Wire frontend code to the shared helper

**Files:**
- Create: `frontend/src/api/base.js`
- Modify: `frontend/src/api/auth.js`
- Modify: `frontend/src/api/resume.js`
- Modify: `frontend/src/api/adminDashboard.js`
- Modify: `frontend/src/services/AsrService.js`
- Modify: `frontend/src/services/TtsPlayerService.js`
- Modify: `frontend/src/components/SettingsPage.vue`

- [ ] **Step 1: Replace duplicated `/api/v1` assembly with helper calls**
- [ ] **Step 2: Resolve backend-returned relative resource URLs through the helper**
- [ ] **Step 3: Run focused frontend tests and build**

### Task 3: Add collaboration infrastructure and docs

**Files:**
- Create: `docker-compose.yml`
- Create: `.env.example`
- Create: `README.md`
- Modify: `frontend/README.md`

- [ ] **Step 1: Define MySQL, Redis, RabbitMQ, and `db-init` compose services**
- [ ] **Step 2: Add documented defaults aligned with backend dev profile**
- [ ] **Step 3: Replace misleading frontend README with actual usage guidance**
- [ ] **Step 4: Add root onboarding README with startup order and remote-backend option**
