-- V275 seeded POST.MAX_MEDIA at 40. A trip story's generated draft borrows every check-in photo of
-- the author as a media row and stops at that cap, so a story about a photo-heavy trip opened with
-- all 40 slots taken and no photo could be added. The code fallback is already 200 (range 1..500).
-- Only the seeded 40 is raised: a value an admin already changed is kept.
UPDATE config
SET value = '200',
    description = 'So anh/video toi da mot bai viet (1-500); bai ve chuyen muon moi anh check-in nen tran rong',
    data_version = data_version + 1,
    updated_at = NOW()
WHERE label = 'POST' AND key = 'MAX_MEDIA'
  AND TRIM(value) = '40';
