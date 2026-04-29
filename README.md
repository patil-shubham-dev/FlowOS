# FlowOS - AI-Auditable Productivity & Habit Tracking Platform

> **Neurostate Optimization Through Intelligent Task & Habit Synchronization**

FlowOS is a cutting-edge productivity application that combines task management, habit tracking, and AI-powered insights into a unified platform. Built with Kotlin and Jetpack Compose, it provides a modern, interactive interface for managing your daily workflow while leveraging AI to provide intelligent recommendations and insights.

## 🎯 Core Features

### 1. **Task Management System**
- **Smart Task Creation**: Create tasks with priority levels (High/Medium/Low), energy requirements, and time blocks
- **Time Block Organization**: Organize tasks by Morning, Afternoon, Evening, or Night blocks
- **Priority Tracking**: Visual indicators for task priority levels
- **Progress Synchronization**: Real-time sync with backend infrastructure
- **Completion Tracking**: Mark tasks as complete and track your productivity metrics

### 2. **Habit Tracking & Streaks**
- **Daily Rituals**: Build consistent habits with streak tracking
- **Time-Block Scheduling**: Assign habits to specific times of day
- **Streak Visualization**: Visual feedback on your habit consistency
- **Completion Status**: Toggle habits as complete and watch your streaks grow
- **Habit Analytics**: Track habit completion rates and patterns

### 3. **AI-Powered Journal (Reflection)**
- **Rich Text Editing**: Notion-style editor for journal entries with formatting support
- **Voice Input**: Transcribe your thoughts directly into journal entries
- **AI Enhancement**: Let the AI refine and improve your journal entries
- **Mood Tracking**: Rate your daily vibe on a scale of 1-10
- **Streak Tracking**: Build journaling consistency with daily streaks
- **Smart Insights**: Get AI-generated insights from your journal entries

### 4. **Flow Oracle - AI Assistant**
- **Conversational Interface**: Claude-like chat interface for intelligent assistance
- **Real-time Intelligence**: Get instant insights about your tasks and habits
- **Smart Suggestions**: Receive AI-powered recommendations for optimization
- **Command Execution**: Control your entire app through natural language commands
- **Context Awareness**: The AI understands your tasks, habits, and journal entries
- **Tool Integration**: Execute actions directly through conversational prompts

### 5. **Dashboard & Analytics**
- **Brain State Orb**: Visual representation of your overall productivity state
- **Sync Progress**: Real-time synchronization status indicator
- **Flow Score**: Aggregate productivity metric combining all activities
- **Daily Rituals Overview**: Quick view of your habit completion
- **Priority Objectives**: See your most important tasks at a glance
- **AI Insights Banner**: Get actionable intelligence from the Oracle

## 🏗️ Architecture

### Technology Stack
- **Frontend**: Kotlin, Jetpack Compose
- **Database**: Room (Local), Supabase (Remote)
- **Backend API**: Supabase REST API
- **AI Integration**: OpenAI API (GPT-4, Claude)
- **Authentication**: Supabase Auth with OAuth
- **State Management**: ViewModel + Flow/StateFlow

### Project Structure

```
FlowOS/
├── app/src/main/java/com/todo/dailyroutine/
│   ├── data/
│   │   ├── local/
│   │   │   ├── dao/          # Room DAOs for database operations
│   │   │   ├── entity/       # Local data entities
│   │   │   └── db/           # Database configuration
│   │   ├── remote/
│   │   │   ├── api/          # Supabase REST API client
│   │   │   └── dto/          # Data transfer objects
│   │   ├── repository/       # Data repositories (TaskRepository, HabitRepository, etc.)
│   │   ├── model/            # UI-layer data models
│   │   └── session/          # Session and authentication management
│   ├── domain/
│   │   ├── ai/
│   │   │   ├── OracleToolExecutor.kt    # AI tool execution engine
│   │   │   ├── PromptBuilder.kt         # Prompt generation
│   │   │   └── MemoryManager.kt         # Conversation memory
│   │   └── usecase/          # Business logic use cases
│   └── ui/
│       ├── screens/          # Composable screens
│       │   ├── DashboardScreen.kt
│       │   ├── FlowScreen.kt (Tasks & Habits)
│       │   ├── JournalScreen.kt
│       │   └── AiScreen.kt (Oracle)
│       ├── components/       # Reusable UI components
│       ├── theme/            # Design system and theming
│       └── viewmodel/        # ViewModels for state management
└── build.gradle.kts          # Build configuration
```

## 🚀 Getting Started

