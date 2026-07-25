-- Enhance AI trip planning with best practices from travel industry
-- Add fields for group composition, budget, dietary, activity preferences

ALTER TABLE ai_trip_drafts 
ADD COLUMN IF NOT EXISTS group_composition VARCHAR(500),
ADD COLUMN IF NOT EXISTS budget_min DECIMAL(12,2),
ADD COLUMN IF NOT EXISTS budget_max DECIMAL(12,2),
ADD COLUMN IF NOT EXISTS budget_currency VARCHAR(10),
ADD COLUMN IF NOT EXISTS travel_style VARCHAR(50),
ADD COLUMN IF NOT EXISTS activity_types TEXT, -- JSON array
ADD COLUMN IF NOT EXISTS dietary_restrictions TEXT, -- JSON array
ADD COLUMN IF NOT EXISTS mobility_considerations TEXT, -- JSON array
ADD COLUMN IF NOT EXISTS include_backup_activities BOOLEAN DEFAULT TRUE;

COMMENT ON COLUMN ai_trip_drafts.group_composition IS 'Description of travel group, e.g., "Family of 4 (2 adults, 2 kids aged 8-12)"';
COMMENT ON COLUMN ai_trip_drafts.budget_min IS 'Minimum daily budget per person';
COMMENT ON COLUMN ai_trip_drafts.budget_max IS 'Maximum daily budget per person';
COMMENT ON COLUMN ai_trip_drafts.budget_currency IS 'Currency code: VND, USD, EUR, etc.';
COMMENT ON COLUMN ai_trip_drafts.travel_style IS 'Relaxed, Adventure, Luxury, Cultural, Family-friendly';
COMMENT ON COLUMN ai_trip_drafts.activity_types IS 'JSON array of preferred activity types: Food, Nature, Culture, Adventure, Photography, Shopping, Wellness';
COMMENT ON COLUMN ai_trip_drafts.dietary_restrictions IS 'JSON array: Vegetarian, Halal, Gluten-free, Vegan, No-pork';
COMMENT ON COLUMN ai_trip_drafts.mobility_considerations IS 'JSON array: Elderly-friendly, Wheelchair-accessible, Kid-friendly';
COMMENT ON COLUMN ai_trip_drafts.include_backup_activities IS 'Whether to include indoor backup activities for bad weather';
