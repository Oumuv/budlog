CREATE TABLE milk_storage_record (
    id BIGSERIAL PRIMARY KEY,
    baby_id BIGINT NOT NULL REFERENCES baby_profile(id),
    stored_at TIMESTAMPTZ NOT NULL,
    amount_ml NUMERIC(6,1) NOT NULL,
    note VARCHAR(500),
    client_request_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_milk_storage_amount CHECK (amount_ml > 0 AND amount_ml <= 1000)
);

CREATE INDEX idx_milk_storage_baby_stored_at
    ON milk_storage_record (baby_id, stored_at DESC)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE milk_storage_record IS '存奶记录，存储每个存奶批次的时间和奶量';
COMMENT ON COLUMN milk_storage_record.id IS '存奶记录主键';
COMMENT ON COLUMN milk_storage_record.baby_id IS '关联的宝宝档案 ID';
COMMENT ON COLUMN milk_storage_record.stored_at IS '奶液装入储存容器的时间';
COMMENT ON COLUMN milk_storage_record.amount_ml IS '存奶量，单位毫升';
COMMENT ON COLUMN milk_storage_record.note IS '备注';
COMMENT ON COLUMN milk_storage_record.client_request_id IS '客户端请求唯一标识，用于幂等写入';
COMMENT ON COLUMN milk_storage_record.created_at IS '创建时间';
COMMENT ON COLUMN milk_storage_record.updated_at IS '最后更新时间';
COMMENT ON COLUMN milk_storage_record.deleted_at IS '软删除时间，NULL 表示未删除';
COMMENT ON CONSTRAINT ck_milk_storage_amount ON milk_storage_record IS '限制存奶量大于 0 且不超过 1000 毫升';
COMMENT ON INDEX idx_milk_storage_baby_stored_at IS '加速按宝宝和存奶时间倒序查询未删除的存奶记录';
