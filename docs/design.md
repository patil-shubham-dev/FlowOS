# FlowOS — design.md

**Status:** Authoritative visual / interaction source of truth for implementation.
**Derives from:** `06-ux-product-principles.md` (intent — wins on feel), `08-non-functional-requirements.md` §8 (accessibility constraints).
**Execution mechanics from:** the consolidated `design` skill (Taste v2 + Emil Kowalski + Impeccable v4.1.2 + make-interfaces-feel-better). There is no standalone "Impeccable skill" with `/init`, `/border`, `/animation` slash commands in this environment — the equivalents used here are `prompts/reference/init.md` (PRODUCT + DESIGN seeding), `make-interfaces-feel-better/surfaces.md` (border/radius/shadow conventions), and `motion/STANDARDS.md` + `prompts/reference/animate.md` + `make-interfaces-feel-better/animations.md` (motion conventions). Section 11 maps each choice to its source.
**Register:** `product` (app UI — design SERVES the product; brand rules do not apply to dense surfaces).

> Physical scene (per `visual-principles/color.md` theme rule): a student/dev at a bright desk, morning light, checking today's plan in under 30 seconds before starting work. Calm, airy, human — not a dark ops cockpit, not an enterprise dashboard. This forces: **light theme default**.

---

## 1. Visual theme

**"Playful light" — white-dominant, calm, human.**

- Base surfaces are white / near-white neutrals with a faint cool tint toward the brand accent (chroma 0.005–0.015, per tinted-neutrals rule — never default warm cream; the warm-paper band `oklch L 0.84–0.97 / C < 0.06 / hue 40–100` and token names like `--paper/--cream/--sand` are banned as body backgrounds).
- Depth comes from **layered soft shadows + 1px ring**, not heavy solid borders (see §6). Dividers and input outlines stay as real borders.
- Generous whitespace: FlowOS is a **Daily App, VISUAL_DENSITY 4–5** (standard `py-16–py-24` section rhythm on marketing-adjacent surfaces; compact but breathing `16–24px` card padding in app surfaces). Never Cockpit density (no 1px-line-only data walls, no mono-for-everything).
- Soft rounded corners everywhere (see §6 radius scale). Nothing sharp except hairline dividers.
- Playfulness is expressed through **accent color + micro-motion + friendly microcopy**, never through novelty layout, gradient text, glassmorphism on cards, or mascot illustration. One accent family; restrained strategy (accent ≤10% of surface, per product-color restrained default).
- Data is primary, chrome is secondary (`06` §1): navigation and panels recede (neutral layer), task/event/note content carries contrast and weight.
- Anti-references (what this is NOT): enterprise dashboard (dark sidebar + dense grid + tiny gray text), AI-purple-glow chatbot (LILA rule — no violet/blue neon glows), warm-beige artisan landing (premium-consumer palette ban), sterile clinical tool (pure gray-on-white, no accent, no motion).

## 2. Color system

Token architecture follows Style Dictionary CTI (primitive → alias/semantic → component tiers, W3C DTCG naming, periods as group separators). All values specified in **OKLCH**. Single accent locked per surface (color-consistency lock).

### 2.1 Primitive palette (global tokens)

