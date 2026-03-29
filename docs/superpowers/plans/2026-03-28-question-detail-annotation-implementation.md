# Question Detail Annotation Refresh Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh the question detail annotation UI so the answer body is readable on a light surface, only highlighted segments use red/green text emphasis, and annotation notes become low-noise helper cards.

**Architecture:** Keep the existing `highlightedSegments` rendering path in `QuestionDetailPage.vue` and change only presentation. Lock the visual contract with a lightweight Node test that reads the component stylesheet and asserts the new CSS rules.

**Tech Stack:** Vue 3 single-file component, scoped CSS, Node `node:test`

---

## Chunk 1: Lock the visual contract with tests

### Task 1: Add regression coverage for the refreshed annotation styles

**Files:**
- Create: `frontend/tests/question-detail-annotation-style.test.js`
- Reference: `frontend/src/components/QuestionDetailPage.vue`

- [ ] **Step 1: Write the failing test**

Add assertions for:
- `.annotated-answer` uses a light background instead of the old dark translucent panel
- `.answer-segment.strength` and `.answer-segment.weakness` do not define `background`
- `.annotation-note` has card-like container styling
- state-specific note styles no longer use the previous bright text colors

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test frontend/tests/question-detail-annotation-style.test.js`
Expected: FAIL because current component still uses dark answer background, segment backgrounds, and bright note text colors.

## Chunk 2: Implement the visual refresh

### Task 2: Update answer annotation presentation in the question detail page

**Files:**
- Modify: `frontend/src/components/QuestionDetailPage.vue`
- Test: `frontend/tests/question-detail-annotation-style.test.js`

- [ ] **Step 1: Update top helper copy if needed**

Adjust the section tip so it matches the new behavior where only marked segments receive text emphasis.

- [ ] **Step 2: Update the answer container and segment styles**

Implement:
- light answer surface
- dark default body text
- stronger but non-neon green/red text emphasis
- no segment background blocks

- [ ] **Step 3: Update annotation note styles**

Implement:
- shallow positive/negative card backgrounds
- readable dark note text
- keep icon-based status recognition

- [ ] **Step 4: Run targeted test to verify it passes**

Run: `node --test frontend/tests/question-detail-annotation-style.test.js`
Expected: PASS

## Chunk 3: Verify page safety

### Task 3: Run adjacent checks and build

**Files:**
- Verify only

- [ ] **Step 1: Run related tests**

Run: `node --test frontend/tests/question-domain-feedback.test.js frontend/tests/question-redo-state.test.js`
Expected: PASS

- [ ] **Step 2: Run frontend build**

Run: `npm run build`
Working directory: `frontend`
Expected: successful Vite production build
