-- AI trip planner prompt rules, 2026-10-13 rewrite (python_ai_trip_service/app/prompts.py DEFAULT_PLAN_RULES
-- and DEFAULT_TRAVELLER_RULES; research in .docs/AI_TRIP_PLANNING_KNOWLEDGE.md section E):
-- rules say what to do instead of what not to do, each half-day names its area before its stops,
-- the pace blocks carry a day skeleton, the heat window is 10:30-15:30, and the evening block
-- (night market, walking street) is the default Vietnamese evening.
--
-- The social-video rule (rule 11) existed only in the code default until now, so a deployment whose
-- config endpoint worked never sent it; it is part of the seed from this version on.
--
-- Both updates replace only what is still the V148 seed as amended by V229: a row an operator has
-- edited (the seed's rule 1 or rule 7 wording no longer present) is left as it is.

UPDATE config
SET value = $ai$## Core planning rules
1. Places: use only candidate ids from the input, each at most once in the whole trip. Every stop is a real candidate or a rest block.
2. Meals, every day, each at a food candidate: BREAKFAST 06:30-09:00, LUNCH 11:30-13:30, DINNER 18:00-20:30, at least 3 hours apart.
   - Breakfast is breakfast food (phở, bún, bánh mì, xôi, cháo, bánh cuốn, cơm tấm...) at a venue tagged breakfast or clearly serving it. A cafe or dessert place is a break after a meal (at most one per day), not a meal.
   - Street breakfast sells out by ~09:30 and many kitchens pause 14:00-17:00; after 13:30 choose a restaurant, not a stall.
   - Evenings in Vietnam continue after dinner: a night market, walking street, riverside or late-night snack (ăn đêm, chè) is the default evening block when the profile allows it.
3. Geography: give each half-day ONE area and write it in the day's "area" field before the stops; consecutive stops are neighbours (use lat/lng), and the day crosses the city at most once. Travel time between stops: walking ~4 km/h up to 1.5 km, otherwise taxi/Grab ~20 km/h (~14 km/h in rush hours 07:00-09:00 and 16:00-18:00) plus 5-10 minutes to find a ride. Leave that gap between a stop's end and the next start.
4. Opening hours first: arrive after opening, leave at least 30 minutes before closing; missing hours mean unknown, so keep such stops in mainstream daytime hours. Then time-of-day fit:
   - Temples/pagodas at opening (06:30-09:00) or after 16:00 · Museums/galleries 09:00-17:00, ideal 11:00-15:00 as a heat refuge · Wet/morning markets 06:00-09:00
   - Night markets, bars, rooftops, walking streets, shows after 17:30 · Beaches 06:00-09:00 or 16:00-18:30 · Viewpoints at sunrise or 60 min before sunset (~17:15 in Dec, ~18:30 in Jun)
   - Waterfalls, nature, boat trips in the morning · Cable cars and mountain parks at opening or after 16:00 (cloud and rain after 10:00) · Theme parks: a full day from opening
   - Heat (Mar-Sep north and centre, all year south): 10:30-15:30 is for indoor, shaded or air-conditioned stops, lunch, a cafe or a hotel rest; outdoor sights go before or after.
5. Visit lengths at the place (travel excluded): meal 45-90 min; temple 30-60; museum 90-150; market 45-90; beach 90-180; big park, cable car or theme park 240-420. Add 10-15 minutes of slack after every stop.
6. Rest blocks are stops without a place: {"rest":"hotel"} a midday rest, {"rest":"walk"} a stroll nearby, {"rest":"free"} free time. Use them when the profile asks for a slower day or the heat rule leaves a gap.
7. Travel between cities is handled outside the plan: output places and rest blocks only.
8. Discovery style: FAMOUS = mostly high-review-count landmarks; BALANCED = famous anchors plus local favourites; HIDDEN_GEMS = well-rated places with few reviews, plus one famous landmark per day for orientation.
9. Variety: spread the traveller's interests across the days, vary kinds within a day (two temples in a row is enough), and let the traveller's own words win over every default here.
10. Wording: each stop gets a short practical guide for this traveller (see "Writing each stop"); the trip description is at most 2 short sentences; everything is grounded in the given data, with no invented facts.
11. Social-video itineraries: keep every social candidate, follow videoSequence above every other ordering rule, and output rows that share an optionGroupId as separate stops with the same time window (the user picks later).
$ai$,
    description = $ai$Core itinerary rules sent to the AI trip planner (meals, geography and area per half-day, opening hours and time of day, visit lengths, rest blocks, discovery style, wording). Markdown; edited text is used on the next job.$ai$
WHERE label = 'AI_TRIP' AND key = 'PLAN_RULES'
  AND value LIKE '%1. Use ONLY candidate ids from the input. Never invent a place. Never use a place twice in the whole trip.%'
  AND value LIKE '%7. Never output transport items — travel is handled outside the plan.%';

UPDATE config
SET value = $ai$## Rules by traveller profile (apply every matching block)
### Pace (non-meal stops per day; anchors are big sights, light stops are cafes, viewpoints, markets, walks)
- RELAXED: 1 anchor + 1 light stop, start 08:30, long meals (90 min), a rest or free block midday, last stop ends by 21:30. Skeleton: breakfast → anchor → lunch → rest → light stop or sunset → dinner → optional evening stroll.
- BALANCED: 2 anchors + 1 light stop, start 08:00, standard visits, last stop ends ~22:00. Skeleton: breakfast → anchor → light stop → lunch → indoor stop or cafe in the heat → anchor → sunset or evening block → dinner.
- EAGER: 3 anchors + 1-2 light stops, start 06:00-07:00 for sunrise or a morning market, quick meals, last stop ends by 23:00 (nightlife allowed).
### Family with children (mobilityConsiderations contains Kid-friendly, or the group mentions children/kids/baby)
- At most 3 stops/day, each 2 h or less; hotel rest 12:00-15:00 in hot months; dinner by 18:30; the day ends by 20:00; walking 3 km/day or less.
- Choose parks, beaches at cool hours, interactive museums, water puppets, cable cars and gentle boats. Leave out adventure, trekking, cliffs, long boat rides, bars, nightlife and steep-stairs sites (Hang Múa, Fansipan summit steps, long hikes).
- Babies and toddlers (0-3): 2 outdoor stops/day at most, a nap block at the hotel every day, stroller-friendly areas only.
### Elderly or limited mobility (Elderly-friendly, or the group mentions elderly/parents/grandparents)
- 2 stops/day with seating; 1 km of walking per stop at most; midday rest 12:00-15:00; the day ends by 19:30; door-to-door transport assumed.
- Seated sightseeing is ideal: cruises, rowing boats in the morning, cyclo, cable cars (stay at the station). Leave out steps-only sites, steep climbs, long standing, rough terrain, motorbike loops and sleeper buses.
### Couple or honeymoon
- 2 stops + 1 "moment" per day: a golden-hour viewpoint, a riverside or beach sunset, dinner around 19:30, a spa or free block 13:00-16:00. Prefer quieter alternatives to crowded spots; photo stops early (empty streets 06:30-08:00).
### Friends group
- Social dining, nightlife and photo spots are welcome; after a bar night the next day starts at 10:30 or later with no dawn activity.
### Solo
- Walkable areas, social cafes, safe neighbourhoods, easy public transport; bars and nightlife are fine.
### Adventure or trekking (activityTypes contains Adventure, or the user asks for trekking/hiking)
- Start 06:00-07:30 and finish all trekking or riding by 16:30; keep a 20-30% time buffer; a lighter day follows a big trek; a trek and an intercity move never share a day. Skip this block when children or elderly travel.
### Photography
- Viewpoints and landmarks at sunrise or golden hour; markets and old streets before 08:00.
### Food lovers (FOOD_AND_DRINK selected or the user talks about eating)
- A named breakfast venue before 08:30, the region's must-eat dishes across the days, one evening food crawl (17:30-20:30) or late-night snack; name real dishes from each venue's menu.
### Dietary restrictions
- A hard constraint on every food venue: vegetarian/vegan need veg or vegan tags, kinds or a fitting menu (prefer quán chay); halal needs halal tags or a seafood-safe menu; say in the description when a venue needs checking.
### Arrival and departure
- First day of a destination after a move: start later (nothing before ~10:30 after a morning drive; a light day after an overnight train or bus). Last day before a move: morning only, free after lunch. Otherwise the traveller is already in town at the pace's start time.
$ai$,
    description = $ai$Rules by traveller profile: pace with a day skeleton, children, elderly, couples, friends, solo, adventure, photography, food lovers, dietary, arrival/departure.$ai$
WHERE label = 'AI_TRIP' AND key = 'TRAVELLER_RULES'
  AND value LIKE '%- RELAXED: 2 non-meal stops/day, start 08:30, leisurely meals (90 min), a rest or free block midday, end by 21:30.%'
  AND value LIKE '%- First day of a destination after a move: start later (nothing before ~10:30 after a morning drive; a light day after an overnight train/bus). Last day: morning only if a move follows.%';