### Prerequisites
- Android Studio 2023.1 or later
- Android SDK 33+
- Java 17+
- Kotlin 1.9+

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/patil-shubham-dev/FlowOS.git
   cd FlowOS
   ```

2. **Configure Android SDK**
   ```bash
   # Update local.properties with your Android SDK path
   echo "sdk.dir=/path/to/android-sdk" > local.properties
   ```

3. **Build the project**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Install on device/emulator**
   ```bash
   ./gradlew installDebug
   ```

### Environment Setup

Create a `.env` file in the project root with:
```
SUPABASE_URL=your_supabase_url
SUPABASE_ANON_KEY=your_supabase_anon_key
OPENAI_API_KEY=your_openai_api_key
```

## 🔧 Recent Fixes & Improvements

### Bug Fixes
- ✅ **Fixed Repository Methods**: Added overloaded methods for `toggleHabit(habitId: String)` and `toggleTask(taskId: String)` to support AI tool execution
- ✅ **Fixed Habit Deletion**: Implemented proper soft-delete mechanism using syncStatus = 3
- ✅ **Fixed Task Deletion**: Proper handling of task deletion through repository layer
- ✅ **Fixed AI Tool Executor**: Corrected type mismatches in OracleToolExecutor for habit and task operations
- ✅ **Fixed Build Configuration**: Updated gradle.properties with correct Java home path for the build environment

### Enhancements
- ✅ **Enhanced TaskRepository**: Added support for priority, energy, and timeBlock parameters
- ✅ **Enhanced HabitRepository**: Added timeBlock support and additional deletion methods
- ✅ **Improved AI Integration**: Better tool execution with proper error handling
- ✅ **Fixed Sync Status Management**: Proper handling of syncStatus for local-remote synchronization

## 📱 Usage Guide

### Creating a Task
1. Navigate to the **Flow** tab
2. Tap the **+** button to create a new task
3. Enter task title, select category, priority, and energy level
4. Assign to a time block (Morning/Afternoon/Evening/Night)
5. Tap **Save** to create the task

### Adding a Habit
1. Go to **Flow** tab
2. Scroll to the **Daily Rituals** section
3. Tap **+ Add Ritual**
4. Enter habit name and select time block
5. Tap **Create** to start tracking

### Writing a Journal Entry
1. Navigate to the **Reflection** tab
2. Rate your current vibe (1-10 scale)
3. Use the rich text editor to write your thoughts
4. Tap **🎤** to add voice input
5. Tap **✨** to enhance with AI
6. Tap **Document Reflection** to save

### Using the AI Assistant
1. Open the **Oracle** tab
2. Type your command or question
3. The AI will:
   - Analyze your current tasks and habits
   - Provide insights and recommendations
   - Execute actions (create tasks, complete habits, etc.)
   - Generate reports and suggestions

**Example Commands:**
- "Create a task for exercise tomorrow morning"
- "What's my productivity score today?"
- "Optimize my schedule for maximum flow"
- "Give me insights on my habits"
- "Complete my morning meditation ritual"

## 🤖 AI Assistant Capabilities

The **Flow Oracle** is powered by advanced language models and can:

### Task Management
- Create, update, and delete tasks via natural language
- Suggest optimal task scheduling based on energy levels
- Identify task dependencies and priorities

### Habit Optimization
- Recommend new habits based on your goals
- Suggest optimal times for habit completion
- Analyze habit patterns and streaks

### Journal Analysis
- Extract key themes and patterns from entries
- Provide emotional insights and recommendations
- Generate weekly/monthly summaries

### Productivity Insights
- Calculate personalized flow scores
- Identify productivity patterns
- Suggest optimization strategies

### Reporting
- Generate daily/weekly/monthly reports
- Visualize productivity trends
- Export insights and recommendations

## 🔐 Data Synchronization

FlowOS uses a sophisticated sync mechanism:

### Sync Status Codes
- **0**: Synced with server
- **1**: Pending creation on server
- **2**: Pending update on server
- **3**: Marked for deletion (soft delete)

### Sync Flow
1. Local changes are marked with appropriate syncStatus
2. Background sync service detects pending changes
3. Changes are sent to Supabase backend
4. Server confirms sync, status updated to 0
5. Offline changes are queued and synced when online

## 🎨 Design System

### Color Palette
- **Primary Accent**: `#7C5CFF` (Purple)
- **Success**: `#30D158` (Green)
- **Info**: `#5B9CFF` (Blue)
- **Warning**: `#FF9500` (Orange)
- **Background Base**: `#0A0E27` (Dark Blue)
- **Surface Card**: `#1A1F3A` (Elevated Dark)

