-- Weekend 4: minimal entries table.
-- Deferred to later migrations:
--   * embedding VECTOR(1536) + ivfflat index   (Weekend 7, pgvector)
--   * FK user_id -> auth.users(id)             (Weekend 8, Supabase Auth)
--   * user_context table                       (Weekend 5+)

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    entry_date DATE NOT NULL DEFAULT CURRENT_DATE,

    transcript TEXT NOT NULL,
    summary TEXT NOT NULL,
    work_section TEXT,
    personal_section TEXT,
    mood VARCHAR(50),

    people_mentioned TEXT[] NOT NULL DEFAULT '{}',
    tags TEXT[] NOT NULL DEFAULT '{}',

    duration_seconds INTEGER
);

CREATE INDEX entries_user_date_idx ON entries(user_id, entry_date DESC);
