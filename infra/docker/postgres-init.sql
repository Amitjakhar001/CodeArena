-- Mounted by docker-compose into execution-postgres at:
--   /docker-entrypoint-initdb.d/init.sql
-- Postgres only runs files in this directory on first startup of an empty
-- volume — re-running requires `docker compose down -v`.

CREATE TABLE IF NOT EXISTS execution_languages (
  id              INTEGER PRIMARY KEY,
  name            VARCHAR(100) NOT NULL,
  is_active       BOOLEAN NOT NULL DEFAULT TRUE,
  cached_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS executions (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  judge0_token        VARCHAR(64) UNIQUE NOT NULL,
  user_id             VARCHAR(64) NOT NULL,
  submission_id       VARCHAR(64),
  language_id         INTEGER NOT NULL REFERENCES execution_languages(id),
  source_code_hash VARCHAR(64) NOT NULL,
  stdin               TEXT,
  expected_output     TEXT,
  cpu_time_limit      NUMERIC(5,2) NOT NULL DEFAULT 5.0,
  memory_limit        INTEGER NOT NULL DEFAULT 128000,
  status_id           INTEGER,
  status_description  VARCHAR(50),
  stdout              TEXT,
  stderr              TEXT,
  compile_output      TEXT,
  time_ms             INTEGER,
  memory_kb           INTEGER,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at        TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_executions_user_id        ON executions(user_id);
CREATE INDEX IF NOT EXISTS idx_executions_user_created   ON executions(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_executions_submission     ON executions(submission_id);
CREATE INDEX IF NOT EXISTS idx_executions_status         ON executions(status_id);
-- Partial index for the recovery poller's "stuck pending" query.
CREATE INDEX IF NOT EXISTS idx_executions_pending        ON executions(created_at)
  WHERE completed_at IS NULL;

-- Seed common Judge0 languages. Execution Service refreshes this from Judge0
-- on startup and daily via LanguageSyncService.
INSERT INTO execution_languages (id, name) VALUES
  (50, 'C (GCC 9.2.0)'),
  (54, 'C++ (GCC 9.2.0)'),
  (62, 'Java (OpenJDK 13.0.1)'),
  (63, 'JavaScript (Node.js 12.14.0)'),
  (71, 'Python (3.8.1)'),
  (73, 'Rust (1.40.0)'),
  (60, 'Go (1.13.5)')
ON CONFLICT (id) DO NOTHING;
