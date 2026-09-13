# FlowOS — Self-Extension Vision (AI-Generated Modules)
**Status: Long-Term Research. Explicitly gated behind a mature Extensibility Model (see previous doc) and a proven, trustworthy core agent (MVP + v1 must be solid first). Not started until those preconditions hold.**

## 1. The vision, restated
A user asks for functionality that doesn't exist yet — e.g., "I need a college attendance tracker" — and rather than only saying "that doesn't exist," FlowOS can eventually design, generate, test, and install a new module for it, using the module container defined in the Extensibility & Plugin Model.

## 2. Why this is gated, not rejected
This is the single highest-risk, highest-complexity part of the entire product. It requires: a code-generation agent, a sandboxed execution/test environment, a review mechanism, and an install pipeline that can't corrupt the running application. Attempting this before the core agent has earned trust on ordinary data mutations would be building the hardest feature on the shakiest foundation. The sequencing in the Roadmap document reflects this directly.

## 3. Conceptual pipeline
```
User request ("I need a college attendance tracker")
        ↓
1. INTENT UNDERSTANDING — clarify scope with the user (fields, calculations, views wanted)
        ↓
2. SPECIFICATION — agent drafts a module spec: entities, calculations, UI, tools (mirrors this doc's format)
        ↓
3. USER REVIEW OF SPEC — plain-language spec shown before any code is written; user approves/edits
        ↓
4. MODULE DESIGN — map spec to the manifest format (Extensibility Model)
        ↓
5. CODE GENERATION — a coding agent (e.g., OpenCode) generates the module against the Module SDK
        ↓
6. SANDBOXED BUILD & TEST — generated module is built and tested in isolation, never against production data
        ↓
7. HUMAN-VISIBLE DIFF/REVIEW — generated code and its declared permissions are shown before install (this is a harder gate than a normal Confirm-tier action — it is closer to reviewing a pull request than approving a task edit)
        ↓
8. INSTALL — module registers into the running app per the Extensibility Model
        ↓
9. FOLLOW-UP MODIFICATION — later requests ("add a feature that tells me how many lectures I can miss") re-enter this pipeline at step 1, scoped to the existing module
```

## 4. Non-negotiable safety gates
- **No generated code installs without explicit human review and approval** — this is never an Auto or even ordinary Confirm-tier action; it is its own distinct, heavier-weight approval flow.
- **Sandboxed testing before install**, always, with no path for a generated module to touch real user data during generation/testing.
- **Full versioning and uninstall/rollback** for every installed generated module, same as any other module.
- **Permissions requested by a generated module are visible and must be individually justifiable** (e.g., if an attendance tracker requests write access to Calendar, the user should be able to see why and deny it while still installing a limited version).

## 5. Realism assessment (honest)
This is technically achievable in pieces — LLM code generation, sandboxed execution, and manifest-based plugin systems all exist independently and are well-understood. What's genuinely hard is the *end-to-end reliability* of the pipeline (generated code that's correct, tested, and safely installable without ongoing human engineering babysitting) and the *UX of review* (most users can't meaningfully review generated code — the review step needs to be spec/behavior-level, not diff-level, for non-developer users, while still offering the code diff for those who want it). This is a genuine, multi-quarter research effort once started, not a feature sprint. It should be scoped and resourced as such, separately from core product development, when the time comes.

## 6. What can be done now without violating "don't build this yet"
Nothing in the current phase requires building any part of this pipeline. The only obligation now is architectural: keep the module boundary (Extensibility Model) clean enough that this pipeline has a real container to target later.
