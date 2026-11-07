CREATE TABLE IF NOT EXISTS storyforge_build_jobs (
  id uuid PRIMARY KEY,
  user_id text NOT NULL,
  idempotency_key text NOT NULL,
  status text NOT NULL CHECK (status IN ('queued','in_progress','completed','failed')),
  build_ref text NOT NULL,
  game_title varchar(40) NOT NULL,
  application_id varchar(255),
  version_name varchar(32) NOT NULL,
  version_code bigint NOT NULL,
  build_type text NOT NULL CHECK (build_type IN ('debug','release')),
  storygame_url text NOT NULL,
  icon_url text,
  github_run_id bigint UNIQUE,
  github_conclusion text,
  error_code text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(user_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS storyforge_build_jobs_user_created ON storyforge_build_jobs(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS storyforge_build_jobs_status_created ON storyforge_build_jobs(status, created_at DESC);
