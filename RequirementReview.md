# Requirement Review — URL Shortener

**Purpose:** Validate the delivered `url-shortener` implementation (and supporting deliverable artifacts)
against every requirement captured in [RequirementAnalysis.md](RequirementAnalysis.md): functional
requirements (product + process), non-functional requirements, and the acceptance-criteria checklists.
**Method:** Direct inspection of source code, tests, config, and repo-root documents — not a re-read of
requirement intent alone. Each row cites the concrete evidence found (or the concrete absence of it).
**Related documents:** [StaffEngineerReview.md](StaffEngineerReview.md) and [PrincipalEngineerReview.md](PrincipalEngineerReview.md) cover code-quality/production-readiness in depth; this
document is scoped to requirement traceability and does not repeat their full findings, only references
them where a code-level finding directly affects whether a requirement is met.

**Legend:** ✅ Met · ⚠️ Partially met · ❌ Not met

---

## 1. Functional Requirements — Product (FR-1–FR-10)

| ID | Requirement | Status | Evidence |
|----|---|---|---|
| FR-1 | Accept a long URL, return a unique short URL | ✅ | `POST /urls` in [LinkController.java](url-shortener/src/main/java/com/urlshortener/controller/LinkController.java) → `UrlServiceImpl.createLink`, short code from `SnowflakeIdGenerator` + `Base62Encoder`. |
| FR-2 | Redirect short code to original URL | ✅ | `GET /{shortCode}` in [RedirectController.java](url-shortener/src/main/java/com/urlshortener/controller/RedirectController.java), HTTP 302 (`FOUND`) per A6. |
| FR-3 | Validate/reject malformed or unsafe URLs | ✅ | [UrlValidationServiceImpl.java](url-shortener/src/main/java/com/urlshortener/service/impl/UrlValidationServiceImpl.java) — scheme allow-list, length cap, control-character rejection, private/loopback/link-local host blocking. |
| FR-4 | Persist short-code → URL mapping | ✅ | `Link` JPA entity + `LinkRepository`, Postgres via Flyway-managed schema (`V1__init_schema.sql`). |
| FR-5 | Retrieve metadata for a short URL | ✅ | `GET /urls/{shortCode}` → `LinkResponse` (original URL, status, created/expires timestamps). |
| FR-6 | Track analytics: click count, timestamp, referrer, user agent | ✅ | `ClickEvent` entity + `AnalyticsServiceImpl.recordClick`; referrer sanitized (scheme+host+path only), UA/IP hashed. |
| FR-7 | API to query analytics | ✅ | `GET /analytics/{shortCode}` in [AnalyticsController.java](url-shortener/src/main/java/com/urlshortener/controller/AnalyticsController.java). |
| FR-8 | Expiration and/or deactivation | ✅ | `expiresAt` + tier-aware TTL (`UrlServiceImpl.computeExpiry`), `LinkExpiryScheduler` sweep, `PATCH /urls/{shortCode}` for reactivate/deactivate. |
| FR-9 | Handle duplicate submissions predictably | ⚠️ | Implementation always mints a **new** short code per `POST /urls` call (no dedup lookup against an existing mapping for the same `longUrl`) — this is one of the two policies FR-9 explicitly allows. **Gap:** this policy choice is never written down anywhere (not in README, not in Architecture.md, not as an assumption note) — it's only observable by reading `UrlServiceImpl.createLink`. FR-9 asks for "predictable," and undocumented-but-consistent behavior is a weaker form of that than a stated policy. |
| FR-10 | Documented error responses for unknown/expired/invalid short codes | ✅ | `GlobalExceptionHandler`: 404 (`LinkNotFoundException`), 410 (`LinkGoneException`), 400 (`InvalidUrlException`/validation), all with a consistent `ErrorResponse` shape; verified by `GlobalExceptionHandlerTest`. |

**Section verdict:** 9/10 met, 1 partially met (a documentation gap, not a behavioral one).

---

## 2. Functional Requirements — Process/Deliverable (FR-11–FR-17)

