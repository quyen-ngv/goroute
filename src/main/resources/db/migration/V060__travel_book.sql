CREATE TABLE IF NOT EXISTS book_templates (
    id UUID PRIMARY KEY,
    template_type VARCHAR(20) NOT NULL,
    ref_id VARCHAR(255),
    ref_name VARCHAR(255),
    backgrounds JSONB NOT NULL DEFAULT '[]'::jsonb,
    stickers JSONB NOT NULL DEFAULT '[]'::jsonb,
    frame_styles JSONB NOT NULL DEFAULT '["polaroid"]'::jsonb,
    color_palette JSONB NOT NULL DEFAULT '[]'::jsonb,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_book_templates_lookup
    ON book_templates(template_type, ref_id, version DESC);

CREATE TABLE IF NOT EXISTS book_skeletons (
    id UUID PRIMARY KEY,
    skeleton_key VARCHAR(50) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    name VARCHAR(255),
    page_type VARCHAR(20) NOT NULL,
    photo_count INT,
    canvas_width INT NOT NULL DEFAULT 375,
    canvas_height INT NOT NULL DEFAULT 500,
    slot_defs JSONB NOT NULL DEFAULT '[]'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(skeleton_key, version)
);

CREATE INDEX IF NOT EXISTS idx_book_skeletons_active
    ON book_skeletons(skeleton_key, is_active, version DESC);

CREATE TABLE IF NOT EXISTS trip_books (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(trip_id)
);

CREATE INDEX IF NOT EXISTS idx_trip_books_trip
    ON trip_books(trip_id);

CREATE TABLE IF NOT EXISTS book_pages (
    id UUID PRIMARY KEY,
    book_id UUID NOT NULL,
    activity_id UUID,
    page_type VARCHAR(20) NOT NULL,
    page_order INT NOT NULL,
    skeleton_id VARCHAR(50) NOT NULL,
    skeleton_key VARCHAR(50),
    skeleton_version INT NOT NULL DEFAULT 1,
    template_id UUID,
    slots JSONB NOT NULL DEFAULT '[]'::jsonb,
    layout_mode VARCHAR(20) NOT NULL DEFAULT 'skeleton',
    layout_edited_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(book_id, page_order)
);

CREATE INDEX IF NOT EXISTS idx_book_pages_book_order
    ON book_pages(book_id, page_order);

CREATE INDEX IF NOT EXISTS idx_book_pages_activity
    ON book_pages(activity_id);

CREATE TABLE IF NOT EXISTS book_page_slots (
    id UUID PRIMARY KEY,
    page_id UUID NOT NULL,
    slot_id VARCHAR(80) NOT NULL,
    type VARCHAR(30) NOT NULL,
    value TEXT,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    frame_style VARCHAR(50),
    crop_rect JSONB,
    transform JSONB,
    style JSONB,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(page_id, slot_id)
);

CREATE INDEX IF NOT EXISTS idx_book_page_slots_page
    ON book_page_slots(page_id);

INSERT INTO book_skeletons (id, skeleton_key, version, name, page_type, photo_count, canvas_width, canvas_height, slot_defs)
VALUES
('00000000-0000-0000-0000-000000060001', 'cover_page', 1, 'Cover page', 'cover', 1, 375, 500, '[
  {"slotId":"background","type":"background","x":0,"y":0,"w":375,"h":500,"rotation":0,"zIndex":0},
  {"slotId":"cover_photo","type":"photo","x":66,"y":86,"w":242,"h":244,"rotation":-3,"zIndex":2},
  {"slotId":"trip_title","type":"text","x":34,"y":350,"w":307,"h":54,"rotation":0,"zIndex":5},
  {"slotId":"trip_dates","type":"text","x":68,"y":408,"w":240,"h":28,"rotation":0,"zIndex":5},
  {"slotId":"trip_desc","type":"text","x":48,"y":438,"w":280,"h":40,"rotation":0,"zIndex":5},
  {"slotId":"cities_list","type":"text","x":48,"y":32,"w":280,"h":34,"rotation":0,"zIndex":5},
  {"slotId":"total_places","type":"stat","x":270,"y":32,"w":64,"h":34,"rotation":0,"zIndex":6}
]'::jsonb),
('00000000-0000-0000-0000-000000060002', 'single_photo', 1, 'Single photo', 'place', 1, 375, 500, '[
  {"slotId":"background","type":"background","x":0,"y":0,"w":375,"h":500,"rotation":0,"zIndex":0},
  {"slotId":"photo_1","type":"photo","x":44,"y":74,"w":287,"h":300,"rotation":-4,"zIndex":2},
  {"slotId":"sticker_1","type":"sticker","x":278,"y":34,"w":70,"h":70,"rotation":8,"zIndex":4},
  {"slotId":"place_name","type":"text","x":28,"y":26,"w":230,"h":42,"rotation":0,"zIndex":5},
  {"slotId":"note_text","type":"text","x":34,"y":396,"w":250,"h":58,"rotation":-1,"zIndex":5},
  {"slotId":"visit_date","type":"text","x":250,"y":444,"w":100,"h":30,"rotation":0,"zIndex":5}
]'::jsonb),
('00000000-0000-0000-0000-000000060003', 'two_photo', 1, 'Two photo', 'place', 2, 375, 500, '[
  {"slotId":"background","type":"background","x":0,"y":0,"w":375,"h":500,"rotation":0,"zIndex":0},
  {"slotId":"photo_1","type":"photo","x":24,"y":82,"w":165,"h":220,"rotation":-5,"zIndex":2},
  {"slotId":"photo_2","type":"photo","x":186,"y":124,"w":165,"h":220,"rotation":4,"zIndex":3},
  {"slotId":"sticker_1","type":"sticker","x":294,"y":352,"w":62,"h":62,"rotation":8,"zIndex":4},
  {"slotId":"place_name","type":"text","x":24,"y":28,"w":240,"h":42,"rotation":0,"zIndex":5},
  {"slotId":"note_text","type":"text","x":28,"y":378,"w":260,"h":72,"rotation":-1,"zIndex":5},
  {"slotId":"visit_date","type":"text","x":248,"y":452,"w":100,"h":28,"rotation":0,"zIndex":5}
]'::jsonb),
('00000000-0000-0000-0000-000000060004', 'three_photo', 1, 'Three photo', 'place', 3, 375, 500, '[
  {"slotId":"background","type":"background","x":0,"y":0,"w":375,"h":500,"rotation":0,"zIndex":0},
  {"slotId":"photo_1","type":"photo","x":20,"y":68,"w":180,"h":220,"rotation":-5,"zIndex":2},
  {"slotId":"photo_2","type":"photo","x":194,"y":104,"w":160,"h":200,"rotation":4,"zIndex":3},
  {"slotId":"photo_3","type":"photo","x":62,"y":296,"w":200,"h":144,"rotation":-2,"zIndex":2},
  {"slotId":"sticker_1","type":"sticker","x":290,"y":348,"w":70,"h":70,"rotation":8,"zIndex":4},
  {"slotId":"place_name","type":"text","x":20,"y":22,"w":230,"h":40,"rotation":0,"zIndex":5},
  {"slotId":"note_text","type":"text","x":24,"y":446,"w":240,"h":42,"rotation":-1,"zIndex":5},
  {"slotId":"visit_date","type":"text","x":268,"y":454,"w":90,"h":28,"rotation":0,"zIndex":5}
]'::jsonb),
('00000000-0000-0000-0000-000000060005', 'four_photo', 1, 'Four photo', 'place', 4, 375, 500, '[
  {"slotId":"background","type":"background","x":0,"y":0,"w":375,"h":500,"rotation":0,"zIndex":0},
  {"slotId":"photo_1","type":"photo","x":28,"y":72,"w":148,"h":162,"rotation":-4,"zIndex":2},
  {"slotId":"photo_2","type":"photo","x":192,"y":70,"w":150,"h":162,"rotation":3,"zIndex":3},
  {"slotId":"photo_3","type":"photo","x":34,"y":246,"w":148,"h":162,"rotation":3,"zIndex":2},
  {"slotId":"photo_4","type":"photo","x":190,"y":242,"w":150,"h":162,"rotation":-4,"zIndex":3},
  {"slotId":"sticker_1","type":"sticker","x":294,"y":398,"w":58,"h":58,"rotation":8,"zIndex":4},
  {"slotId":"place_name","type":"text","x":24,"y":24,"w":230,"h":38,"rotation":0,"zIndex":5},
  {"slotId":"note_text","type":"text","x":28,"y":424,"w":230,"h":48,"rotation":0,"zIndex":5},
  {"slotId":"visit_date","type":"text","x":254,"y":448,"w":100,"h":28,"rotation":0,"zIndex":5}
]'::jsonb),
('00000000-0000-0000-0000-000000060006', 'text_heavy', 1, 'Text heavy', 'place', 1, 375, 500, '[
  {"slotId":"background","type":"background","x":0,"y":0,"w":375,"h":500,"rotation":0,"zIndex":0},
  {"slotId":"photo_1","type":"photo","x":210,"y":76,"w":128,"h":148,"rotation":5,"zIndex":2},
  {"slotId":"sticker_1","type":"sticker","x":276,"y":252,"w":70,"h":70,"rotation":8,"zIndex":4},
  {"slotId":"place_name","type":"text","x":28,"y":36,"w":250,"h":48,"rotation":0,"zIndex":5},
  {"slotId":"note_text","type":"text","x":32,"y":118,"w":245,"h":270,"rotation":-1,"zIndex":5},
  {"slotId":"visit_date","type":"text","x":34,"y":420,"w":140,"h":30,"rotation":0,"zIndex":5}
]'::jsonb),
('00000000-0000-0000-0000-000000060007', 'no_photo', 1, 'No photo', 'place', 0, 375, 500, '[
  {"slotId":"background","type":"background","x":0,"y":0,"w":375,"h":500,"rotation":0,"zIndex":0},
  {"slotId":"sticker_1","type":"sticker","x":245,"y":78,"w":88,"h":88,"rotation":8,"zIndex":4},
  {"slotId":"sticker_2","type":"sticker","x":44,"y":352,"w":72,"h":72,"rotation":-8,"zIndex":4},
  {"slotId":"place_name","type":"text","x":34,"y":68,"w":230,"h":52,"rotation":0,"zIndex":5},
  {"slotId":"note_text","type":"text","x":40,"y":158,"w":288,"h":190,"rotation":-1,"zIndex":5},
  {"slotId":"visit_date","type":"text","x":120,"y":390,"w":140,"h":32,"rotation":0,"zIndex":5}
]'::jsonb),
('00000000-0000-0000-0000-000000060008', 'summary_page', 1, 'Summary page', 'summary', 1, 375, 500, '[
  {"slotId":"background","type":"background","x":0,"y":0,"w":375,"h":500,"rotation":0,"zIndex":0},
  {"slotId":"highlight_photo","type":"photo","x":92,"y":56,"w":190,"h":190,"rotation":-3,"zIndex":2},
  {"slotId":"trip_title","type":"text","x":42,"y":270,"w":290,"h":46,"rotation":0,"zIndex":5},
  {"slotId":"total_days","type":"stat","x":42,"y":340,"w":88,"h":64,"rotation":0,"zIndex":5},
  {"slotId":"total_places","type":"stat","x":144,"y":340,"w":88,"h":64,"rotation":0,"zIndex":5},
  {"slotId":"total_cities","type":"stat","x":246,"y":340,"w":88,"h":64,"rotation":0,"zIndex":5},
  {"slotId":"total_expense","type":"stat","x":78,"y":422,"w":220,"h":44,"rotation":0,"zIndex":5}
]'::jsonb)
ON CONFLICT (skeleton_key, version) DO NOTHING;
