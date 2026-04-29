# FlowOS Audit Report

## 1. Logic & Bug Analysis

### AI & Memory System
- **Memory Pipeline**: Current implementation in `AiContextManager` is rule-based and keyword-driven, not vector-based. It lacks the sophisticated classification, importance thresholding, and deduplication required.
- **Deduplication**: No logic exists to update similar memories; it just inserts new ones.
- **Memory Decay**: Simplistic global decay on every user message without importance-based logic.
- **Provider Detection**: `AiViewModel` has basic detection but doesn't persist it properly to local storage.
- **Config Persistence**: `AiConfigRepository` is heavily dependent on Supabase. Saves don't write to Room, and local reads lose custom headers.
- **Tool Execution**: `OracleToolExecutor` has several placeholders (`get_daily_summary`, `update_vibe_score`, `schedule_reminder`) that don't perform actual logic.

### Data & Sync
- **Mappers**: `Mappers.kt` is missing critical fields (energy, timeBlock, scheduledTime, sortOrder) during conversion, causing data loss on UI round-trips.
- **Supabase Dependency**: The project is tightly coupled to Supabase for Auth, Tasks, Habits, and AI Config, violating the "Local-first" requirement.
- **Journal Streaks**: `JournalRepository` always resets `longestStreak` to 0.

### Navigation & Auth
- **Start Destination**: Always starts at `AuthScreen` even if logged in.
- **Auth Flows**: `AuthScreen` is missing UI for Forgot Password and Reset Password, though the ViewModel supports them.
- **Onboarding**: Cosmetic only; doesn't persist data or affect the user profile.

## 2. UI/UX & Mobile Optimization

### Layout & Spacing
- **Scaffold Rigidness**: `DashboardScaffold` uses fixed 56dp top spacer and 24dp horizontal padding, which might not look premium on all devices.
- **Window Insets**: No handling for status bar, navigation bar, or IME (keyboard) padding.
- **Component Density**: Some components like `BrainStateOrb` (280dp) might be too large for smaller mobile screens.

### Premium Feel
- **Placeholders**: Profile screen has dead buttons.
- **Transitions**: Navigation transitions are basic.
- **Feedback**: Lack of haptic feedback or sophisticated animations during state changes.

## 3. Plan for Phase 3 & 4
1. **Rebuild Memory System**: Implement a local "Vector-like" DB using Room with the requested classification and filtering logic.
2. **Local-First Migration**: Move all storage (Tasks, Habits, AI Config) to Room-first, treating Supabase as an optional sync layer (or removing it as requested).
3. **UI Overhaul**: Implement a more dynamic, inset-aware layout system with premium animations and mobile-first spacing.
4. **Complete Features**: Fully implement all tool executors and the multi-provider detection/selection UI.
