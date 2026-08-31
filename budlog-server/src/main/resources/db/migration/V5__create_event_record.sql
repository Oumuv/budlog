CREATE TABLE event_record (
    id BIGSERIAL PRIMARY KEY,
    baby_id BIGINT NOT NULL REFERENCES baby_profile(id),
    event_type VARCHAR(16) NOT NULL,
    title VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    note VARCHAR(1000),
    client_request_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_event_type CHECK (event_type IN ('VACCINE', 'DOCUMENT', 'MOMENT', 'OTHER'))
);

CREATE INDEX idx_event_baby_occurred_at
    ON event_record (baby_id, occurred_at DESC)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE event_record IS '宝宝已经发生的事件记录';
COMMENT ON COLUMN event_record.event_type IS '事件分类：疫苗、证件、小事或其他';
COMMENT ON COLUMN event_record.occurred_at IS '事件发生时间';
COMMENT ON COLUMN event_record.client_request_id IS '客户端请求唯一标识，用于幂等写入';

