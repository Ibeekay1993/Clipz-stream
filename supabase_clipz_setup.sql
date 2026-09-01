create table if not exists public.jobs (
  job_id text primary key,
  user_id uuid null references auth.users(id) on delete set null,
  status text,
  progress integer,
  current_step text,
  result jsonb,
  error text,
  retention_policy text not null default 'anonymous_24h',
  expires_at bigint,
  updated_at timestamptz not null default now()
);

alter table public.jobs add column if not exists user_id uuid null references auth.users(id) on delete set null;
alter table public.jobs add column if not exists retention_policy text not null default 'anonymous_24h';
alter table public.jobs add column if not exists expires_at bigint;
alter table public.jobs add column if not exists updated_at timestamptz not null default now();

create index if not exists jobs_user_id_idx on public.jobs(user_id);
create index if not exists jobs_expires_at_idx on public.jobs(expires_at);

alter table public.jobs enable row level security;

drop policy if exists "Users can read own jobs" on public.jobs;
create policy "Users can read own jobs"
on public.jobs for select
to authenticated
using (auth.uid() = user_id);

drop policy if exists "Users can upsert own jobs" on public.jobs;
create policy "Users can upsert own jobs"
on public.jobs for insert
to authenticated
with check (auth.uid() = user_id);

drop policy if exists "Users can update own jobs" on public.jobs;
create policy "Users can update own jobs"
on public.jobs for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

insert into storage.buckets (id, name, public)
values ('clips', 'clips', true)
on conflict (id) do update set public = true;

drop policy if exists "Users can upload own clips" on storage.objects;
create policy "Users can upload own clips"
on storage.objects for insert
to authenticated
with check (bucket_id = 'clips' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "Users can update own clips" on storage.objects;
create policy "Users can update own clips"
on storage.objects for update
to authenticated
using (bucket_id = 'clips' and (storage.foldername(name))[1] = auth.uid()::text)
with check (bucket_id = 'clips' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "Public can read clips" on storage.objects;
create policy "Public can read clips"
on storage.objects for select
to public
using (bucket_id = 'clips');
