INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('APP', 'REPORT_ERROR_URL',
     'https://docs.google.com/forms/d/10-OaLvtXgd3CsscJNWBun7fG9R2tlPivRa2wETScwcM/edit',
     'Link form bao loi / gop y tu ung dung, hien thi tren app khi co loi hoac trong Cai dat', TRUE)
ON CONFLICT (label, key) DO UPDATE SET
    value = EXCLUDED.value,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active,
    data_version = config.data_version + 1,
    updated_at = NOW();

UPDATE config
SET value = value || ', {label=''APP'',key=''REPORT_ERROR_URL''}',
    data_version = data_version + 1,
    updated_at = NOW()
WHERE label = 'CONFIG' AND key = 'PUBLIC_CONFIG'
  AND value NOT LIKE '%REPORT_ERROR_URL%';
