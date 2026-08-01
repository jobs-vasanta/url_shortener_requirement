# Requirement Analysis: AI-Assisted URL Shortener Engineering System

**Author:** Lead Software Engineer (AI-assisted analysis)
**Source document:** Interview Assignment — "Build an AI-Assisted Software Engineering System - URL Shortener" (Schwab Internal)
**Date:** 2026-07-31

## 0. Context Summary

The assignment has **two layers of requirements** that must both be satisfied:

1. **Product layer** — build a working URL shortener service (core APIs, analytics, reliability).
2. **Process layer** — demonstrate a disciplined, AI-assisted engineering workflow (requirement understanding, decomposition, execution with traceability, quality gates, validation, and a final engineering summary) across greenfield, brownfield, and ambiguous scenarios.

The assignment is intentionally under-specified at the product layer (no endpoint contracts, data model, scale targets, or SLAs are given). This is treated as a deliberate ambiguity test rather than an oversight, so this document captures assumptions and open questions explicitly rather than guessing silently.

---

## 1. Functional Requirements

### 1.1 Product: URL Shortener Service (Greenfield core)

| ID | Requirement |
|----|-------------|
| FR-1 | System shall accept a long URL and return a shortened URL with a unique short code. |
| FR-2 | System shall redirect requests on a short code to the original long URL (HTTP redirect, e.g., 301/302). |
| FR-3 | System shall validate that submitted URLs are well-formed and reject malformed/unsafe input. |
| FR-4 | System shall persist the mapping between short code and original URL. |
| FR-5 | System shall expose a way to retrieve metadata for a short URL (original URL, creation date, status). |
| FR-6 | System shall track analytics per short URL: at minimum click/redirect count, timestamp of access, and basic metadata (e.g., referrer, user agent). |
| FR-7 | System shall expose an API to query analytics for a given short code. |
| FR-8 | System shall support URL expiration and/or deactivation (deletion or disabling of a short link). |
| FR-9 | System shall handle duplicate submissions predictably (either reuse existing short code or generate a new one — policy to be decided, see Clarifying Questions). |
| FR-10 | System shall return appropriate error responses for unknown/expired/invalid short codes (e.g., 404/410). |

### 1.2 Process/Deliverable Functional Requirements (from assignment)

| ID | Requirement |
|----|-------------|
| FR-11 | Deliver a working, runnable end-to-end prototype. |
| FR-12 | Produce an architecture overview describing components, tools, execution approach, control flow, and key decisions. |
| FR-13 | Demonstrate three distinct scenarios — **greenfield**, **brownfield**, and **ambiguous** — each showing decomposition, AI-assisted execution, and validation. |
| FR-14 | Provide setup instructions sufficient for an independent reviewer to run the prototype. |
| FR-15 | Provide a testing approach description plus stated limitations and trade-offs. |
| FR-16 | Maintain traceability of AI-generated vs. human-edited vs. rejected output, with rationale. |
| FR-17 | Produce a final engineering summary covering plan/rationale, artifacts, risks/trade-offs/validation, assumptions, and limitations. |

---

## 2. Non-Functional Requirements

| Category | Requirement |
|----------|-------------|
| **Performance** | Redirect path should be low-latency (target: sub-100ms server-side excluding network) since it is the highest-traffic operation. |
| **Scalability** | Design should tolerate read-heavy traffic skew (reads/redirects ≫ writes/creates); short code generation must avoid collisions at scale. |
| **Reliability** | Redirect service should degrade gracefully; no single point of failure in the critical read path where reasonably achievable within prototype scope. |
| **Security** | Input validation/sanitization to prevent open-redirect abuse, SSRF via URL fetching (if previews are added), and injection attacks; rate limiting on creation endpoint to deter abuse/spam link generation. |
| **Data Integrity** | Short code collisions must be prevented or detected; analytics counters must not be lost under concurrent access. |
| **Maintainability** | Modular, testable code; clear separation of API layer, business logic, and persistence. |
| **Observability** | Basic logging/metrics for error rates and latency; analytics themselves are a first-class feature, not just ops telemetry. |
| **Testability** | Unit and integration test coverage for core flows (create, redirect, analytics, expiration). |
| **Portability/Setup** | Should run locally with minimal external dependencies for evaluator convenience. |
| **Auditability (process)** | AI-assisted engineering artifacts (prompts, generated/edited/rejected content) must be traceable and reviewable. |

---

## 3. Assumptions

Since the assignment provides no concrete specification for the product, the following assumptions are made explicit and will guide the greenfield build:

