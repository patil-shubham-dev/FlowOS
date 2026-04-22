-- Project: teranqxkhvxzxxvskhtj
-- Daily Routine AI schema + RLS policies

create extension if not exists pgcrypto;

create table if not exists public.users_profile (
  id uuid primary key references auth.users(id) on delete cascade,
  email text not null unique,
  xp integer not null default 0,
  level text not null default 'Beginner',
  created_at timestamptz not null default now()
);

create table if not exists public.tasks (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  title text not null check (char_length(title) between 1 and 160),
  category text not null check (category in ('morning', 'work', 'evening')),
  completed boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists public.habits (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null check (char_length(name) between 1 and 120),
  streak integer not null default 0,
  completed_today boolean not null default false,
  last_completed date,
  created_at timestamptz not null default now()
);

create table if not exists public.ai_reminder_settings (
  user_id uuid primary key references auth.users(id) on delete cascade,
  enabled boolean not null default false,
  hour24 integer not null default 8 check (hour24 between 0 and 23),
  minute integer not null default 0 check (minute between 0 and 59),
  title text not null default 'Daily Routine Check-in',
  body text not null default 'Open your app and complete your next action.',
  updated_at timestamptz not null default now()
);

create index if not exists idx_tasks_user_created on public.tasks(user_id, created_at desc);
create index if not exists idx_habits_user_created on public.habits(user_id, created_at desc);

alter table public.users_profile enable row level security;
alter table public.tasks enable row level security;
alter table public.habits enable row level security;
alter table public.ai_reminder_settings enable row level security;

drop policy if exists "users_profile_select_own" on public.users_profile;
create policy "users_profile_select_own"
on public.users_profile for select
to authenticated
using (auth.uid() = id);

drop policy if exists "users_profile_insert_own" on public.users_profile;
create policy "users_profile_insert_own"
on public.users_profile for insert
to authenticated
with check (auth.uid() = id);

drop policy if exists "users_profile_update_own" on public.users_profile;
create policy "users_profile_update_own"
on public.users_profile for update
to authenticated
using (auth.uid() = id)
with check (auth.uid() = id);

drop policy if exists "tasks_select_own" on public.tasks;
create policy "tasks_select_own"
on public.tasks for select
to authenticated
using (auth.uid() = user_id);

drop policy if exists "tasks_insert_own" on public.tasks;
create policy "tasks_insert_own"
on public.tasks for insert
to authenticated
with check (auth.uid() = user_id);

drop policy if exists "tasks_update_own" on public.tasks;
create policy "tasks_update_own"
on public.tasks for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists "tasks_delete_own" on public.tasks;
create policy "tasks_delete_own"
on public.tasks for delete
to authenticated
using (auth.uid() = user_id);

drop policy if exists "habits_select_own" on public.habits;
create policy "habits_select_own"
on public.habits for select
to authenticated
using (auth.uid() = user_id);

drop policy if exists "habits_insert_own" on public.habits;
create policy "habits_insert_own"
on public.habits for insert
to authenticated
with check (auth.uid() = user_id);

drop policy if exists "habits_update_own" on public.habits;
create policy "habits_update_own"
on public.habits for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists "habits_delete_own" on public.habits;
create policy "habits_delete_own"
on public.habits for delete
to authenticated
using (auth.uid() = user_id);

drop policy if exists "ai_reminder_select_own" on public.ai_reminder_settings;
create policy "ai_reminder_select_own"
on public.ai_reminder_settings for select
to authenticated
using (auth.uid() = user_id);

drop policy if exists "ai_reminder_insert_own" on public.ai_reminder_settings;
create policy "ai_reminder_insert_own"
on public.ai_reminder_settings for insert
to authenticated
with check (auth.uid() = user_id);

drop policy if exists "ai_reminder_update_own" on public.ai_reminder_settings;
create policy "ai_reminder_update_own"
on public.ai_reminder_settings for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);
