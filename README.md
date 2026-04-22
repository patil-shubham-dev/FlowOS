# Daily Routine AI (Android)

Production-style Android app using Kotlin + Jetpack Compose + MVVM + Supabase + Retrofit AI integration.

## Stack
- Kotlin, Jetpack Compose, Material 3
- MVVM with repositories
- Room local database (offline-first reads)
- Supabase (Auth + PostgREST database)
- Retrofit + OkHttp (Supabase REST + Google AI Studio)
- WorkManager notifications (AI-controlled reminder schedule)

## Setup
1. Open project in Android Studio (JDK 17).
2. Sync Gradle.
3. Run on emulator/device (API 26+).

## Security Notes
- Android app uses **Supabase anon key** only.
- Never include the Supabase service role key in client apps.

## AI-controlled Notifications
- All reminder schedules are generated from AI Assistant responses.
- AI returns structured JSON including reminder state/time/message.
- App applies AI plan with WorkManager and local persisted settings.
- AI assistant now uses an in-app system prompt for proactive routine coaching, task/habit optimization, and notification governance.

## Premium UX additions
- Adaptive dashboard widget updates by time-of-day (morning/midday/evening).
- AI-generated pinned "Next Best Action" card on dashboard.
- Richer motion: staggered dashboard reveal and smoother route-level transitions.

## Supabase Tables (expected)
```sql
create table if not exists tasks (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  title text not null,
  category text not null check (category in ('morning','work','evening')),
  completed boolean not null default false,
  created_at timestamp with time zone default now()
);

create table if not exists habits (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  name text not null,
  streak int not null default 0,
  completed_today boolean not null default false,
  created_at timestamp with time zone default now()
);
```

## RLS (recommended)
Enable RLS and create policies so users can only read/write rows where `user_id = auth.uid()`.

## Supabase migration file
- Run `supabase/migrations/20260420_daily_routine_schema_and_rls.sql` in your Supabase SQL editor.
