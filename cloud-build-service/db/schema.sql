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


-- Community: public story discovery with authenticated ownership and basic moderation.
CREATE TABLE IF NOT EXISTS community_profiles (
  user_id text PRIMARY KEY,
  display_name varchar(40) NOT NULL,
  bio varchar(240) NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS community_posts (
  id uuid PRIMARY KEY,
  user_id text NOT NULL,
  title varchar(80) NOT NULL,
  body varchar(4000) NOT NULL DEFAULT '',
  genre varchar(32) NOT NULL DEFAULT 'other',
  story_json jsonb,
  status text NOT NULL DEFAULT 'published' CHECK (status IN ('published','hidden','removed')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS community_posts_feed ON community_posts(status, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS community_posts_owner ON community_posts(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS community_comments (
  id uuid PRIMARY KEY,
  post_id uuid NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
  user_id text NOT NULL,
  body varchar(1200) NOT NULL,
  status text NOT NULL DEFAULT 'published' CHECK (status IN ('published','hidden','removed')),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS community_comments_post ON community_comments(post_id, created_at ASC);

CREATE TABLE IF NOT EXISTS community_reactions (
  post_id uuid NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
  user_id text NOT NULL,
  reaction text NOT NULL DEFAULT 'like' CHECK (reaction IN ('like','love','inspiring')),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(post_id,user_id)
);

CREATE TABLE IF NOT EXISTS community_follows (
  follower_id text NOT NULL,
  followed_id text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(follower_id,followed_id),
  CHECK (follower_id <> followed_id)
);

CREATE TABLE IF NOT EXISTS community_reports (
  id uuid PRIMARY KEY,
  reporter_id text NOT NULL,
  target_type text NOT NULL CHECK (target_type IN ('post','comment','user')),
  target_id text NOT NULL,
  reason text NOT NULL CHECK (reason IN ('spam','harassment','violence','copyright','other')),
  details varchar(500) NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(reporter_id,target_type,target_id)
);
