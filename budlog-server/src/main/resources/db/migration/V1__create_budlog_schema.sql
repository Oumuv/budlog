CREATE TABLE baby_profile (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    birth_time TIMESTAMPTZ NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE custom_milestone (
    id BIGSERIAL PRIMARY KEY,
    baby_id BIGINT NOT NULL REFERENCES baby_profile(id),
    title VARCHAR(100) NOT NULL,
    target_time TIMESTAMPTZ NOT NULL,
    note VARCHAR(500),
    client_request_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE feeding_record (
    id BIGSERIAL PRIMARY KEY,
    baby_id BIGINT NOT NULL REFERENCES baby_profile(id),
    feeding_type VARCHAR(32) NOT NULL,
    breast_side VARCHAR(16),
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ,
    amount_ml NUMERIC(6,1),
    note VARCHAR(500),
    client_request_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_feeding_type CHECK (feeding_type IN ('BREAST_DIRECT', 'BREAST_BOTTLE', 'FORMULA_BOTTLE')),
    CONSTRAINT ck_breast_side CHECK (breast_side IS NULL OR breast_side IN ('LEFT', 'RIGHT', 'BOTH')),
    CONSTRAINT ck_feeding_time CHECK (end_time IS NULL OR end_time >= start_time),
    CONSTRAINT ck_feeding_amount CHECK (amount_ml IS NULL OR (amount_ml > 0 AND amount_ml <= 1000))
);

CREATE INDEX idx_feeding_baby_start
    ON feeding_record (baby_id, start_time DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE diaper_record (
    id BIGSERIAL PRIMARY KEY,
    baby_id BIGINT NOT NULL REFERENCES baby_profile(id),
    record_type VARCHAR(16) NOT NULL,
    record_time TIMESTAMPTZ NOT NULL,
    note VARCHAR(500),
    client_request_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_diaper_type CHECK (record_type IN ('PEE', 'POOP', 'BOTH'))
);

CREATE INDEX idx_diaper_baby_time
    ON diaper_record (baby_id, record_time DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE todo_task (
    id BIGSERIAL PRIMARY KEY,
    baby_id BIGINT NOT NULL REFERENCES baby_profile(id),
    title VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    due_time TIMESTAMPTZ NOT NULL,
    remind_time TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL DEFAULT 'TODO',
    completed_at TIMESTAMPTZ,
    client_request_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_task_status CHECK (status IN ('TODO', 'DONE', 'CANCELED')),
    CONSTRAINT ck_task_reminder CHECK (remind_time IS NULL OR remind_time <= due_time)
);

CREATE INDEX idx_task_baby_status_due
    ON todo_task (baby_id, status, due_time)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_task_status_remind
    ON todo_task (status, remind_time)
    WHERE deleted_at IS NULL;

CREATE TABLE app_setting (
    id SMALLINT PRIMARY KEY,
    default_feeding_interval_min INTEGER NOT NULL DEFAULT 180,
    feeding_interval_anchor VARCHAR(16) NOT NULL DEFAULT 'START',
    reminder_sound_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    reminder_vibrate_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_feeding_interval CHECK (default_feeding_interval_min BETWEEN 30 AND 720),
    CONSTRAINT ck_feeding_anchor CHECK (feeding_interval_anchor = 'START')
);

INSERT INTO app_setting (
    id,
    default_feeding_interval_min,
    feeding_interval_anchor,
    reminder_sound_enabled,
    reminder_vibrate_enabled
) VALUES (1, 180, 'START', FALSE, TRUE);
