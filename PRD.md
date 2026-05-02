# 📱 FlowOS – Product Requirements Document (PRD)

## 1. Overview
FlowOS is a high-performance **personal life operating system** designed to synchronize a user's biological state with their daily execution. It goes beyond simple task management by integrating habits, journaling, and proactive AI intelligence to optimize consistency, focus, and reflection.

---

## 2. Strategic Goals
*   **Biological Synchronization**: Align daily actions with energy levels and circadian rhythms.
*   **Execution Mastery**: Provide a frictionless "Flow" for completing daily rituals and objectives.
*   **Memory-Augmented Intelligence**: Use AI that remembers user preferences, history, and patterns.
*   **Reflective Growth**: Capture daily "vibes" and insights to drive long-term consistency.

---

## 3. Core Features

### 3.1 Neuro-Dashboard (Home)
*   **Bio-Optimization Stats**: Real-time display of "Sync %" and "Flow Hours."
*   **Oracle Insights**: Proactive AI suggestions (e.g., "High focus window opening in 14 minutes").
*   **Current Protocol**: Visual progress tracking of the active daily state.

### 3.2 Execution Flow (Tasks & Habits)
*   **Unified List**: A combined view of "Daily Rituals" (Habits) and "Objectives" (Tasks).
*   **Priority & Energy**: Tasks tagged with priority levels and energy requirements (1-10) for AI scheduling.
*   **Time-Blocking**: Categorization into Morning, Deep Work, Evening, and Night.
*   **Deployment Flow**: Smooth UI for adding/toggling rituals and objectives.

### 3.3 Reflection System (Journal)
*   **Daily Capture**: Narrative journaling interface to document the journey.
*   **Vibe Rating**: 1-10 scale rating of the day's biological/mental state.
*   **AI Insight Injection**: Automated AI analysis of journal entries saved back to the record.
*   **Insight History**: Scrollable timeline of past reflections and scores.

### 3.4 Flow Intelligence (AI Assistant)
*   **Oracle Chat**: Interactive interface for optimizing routines and solving productivity blocks.
*   **3-Layer Memory Architecture**:
    *   **Layer 1 (Short-Term)**: Sliding window of recent message history for immediate context.
    *   **Layer 2 (Rolling Summary)**: Automated summarization of long interactions every 12 messages to prevent context dilution.
    *   **Layer 3 (Long-Term)**: Context-aware retrieval of importance-scored "Memories" and user facts.
*   **Intent Classification**: Dynamic response tailoring based on user intent (Planning, Instructional, Emotional, or General Chat).
*   **State Summarization**: Automated "Oracle Insight" and "Reminder Banners" based on the system's understanding of user state.
*   **Configurable Providers**: Support for multiple AI models and custom API configurations.

### 3.5 Identity & Security (Auth)
*   **Multi-Mode Auth**: OTP-based verification, secure login, and signup.
*   **Session Recovery**: Integrated "Forgot Password" and "Security Key Update" flows.
*   **Local Persistence**: Reliable on-device data storage with Room database for maximum privacy and performance.

---

## 4. System Architecture (Screens)

### 4.1 Session Management
*   Local-only profile creation.
*   Secure on-device data isolation.
*   Optional backup/export flows.

### 4.2 Neurostate (Dashboard)
*   High-level bio-optimization summary.
*   Stat cards (Sync, Flow, Vibe).
*   Oracle Insight banner.

### 4.3 Execution Flow (The "Flow")
*   Scrollable list of rituals and objectives.
*   Quick-add dialog with "Deploy" confirmation.
*   Progress-based animations for completed rituals.

### 4.4 Reflection (Journal)
*   "Document Journey" text area.
*   Vibe slider (1-10).
*   Past Insights feed with AI tags.

### 4.5 Flow Intelligence (AI Chat)
*   Chat interface with typing indicators.
*   Oracle Reminder Banners.
*   Message history with local persistence.

### 4.6 System Settings (Profile)
*   Active Identity (Email/User info).
*   Security status overview.
*   Session termination.

---

## 5. Tech Stack
### Frontend
*   **Kotlin / Jetpack Compose**: Modern declarative UI.
*   **Material 3**: Premium design system components.
*   **Room Database**: Local persistence for offline-first usage.

### Data Storage
*   **Room Database**: Primary data store for all user information.
*   **Encrypted SharedPreferences**: Secure storage for API keys and sensitive settings.

### AI Integration
*   **AI Studio / Retrofit**: Connection to advanced LLMs.
*   **Context Manager**: Custom logic for memory and summary management.

---

## 6. Database Schema (Room)

### tasks (LocalTask)
*   `id` (UUID), `userId`, `title`, `category`
*   `completed` (Boolean), `priority` (Int), `energyRequired` (Int 1-10)
*   `timeBlock` (Morning/Deep Work/Evening/Night)
*   `lastUpdated` (Long), `isDeleted` (Boolean)

### habits (LocalHabit)
*   `id`, `userId`, `name`, `streak`
*   `completedToday` (Boolean), `timeBlock`

### journal_entries (LocalJournalEntry)
*   `id`, `userId`, `content`, `rating` (1-10)
*   `aiInsight` (String?), `date` (ISO), `timestamp`

### ai_configs (LocalAiConfig)
*   `providerName`, `model`, `apiKeyEncrypted`, `isActive`

### ai_context (Messages, Memories, Summaries)
*   `ai_messages`: Role (user/assistant), content, timestamp.
*   `ai_memories`: Key-Value storage for importance-scored user facts.
*   `conversation_state`: Running summary of the current session state.

---

## 7. UX & Premium Aesthetics
*   **Theme**: Strict OLED Dark Theme (#0B0B0F).
*   **Accents**: Electric Purple (#7C5CFF) and Deep Blue gradients.
*   **Interactions**: Micro-animations for task completion, slide-ins, and radial gradients.
*   **Typography**: Bold, high-contrast semantic headings.

---

## 8. Development Progress
*   [x] Local-first session management.
*   [x] Room database with reliable on-device persistence.
*   [x] Unified Execution Flow (Habits + Tasks).
*   [x] Reflection/Journal system with mood tracking.
*   [x] AI Context Manager (Memories & Summaries).
*   [x] Notification Scheduler & Reminder Workers.
*   [ ] Smart Scheduling Logic (AI-driven task ordering).
*   [ ] Advanced Biometric Integration.

---

## 9. Success Criteria
Users achieve "Flow" by consistently completing 80%+ of their rituals and documenting their journey, while leveraging AI Oracle insights to avoid energy crashes.
