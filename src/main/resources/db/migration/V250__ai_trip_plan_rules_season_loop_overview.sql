-- AI trip planner PLAN_RULES, 2026-10-23 (python_ai_trip_service/app/prompts.py DEFAULT_PLAN_RULES).
-- A Mộc Châu plan read like a list of addresses: a restaurant and a food store booked as sights, the
-- day crossing the plateau east and west, and a two-sentence trip description. The rules now add:
-- signature dishes across the meals (rule 2), one loop out of the base per day with the farthest
-- sight first and mountain-road speeds (rule 3), a trip overview instead of two sentences (rule 10),
-- season first (rule 12) and "a restaurant, shop, bar or club is never a sight" (rule 13).
-- Research: .docs/AI_TRIP_PLANNING_KNOWLEDGE.md section F.
--
-- Replaces only the V235 seed: a row an operator has edited (V235's rule 3 or rule 10 wording no
-- longer present) is left as it is.

UPDATE config
SET value = $ai$## Core planning rules
1. Places: use only candidate ids from the input, each at most once in the whole trip. Every stop is a real candidate or a rest block.
2. Meals, every day, each at a food candidate: BREAKFAST 06:30-09:00, LUNCH 11:30-13:30, DINNER 18:00-20:30, at least 3 hours apart.
   - Breakfast is breakfast food (phở, bún, bánh mì, xôi, cháo, bánh cuốn, cơm tấm...) at a venue tagged breakfast or clearly serving it. A cafe or dessert place is a break after a meal (at most one per day), not a meal.
   - Street breakfast sells out by ~09:30 and many kitchens pause 14:00-17:00; after 13:30 choose a restaurant, not a stall.
   - Evenings in Vietnam continue after dinner: a night market, walking street, riverside or late-night snack (ăn đêm, chè) is the default evening block when the profile allows it.
   - Across the trip the meals carry the destination's signature dishes, a different one at each main meal when the candidates allow (Mộc Châu: bê chao, cá suối nướng, xôi nương, lẩu cá hồi; Hà Nội: phở, bún chả, bánh cuốn). Lunch is near the morning's last stop; dinner is near the base.
3. Geography: give each half-day ONE area and write it in the day's "area" field before the stops; consecutive stops are neighbours (use lat/lng), and the day crosses the city at most once. Run each day as one loop out of the base (the town centre where travellers sleep): the farthest sight goes first, in the morning, and the day works its way back towards the base. In a spread-out mountain or countryside destination (Mộc Châu, Sa Pa, Hà Giang, Đà Lạt's outskirts) that means one direction per day, never out to one side and across to the other in the same afternoon. Travel time between stops: walking ~4 km/h up to 1.5 km; in a city taxi/Grab ~20 km/h (~14 km/h in rush hours 07:00-09:00 and 16:00-18:00) plus 5-10 minutes to find a ride; on mountain and country roads ~35 km/h by car or motorbike. Leave that gap between a stop's end and the next start.
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
10. Wording: each stop gets a practical guide for this traveller (see "Writing each stop") and the trip gets an overview (see "Writing the trip overview"); everything is grounded in the given data or stable, widely known facts, with no invented specifics.
11. Social-video itineraries: keep every social candidate, follow videoSequence above every other ordering rule, and output rows that share an optionGroupId as separate stops with the same time window (the user picks later).
12. Season first: plan around what makes these dates special here. The season's highlight (a flower or harvest season, cloud hunting, swimming weather, a festival) gets a stop in its best light and is named in the trip overview; a sight whose season is over is left out or described honestly (no blooming fields in the wrong month).
13. What a row is: its title and kinds decide. A restaurant, food shop, store, bar or club is a meal or a break, never a sight stop, whatever group it came in; a sight stop is somewhere people go to see or do something.
$ai$
WHERE label = 'AI_TRIP' AND key = 'PLAN_RULES'
  AND value LIKE '%3. Geography: give each half-day ONE area and write it in the day''s "area" field before the stops;%'
  AND value LIKE '%the trip description is at most 2 short sentences; everything is grounded in the given data, with no invented facts.%';