1. **A1 — Scale target:** This is a prototype/interview deliverable, not a production system handling millions of requests/day; design will favor clean architecture that *could* scale (e.g., stateless API, indexable storage) over premature infrastructure complexity (no distributed cache/sharding required for the prototype).
2. **A2 — Short code strategy:** Base62-encoded auto-increment ID or random string (~7 chars) is acceptable; custom aliases are a stretch feature, not a baseline requirement.
3. **A3 — Persistence:** A single relational or embedded datastore (e.g., SQLite/Postgres) is sufficient for the prototype; no requirement for a specific cloud database.
4. **A4 — Authentication:** No user accounts/auth are required for baseline create/redirect; analytics endpoints may be open for the prototype unless stated otherwise.
5. **A5 — Expiration default:** Links do not expire by default unless a TTL/expiration is explicitly provided at creation time.
6. **A6 — Redirect type:** HTTP 302 (temporary redirect) is used by default so analytics can be captured reliably (301 responses risk being cached by browsers, undercounting clicks).
7. **A7 — Analytics granularity:** Aggregate counters plus a raw event log (timestamp, referrer, user agent, approximate location if easily derivable) satisfy "analytics"; real-time dashboards are out of scope for the prototype.
8. **A8 — Deployment:** Local/dev-mode runnable prototype (e.g., via a single command) satisfies "runnable end-to-end"; cloud deployment is not required.
9. **A9 — Tech stack:** Implementer (candidate) selects the stack; no language/framework is mandated by the assignment.
10. **A10 — Brownfield scenario:** Since no existing codebase is provided, the "brownfield" scenario will be self-created — i.e., the candidate builds an initial version, then performs an enhancement/refactor/bug-fix pass against their own greenfield codebase to demonstrate brownfield reasoning.
11. **A11 — Ambiguous scenario:** The ambiguous scenario will be sourced from a genuinely underspecified feature request (e.g., "add link expiration" or "add custom aliases") to demonstrate ambiguity resolution, not a fabricated trivial case.

---

## 4. Missing Requirements

Gaps in the source document that a real product backlog would need before implementation, beyond what's covered by assumptions:

1. No defined **API contract** (routes, request/response schemas, versioning strategy).
2. No **volume/traffic targets** (requests/sec, expected link count) to size the architecture.
3. No **SLA/uptime target** or error-budget definition.
4. No **security/compliance requirements** stated (e.g., PII handling in analytics, GDPR-style data retention, malicious URL/malware-link screening) — notable given "Schwab Internal" classification suggests a regulated-industry context.
5. No **authentication/authorization model** for who can create, view, or delete links/analytics.
6. No **multi-tenancy** requirement (single global namespace vs. per-user/organization links).
7. No **retention policy** for analytics events (how long raw click data is kept).
8. No **custom domain / branded short link** requirement.
9. No explicit **rate-limiting/abuse-prevention** requirement, despite being a common URL-shortener risk (spam/phishing links).
10. No **environment/infra constraints** (must it deploy to a specific cloud, container runtime, or on-prem environment?).
11. No **review/sign-off process** definition — "human sign-off for high-impact changes" is mentioned as a principle but no concrete gate/checklist is specified.
12. No **specific AI tools mandated** — "Copilot/Claude/etc." implies flexibility, but the grading rubric for "effectiveness of AI-assisted execution" isn't operationalized (e.g., what artifacts prove it).

---

## 5. Risks

| ID | Risk | Impact | Likelihood | Mitigation |
|----|------|--------|------------|------------|
| R1 | Short code collisions under concurrent writes | Data corruption (wrong redirect target) | Medium | Use DB unique constraint on short code + retry-on-conflict, or deterministic encoding of an auto-increment key. |
| R2 | Open redirect / SSRF abuse via arbitrary long URLs | Security incident, reputational/compliance risk | Medium | URL scheme allow-list (http/https only), block private/internal IP ranges, optional safe-browsing/URL reputation check. |
| R3 | Analytics write contention on hot links (viral link) | Lost click counts, latency spikes on redirect path | Low-Medium (prototype scale) | Asynchronous/batched analytics writes decoupled from the redirect response path. |
| R4 | Ambiguity in assignment interpreted incorrectly, causing scope mismatch with grader expectations | Deliverable misses evaluation criteria | Medium | This document + clarifying questions below; explicit assumptions log; align on scope before deep implementation. |
| R5 | Over-engineering the prototype (premature scaling, unnecessary infra) given "2-3 days" timebox | Wasted effort, missed deliverables (docs, scenarios, tests) | Medium | Timebox architecture decisions; favor simple, defensible design over speculative scale features. |
| R6 | Under-engineering AI traceability (not capturing generated/edited/rejected rationale as work progresses) | Fails "Critical Differentiator" evaluation criterion | Medium-High | Maintain a running decision/traceability log (e.g., `AI_TRACEABILITY.md`) from the first task onward, not retrofitted at the end. |
| R7 | No automated tests / quality gates before "sign-off" | Fails "quality gates" and "production-grade" expectation | Medium | Set up lint + unit/integration tests + basic CI-style check early, run before each milestone. |
| R8 | Sensitive/malicious content in shortened URLs (phishing, malware) not screened at all | Security/compliance exposure in a real deployment | Low (prototype), High (production) | Document as a known limitation; note as a follow-up (e.g., URL reputation API integration) rather than building unless in scope. |
| R9 | PII in analytics (IP address, user agent) creates data-privacy exposure | Compliance risk given corporate/regulated context | Low-Medium | Minimize stored fields, document retention assumption, avoid storing raw IP if not required. |

