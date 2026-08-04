import json
import csv
from decimal import Decimal, InvalidOperation


def recalculated_score(place):
    """Return the backend's recalculated score, with missing values sorted last."""
    try:
        score = Decimal(str(place.get('adjustedRating')))
    except (InvalidOperation, TypeError, ValueError):
        return Decimal('-Infinity')
    return score if score.is_finite() else Decimal('-Infinity')

# Đọc file JSON
with open('places.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

# Lọc chỉ các place có visibilityStatus = "ACTIVE"
active_places = [
    place for place in data['data'] 
    if place.get('visibilityStatus') == 'ACTIVE'
]

# adjustedRating is the score recalculated by the backend from review data.
# Python's sort is stable, so places with the same/missing score retain source order.
active_places.sort(key=recalculated_score, reverse=True)

# Ghi ra CSV
with open('places.csv', 'w', encoding='utf-8', newline='') as f:
    writer = csv.writer(f)
    
    # Header
    writer.writerow(['id', 'name', 'address', 'about'])
    
    # Data rows
    for place in active_places:
        about_json = json.dumps(place.get('about', []), ensure_ascii=False, separators=(',', ':'))
        writer.writerow([
            place.get('id', ''),
            place.get('name', ''),
            place.get('address', ''),
            about_json
        ])

print(f"Created places.csv with {len(active_places)} active places")