| ID | Requirement | Status | Evidence |
|----|---|---|---|
| FR-11 | Working, runnable end-to-end prototype | ✅ | [docker-compose.yml](url-shortener/docker-compose.yml) brings up Postgres + Redis + app with health-gated startup; [Dockerfile](url-shortener/Dockerfile) present; README setup steps. |
| FR-12 | Architecture overview (components, tools, control flow, decisions) | ✅ | [Architecture.md](Architecture.md) — component breakdown, request-flow, key decisions with rationale, traceability section back to FRs/NFRs. |
| FR-13 | Three distinct scenarios: greenfield, brownfield, ambiguous | ⚠️ | The **work exists and is traceable** in [TaskBreakdown.md](TaskBreakdown.md) (T14 = brownfield custom-alias enhancement, T16 = ambiguous "reliability features" interpretation, T01–T08 = greenfield core) and in this conversation's history — but **T21 ("AI Traceability & Scenario Consolidation"), which was explicitly planned to produce one reviewable artifact tying the three scenarios together, was never actually produced.** No `Scenarios.md` or equivalent exists in the repo. The scenarios happened; the required consolidated write-up deliverable does not exist. |
| FR-14 | Setup instructions sufficient for independent review | ✅ | [README.md](url-shortener/README.md) setup section + docker-compose; consistent with Portability/Setup NFR. |
| FR-15 | Testing approach + limitations/trade-offs | ✅ | README "Testing," "Tradeoffs," and "Future Enhancements" sections explicitly cover known limitations (e.g., async click-loss on crash, no auth). |
| FR-16 | Traceability of AI-generated vs. human-edited vs. rejected output, with rationale | ❌ | No `AI_TRACEABILITY.md` (or equivalently named artifact) exists anywhere in the workspace. `RequirementAnalysis.md` (R6) and `TaskBreakdown.md` (T21) both call for this file specifically; it was never created. This conversation's transcript contains the raw material for it, but it hasn't been distilled into a standalone, reviewable document. |
| FR-17 | Final engineering summary (plan, artifacts, risks/trade-offs/validation, assumptions, limitations) | ❌ | `TaskBreakdown.md` defines T22 ("Final Engineering Summary & Sign-off") as the task that produces this, but no such summary document exists in the repo. `Architecture.md`, `RequirementAnalysis.md`, and the two code-review documents each cover a slice of this, but none of them is the consolidated final-summary deliverable FR-17 asks for. |

**Section verdict:** 4/7 met, 1 partially met, 2 not met. The two "not met" items (FR-16, FR-17) are the process-layer deliverables the assignment explicitly flags as a "Critical Differentiator" (per `RequirementAnalysis.md` R6) — this is the single biggest gap in this review.

---

## 3. Non-Functional Requirements

| Category | Status | Evidence |
|---|---|---|
| **Performance** (sub-100ms redirect) | ⚠️ | Design supports it (Redis cache-aside, atomic bulk click-count `UPDATE`, async analytics off the response path), but it is **never empirically measured** — `TaskBreakdown.md` T18 ("Performance/Load Smoke Test") has no corresponding script, tool output, or recorded latency baseline anywhere in the repo. Additionally, `ApiKeyService.resolveTier` (uncached) now runs on every request bearing `X-Api-Key`, including redirects — a regression against this exact NFR (see `PrincipalEngineerReview.md`, finding P1). |
| **Scalability** | ✅ | Cache-aside handles read-heavy skew; `SnowflakeIdGenerator` gives coordination-free short-code generation with a DB unique-constraint backstop. Gap: no concurrency test proves ID-uniqueness under parallel load (design-verified, not test-verified — see `PrincipalEngineerReview.md` missing-tests section). |
| **Reliability** (no SPOF in critical read path, "where reasonably achievable") | ⚠️ | `CacheService`'s circuit breaker degrades a Redis outage to a DB read rather than failing the request; rate limiter has a configurable fail-open/fail-closed policy (both paths unit-tested). Gap: the bounded analytics executor can throw synchronously back into the redirect path when saturated (`PrincipalEngineerReview.md` D1) — an unhandled failure mode in exactly the path this NFR protects. |
| **Security** | ⚠️ | Scheme allow-list + private/loopback IP blocking (SSRF/open-redirect), rate limiting applied globally including the creation endpoint, parameterized JPA queries, generic error responses with no stack-trace leakage. Gaps: management endpoints fully open (no auth — self-disclosed limitation), Redis has no password by default plus unconstrained polymorphic deserialization, and IP/User-Agent "hashing" for analytics doesn't achieve real anonymization (unsalted SHA-256 over low-entropy input is reversible by brute force) — notable given the assignment's "Schwab Internal" / regulated-industry framing. |
| **Data Integrity** | ⚠️ | Short-code collisions prevented (Snowflake uniqueness + DB constraint); click-count increments are lost-update-safe via atomic bulk `UPDATE`. Gap: the async `ClickRecordedEventListener` silently drops a raw click event on any exception (logged TODO, no retry/DLQ) — self-disclosed in README as a known limitation, but still a partial miss against "analytics counters must not be lost." |
| **Maintainability** | ✅ | Clean layering (controller/service/repository/cache), single-responsibility collaborators, rationale-focused Javadoc rather than restated code. |
| **Observability** | ✅ | Correlation-ID propagation, structured access logging, slow-call detection aspect, Micrometer/Prometheus + circuit-breaker metrics via Actuator; analytics itself is a first-class queryable feature, not just ops telemetry. |
| **Testability** | ✅ | 19 test classes cover all four required core flows (create, redirect, analytics, expiration) at unit and integration level. Minor gaps already logged in `PrincipalEngineerReview.md` (no direct `SnowflakeIdGenerator`/`Base62Encoder`/`CacheService` unit tests, no `@WebMvcTest` controller layer). |
| **Portability/Setup** | ✅ | `docker-compose.yml` + README; only Docker required of an evaluator; healthchecks gate app startup on dependency readiness. |
| **Auditability (process)** | ❌ | Same finding as FR-16: no dedicated AI traceability artifact exists in the repository. |