---

## 6. Clarifying Questions

These would be asked of a real stakeholder/interviewer before or during execution; where no answer is available, Section 3 (Assumptions) states the default taken.

1. What are the expected **traffic/scale** targets (requests/sec, total links, retention period for analytics)?
2. Is **authentication/authorization** required (e.g., per-user link ownership, admin-only analytics)?
3. Should short codes support **custom aliases** (vanity URLs), or is auto-generated only sufficient?
4. Do links need **expiration/TTL** support, and if so, what's the default (never vs. e.g., 90 days)?
5. What **analytics dimensions** matter most to stakeholders — raw counts, referrer, geography, device/browser, time-series trends?
6. Is there a **specific tech stack, cloud provider, or deployment target** mandated by the evaluators, or is stack choice fully open?
7. What does "**brownfield**" mean concretely here — is an existing sample codebase expected to be provided, or should the candidate simulate brownfield work against their own greenfield build?
8. What **level of security hardening** is expected for a prototype (e.g., is malicious-URL screening in scope, or explicitly out of scope/documented as a limitation)?
9. How should **AI traceability** be evidenced — chat logs, commit messages, a dedicated log file, PR descriptions?
10. Is **redirect type** (301 vs 302) significant to the evaluators, given its analytics trade-off?
11. Should the system support **link deactivation/soft-delete** by the creator, and is there a defined authorization model for that action?

---

## 7. Acceptance Criteria

### 7.1 Product acceptance criteria

- [ ] `POST` endpoint accepts a long URL and returns a unique short URL/code within a documented response schema.
- [ ] `GET` on the short code redirects to the correct original URL with correct HTTP status.
- [ ] Invalid/unknown short codes return a documented error response (e.g., 404), not a server error.
- [ ] Each redirect increments/records an analytics event retrievable via an API.
- [ ] Analytics endpoint returns at least: total click count, first/last accessed timestamps.
- [ ] Duplicate/edge-case URL submissions (empty, malformed, non-http(s) scheme) are rejected with clear error messages.
- [ ] Short code generation has no observed collisions under the test suite's concurrency test.
- [ ] Expiration/deactivation (if implemented) correctly blocks further redirects once a link is expired/deactivated.
- [ ] Unit tests cover creation, redirection, analytics recording, and error paths.
- [ ] Integration test(s) cover the full create → redirect → analytics-read flow end-to-end.
- [ ] Basic security checks pass: no open-redirect via disallowed schemes, input validation on create endpoint, no unhandled exceptions leaking stack traces.
- [ ] Prototype runs end-to-end via documented setup steps on a clean environment.

### 7.2 Process/deliverable acceptance criteria

- [ ] Architecture overview document exists covering components, tools/AI usage, control flow, and key decisions with rationale.
- [ ] Three worked scenarios (greenfield, brownfield, ambiguous) are documented, each showing: decomposition → AI-assisted execution → validation.
- [ ] Traceability record exists distinguishing AI-generated, human-edited, and rejected AI output, with rationale for edits/rejections.
- [ ] Quality gates evidenced: linting, tests, and at least a basic security/review pass, run before final sign-off.
- [ ] Setup instructions allow an independent party to run the prototype without additional clarification.
- [ ] Final engineering summary document exists, covering plan/rationale, artifacts produced, risks/trade-offs/validation performed, assumptions, and known limitations.
- [ ] All AI-assisted, high-impact changes show explicit engineer review/sign-off (not blindly accepted).

---

## Next Steps

1. Confirm/refute assumptions in Section 3 with stakeholder if possible; otherwise proceed with assumptions as documented defaults.
2. Move to **Task Decomposition** (separate `TaskBreakdown.md` or project board) covering: API design, data model, short-code generation, analytics pipeline, redirect handling, security hardening, testing, and the three demonstration scenarios.
3. Stand up project scaffolding (greenfield) before layering in the brownfield enhancement and ambiguous-requirement scenarios.
