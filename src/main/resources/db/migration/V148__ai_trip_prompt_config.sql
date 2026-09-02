-- Editable prompt rules for the AI trip planner. Seeded from python_ai_trip_service/app/prompts.py defaults;
-- product edits these rows in goroute-admin (Config), the worker reads them at the start of every job.
INSERT INTO config (label, key, value, description, is_active) VALUES
    ('AI_TRIP', 'PLAN_RULES', $ai$## Core planning rules
1. Use ONLY candidate ids from the input. Never invent a place. Never use a place twice in the whole trip.
2. Every day: BREAKFAST (06:30-09:00), LUNCH (11:30-13:30), DINNER (18:00-20:30) — each at a real food venue.
   - Breakfast = real breakfast food (phở, bún, bánh mì, xôi, cháo, bánh cuốn, cơm tấm...). NEVER a cafe/coffee/dessert place — nobody drinks coffee on an empty stomach.
   - A cafe/dessert stop is a break AFTER a meal, at most one per day. Late-night food (ăn đêm, chè, night-market snacks) after dinner is welcome, especially for food lovers.
   - Keep >= 3 hours between main meals. Vietnamese breakfast places sell out by ~09:30; many kitchens close 14:00-17:00.
3. Geography: plan each half-day inside ONE area; consecutive stops must be close (use lat/lng). Never zig-zag across the city, never cross the city more than once a day. Budget realistic travel time between stops: walking ~4 km/h for < 1.5 km, otherwise taxi/Grab ~20 km/h in the city (~14 km/h in rush hours 07:00-09:00 and 16:00-18:00), plus 5-10 minutes to find a ride.
4. Time-of-day fit (respect opening hours first when given):
   - Temples/pagodas 06:30-09:30 or 16:00-17:30 · Museums/galleries daytime, ideal 11:00-15:00 as a heat refuge · Wet/morning markets 06:00-09:00
   - Night markets, bars, rooftops, walking streets, shows: after 17:30 only · Beaches 06:00-09:00 or 16:00-18:30 · Viewpoints at sunrise or 60 min before sunset (sunset ~17:15 in Dec, ~18:30 in Jun)
   - Waterfalls/nature/boat trips in the morning · Cable cars/mountain parks at opening or after 16:00 (clouds/rain after 10:00) · Theme parks: full day, arrive at opening
   - Heat (Mar-Sep north/central, all year south): no long outdoor stop 11:00-15:00; put indoor/AC, lunch, cafe or a hotel rest there.
5. Visit lengths: meal 45-90 min; temple 30-60; museum 90-150; market 45-90; beach 90-180; big park/cable car/theme park 240-420. Add a 10-15 min buffer after every stop.
6. Rest blocks (no place) are allowed and encouraged when the traveller profile calls for them: {"rest":"hotel"} midday rest, {"rest":"walk"} a stroll in the neighbourhood, {"rest":"free"} free time.
7. Never output transport items — travel is handled outside the plan.
8. Discovery style: FAMOUS = mostly high-review-count landmarks; BALANCED = famous anchors + local favourites; HIDDEN_GEMS = local favourites with high ratings, one famous landmark per day for orientation.
9. Cover the traveller's chosen interests across the trip (not all on one day); vary categories within a day (no three temples in a row); the user's own words override every default.
10. Descriptions: ONE concise sentence per stop (why go + what to do/eat there), grounded in the given data (a menu dish, the view, the vibe). Trip description: at most 2 short sentences. No marketing fluff, no invented facts.
$ai$, $ai$Core itinerary rules sent to the AI trip planner (meals, geography, time-of-day, descriptions). Markdown; edited text is used on the next job.$ai$, TRUE),
    ('AI_TRIP', 'TRAVELLER_RULES', $ai$## Rules by traveller profile (apply every matching block)
### Pace
- RELAXED: 2 non-meal stops/day, start 08:30, leisurely meals (90 min), a rest or free block midday, end by 21:30. Evenings still welcome (sunset, night market).
- BALANCED: 3 non-meal stops/day, start 08:00, standard visits, end ~22:00. An evening activity is welcome.
- EAGER: 4-5 non-meal stops/day, can start 06:00-07:00 (sunrise, morning markets) and end 23:00 (nightlife). Efficient transitions, quick meals.
### Family with children (mobilityConsiderations contains Kid-friendly, or the group mentions children/kids/baby)
- Max 3 stops/day, each <= 2 h; hotel rest 12:00-15:00 in hot months; dinner by 18:30; nothing after 20:00; walking <= 3 km/day.
- NO adventure, extreme, trekking, cliffs, long boat rides, bars, nightlife or steep-stairs sites (e.g. Hang Múa, Fansipan summit steps, long hikes). Prefer parks, beaches at cool hours, interactive museums, water puppets, cable cars, gentle boats.
- Babies/toddlers (0-3): 2 outdoor stops/day max, a nap block at the hotel is mandatory, stroller-friendly areas only.
### Elderly / limited mobility (Elderly-friendly, or the group mentions elderly/parents/grandparents)
- 2 stops/day with seating; <= 1 km walking per stop; midday rest 12:00-15:00; end by 19:30; door-to-door transport assumed.
- Avoid steps-only sites, steep climbs, long standing, rough terrain, motorbike loops, sleeper buses. Seated sightseeing is ideal: cruises, rowing boats in the morning, cyclo, cable cars (stay at the station).
### Couple / honeymoon
- 2 stops + 1 "moment" per day: golden-hour viewpoint, riverside or beach sunset, a good dinner around 19:30, a spa/free block 13:00-16:00. Quieter alternatives over crowded spots; photo stops early morning (empty streets 06:30-08:00).
### Friends group
- Social dining, nightlife and photo spots welcome; next-day first stop >= 10:30 after a bar night; no dawn activities the morning after.
### Solo
- Walkable areas, social cafes, safe neighbourhoods, easy public transport; bars/nightlife fine.
### Adventure / trekking (activityTypes contains Adventure, or the user asks for trekking/hiking)
- Start 06:00-07:30, finish all trekking/riding by 16:30; 20-30% time buffer; rest day after a big trek; never combine a trek with an intercity move on the same day. Skip entirely when children or elderly travel.
### Photography
- Put viewpoints/landmarks at sunrise or golden hour; markets and old streets before 08:00.
### Food lovers (FOOD_AND_DRINK selected or the user talks about eating)
- Named breakfast venue before 08:30, regional must-eat dishes, one evening food crawl (17:30-20:30) or late-night snack; use the menu highlights given.
### Dietary restrictions
- Hard constraint on every food venue. Vegetarian/vegan: prefer quán chay; halal: only venues that read as halal/seafood-safe; warn via description when a venue is uncertain.
### Arrival / departure
- First day of a destination after a move: start later (nothing before ~10:30 after a morning drive; a light day after an overnight train/bus). Last day: morning only if a move follows.
$ai$, $ai$Rules by traveller profile: pace, children, elderly, couples, friends, solo, adventure, photography, food lovers, dietary, arrival/departure.$ai$, TRUE),
    ('AI_TRIP', 'DESTINATION_NOTES', $ai$## Hà Nội
Clusters: Hoàn Kiếm/Old Quarter (lake, Ngọc Sơn, water puppets 15:00-21:15 shows, Tạ Hiện beer street 19:00-24:00, Đồng Xuân market) · Ba Đình 3 km west (Mausoleum only mornings Tue/Wed/Thu/Sat/Sun, One Pillar Pagoda, Temple of Literature 08:00-17:00, Hỏa Lò 08:00-17:00, Citadel closed Mon) · Tây Hồ north (Trấn Quốc 07:30-11:30 & 13:30-17:00, lakeside cafes for sunset) · Long Biên bridge at sunrise. Weekend walking streets around the lake Fri 19:00-Sun 24:00 (no taxis inside). Best food: phở bò, bún chả (lunch), chả cá, bún riêu, cà phê trứng.
## Ninh Bình
Everything within 15 km. Tràng An boat 07:00-16:30 (3 h, go at 07:00 for empty caves) · Bái Đính 06:00-21:00 (2-3 h) · Tam Cốc boat 07:30-17:00 (2 h) · Hang Múa 500 steep steps (sunset, NOT for children/elderly) · Hoa Lư 07:00-17:00. Do not stack Tràng An + Bái Đính + Hang Múa in one day. Dê núi, cơm cháy.
## Hạ Long / Cát Bà
Day cruise 12:00-17:45; overnight cruise far better. Sửng Sốt cave ~100 steps. Lan Hạ (from Cát Bà) is quieter and best for kayaking (08:00-16:30). Cable car weekdays from 14:00. Storm cancellations Jul-Sep; fog Dec-Feb.
## Sa Pa
Fansipan cable car 07:15-14:15 up (weekday best, clearest 07:30-09:30; summit needs 600 steps unless funicular) · Cát Cát 05:30-18:30 (1.5-2 h, steps) · Ô Quy Hồ pass sunset 16:30-17:30 · Lao Chải-Tả Van trek 4-5 h. Cold/fog Dec-Feb; rice gold late Sep.
## Huế
Citadel 06:30-17:30 (go at 07:00, 2-3 h) · Thiên Mụ 08:00-18:00 (dragon boat) · Tombs 8-12 km south: Tự Đức (shade), Khải Định (exposed steps, not at noon), Minh Mạng (07:00-17:30) · Vọng Cảnh hill sunset · Đông Ba market after 15:00 · walking street Fri-Sat 18:00-02:00. Bún bò, cơm hến, bánh bèo/nậm/lọc, chè. Very wet Oct-Dec.
## Đà Nẵng
Mỹ Khê sunrise 05:15-05:45 or after 16:00 · Sơn Trà: Linh Ứng 06:00-21:00 + Bàn Cờ peak early (mist, no automatic scooters) · Cham Museum 07:00-17:00 · Hàn market 06:00-19:00 · Marble Mountains 07:00-17:30 (elevator; on the way to Hội An, cool caves at midday) · Dragon Bridge fire 21:00 Fri-Sun · Bà Nà Hills = a full separate day (arrive 07:45, cloud/rain any month) · Hải Vân pass motorbike loop 2-3 h mornings. Mì Quảng, bún chả cá, bánh tráng cuốn thịt heo, seafood.
## Hội An
Ancient Town pedestrian 09:00-11:00 & 15:00-21:30; hot and motorbikes 11:00-15:00; lanterns and river boats 18:00-21:00 (best 18:30-20:00); night market 17:00-22:00; Ký Ức show 20:00 (closed Tue) · An Bàng beach late afternoon · Trà Quế vegetable village 06:00-08:00 or 16:00-17:30, cooking classes 08:30 · Cẩm Thanh coconut boats 08:00-17:00 (noisy midday) · Mỹ Sơn 40 km: sunrise tour or be out by 10:00. Cao lầu, cơm gà, bánh mì Phượng. Floods Oct-Dec.
## Quy Nhơn
Eo Gió sunrise/sunset (closes 18:30) · Kỳ Co by canoe from Nhơn Lý (Mar-Sep) · Tháp Đôi 07:00-11:30 & 13:30-17:00 · Ghềnh Ráng sunrise · Bãi Xép. Bánh xèo tôm nhảy, bún cá.
## Nha Trang
Hòn Chồng sunrise · Po Nagar 06:00-17:30 (morning) · 4-island boat 08:30-16:00 · VinWonders = full day · mud bath late afternoon · night market. Rough sea Oct-Dec.
## Đà Lạt
Compact. Cloud hunting 04:30-06:30 (Cầu Đất tea hill, Thiên Phúc Đức) · Datanla 07:30-16:30 (alpine coaster) · Trúc Lâm cable car 07:30-17:00 · Crazy House 08:30-18:00 · Langbiang golden hour · night market from 17:00. Rain 14:00-17:00 Jun-Oct: outdoors in the morning. Bánh tráng nướng, lẩu gà lá é.
## Phan Thiết / Mũi Né
White dunes at sunrise (04:30 jeep) · fishing village 05:30-07:30 · Fairy Stream morning or 16:00 · Red dunes sunset 16:30-18:00 · Po Shanu towers. Dunes unbearable 09:00-16:00.
## TP.HCM
District 1 core walkable: Reunification Palace 08:00-16:30 (tickets to 15:30), War Remnants 07:30-17:30, Post Office 07:00-19:00, Book Street, Bến Thành 07:00-19:00 + side-street night market, Nguyễn Huệ evenings, Bitexco/Landmark 81 at sunset · Chợ Lớn 20 min west: Thiên Hậu 06:00-11:30 & 13:00-16:30, Bình Tây market mornings · Jade Emperor pagoda 07:00-18:00 · Bùi Viện Sat-Sun 19:00-02:00 · Củ Chi = half day (leave 07:00, back 13:00). Rain 15:00-17:00 May-Oct: museum slot. Cơm tấm, hủ tiếu, bánh mì, ốc.
## Cần Thơ / Mekong
Cái Răng floating market from Ninh Kiều pier at 05:00 (peak 05:30-07:00, empty after 08:00) · Bình Thủy ancient house 07:00-17:00 · Cồn Sơn islet 09:00-15:00 · Ninh Kiều night market. Arrive the evening before.
## Phú Quốc
North and south are 50 km apart: one region per day. South: Hàm Ninh sunrise, Bãi Sao morning, Hòn Thơm cable car only in set windows, Sunset Town/Kiss Bridge sunset, Kiss of the Sea 21:00 (closed Tue). North: VinWonders/Safari full day, Grand World evenings, west-coast beaches for sunset. Dương Đông night market 17:00-22:00. Rainy May-Oct.
## Vũng Tàu
Christ statue 07:00-17:00 (800 steps, go at 07:00; lunch break 11:30-13:00) · lighthouse sunset · Nghinh Phong sunrise · Bạch Dinh 07:00-18:00 · Bãi Sau swim mornings. Bánh khọt.
## Côn Đảo
Prison/museum 07:00-11:30 & 13:30-17:00 · Đầm Trầu beach mornings · Hàng Dương cemetery evening pilgrimage · boats 08:00-15:00 (Mar-Sep). Book restaurants/cars ahead.
## Hà Giang
Loop = 3-4 riding days, 07:30-16:30 only: Hà Giang → Quản Bạ → Yên Minh → Đồng Văn → Mã Pì Lèng (best 14:00-16:00) → Nho Quế boat → Mèo Vạc → Du Già → Hà Giang. Sunday markets in Đồng Văn/Mèo Vạc 06:00-10:00. NOT for children/elderly; rain Jun-Aug.
$ai$, $ai$Local knowledge per destination as '## <City>' sections; only the section matching the destination name is sent to the model.$ai$, TRUE),
    ('AI_TRIP', 'DESCRIPTION_MAX_CHARS', $ai$200$ai$, $ai$Maximum characters for each activity description written by the AI.$ai$, TRUE)
ON CONFLICT (label, key) DO NOTHING;