| Role | Token (example) | Value (OKLCH) | Notes |
|---|---|---|---|
| Base canvas | `color.canvas.base` | `oklch(0.99 0.004 250)` | Near-white, faint cool tint toward accent hue |
| Surface raised | `color.surface.raised` | `oklch(1.0 0 0)` | Pure-white cards on canvas |
| Surface sunken | `color.surface.sunken` | `oklch(0.97 0.006 250)` | Input wells, chat bubbles (user), code-ish areas |
| Second neutral (sidebar/toolbar) | `color.surface.panel` | `oklch(0.96 0.008 250)` | Slightly cooler/darker than content surface |
| Ink primary | `color.ink.primary` | `oklch(0.25 0.02 250)` | Body text. ≥4.5:1 on base — verify, never eyeball |
| Ink secondary | `color.ink.secondary` | `oklch(0.45 0.02 250)` | Meta, timestamps. Must still hit 4.5:1 on base; bump toward ink if close |
| Ink faint | `color.ink.faint` | `oklch(0.55 0.02 250)` | Placeholders — same 4.5:1 requirement, not muted-gray default |
| Primary accent (actions, selection) | `color.accent.500` | `oklch(0.62 0.16 250)` | Friendly blue. Saturation <80%. Only accent for primary actions/selection/state |
| Accent ink (text on accent) | `color.accent.contrast` | `oklch(1.0 0 0)` | White on accent; verify ≥4.5:1 |
| Accent soft (tint wash) | `color.accent.100` | `oklch(0.94 0.04 250)` | Selected-row wash, active nav pill, Home ambient card highlight |
| Success | `color.status.success` | `oklch(0.72 0.15 155)` | Done, applied, healthy |
| Warning | `color.status.warning` | `oklch(0.78 0.14 85)` | Conflict, needs attention |
| Danger | `color.status.danger` | `oklch(0.62 0.18 25)` | Destructive, error, blocked |
| Info | `color.status.info` | `oklch(0.70 0.12 195)` | Neutral informational. Teal (~195°) sits ~55° from the primary accent (250°) so actions vs. informational states are unambiguous on hue alone — icon + label still required alongside, per the never-color-alone rule |
| Priority urgent | `color.priority.urgent` | `oklch(0.62 0.18 25)` (danger ramp) | Always paired with ▲/● icon + "Urgent" label |
| Priority normal | `color.priority.normal` | `color.accent.500` ramp | Paired with label |
| Priority low | `color.priority.low` | `color.ink.faint` + ○ icon | Paired with label |
| Focus ring | `color.focus.ring` | `oklch(0.62 0.16 250 / 0.55)` | 2px outline offset 2px, always visible on keyboard focus |

No pure black (`#000`) anywhere. No neon/outer glows — elevation via inner ring + tinted shadow (§6).

### 2.2 Semantic / alias tokens (selection)

- `color.background.button.primary → {color.accent.500}`, `color.text.button.primary → {color.accent.contrast}`
- `color.background.selected → {color.accent.100}`, `color.background.canvas → {color.canvas.base}`
- `color.background.card → {color.surface.raised}`, `color.background.panel → {color.surface.panel}`
- `color.border.divider → oklch(0 0 0 / 0.08)` (layout separation only), `color.border.input → oklch(0 0 0 / 0.14)` (form outlines, a11y)
- `color.text.success/warning/danger/info → darker shade of the status hue` (never gray text on colored bg — rule `skill-color-gray-on-color`)
- State vocabulary standardized per surface: `hover, focus, active, disabled, selected, loading, error, warning, success, info` — accent reserved for primary actions, current selection, state indicators; never decoration.

### 2.3 Risk-tier color + non-color encoding (accessibility requirement, `08` §8)

Risk tiers and conflicts must **never rely on color alone** — every tier has icon + text label + (where applicable) pattern/position:

