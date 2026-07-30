CREATE TABLE config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    label VARCHAR(100) NOT NULL,
    key VARCHAR(150) NOT NULL,
    value TEXT NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    data_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_config_label_key UNIQUE (label, key)
);

CREATE INDEX idx_config_active_label ON config (is_active, label);

INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('USER', 'BETA_USER', 'luxofons', 'User beta dùng các tính năng mới', TRUE),
    ('CONFIG', 'PUBLIC_CONFIG', '{label=''USER'',key=''BETA_USER''}',
     'Danh sách config được công khai cho client', TRUE)
ON CONFLICT (label, key) DO UPDATE SET
    value = EXCLUDED.value,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active,
    data_version = config.data_version + 1,
    updated_at = NOW();
