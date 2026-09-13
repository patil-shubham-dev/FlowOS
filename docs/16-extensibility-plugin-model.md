# FlowOS — Extensibility & Plugin Model
**Status: Future / Experimental — conceptual design only. Not implemented in MVP or v1.**

## 1. Purpose of designing this now
Even though nothing here is built yet, the core architecture (Architecture Overview §4 — module boundary discipline) is deliberately shaped so this becomes possible later without a rewrite. This document exists so that discipline has a target to aim at.

## 2. Module concept
A **module** is a self-contained unit that can, in principle, be added to a running FlowOS instance without modifying core code:
- Declares its own **data entities** (schema fragment).
- Declares its own **service** (business logic over those entities).
- Declares its own **tools** (registered into the shared Tool Registry, subject to the same risk-tiering and confirmation system as core tools — a plugin tool is not exempt from Permission & Confirmation rules).
- Declares its own **UI slot(s)** — a page, a widget on Home, or both.
- Declares a **manifest**: id, version, requested permissions, declared entities/tools/UI slots, dependencies (if any on core entities like Task).

## 3. Manifest (conceptual shape)
```
module_id, version, display_name
entities: [ ... schema fragments ... ]
tools: [ ... tool definitions, each with risk tier ... ]
ui_slots: [ page | home_widget, ... ]
permissions_requested: [ read:tasks, write:tasks, ... ]  (if it needs to interact with core modules)
```

## 4. Isolation & sandboxing principle
A module must not be able to:
- Access core data outside declared permissions.
- Register a tool at a risk tier lower than what the Permission & Confirmation Model would assign based on its actual effect (i.e., a module cannot self-declare "I'm low risk" to bypass confirmation — the same code-enforced classification applies).
- Modify core application code or other modules.

Exact sandboxing mechanism (process isolation, language-level sandboxing, capability-scoped API tokens) is an implementation decision for whenever this phase is actually built — the product requirement is the isolation guarantee, not a specific technique.

## 5. Versioning & rollback
- Modules are versioned independently of core.
- Installing/updating a module must be a reversible operation — uninstalling should cleanly remove its registered tools/UI without corrupting core data (its own entities may be archived rather than hard-deleted, consistent with the Undo philosophy elsewhere in the product).

## 6. Relationship to self-extension
This document defines the **container** a module lives in. It says nothing about *how a module gets created*. Human-authored modules (a developer writes one by hand using a future Module SDK) and AI-generated modules (see Self-Extension Vision) both target this same manifest/container model — extensibility and self-extension are two separate concerns that share one substrate.

## 7. Non-goals for this phase
- A public marketplace or sharing mechanism for modules.
- Any actual SDK, sandbox runtime, or installer — this document is intentionally conceptual only.