| Tier | Color | Icon | Label | Usage |
|---|---|---|---|---|
| Read | neutral ink | ○/👁 (Phosphor `Eye`) | "Read-only" / no badge (default) | Summaries, searches |
| Auto | success green wash | ✓ (`Check`) | Toast "Done — Undo" | Single-item reversible |
| Confirm | warning amber wash + border | ⚠ (`Warning`) | "Needs your approval" header on diff card | Multi-item / cascading |
| Blocked | danger red wash + border | ⛔ (`Prohibit`) | "Blocked — review needed" | Bulk destructive; card explains the limitation AND carries the itemized confirm-tier breakdown inline as a secondary action ([Review itemized plan]) — the user never has to ask twice (decision log #5) |

Calendar conflicts: overlapping events get warning wash **plus** a `Warning` icon, "Overlaps with X" text, and a left 3px solid bar (position cue) — not color wash alone. Priority: ●/▲/○ glyph + text label alongside hue.

Contrast: body ≥4.5:1, large text ≥3:1, placeholders ≥4.5:1, focus ring visible against all surfaces. Verify with `ux/wcag-contrast.md` procedure before shipping any token change.

## 3. Typography scale

**One family (product rule).** Sans throughout; no display/body pairing, no serif default.

- Family: `font.family.sans = "Geist", "Satoshi", system-ui, sans-serif` — first available at build time. **Open-license constraint (OSS redistribution):** `Geist` (SIL OFL) is the primary; `Satoshi` (Fontshare free license) + `system-ui` carry the fallback. `Cabinet Grotesk` is deliberately excluded — it is not freely licensed for redistribution in a forkable self-hosted project; a deployer who holds a license may add it locally via a `font.family.sans.override` token, never in the default stack. **Inter discouraged as default** (acceptable only if project standardizes on Linear-style neutrality — document the exception). Mono: `font.family.mono = "Geist Mono", "JetBrains Mono", ui-monospace` (both SIL OFL) — for timestamps, token/cost figures, debug view only.
- Fixed rem scale (not fluid `clamp()` — product UI viewed at consistent DPI), ratio **1.125–1.2** between steps:

| Token | Size / line-height / weight | Usage |
|---|---|---|
| `size.font.display` | 28px / 1.2 / 650, `tracking -0.02em` | Page titles (Tasks, Calendar day header, Home greeting). Ceiling 96px never approached; floor tracking −0.04em respected |
| `size.font.h2` | 20px / 1.3 / 600 | Section headers, card group titles |
| `size.font.h3` | 16px / 1.4 / 600 | Card titles, event titles |
| `size.font.body` | 15px / 1.55 / 400 ink-primary | Default reading; prose capped `max-width 65–75ch` |
| `size.font.meta` | 13px / 1.45 / 500 ink-secondary | Timestamps, filter labels, toast text |
| `size.font.micro` | 12px / 1.4 / 600 uppercase `tracking 0.04em` | Eyebrows, tier badges, "BEFORE → AFTER" labels. Eyebrow count ≤1 per card (pre-flight rule) |

- `text-wrap: balance` on h1–h3; `text-wrap: pretty` on body/prose; tabular numbers (`font-variant-numeric: tabular-nums`) on all times/durations/costs; macOS `‑webkit-font-smoothing: antialiased` at root.
- Emphasis inside headlines: italic/bold of the **same** family — never inject a serif word for decoration. Serif banned unless a future brand brief explicitly names one.

## 4. Spacing / layout scale

Base unit 4px. Fixed spacing tokens (no fluid `clamp()` on app surfaces):

```
space.1 = 4px · space.2 = 8px · space.3 = 12px · space.4 = 16px
space.5 = 20px · space.6 = 24px · space.8 = 32px · space.12 = 48px
```

- Card padding: `space.4–space.6` (16–24px). Page gutter: `space.6` desktop, `space.4` mobile. Gap between cards in a list: `space.3`. Gap between sections: `space.8`.
- Vary spacing for rhythm (generous separations, tight groupings) — never uniform `space.4` everywhere.
- App shell: left nav (icons + labels, 232px) / top bar (page title + provider status dot + settings gear) / content max-width 1080px centered. Home chat column max-width 760px centered (readability + Claude-style familiarity); ambient cards sit in a right rail (desktop ≥1100px) or stack above chat (mobile).
- Lists with >5 items: group into 2–3 chunks with sparse dividers or card-per-group — never 10+ rows each with top+bottom hairlines.
- Skeletons must match content dimensions (no layout shift); crossfade to content 150–200ms.

## 5. Component states

Every interactive component ships all 9 states: **default, hover, focus, active/pressed, disabled, loading, error, empty/zero, success/confirmation**. Shipping default+hover+disabled only is a failure. Icon family: one set project-wide — **Phosphor** (fallback HugeIcons); Lucide discouraged; hand-rolled SVG banned.

### 5.1 Task cards
- Default: raised card, h3 title, meta row (due · priority glyph+label · tag pill), status checkbox left (40px hit area min).
- Hover: `shadow-border-hover` lift + `background-color 100ms ease-out` (no bounce, no entrance replay — high-frequency restraint).
- Focus: 2px accent ring, offset 2px, keyboard navigable (CRUD + approve/reject reachable by keyboard per `08` §8).
- Active: `scale(0.97)` press feedback on buttons, 160ms ease-out; `static` prop disables where distracting.
- Loading: skeleton matching card shape, shown immediately.
- Error: red input-adjacent border + human copy ("Couldn't save — connection to local store failed. [Retry] [Save as draft]").
- Empty: designed state (icon 30% + heading + one-line explanation + CTA), three variants distinguished: first-run ("Add your first task — or try 'Plan my day' below"), cleared ("All done 🎉"), no-results ("No matches — clear filters").
- Success/agent-touched: subtle accent wash highlight that **fades over ~2s** (recently agent-changed cue, `06` §6) — data itself indistinguishable, no "AI-tainted" badge.
- Board vs list share the same card; board columns use sunken wells.

### 5.2 Calendar events
- Pills/blocks with time in tabular mono; flexible events carry a `↔ Flexible` chip, fixed events a `🔒 Fixed` chip (text, not hue).
- Conflict: warning wash + Warning icon + "Overlaps with {title}" + left position bar; group cascade preview uses the same diff-card language as §5.4.
- Drag/resize: interruptible CSS transition (200ms ease-out); pointer capture, friction over hard stops, velocity-based dismiss where applicable.

### 5.3 Scoped command bar (Tasks/Calendar/Notes) — collapsed / expanded
- Collapsed: **single-line input**, full-width bottom-docked, placeholder "Tell me what to do…" (never a persistent chat panel eating page space). Left sparkle icon, right submit + provider-status dot. No open/close animation (high-frequency surface — Raycast rule).
- Expanded (on focus/typing): grows upward max ~40% viewport, shows scope chip row ("Scope: 6 visible · filter Urgent · 1 selected"), suggestion pills (2–3 contextual examples from MVP spec), inline clarifying-question rendering. Enter = send, Esc = collapse.
- States: disabled+labeled when no provider configured ("Configure a provider in Settings to enable the agent — your requests go to whichever provider you choose"); loading = thinking indicator (§7) + streaming plan steps within ~1s (`08` §1); error = inline specific message with cause + next step + Settings link (never generic).

### 5.4 Home chat interface
- Claude/ChatGPT-style thread as the **dominant element** (centered 760px column): user bubbles right (sunken tint), agent turns left (plain, no bubble chrome — data primary). Session/thread history persists; auto-scroll only if already at bottom.
- Ambient summary cards (today's plan · next 3 events · tasks due today) live in the right rail / stacked strip — visually quieter than chat (smaller type, neutral surface, single accent datum each), never competing. Each card has one CTA ("Open in Calendar", "Review plan") and an empty-state line, never a blank box.
- Agent turns that mutate show **inline confirmation/diff card** (§5.5) in-thread; result summaries follow as compact agent messages naming what changed and why ("because Maths conflicted with your 7:30 gym block"; "Since you usually study at 7pm…" when a preference was used).
- Provider switch mid-session: an in-flight request always completes against the provider/model it started with — a switch never re-routes or restarts a pending turn. The new provider takes effect on the next turn, announced by a small inline thread note ("Now using {provider} · {model}") with timestamp, so it is never ambiguous which model produced a given action. The same rule applies to scoped command bars: the pending command finishes on the old provider, the scope chip row notes the switch for the next command. Provider identity per turn is part of the debug/developer view (§8) and the action-log `session reference`.

### 5.5 Confirmation / diff cards (Approve / Edit / Cancel)
- Rendered **inline** (in-thread on Home, docked above command bar on scoped pages) — never a modal, never a wall of text.
- Anatomy: header (⚠ + "Needs your approval" + tier label + undoable-reassurance line "This will be undoable"), before→after rows per item (old value struck, new value in accent-ink, grouped under one card with sub-items for cascades, expandable), footer with three buttons: **Approve** (primary accent), **Edit** (secondary — toggles inline editable fields; re-classifies tier on change), **Cancel** (ghost). Keyboard: `⌘/Ctrl+Enter` approve, `Esc` cancel.
- Appear: `opacity 0 + translateY 12px + blur 4px → visible`, 200–250ms ease-out, stagger sub-items 30–80ms (max 500ms total; decorative, never blocks Approve). Resolve: subtle exit — `opacity 0, translateY −12px, blur`, 150ms (~75% of enter). Interruptible transitions (not keyframes) so rapid approve/cancel never janks. `prefers-reduced-motion`: keep opacity fade, drop translate/blur.
- Cancel writes no mutation log entry (debug-only "proposed but cancelled" trace allowed).
- Blocked-tier variant: same inline card in danger wash, but the footer replaces **Approve** with **[Review itemized plan]** (primary — converts the blocked bulk request into an explicit itemized confirm-tier diff the user reviews item by item) plus **[Do manually]** hint text naming the manual path, plus **Cancel** (ghost). The itemized list is rendered inline in the card body, not behind a second round-trip — per decision log #5 the breakdown is auto-proposed, never gated on the user asking for it.

### 5.6 Toasts for auto-executed actions
- Bottom-left stack, `Sonner`-style: "Marked 'Buy groceries' as done — [Undo] [View history]", auto-dismiss, dismissible, action affordance inline. Enter 200ms ease-out; exit 150ms; CSS transitions (interruptible — rapid successive toasts retarget smoothly). Never the only feedback channel (log entry + subtle card highlight accompany it).

### 5.7 Error states
- Copy rule (all failures, `20-error-handling`): message must answer **what failed, why, what next** — else unacceptable. Inline errors next to the causing field/action; per-section error boundaries (one section failing never blanks the page); every transient error offers [Retry] in-surface. Provider failure: "Couldn't reach {provider}: {cause}. [Open Settings → AI Provider]" — app stays fully usable manually. Malformed tool call / ambiguous request: agent asks a clarifying question rather than guessing (esp. before Confirm+). Partial multi-step failure: "Did X, didn't do Y — [Roll back X]" with precise accounting. Undo-unavailable: explains why (downstream edit invalidated snapshot), never silent.

### 5.8 Empty states
- Anatomy everywhere: icon + heading + one-line explanation + CTA (manual action **or** example agent command, `06` §5). First-run gates agent features behind provider config with a friendly why-explanation. No blank tables/cards/panels ever.

## 6. Borders (Impeccable `/border` equivalent — `surfaces.md` conventions)

- **Elevation without heavy borders:** cards, command bar, confirmation cards, modals, dropdowns use **shadow-as-border** (adapts to any background via transparency; solid border colors don't):
  ```css
  --shadow-border:
    0 0 0 1px oklch(0 0 0 / 0.06),
    0 1px 2px -1px oklch(0 0 0 / 0.06),
    0 2px 4px 0 oklch(0 0 0 / 0.04);
  --shadow-border-hover:
    0 0 0 1px oklch(0 0 0 / 0.08),
    0 1px 2px -1px oklch(0 0 0 / 0.08),
    0 2px 4px 0 oklch(0 0 0 / 0.06);
  ```
  Hover transitions `box-shadow` 150ms ease-out only (never `transition: all`).
- **Real borders reserved for:** hairline dividers between list rows/groups (`1px oklch(0 0 0 / 0.08)` — one side only, never top+bottom per row), form-input outlines (`1px oklch(0 0 0 / 0.14)`, error `danger`, focus `2px accent ring`), table cell boundaries, image outlines (`1px oklch(0 0 0 / 0.1)` inset via `outline-offset: -1px` — pure black/white only, never palette-tinted).
- **Radius (concentric rule `outer = inner + padding`; >24px padding → independent radii):**
  ```
  radius.sm = 8px   (chips, pills, tag, inline badges)
  radius.md = 12px  (inputs, buttons, toasts, inner card content)
  radius.lg = 16–20px (cards, confirmation cards, command bar, chat bubbles)
  radius.xl = 24px  (Home ambient cards, modals, onboarding panels)
  ```
  Nested example: card `radius.lg (20px)` + `padding 8px` → inner content `12px`. Mismatched equal radii on nested layers is a ship-blocker.
- Buttons with text+icon: icon-side padding = text-side − 2px (optical alignment). Min hit area 40×40px desktop / 44×44px touch (pseudo-element extension, never overlapping neighbors). Icon stroke matches text weight (1.5 regular / 2 semibold / 2.5 bold); outline default, fill active; directional icons flip in RTL.

## 7. Animation (Impeccable `/animation` equivalent — `STANDARDS.md` + `animate.md` + micro-polish values)

Intent-first: every animation must justify itself as **feedback, focus, spatial orientation, or explanation** — "looks cool" on a frequent surface is invalid. Keyboard-initiated and 100+/day actions: **no animation, ever**.

- Curves (strong customs, never CSS defaults, never `ease-in` on UI):
  `--ease-out: cubic-bezier(0.23, 1, 0.32, 1)` (enter/exit), `--ease-in-out: cubic-bezier(0.77, 0, 0.175, 1)` (on-screen moves), `--ease-drawer: cubic-bezier(0.32, 0.72, 0, 1)` (drawers/sheets).
- Durations (100/300/500 rule; UI stays <300ms; exits ~75% of enters):
  press feedback 100–160ms · tooltips/popovers 125–200ms · dropdowns/command-bar expand 150–250ms · diff cards 200–250ms in / 150ms out · modals/drawers 200–500ms · page transitions ≤300ms crossfade+rise.
- Physics: never `scale(0)` — enter from `0.9–0.97 + opacity 0`; popovers scale from trigger origin (modals exempt, center origin); press `scale(0.96)` (never below 0.95) with `static` opt-out; contextual icon swaps exactly `scale 0.25→1, opacity 0→1, blur 4px→0`, spring `duration 0.3, bounce 0`; stagger 30–80ms, cap 500ms total, never blocks interaction; only animate `transform` + `opacity` (GPU); interruptible **transitions** for interactive elements, keyframes for one-shot entrances only; `AnimatePresence initial={false}` so state swaps don't replay on page load; library `motion/react` (or CSS cross-fade if no motion dep — never add one just for icons).
- Specified behaviors:
  - *Confirmation cards appearing/resolving:* §5.5 values above.
  - *Task/event state transitions:* card moves/completes via `transform` FLIP-style shift 200ms ease-in-out + success wash fade; checkbox toggle = icon cross-fade (exact values above) + strike-through color transition (motion never the only cue — icon + label persist).
  - *Agent "thinking" indicators:* three-dot pulse via **opacity only** (≤150ms cycle, no positional bounce), plus streaming plan-step text within ~1s of submit; skeleton for anticipated content shape; faster-feeling spinner only if skeleton infeasible (single small spinner, no logo theater).
  - *Page transitions:* 150–200ms crossfade + 12px rise ease-out; no scroll-jacking, no layout-property animation; `IntersectionObserver`/Motion `whileInView` for reveals (never `scroll` listeners driving React state).
- `prefers-reduced-motion: reduce` (non-negotiable): keep opacity/color static cues, drop transform/blur/stagger; gate hover motion behind `(hover: hover) and (pointer: fine)`.

## 8. The Home AI hub layout (spec)

```
┌ top bar: FlowOS · page title · provider dot · cost/session · settings ┐
┌──────────────────────────────┬───────────────────┐
│  ambient strip (stacked on   │                   │
│  mobile; right rail ≥1100px) │   chat column     │
│  ┌ today’s plan ┐            │   (760px max,     │
│  ┌ next 3 events ┐           │   dominant)       │
│  ┌ tasks due ┐               │                   │
│  provider empty-state here   │  thread + inline  │
│  when unconfigured           │  diff cards +     │
│                              │  summaries        │
├──────────────────────────────┴───────────────────┤
│  home command input (chat-style, multiline)      │
└──────────────────────────────────────────────────┘
```

- Chat is dominant; ambient cards are quiet summaries with one datum + one CTA each, expandable to their module. No workspace data preloaded — ambient shows counts/highlights; agent pull-tools (`getWorkspaceContext` etc.) on demand.
- First-run: provider gate card (why + [Open Settings]) replaces agent input affordance; manual UI remains usable.
- Thread entries: agent mutations render diff card + plain-language summary (cause + preference citation when used); toasts confirm auto-tier actions; Action History link per turn (debug/developer view exposes input context, plan, tool calls/results per `08` §6).

## 9. Self-extension surface design (UI/UX only — pipeline explicitly NOT built)

Per `17-self-extension-vision.md`, design the end-to-end screens now so the future pipeline inherits the visual language. Heavier-weight than Confirm-tier — closer to **reviewing a pull request** than approving a task edit. Four screens:

1. **Spec-review screen** — plain-language module spec (entities, calculations, views, tools it will need) in a document-style panel reusing diff-card anatomy at large scale: section-by-section before(∅)→after(proposed) rows, per-section Edit affordances, global Approve-spec / Request-changes / Cancel. Copy states "No code has been written yet." Progress stepper at top: `Spec → Design → Build → Test → Review → Install` (current step highlighted, text + position, not color alone).
2. **Sandboxed-test status view** — build/test pipeline rendered as a checklist timeline (queued → building → testing → passed/failed), each step with duration + log expander; test results as pass/fail rows with icon+label; banner states "Sandboxed — your real data is untouched" persistently. Failure = inline error + [Retry build] + [Edit spec] (never silent, never auto-install on partial pass).
3. **Code/permissions review screen** — two-pane: left behavior summary ("This module will…", permission justifications per requested scope with Allow/Deny toggles — e.g. Calendar-write denied still installs a limited version), right code diff in mono with line numbers (for developers) + collapsible "What changed in plain language" (for everyone). Install button disabled until all requested permissions are explicitly triaged. Version + rollback note ("v0.1.0 — uninstall cleanly removes tools/UI; its data archives") shown pre-install.
4. **Installed-module appearance** — module registers as: a nav item (same icon+label language as core modules), an optional Home ambient widget (same quiet card treatment as §8), and a scoped command bar on its page (same collapsed single-line pattern as §5.3). Uninstall/update entry in Settings with version history. Visually indistinguishable from core — no "AI-generated" stigma badge; provenance lives in Settings detail, not as a permanent visual mark (mirrors `06` §6 consistency rule).

All four screens reuse: shadow-border cards, concentric radii, accent-only-for-primary-actions, icon+label tier encoding, 9-state coverage, reduced-motion handling. Nothing here implies pipeline logic — no SDK, sandbox runtime, or installer is specified or built in this pass.

## 10. Tokens appendix (implementation-ready seed)

```css
:root {
  --color-canvas-base: oklch(0.99 0.004 250);
  --color-surface-raised: oklch(1 0 0);
  --color-surface-sunken: oklch(0.97 0.006 250);
  --color-surface-panel: oklch(0.96 0.008 250);
  --color-ink-primary: oklch(0.25 0.02 250);
  --color-ink-secondary: oklch(0.45 0.02 250);
  --color-ink-faint: oklch(0.55 0.02 250);
  --color-accent-500: oklch(0.62 0.16 250);
  --color-accent-contrast: oklch(1 0 0);
  --color-accent-100: oklch(0.94 0.04 250);
  --shadow-border: 0 0 0 1px oklch(0 0 0 / 0.06), 0 1px 2px -1px oklch(0 0 0 / 0.06), 0 2px 4px 0 oklch(0 0 0 / 0.04);
  --shadow-border-hover: 0 0 0 1px oklch(0 0 0 / 0.08), 0 1px 2px -1px oklch(0 0 0 / 0.08), 0 2px 4px 0 oklch(0 0 0 / 0.06);
  --radius-sm: 8px; --radius-md: 12px; --radius-lg: 20px; --radius-xl: 24px;
  --ease-out: cubic-bezier(0.23, 1, 0.32, 1);
  --ease-in-out: cubic-bezier(0.77, 0, 0.175, 1);
  --ease-drawer: cubic-bezier(0.32, 0.72, 0, 1);
  --font-sans: "Geist", "Satoshi", system-ui, sans-serif; /* open-licensed stack only — see §3; Cabinet Grotesk via deployer-local override if licensed */
  --font-mono: "Geist Mono", "JetBrains Mono", ui-monospace, monospace;
}
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { transition-duration: 0.01ms !important; animation-duration: 0.01ms !important; }
}
```

Pre-flight gate before implementation: eyebrow count ≤1/card, contrast verified, single accent locked, CTA hierarchy single-primary, copy audit (no "Something went wrong"), `prefers-reduced-motion` path tested, skeletons dimension-matched, no `transition: all`, no `will-change: all`, one icon family, hit areas ≥40px.

## 11. Convention traceability (Impeccable-equivalence map)

| Prompt requirement | Convention applied | Source file |
|---|---|---|
| `/init` (design system structure) | PRODUCT register=`product`; DESIGN seeded pre-implementation from docs (no code to crawl); brief-inference physical scene drove light-theme call | `prompts/reference/init.md`, `philosophy/brief-inference.md`, `visual-principles/v2-additions.md` |
| `/border` (soft border treatment) | Shadow-as-border for elevation; real borders only for dividers/inputs; concentric radius; image outlines; hit areas; optical alignment | `make-interfaces-feel-better/surfaces.md` |
| `/animation` (micro-animations) | Intent-gated motion; STANDARDS curves/durations; animate.md 100/300/500 rule; exact icon/press/stagger values; interruptibility; restraint; reduced-motion | `motion/STANDARDS.md`, `prompts/reference/animate.md`, `make-interfaces-feel-better/animations.md` |
| Color system | OKLCH, anti-cream, tinted neutrals, LILA rule, restrained product strategy, state vocabulary, contrast verification | `visual-principles/color.md`, `ux/wcag-contrast.md` |
| Typography / spacing | One-family product type, fixed rem scale 1.125–1.2, balance/pretty wrap, tabular nums; Daily-App density, varied rhythm | `visual-principles/typography.md`, `visual-principles/spacing.md` |
| Component states | 9-state coverage, skeleton-first loading, designed empty states, human error copy, inline errors, per-section boundaries | `patterns/ui-states.md`, `patterns/feedback-and-state.md` |
| Tokens | CTI hierarchy, primitive/alias/component tiers, W3C naming | `ux/design-tokens.md` |
| Polish | Phosphor-only icons, stroke-weight matching, `transition-[specific-props]`, font smoothing | `make-interfaces-feel-better/icons.md`, `typography.md`, `performance.md` |

Where this file conflicts with `06-ux-product-principles.md` on intent, `06` wins; where it specifies mechanics, this file wins (per project-note arbitration rule).
