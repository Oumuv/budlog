CREATE TABLE weight_record (
    id BIGSERIAL PRIMARY KEY,
    baby_id BIGINT NOT NULL REFERENCES baby_profile(id),
    measured_at TIMESTAMPTZ NOT NULL,
    record_date DATE NOT NULL,
    weight_kg NUMERIC(6,3) NOT NULL,
    note VARCHAR(500),
    client_request_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_weight_kg CHECK (weight_kg >= 0.100 AND weight_kg <= 100.000)
);

CREATE UNIQUE INDEX uk_weight_baby_record_date
    ON weight_record (baby_id, record_date)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_weight_baby_measured_at
    ON weight_record (baby_id, measured_at DESC)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE weight_record IS '宝宝每日体重记录';
COMMENT ON COLUMN weight_record.measured_at IS '实际测量时间';
COMMENT ON COLUMN weight_record.record_date IS '按宝宝时区推导的记录日期';
COMMENT ON COLUMN weight_record.weight_kg IS '体重，单位千克，最多三位小数';
COMMENT ON COLUMN weight_record.client_request_id IS '客户端请求唯一标识，用于幂等写入';
COMMENT ON INDEX uk_weight_baby_record_date IS '保证每个宝宝每天最多一条未删除体重记录';

