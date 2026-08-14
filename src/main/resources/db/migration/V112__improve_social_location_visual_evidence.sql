UPDATE config
SET value = '448',
    description = 'Maximum frame width sent to the social-location extraction model'
WHERE label = 'SOCIAL_LOCATION'
  AND key = 'IMAGE_MAX_WIDTH'
  AND value = '320';

UPDATE config
SET value = '12',
    description = 'FFmpeg JPEG q:v for social-location frames; lower values retain more detail'
WHERE label = 'SOCIAL_LOCATION'
  AND key = 'IMAGE_JPEG_QUALITY'
  AND value = '18';