**Section verdict:** 5/10 met, 4 partially met, 1 not met.

---

## 4. Acceptance Criteria Checklist

### 4.1 Product acceptance criteria (RequirementAnalysis.md §7.1)

| Criterion | Status |
|---|---|
| `POST` returns unique short URL/code within a documented schema | ✅ |
| `GET` redirects correctly with correct HTTP status | ✅ |
| Unknown/invalid short codes return documented error, not a server error | ✅ |
| Each redirect records a retrievable analytics event | ✅ |
| Analytics endpoint returns total clicks + first/last accessed | ✅ |
| Invalid submissions rejected with clear error messages | ✅ |
| No observed short-code collisions under the test suite's concurrency test | ❌ — **no concurrency test for short-code generation exists** (see NFR: Scalability, and `PrincipalEngineerReview.md` missing tests). This specific acceptance criterion presupposes a test that was never written. |
| Expiration/deactivation correctly blocks further redirects | ✅ — `Link.isRedirectable`, covered by `LinkLifecycleIntegrationTest`. |
| Unit tests cover creation, redirection, analytics, error paths | ✅ |
| Integration test(s) cover full create → redirect → analytics-read flow | ✅ — `RedirectFlowIntegrationTest`. |
| Basic security checks pass (no open redirect, input validation, no leaked stack traces) | ✅ |
| Prototype runs end-to-end via documented setup on a clean environment | ✅ |

**11/12 met.** The one unmet item is concrete and actionable: add a concurrency test asserting no duplicate short codes across parallel `nextId()`/`generate()` calls.

### 4.2 Process/deliverable acceptance criteria (RequirementAnalysis.md §7.2)

| Criterion | Status |
|---|---|
| Architecture overview document exists | ✅ — `Architecture.md` |
| Three worked scenarios documented (decomposition → execution → validation) | ⚠️ — work exists in `TaskBreakdown.md`/conversation history, but no consolidated artifact (see FR-13) |
| Traceability record (AI-generated/edited/rejected + rationale) exists | ❌ — see FR-16 |
| Quality gates evidenced (lint, tests, security/review pass) before sign-off | ⚠️ — tests clearly exist and pass; two dedicated security-review documents exist (`StaffEngineerReview.md`, `PrincipalEngineerReview.md`); but there's no evidence lint (Spotless) was actually run and passed as a recorded gate, and no sign-off record ties these together |
| Setup instructions allow independent run without clarification | ✅ |
| Final engineering summary document exists | ❌ — see FR-17 |
| All AI-assisted, high-impact changes show explicit engineer review/sign-off | ⚠️ — true in practice throughout this conversation (e.g., the TTL/tier design was clarified before implementation), but not captured as a standalone reviewable record independent of chat history |

**2/7 fully met, 3 partially met, 2 not met.** This is the weakest section of the whole review, and it is entirely about missing *documentation artifacts*, not missing engineering work — the underlying work (scenarios, review, validation) was actually done.

---

## 5. Summary

| Area | Met | Partial | Not Met |
|---|---|---|---|
| Functional — Product (FR-1–10) | 9 | 1 | 0 |
| Functional — Process (FR-11–17) | 4 | 1 | 2 |
| Non-Functional | 5 | 4 | 1 |
| Acceptance — Product | 11 | 0 | 1 |
| Acceptance — Process | 2 | 3 | 2 |

**The product itself is in strong shape** — every core functional requirement is met, and most NFRs are met or only partially met due to small, well-scoped, already-documented gaps (most of which are cross-referenced in `PrincipalEngineerReview.md` with concrete fixes).

**The process/deliverable layer is where real gaps remain**, and they are consistent, not scattered: every "not met" or "partial" finding in Sections 2 and 4.2 traces back to **three missing documents** that were planned (in `TaskBreakdown.md`) but never produced:

1. **`AI_TRACEABILITY.md`** — closes FR-16, the Auditability NFR, and the traceability acceptance criterion.
2. **A consolidated scenario write-up** (greenfield/brownfield/ambiguous) — closes FR-13 and its acceptance criterion.
3. **A final engineering summary document** — closes FR-17.

Given the assignment explicitly names AI traceability as a "Critical Differentiator" (`RequirementAnalysis.md` R6), producing these three documents is the highest-leverage remaining work — the underlying engineering to describe in them has already happened.

**Secondary, code-level gaps** (already detailed in `PrincipalEngineerReview.md` and cross-referenced above): the async-executor backpressure leak (D1), the uncached API-key tier lookup now sitting on the redirect hot path (P1), the non-anonymizing IP/UA hash (S1), and the missing short-code concurrency test.
