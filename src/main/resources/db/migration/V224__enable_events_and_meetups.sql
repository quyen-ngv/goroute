-- Events (epic 17) and meetups (epic 18) leave beta: every user gets the Home tabs, joining an
-- event with its group chat, and hosting / joining / inviting to meetups. Beta users already had
-- them; the flags stay so an operator can still switch either feature off from the console.

UPDATE config
SET value = 'true',
    description = 'Bat tab Su kien, tham gia su kien va nhom chat su kien cho moi nguoi dung; true/false',
    data_version = data_version + 1,
    updated_at = NOW()
WHERE label = 'EVENT' AND key = 'ENABLED';

UPDATE config
SET value = 'true',
    description = 'Bat tab Hoat dong (meetup) cho moi nguoi dung; true/false',
    data_version = data_version + 1,
    updated_at = NOW()
WHERE label = 'MEETUP' AND key = 'ENABLED';