### Typography
- **Display Large**: 32sp, Bold
- **Title Large**: 22sp, Bold
- **Title Medium**: 16sp, Semi-Bold
- **Body Medium**: 14sp, Regular
- **Label Medium**: 12sp, Medium
- **Label Small**: 11sp, Medium

## 📊 Data Models

### Task
```kotlin
data class TaskItem(
    val id: String,
    val userId: String,
    val title: String,
    val category: String,
    val completed: Boolean,
    val priority: Int = 2,
    val energyRequired: Int = 5,
    val timeBlock: String = "Morning",
    val syncStatus: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
```

### Habit
```kotlin
data class HabitItem(
    val id: String,
    val userId: String,
    val name: String,
    val streak: Int = 0,
    val completedToday: Boolean = false,
    val timeBlock: String = "Morning",
    val syncStatus: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
```

### Journal Entry
```kotlin
data class JournalEntry(
    val id: String,
    val userId: String,
    val content: String,
    val vibe: Int = 5,
    val timestamp: Long = System.currentTimeMillis(),
    val enhanced: Boolean = false
)
```

## 🛠️ Development

### Building from Source

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test

# Generate APK
./gradlew bundleRelease
```

### Key Dependencies
- `androidx.compose.ui:ui:1.6.0`
- `androidx.room:room-runtime:2.6.1`
- `com.squareup.retrofit2:retrofit:2.10.0`
- `io.coil-kt:coil-compose:2.5.0`
- `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3`

## 📝 API Reference

### TaskRepository
```kotlin
// Add a new task
suspend fun addTask(
    title: String,
    category: String,
    priority: Int = 2,
    energy: Int = 5,
    timeBlock: String = "Morning"
): Result<Unit>

// Toggle task completion
suspend fun toggleTask(task: TaskItem): Result<Unit>
suspend fun toggleTask(taskId: String): Result<Unit>

// Fetch tasks from server
suspend fun fetchTasks(): Result<Unit>
```

### HabitRepository
```kotlin
// Add a new habit
suspend fun addHabit(
    name: String,
    timeBlock: String = "Morning"
): Result<Unit>

// Toggle habit completion
suspend fun toggleHabit(habit: HabitItem): Result<Unit>
suspend fun toggleHabit(habitId: String): Result<Unit>

// Delete a habit
suspend fun deleteHabit(habitId: String): Result<Unit>
```

## 🐛 Known Issues & Limitations

1. **Offline Mode**: Limited functionality when offline; sync queues changes
2. **Rich Text Editor**: Some advanced formatting options not yet implemented
3. **Voice Input**: Requires microphone permissions; may vary by device
4. **AI Response Time**: Depends on API latency; typically 1-3 seconds
5. **Data Export**: Currently limited to JSON format

## 🗺️ Roadmap

### Phase 1 (Current)
- ✅ Core task and habit management
- ✅ Basic journal functionality
- ✅ AI assistant integration
- ✅ Local-remote sync

### Phase 2 (Planned)
- 📅 Advanced scheduling and time management
- 📊 Enhanced analytics and reporting
- 🎨 Customizable themes and layouts
- 🔔 Smart notifications and reminders
- 📱 Mobile app optimization

### Phase 3 (Future)
- 🌐 Web dashboard
- 👥 Team collaboration features
- 🤖 Advanced AI features (predictive analytics)
- 📈 Integration with fitness trackers
- 🎯 Goal setting and tracking

## 🤝 Contributing

We welcome contributions! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style
- Follow Kotlin conventions
- Use meaningful variable names
- Add comments for complex logic
- Write unit tests for new features

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 👨‍💻 Author

**Shubham Patil**
- GitHub: [@patil-shubham-dev](https://github.com/patil-shubham-dev)
- Email: contact@shubhampatil.dev

## 🙏 Acknowledgments

- Built with [Jetpack Compose](https://developer.android.com/jetpack/compose)
- Powered by [Supabase](https://supabase.com) for backend
- AI capabilities via [OpenAI](https://openai.com)
- Design inspiration from modern productivity apps

## 📞 Support

For issues, questions, or suggestions:
1. Check existing [GitHub Issues](https://github.com/patil-shubham-dev/FlowOS/issues)
2. Create a new issue with detailed description
3. Include device info, Android version, and reproduction steps

---

**Last Updated**: April 29, 2026
**Version**: 1.0.0
**Status**: Active Development

Made with ❤️ for productivity enthusiasts
