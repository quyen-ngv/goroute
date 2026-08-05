"""Generic GoRoute place-attribute enrichment runner.

The runner deliberately keeps web research outside the process.  The operator
passes a JSON evidence ledger on stdin after searching and opening the exact
address pages.  The same code path then handles CSV selection, authentication,
full review context, per-place context-cache writes, schema-shaped attributes,
description rendering, PUT, PUT-envelope validation, and resumable logs for one
row, a range, selected IDs, or the whole CSV.
"""

from __future__ import annotations

import argparse
import base64
import copy
import csv
import json
import os
import random
import re
import sys
import tempfile
import time
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Any, Iterable

import requests


JSON_STRING_FIELDS = {
    "reviewsPerRating",
    "images",
    "openHours",
    "regular",
    "popularTimes",
    "reservations",
    "orderOnline",
    "menu",
    "completeAddress",
    "about",
    "owner",
    "emails",
}

SOURCE_BANNED = (
    "theo foody",
    "theo review",
    "một bài viết",
    "nguồn cho biết",
    "nguồn ghi",
    "http://",
    "https://",
    "[cite",
    "chưa có thông tin",
    "đang cập nhật",
    "nên kiểm tra trước khi đến",
    "giờ có thể thay đổi",
)


REVIEW_PAGE_SIZE = 20
DEFAULT_REVIEW_MAX_PAGES = None
CONTEXT_DIR_NAME = "place_context"
REVIEW_SUMMARY_HEADING = "## 📝 Tóm tắt review của thực khách"
EN_REVIEW_SUMMARY_HEADING = "## 📝 Guest review summary"

HEADING_ICON_PREFIXES = (
    "📖 ",
    "🗺️ ",
    "🕐 ",
    "🍜 ",
    "🏠 ",
    "📝 ",
    "⏰ ",
    "💰 ",
    "📍 ",
    "💡 ",
    "♿ ",
    "🌊 ",
    "🍽️ ",
    "🎟️ ",
    "🧭 ",
    "🏛️ ",
    "🏡 ",
    "🏔️ ",
    "🎒 ",
    "🌤️ ",
    "💧 ",
    "🥾 ",
    "🥢 ",
    "🛏️ ",
    "🍲 ",
    "🎭 ",
    "🚗 ",
    "🛍️ ",
    "⚠️ ",
    "🛡️ ",
    "🎉 ",
    "🌅 ",
    "🪨 ",
    "🏊 ",
    "🍤 ",
    "🚶 ",
    "🌴 ",
    "🌿 ",
    "🛕 ",
    "🚲 ",
    "🚤 ",
    "🌉 ",
    "🌲 ",
    "📷 ",
)


def semantic_heading_icon(title: str, *, place_type: str = "attraction") -> str:
    normalized = title.casefold()
    for prefix in HEADING_ICON_PREFIXES:
        if title.startswith(prefix):
            return prefix.rstrip()
    if "review" in normalized or "tóm tắt" in normalized or "guest" in normalized:
        return "📝"
    if "overview" in normalized or "tổng quan" in normalized:
        return "📖" if place_type == "food" else "🗺️"
    if "món" in normalized or "dish" in normalized or "eat" in normalized:
        return "🍜"
    if "trải nghiệm" in normalized or "experience" in normalized or "ambience" in normalized:
        return "🏠"
    if "thời điểm" in normalized or "timing" in normalized or "when to" in normalized:
        return "⏰"
    if "giá" in normalized or "price" in normalized:
        return "💰"
    if "gần đây" in normalized or "nearby" in normalized or "combine" in normalized:
        return "📍"
    if "tiện ích" in normalized or "amenit" in normalized or "access" in normalized:
        return "♿"
    if "tip" in normalized or "lưu ý" in normalized or "practical" in normalized:
        return "💡"
    if any(word in normalized for word in ("history", "lịch sử", "origin", "nguồn gốc")):
        return "🏛️"
    if any(word in normalized for word in ("architecture", "kiến trúc", "house", "nhà trình")):
        return "🏡"
    if any(word in normalized for word in ("stay", "accommodation", "lưu trú", "homestay", "phòng")):
        return "🛏️"
    if any(word in normalized for word in ("season", "best time", "mùa", "thời điểm đẹp")):
        return "🌤️"
    if any(word in normalized for word in ("transport", "getting there", "di chuyển", "đường đi")):
        return "🚗"
    if any(word in normalized for word in ("culture", "văn hóa", "truyền thống", "festival", "lễ hội")):
        return "🎭"
    if any(word in normalized for word in ("safety", "an toàn", "lưu ý an toàn")):
        return "⚠️"
    if any(word in normalized for word in ("souvenir", "shopping", "quà", "mua sắm")):
        return "🛍️"
    return "🧭"


def normalize_heading_format(value: str, *, place_name: str = "", place_type: str = "attraction") -> str:
    """Keep every human-facing title at one visual Markdown level.

    Markdown ``##`` is intentionally used as the single title level: it is
    rendered larger and bolder than body text while avoiding place-name title
    banners. Standalone bold lines are treated as headings as well so an
    evidence ledger cannot accidentally create a visually different title.
    """
    lines: list[str] = []
    normalized_name = place_name.strip().casefold()
    for raw_line in value.splitlines():
        stripped = raw_line.strip()
        match = re.match(r"^#{1,6}\s*(.*?)\s*$", stripped)
        if match:
            title = match.group(1).strip()
        else:
            bold_match = re.match(r"^\*\*(.+?)\*\*\s*$", stripped)
            if not bold_match:
                lines.append(raw_line.rstrip())
                continue
            title = bold_match.group(1).strip()
        if not title:
            continue
        if normalized_name and title.casefold() == normalized_name:
            continue
        icon = semantic_heading_icon(title, place_type=place_type)
        for prefix in HEADING_ICON_PREFIXES:
            if title.startswith(prefix):
                title = title[len(prefix):].strip()
                break
        title = re.sub(r"^\*+|\*+$", "", title).strip()
        if title:
            lines.append(f"## {icon} {title}")
    return "\n".join(lines).strip()


class RunnerError(RuntimeError):
    pass


def utc_today() -> str:
    return datetime.now(timezone.utc).date().isoformat()


def read_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def write_json_atomic(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temp_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=str(path.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="") as handle:
            json.dump(value, handle, ensure_ascii=False, separators=(",", ":"))
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temp_name, path)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)


def write_text_atomic(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temp_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=str(path.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="") as handle:
            handle.write(text)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temp_name, path)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)


def parse_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    with path.open("r", encoding="utf-8-sig") as handle:
        for raw in handle:
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            value = value.strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            values[key.strip()] = value
    return values


def addresses_match(csv_address: Any, api_address: Any) -> bool:
    """Compare addresses while ignoring a display-only CSV prefix."""
    def normalize(value: Any) -> str:
        text = str(value or "").strip()
        text = re.sub(r"^address\s*:\s*", "", text, flags=re.IGNORECASE)
        return re.sub(r"\s+", " ", text).casefold()

    return normalize(csv_address) == normalize(api_address)


def resolve_placeholders(value: str, env: dict[str, str]) -> str:
    def replace(match: re.Match[str]) -> str:
        key = match.group(1)
        return env.get(key, match.group(0))

    return re.sub(r"\$\{([^}]+)\}", replace, value)


def safe_json_load(value: Any) -> Any:
    if isinstance(value, str):
        try:
            return json.loads(value)
        except json.JSONDecodeError:
            return value
    return value


def json_text(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def contains_replacement(value: Any) -> bool:
    if isinstance(value, str):
        return "\ufffd" in value
    if isinstance(value, dict):
        return any(contains_replacement(k) or contains_replacement(v) for k, v in value.items())
    if isinstance(value, list):
        return any(contains_replacement(item) for item in value)
    return False


def sanitize_reason(reason: str) -> str:
    clean = re.sub(r"[\r\n|]+", " ", str(reason)).strip()
    clean = re.sub(r"Bearer\s+\S+", "Bearer <redacted>", clean, flags=re.IGNORECASE)
    if len(clean) > 240:
        clean = clean[:237] + "..."
    return clean or "unknown failure"


def configure_utf8_stdout() -> None:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except (AttributeError, OSError):
        pass


def response_error(response: requests.Response) -> str:
    try:
        body = response.content.decode("utf-8", errors="replace")
        parsed = json.loads(body)
        meta = parsed.get("meta") if isinstance(parsed, dict) else None
        if isinstance(meta, dict) and meta.get("message"):
            return sanitize_reason(str(meta["message"]))
    except (UnicodeDecodeError, json.JSONDecodeError, TypeError, ValueError):
        pass
    return sanitize_reason(f"HTTP {response.status_code}")


def envelope(response: requests.Response, *, operation: str) -> dict[str, Any]:
    try:
        raw = response.content.decode("utf-8", errors="strict")
        data = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise RunnerError(f"{operation}: invalid UTF-8/JSON response") from exc
    if not isinstance(data, dict):
        raise RunnerError(f"{operation}: response is not an object")
    meta = data.get("meta")
    if not isinstance(meta, dict) or meta.get("code") != 200000:
        message = meta.get("message") if isinstance(meta, dict) else None
        raise RunnerError(f"{operation}: {sanitize_reason(message or 'application error')}")
    return data


def jwt_exp(token: str) -> int | None:
    try:
        parts = token.split(".")
        if len(parts) != 3:
            return None
        encoded = parts[1] + "=" * (-len(parts[1]) % 4)
        payload = json.loads(base64.urlsafe_b64decode(encoded).decode("utf-8"))
        exp = payload.get("exp")
        return int(exp) if exp is not None else None
    except (ValueError, TypeError, KeyError, UnicodeDecodeError, json.JSONDecodeError):
        return None


class GoRouteClient:
    def __init__(self, root: Path, env: dict[str, str]) -> None:
        self.root = root
        self.env = env
        base = resolve_placeholders(env.get("API_BASE_URL", ""), env).rstrip("/")
        if not base:
            raise RunnerError("API_BASE_URL is missing")
        self.base = base
        self.login_endpoint = resolve_placeholders(
            env.get("LOGIN_ENDPOINT", "${API_BASE_URL}/auth/login"), env
        )
        self.refresh_endpoint = resolve_placeholders(
            env.get("REFRESH_ENDPOINT", "${API_BASE_URL}/auth/refresh"), env
        )
        self.update_template = resolve_placeholders(
            env.get("UPDATE_ENDPOINT", "${API_BASE_URL}/admin/places/{id}"), env
        )
        self.review_template = resolve_placeholders(
            env.get("PLACE_REVIEWS_ENDPOINT", "${API_BASE_URL}/places/{id}/reviews"), env
        )
        self.cache_path = root / "token_cache.json"
        self.access_token: str | None = None
        self.refresh_token: str | None = None
        self.expires_at: int | None = None
        self.session = requests.Session()
        self._load_cache()

    def _load_cache(self) -> None:
        if not self.cache_path.exists():
            return
        try:
            cached = read_json(self.cache_path)
        except (OSError, json.JSONDecodeError):
            return
        if not isinstance(cached, dict):
            return
        self.access_token = cached.get("accessToken")
        self.refresh_token = cached.get("refreshToken")
        self.expires_at = cached.get("expires_at")
        if not self.expires_at and self.access_token:
            self.expires_at = jwt_exp(self.access_token)

    def _save_cache(self, data: dict[str, Any]) -> None:
        access = data.get("accessToken")
        refresh = data.get("refreshToken") or self.refresh_token
        if not access:
            raise RunnerError("authentication response did not contain accessToken")
        expires = jwt_exp(access)
        cache = {
            "accessToken": access,
            "refreshToken": refresh,
            "expires_at": expires,
        }
        write_json_atomic(self.cache_path, cache)
        self.access_token = access
        self.refresh_token = refresh
        self.expires_at = expires

    def _token_valid(self) -> bool:
        return bool(self.access_token and self.expires_at and int(time.time()) < int(self.expires_at) - 30)

    def _authenticate(self) -> None:
        if self._token_valid():
            return
        if self.refresh_token:
            response = self.session.post(
                self.refresh_endpoint,
                json={"refreshToken": self.refresh_token},
                timeout=30,
            )
            if response.status_code in range(200, 300):
                try:
                    body = envelope(response, operation="refresh")
                    self._save_cache(body.get("data") or {})
                    return
                except RunnerError:
                    pass
        identifier = self.env.get("LOGIN_IDENTIFIER")
        password = self.env.get("LOGIN_PASSWORD")
        if not identifier or not password:
            raise RunnerError("credentials are missing from .env")
        response = self.session.post(
            self.login_endpoint,
            json={"email": identifier, "password": password},
            timeout=30,
        )
        if not 200 <= response.status_code < 300:
            raise RunnerError(f"login failed: HTTP {response.status_code}")
        body = envelope(response, operation="login")
        self._save_cache(body.get("data") or {})

    def _request(self, method: str, url: str, *, retry_auth: bool = True, **kwargs: Any) -> requests.Response:
        self._authenticate()
        headers = dict(kwargs.pop("headers", {}) or {})
        headers["Authorization"] = f"Bearer {self.access_token}"
        headers.setdefault("Accept", "application/json")
        for attempt in range(4):
            response = self.session.request(method, url, headers=headers, **kwargs)
            if response.status_code == 401 and retry_auth:
                self.access_token = None
                self.expires_at = None
                self._authenticate()
                headers["Authorization"] = f"Bearer {self.access_token}"
                retry_auth = False
                continue
            if response.status_code == 429 or response.status_code >= 500:
                if attempt == 3:
                    return response
                retry_after = response.headers.get("Retry-After")
                try:
                    delay = min(8.0, max(0.2, float(retry_after))) if retry_after else None
                except ValueError:
                    delay = None
                if delay is None:
                    delay = min(8.0, 0.5 * (2**attempt) + random.random() * 0.25)
                time.sleep(delay)
                continue
            return response
        return response

    def get_place(self, place_id: str) -> dict[str, Any]:
        url = self.update_template.replace("{id}", place_id)
        response = self._request("GET", url, timeout=30)
        if not 200 <= response.status_code < 300:
            raise RunnerError(f"GET place failed: {response_error(response)}")
        return envelope(response, operation="GET place").get("data") or {}

    def get_place_reviews(self, place_id: str, *, max_pages: int | None = None) -> list[dict[str, Any]]:
        if max_pages is not None and max_pages < 1:
            raise RunnerError("review-max-pages must be at least 1")
        url = self.review_template.replace("{id}", place_id)
        result: list[dict[str, Any]] = []
        previous_page_signatures: set[tuple[str, ...]] = set()
        page = 0
        while True:
            response = self._request(
                "GET",
                url,
                params={"page": page, "size": REVIEW_PAGE_SIZE},
                timeout=30,
            )
            if not 200 <= response.status_code < 300:
                raise RunnerError(f"GET place reviews failed: {response_error(response)}")
            raw_page = envelope(response, operation="GET place reviews").get("data")
            if not isinstance(raw_page, list):
                raise RunnerError("GET place reviews: data is not an array")
            page_reviews = [item for item in raw_page if isinstance(item, dict)]
            page_signature = tuple(
                str(item.get("id") or item.get("description") or "") for item in page_reviews
            )
            if page_signature and page_signature in previous_page_signatures:
                break
            if page_signature:
                previous_page_signatures.add(page_signature)
            result.extend(page_reviews)
            page += 1
            if not page_reviews or len(raw_page) < REVIEW_PAGE_SIZE:
                break
            if max_pages is not None and page >= max_pages:
                break
        return result

    def put_place(self, place_id: str, payload: dict[str, Any]) -> None:
        url = self.update_template.replace("{id}", place_id)
        encoded = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        if b"\xef\xbf\xbd" in encoded:
            raise RunnerError("payload contains replacement character")
        response = self._request(
            "PUT",
            url,
            data=encoded,
            headers={"Content-Type": "application/json; charset=utf-8"},
            timeout=45,
        )
        if not 200 <= response.status_code < 300:
            raise RunnerError(f"PUT place failed: {response_error(response)}")
        envelope(response, operation="PUT place")


def as_finite_number(value: Any) -> float | None:
    if isinstance(value, bool):
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if number == number and number not in (float("inf"), float("-inf")) else None


def normalize_review_content(value: Any) -> str:
    text = re.sub(r"https?://\S+", "", str(value or ""), flags=re.IGNORECASE)
    text = re.sub(r"\s+", " ", text).strip(" -–—\t\r\n")
    return text


def review_bucket(rating: float | None) -> str:
    if rating is None:
        return "neutral"
    if rating >= 4:
        return "positive"
    if rating <= 2:
        return "negative"
    return "neutral"


def review_sort_key(review: dict[str, Any]) -> tuple[float, float, str]:
    authenticity = review.get("authenticity_score")
    rating = review.get("rating")
    return (
        authenticity if isinstance(authenticity, float) else -1.0,
        rating if isinstance(rating, float) else -1.0,
        str(review.get("review_date") or ""),
    )


def build_review_context(reviews: Iterable[dict[str, Any]]) -> dict[str, Any]:
    review_rows = list(reviews)
    usable: list[dict[str, Any]] = []
    for raw in review_rows:
        content = normalize_review_content(raw.get("description"))
        normalized = re.sub(r"\W+", "", content.casefold(), flags=re.UNICODE)
        if not normalized:
            continue
        rating = as_finite_number(raw.get("rating"))
        authenticity = as_finite_number(raw.get("authenticityScore"))
        usable.append(
            {
                "rating": rating,
                "authenticity_score": authenticity,
                "review_date": str(raw.get("reviewDate") or "").strip() or None,
                "content": content,
            }
        )

    grouped: dict[str, list[dict[str, Any]]] = {"positive": [], "neutral": [], "negative": []}
    for review in sorted(usable, key=review_sort_key, reverse=True):
        grouped[review_bucket(review.get("rating"))].append(review)

    return {
        "fetched_count": len(review_rows),
        "usable_count": len(usable),
        "context_count": len(usable),
        "sentiment_counts": {bucket: len(items) for bucket, items in grouped.items()},
        "reviews": [{"content": review["content"]} for review in usable],
    }


def context_file_path(root: Path, place_id: str) -> Path:
    return root / CONTEXT_DIR_NAME / f"{place_id}.json"


def load_cached_evidence(root: Path, place_id: str) -> dict[str, Any] | None:
    path = context_file_path(root, place_id)
    if not path.exists():
        return None
    try:
        document = read_json(path)
    except (OSError, json.JSONDecodeError) as exc:
        raise RunnerError(f"context cache is not valid JSON: {path.name}") from exc
    internet = document.get("internet") if isinstance(document, dict) else None
    if not isinstance(internet, dict):
        return None
    if not any(internet.get(key) for key in ("facts", "attribute_updates", "references")):
        return None
    return {
        "place_type": internet.get("place_type"),
        "facts": copy.deepcopy(internet.get("facts") or {}),
        "attribute_updates": copy.deepcopy(internet.get("attribute_updates") or {}),
        "references": copy.deepcopy(internet.get("references") or []),
    }


def build_context_document(
    place_id: str,
    row: dict[str, str],
    current: dict[str, Any],
    review_context: dict[str, Any],
    evidence: dict[str, Any] | None,
    references: list[dict[str, Any]],
    description: str | None,
    description_en: str | None,
    mode: str,
) -> dict[str, Any]:
    csv_info: dict[str, Any] = copy.deepcopy(row)
    if "about" in csv_info:
        csv_info["about"] = safe_json_load(csv_info["about"])
    api_info = copy.deepcopy(current)
    for field in ("attributeSchema", "reviews", "rawData", "distance"):
        api_info.pop(field, None)
    internet = None
    if isinstance(evidence, dict):
        facts = evidence.get("facts") if isinstance(evidence.get("facts"), dict) else {}
        updates = evidence.get("attribute_updates") if isinstance(evidence.get("attribute_updates"), dict) else {}
        internet = {
            "place_type": evidence.get("place_type"),
            "facts": copy.deepcopy(facts),
            "attribute_updates": copy.deepcopy(updates),
            "references": copy.deepcopy(references),
        }
    generated = None
    if description is not None:
        generated = {"description": description}
        if description_en is not None:
            generated["description_en"] = description_en
    return {
        "version": 1,
        "place_id": place_id,
        "collected_at": datetime.now(timezone.utc).isoformat(),
        "collection_mode": mode,
        "csv": csv_info,
        "api": api_info,
        "reviews": copy.deepcopy(review_context),
        "internet": internet,
        "generated": generated,
    }


def save_context_document(root: Path, place_id: str, document: dict[str, Any]) -> None:
    write_json_atomic(context_file_path(root, place_id), document)


def selected_rows(rows: list[dict[str, str]], args: argparse.Namespace) -> list[dict[str, str]]:
    if args.ids:
        wanted = set(args.ids)
        result = [row for row in rows if row.get("id") in wanted]
        missing = wanted - {row.get("id") for row in result}
        if missing:
            raise RunnerError(f"IDs not found in CSV: {', '.join(sorted(missing))}")
        return result
    if args.row:
        if args.row < 1 or args.row > len(rows):
            raise RunnerError(f"row must be between 1 and {len(rows)}")
        return [rows[args.row - 1]]
    if args.start_row or args.end_row:
        start = args.start_row or 1
        end = args.end_row or len(rows)
        if start < 1 or end < start or end > len(rows):
            raise RunnerError("invalid row range")
        return rows[start - 1 : end]
    return rows


def read_logs(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    if not path.exists():
        return result
    with path.open("r", encoding="utf-8-sig") as handle:
        for line in handle:
            line = line.rstrip("\r\n")
            if not line or " | " not in line:
                continue
            place_id = line.split(" | ", 1)[0]
            result[place_id] = line
    return result


def upsert_log(path: Path, place_id: str, line: str) -> None:
    entries = read_logs(path)
    entries[place_id] = line
    text = "".join(f"{entry}\n" for entry in entries.values())
    write_text_atomic(path, text)


def remove_log_id(path: Path, place_id: str) -> None:
    entries = read_logs(path)
    if place_id not in entries:
        return
    del entries[place_id]
    write_text_atomic(path, "".join(f"{entry}\n" for entry in entries.values()))


def description_quality(value: Any, *, require_review_section: bool = False) -> bool:
    if not isinstance(value, str):
        return False
    if not value.strip():
        return False
    if re.search(r"^#(?!#)\s+\S+", value, re.MULTILINE):
        return False
    if not standard_heading_quality(value):
        return False
    if require_review_section and not re.search(
        rf"^{re.escape(REVIEW_SUMMARY_HEADING)}\s*$", value, re.MULTILINE
    ):
        return False
    lowered = value.casefold()
    return not any(banned in lowered for banned in SOURCE_BANNED)


def standard_heading_quality(value: str) -> bool:
    """Require one consistent, icon-led ``##`` heading style in both locales."""
    heading_seen = False
    for raw_line in value.splitlines():
        stripped = raw_line.strip()
        if not stripped:
            continue
        if stripped.startswith("#"):
            heading_seen = True
            match = re.match(r"^##\s+(\S.*)$", stripped)
            if not match:
                return False
            title = match.group(1)
            if title.startswith("**"):
                return False
            first = title[0]
            if not any(title.startswith(prefix) for prefix in HEADING_ICON_PREFIXES) and ord(first) < 0x2300:
                return False
        elif re.match(r"^\*\*.+?\*\*\s*$", stripped):
            return False
    return heading_seen


def translation_description(translations: Any, locale: str) -> str:
    translations = safe_json_load(translations)
    if not isinstance(translations, dict):
        return ""
    entry = translations.get(locale)
    if not isinstance(entry, dict):
        return ""
    return str(entry.get("description") or entry.get("descriptions") or "").strip()


def english_description_quality(value: Any, *, require_review_section: bool = False) -> bool:
    if not description_quality(value, require_review_section=False):
        return False
    if require_review_section and not re.search(
        rf"^{re.escape(EN_REVIEW_SUMMARY_HEADING)}\s*$", str(value), re.MULTILINE
    ):
        return False
    return True


TIME_RANGE_PATTERN = re.compile(
    r"(?P<start_hour>\d{1,2})(?::(?P<start_minute>\d{2}))?\s*(?P<start_period>[AaPp]\.?(?:[Mm])\.?)?"
    r"\s*[-–—]\s*"
    r"(?P<end_hour>\d{1,2})(?::(?P<end_minute>\d{2}))?\s*(?P<end_period>[AaPp]\.?(?:[Mm])\.?)?"
)


def parse_opening_minute(hour: str, minute: str | None, period: str | None) -> int | None:
    try:
        hour_value = int(hour)
        minute_value = int(minute or "0")
    except ValueError:
        return None
    if not 0 <= minute_value < 60:
        return None
    normalized_period = (period or "").replace(".", "").casefold()
    if normalized_period:
        if not 1 <= hour_value <= 12:
            return None
        hour_value %= 12
        if normalized_period == "pm":
            hour_value += 12
    elif not 0 <= hour_value <= 24:
        return None
    return hour_value * 60 + minute_value


def opening_intervals(raw: Any) -> tuple[bool, list[tuple[int, int]]]:
    if isinstance(raw, list):
        text = " ".join(str(item) for item in raw)
    else:
        text = str(raw or "")
    normalized = re.sub(r"\s+", " ", text).strip().casefold()
    if not normalized:
        return False, []
    if "closed" in normalized or "đóng cửa" in normalized:
        return True, []
    if "24 hours" in normalized or "mở cửa 24 giờ" in normalized:
        return False, [(0, 24 * 60)]
    intervals: list[tuple[int, int]] = []
    for match in TIME_RANGE_PATTERN.finditer(text):
        start = parse_opening_minute(
            match.group("start_hour"), match.group("start_minute"), match.group("start_period")
        )
        end = parse_opening_minute(
            match.group("end_hour"), match.group("end_minute"), match.group("end_period")
        )
        if start is None or end is None:
            continue
        if end <= start:
            end += 24 * 60
        intervals.append((start, end))
    return False, intervals


def join_vietnamese(items: list[str]) -> str:
    if len(items) <= 1:
        return "".join(items)
    if len(items) == 2:
        return f"{items[0]} và {items[1]}"
    return ", ".join(items[:-1]) + f" và {items[-1]}"


def special_hours_note(value: Any) -> str | None:
    hours = safe_json_load(value)
    if not isinstance(hours, dict):
        return None
    day_names = {
        "Monday": "thứ Hai",
        "Tuesday": "thứ Ba",
        "Wednesday": "thứ Tư",
        "Thursday": "thứ Năm",
        "Friday": "thứ Sáu",
        "Saturday": "thứ Bảy",
        "Sunday": "Chủ nhật",
    }
    normalized_hours = {str(key).casefold(): raw for key, raw in hours.items()}
    closed_days: list[str] = []
    all_intervals: list[tuple[int, int]] = []
    opening_days = 0
    has_split_schedule = False
    for english_day, vietnamese_day in day_names.items():
        raw = normalized_hours.get(english_day.casefold())
        if raw is None:
            continue
        is_closed, intervals = opening_intervals(raw)
        if is_closed:
            closed_days.append(vietnamese_day)
            continue
        if intervals:
            opening_days += 1
            all_intervals.extend(intervals)
            has_split_schedule = has_split_schedule or len(intervals) > 1

    notes: list[str] = []
    if closed_days:
        notes.append(f"Quán thường nghỉ vào {join_vietnamese(closed_days)}.")
    if opening_days >= 3 and all_intervals:
        if all(start >= 9 * 60 and end <= 15 * 60 + 30 for start, end in all_intervals):
            notes.append("Quán chủ yếu phục vụ buổi trưa.")
        elif all(start >= 16 * 60 for start, _ in all_intervals):
            notes.append("Quán chủ yếu phục vụ vào buổi tối.")
        elif has_split_schedule:
            notes.append("Quán phục vụ theo nhiều khung giờ trong ngày.")
    return " ".join(notes) or None


def as_bullets(items: Any) -> list[str]:
    if not isinstance(items, list):
        return []
    result: list[str] = []
    for item in items:
        if isinstance(item, str) and item.strip():
            result.append(item.strip())
        elif isinstance(item, dict):
            name = str(item.get("name") or item.get("title") or "").strip()
            detail = str(item.get("description") or item.get("detail") or "").strip()
            if name and detail:
                result.append(f"{name} — {detail}")
            elif name:
                result.append(name)
    return result


def custom_description_sections(items: Any) -> list[tuple[str, str]]:
    """Return optional editorial sections without forcing a fixed template.

    Evidence may provide a list of sections with ``title``/``heading`` and
    ``body``/``content`` plus optional ``bullets``.  The runner still applies
    the shared heading normalizer later, so the evidence author can focus on
    the editorial order and useful content for that place.
    """
    if not isinstance(items, list):
        return []
    result: list[tuple[str, str]] = []
    for item in items:
        if not isinstance(item, dict):
            continue
        title = str(item.get("title") or item.get("heading") or "").strip()
        title = re.sub(r"^#{1,6}\s*", "", title).strip()
        bold_title = re.fullmatch(r"\*\*(.+?)\*\*", title)
        if bold_title:
            title = bold_title.group(1).strip()
        if not title:
            continue

        raw_body = item.get("body")
        if raw_body is None:
            raw_body = item.get("content")
        if raw_body is None:
            raw_body = item.get("description")
        if isinstance(raw_body, list):
            body = "\n".join(str(value).strip() for value in raw_body if str(value).strip())
        else:
            body = str(raw_body or "").strip()
        bullets = as_bullets(item.get("bullets") or item.get("items"))
        if bullets:
            if body:
                body += "\n"
            body += "\n".join(f"- {bullet}" for bullet in bullets)
        if body:
            result.append((title, body))
    return result


def render_description(
    row: dict[str, str],
    record: dict[str, Any],
    evidence: dict[str, Any],
    review_context: dict[str, Any],
    force: bool,
) -> tuple[str, str, bool]:
    has_review_context = bool(review_context.get("reviews"))
    existing = record.get("descriptions")
    existing_en = translation_description(record.get("translations"), "en")
    if (
        not force
        and description_quality(existing, require_review_section=has_review_context)
        and english_description_quality(existing_en, require_review_section=has_review_context)
    ):
        return str(existing).strip(), existing_en, True
    facts = evidence.get("facts") if isinstance(evidence.get("facts"), dict) else evidence
    place_type = str(evidence.get("place_type") or "").lower()
    if not place_type:
        category = f"{row.get('category', '')} {row.get('placeGroup', '')} {record.get('category', '')} {record.get('placeGroup', '')}".lower()
        place_type = "food" if any(word in category for word in ("food", "drink", "restaurant", "cafe", "bistro", "ăn")) else "attraction"
    overview = str(facts.get("overview") or "").strip()
    sensory = str(facts.get("sensory_detail") or "").strip()
    if not overview or not sensory:
        raise RunnerError("INSUFFICIENT_DESCRIPTION_EVIDENCE")
    if sensory.casefold() not in overview.casefold():
        overview = f"{overview} {sensory}"
    name = str(record.get("title") or record.get("name") or row.get("name") or "").strip()
    if not name:
        raise RunnerError("missing place title")
    review_summary = str(facts.get("review_summary") or "").strip()
    if has_review_context and not review_summary:
        raise RunnerError("missing review_summary for available review context")
    description_en = str(facts.get("description_en") or "").strip()
    if not description_en:
        raise RunnerError("missing facts.description_en")
    description_en = normalize_heading_format(
        description_en,
        place_name=name,
        place_type=place_type,
    )
    # Evidence may be drafted from the Vietnamese template. Keep the English
    # copy readable for foreign visitors when a localized heading slips in.
    description_en = description_en.replace("## 📍 Gần đây", "## 📍 Nearby / combine with")
    description_en = description_en.replace("## 📖 Overview", "## 🗺️ Overview")
    if not re.search(r"(?m)^## 🗺️ Overview$", description_en):
        description_en = f"## 🗺️ Overview\n\n{description_en}"

    lines = ["## 📖 Overview" if place_type == "food" else "## 🗺️ Overview", overview]
    dishes = as_bullets(facts.get("dishes"))
    if dishes and place_type == "food":
        lines.extend(["", "## 🍜 Món nên ăn", *[f"- {item}" for item in dishes]])
    experience = str(facts.get("experience") or facts.get("ambience") or "").strip()
    if experience:
        lines.extend(["", "## 🏠 Không khí / trải nghiệm", experience])
    for section_title, section_body in custom_description_sections(facts.get("sections")):
        lines.extend(["", f"## {section_title}", section_body])
    if review_summary:
        lines.extend(["", REVIEW_SUMMARY_HEADING, review_summary])
    visit_timing = special_hours_note(record.get("openHours"))
    if visit_timing:
        lines.extend(["", "## 🕐 Thời điểm ghé", visit_timing])
    price = str(facts.get("price") or "").strip()
    if price:
        lines.extend(["", "## 💰 Giá tham khảo", price])
    nearby = as_bullets(facts.get("nearby"))
    if nearby:
        lines.extend(["", "## 📍 Gần đây", *[f"- {item}" for item in nearby]])
    tips = as_bullets(facts.get("tips"))
    if tips:
        lines.extend(["", "## 💡 Tips", *[f"- {item}" for item in tips]])
    amenities = as_bullets(facts.get("amenities"))
    if amenities:
        lines.extend(["", "## ♿ Tiện ích", *[f"- {item}" for item in amenities]])
    description = normalize_heading_format(
        "\n".join(lines).strip(),
        place_name=name,
        place_type=place_type,
    )
    if not description_quality(description, require_review_section=has_review_context):
        raise RunnerError("INSUFFICIENT_DESCRIPTION_EVIDENCE")
    if not english_description_quality(description_en, require_review_section=has_review_context):
        raise RunnerError("INSUFFICIENT_ENGLISH_DESCRIPTION_EVIDENCE")
    return description, description_en, False


def unknown_entry(field: dict[str, Any] | None, current: Any, key: str) -> dict[str, Any]:
    field_type = field.get("type") if isinstance(field, dict) else None
    multiple = bool(field.get("multiple")) if isinstance(field, dict) else key == "ambience"
    if multiple or field_type not in (None, "BOOLEAN"):
        return {"value": None, "description": None, "source_found": False}
    if isinstance(current, dict) and "description" in current:
        return {"value": None, "description": None, "source_found": False}
    return {"value": None, "source_found": False}


def build_unknown_attributes(current: Any, schema: Any) -> dict[str, Any]:
    current = current if isinstance(current, dict) else {}
    schema_map = {item.get("key"): item for item in schema if isinstance(item, dict) and item.get("key")} if isinstance(schema, list) else {}
    keys = list(current.keys())
    for key in schema_map:
        if key not in keys:
            keys.append(key)
    return {key: unknown_entry(schema_map.get(key), current.get(key), key) for key in keys}


def validate_update(key: str, entry: dict[str, Any], field: dict[str, Any] | None) -> None:
    if not isinstance(entry, dict) or entry.get("source_found") is not True:
        raise RunnerError(f"attribute {key}: supported updates require source_found=true")
    value = entry.get("value")
    if value is None:
        raise RunnerError(f"attribute {key}: supported update cannot be null")
    field_type = field.get("type") if isinstance(field, dict) else None
    multiple = bool(field.get("multiple")) if isinstance(field, dict) else isinstance(value, list)
    allowed = field.get("allowedValues") if isinstance(field, dict) else None
    if multiple:
        if not isinstance(value, list) or not value or any(not isinstance(item, str) for item in value):
            raise RunnerError(f"attribute {key}: invalid multi-value shape")
        if allowed and any(item not in allowed for item in value):
            raise RunnerError(f"attribute {key}: value outside schema enum")
    elif field_type == "BOOLEAN" or isinstance(value, bool):
        if not isinstance(value, bool):
            raise RunnerError(f"attribute {key}: expected boolean")
    else:
        if not isinstance(value, str):
            raise RunnerError(f"attribute {key}: expected enum string")
        if allowed and value not in allowed:
            raise RunnerError(f"attribute {key}: value outside schema enum")
        if not isinstance(entry.get("description"), str) or not entry["description"].strip():
            raise RunnerError(f"attribute {key}: ordinal update needs a description")


def apply_attribute_updates(current: Any, schema: Any, evidence: dict[str, Any]) -> dict[str, Any]:
    attributes = build_unknown_attributes(current, schema)
    updates = evidence.get("attribute_updates") or {}
    if not isinstance(updates, dict):
        raise RunnerError("attribute_updates must be an object")
    schema_map = {item.get("key"): item for item in schema if isinstance(item, dict) and item.get("key")} if isinstance(schema, list) else {}
    for key, raw in updates.items():
        if key not in attributes:
            raise RunnerError(f"attribute {key}: not present in API schema")
        if not isinstance(raw, dict):
            raw = {"value": raw}
        entry = copy.deepcopy(raw)
        entry.setdefault("source_found", True)
        field = schema_map.get(key)
        validate_update(key, entry, field)
        field_type = field.get("type") if isinstance(field, dict) else None
        if field_type == "BOOLEAN" or (isinstance(entry.get("value"), bool) and not isinstance(attributes[key].get("value"), list)):
            attributes[key] = {"value": entry["value"], "source_found": True}
        else:
            attributes[key] = {
                "value": entry["value"],
                "description": entry.get("description"),
                "source_found": True,
            }
    return attributes


def normalize_references(raw: Any) -> list[dict[str, Any]]:
    if raw is None:
        return []
    if not isinstance(raw, list):
        raise RunnerError("references must be an array")
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item in raw:
        if not isinstance(item, dict) or item.get("exact_address_match") is False:
            continue
        url = str(item.get("url") or "").strip()
        if not url.startswith("https://") or url in seen:
            continue
        source_type = str(item.get("type") or "").upper()
        if source_type not in {"ARTICLE", "REVIEW"}:
            raise RunnerError("references require type ARTICLE or REVIEW")
        title = str(item.get("title") or "").strip()
        publisher = str(item.get("publisher") or "").strip()
        relevance = str(item.get("relevance") or "").strip()
        if not title or not publisher or not relevance:
            raise RunnerError("references require title, publisher, and relevance")
        result.append(
            {
                "type": source_type,
                "title": title,
                "url": url,
                "publisher": publisher,
                "publishedAt": item.get("publishedAt"),
                "accessedAt": str(item.get("accessedAt") or utc_today()),
                "relevance": relevance,
            }
        )
        seen.add(url)
    return result


def build_payload(
    record: dict[str, Any],
    attributes: dict[str, Any],
    description: str,
    description_en: str,
    references: list[dict[str, Any]],
) -> dict[str, Any]:
    payload = copy.deepcopy(record)
    payload.pop("attributeSchema", None)
    payload.pop("distance", None)
    payload.pop("reviews", None)
    payload.pop("rawData", None)
    payload.pop("id", None)
    payload.pop("placeId", None)
    payload["attributes"] = attributes
    payload["descriptions"] = description
    payload["aiReferences"] = references
    # The API resolves descriptions from the locale translation table.  Send
    # both manually written copies on every regenerated place: Vietnamese is
    # the default/top-level copy, while English is stored in the EN row.
    raw_translations = safe_json_load(payload.get("translations"))
    translations = copy.deepcopy(raw_translations) if isinstance(raw_translations, dict) else {}
    title = str(record.get("title") or record.get("name") or "").strip()
    for locale, localized_description in (("vi", description), ("en", description_en)):
        current_translation = translations.get(locale)
        entry = copy.deepcopy(current_translation) if isinstance(current_translation, dict) else {}
        entry["name"] = str(entry.get("name") or title).strip()
        entry["description"] = localized_description
        entry.pop("descriptions", None)
        translations[locale] = entry
    payload["translations"] = translations
    for field in JSON_STRING_FIELDS:
        if field in payload and isinstance(payload[field], (dict, list)):
            payload[field] = json_text(payload[field])
    if "attributes" not in payload or not isinstance(payload["attributes"], dict):
        raise RunnerError("payload attributes must be an object")
    if contains_replacement(payload):
        raise RunnerError("payload contains Unicode replacement character")
    serialized = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    if b"\xef\xbf\xbd" in serialized:
        raise RunnerError("payload UTF-8 serialization contains replacement character")
    return payload


def normalize_evidence_value(value: Any) -> dict[str, dict[str, Any]]:
    if isinstance(value, dict) and isinstance(value.get("rows"), list):
        rows = value["rows"]
        return {str(item.get("id")): item for item in rows if isinstance(item, dict) and item.get("id")}
    if not isinstance(value, dict):
        raise RunnerError("evidence must be an object keyed by place ID")
    return {str(key): item for key, item in value.items() if isinstance(item, dict)}


def load_evidence(stream: Any = None, env_name: str | None = None, path: Path | None = None) -> dict[str, dict[str, Any]]:
    try:
        if path:
            value = read_json(path)
        elif env_name:
            value = json.loads(os.environ.get(env_name, ""))
        else:
            value = json.load(stream)
    except json.JSONDecodeError as exc:
        source = f" file {path}" if path else f" environment variable {env_name}" if env_name else " stdin"
        raise RunnerError(f"evidence{source} is not valid JSON") from exc
    return normalize_evidence_value(value)


def make_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    selector = parser.add_mutually_exclusive_group()
    selector.add_argument("--row", type=int, help="one-based data row in places.csv")
    selector.add_argument("--ids", nargs="+", help="selected place IDs")
    parser.add_argument("--start-row", type=int)
    parser.add_argument("--end-row", type=int)
    parser.add_argument("--force", "--rewrite", action="store_true", help="process rows already marked OK")
    parser.add_argument("--evidence-stdin", action="store_true", help="read the opened-page evidence ledger from stdin")
    parser.add_argument("--evidence-env", help="read the opened-page evidence ledger from this environment variable")
    parser.add_argument("--evidence-file", type=Path, help="read the opened-page evidence ledger from this JSON file")
    parser.add_argument(
        "--review-context-only",
        action="store_true",
        help="collect full review context into place_context without updating places",
    )
    parser.add_argument(
        "--print-review-context",
        action="store_true",
        help="with --review-context-only, print every review content instead of only cache metadata",
    )
    parser.add_argument(
        "--review-max-pages",
        type=int,
        default=DEFAULT_REVIEW_MAX_PAGES,
        help="optional review-page cap for debugging; omitted means fetch all pages",
    )
    return parser.parse_args()


def main() -> int:
    configure_utf8_stdout()
    args = make_args()
    if args.review_max_pages is not None and args.review_max_pages < 1:
        raise RunnerError("review-max-pages must be at least 1")
    if args.print_review_context and not args.review_context_only:
        raise RunnerError("--print-review-context requires --review-context-only")
    root = Path(__file__).resolve().parents[1]
    env = parse_env(root / ".env")
    csv_path = root / "places.csv"
    with csv_path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        required = {"id", "name", "address"}
        if not required.issubset(set(reader.fieldnames or [])):
            raise RunnerError("places.csv must contain id, name, and address")
        rows = list(reader)
    targets = selected_rows(rows, args)
    modes = sum(bool(item) for item in (args.evidence_stdin, args.evidence_env, args.evidence_file))
    if modes > 1:
        raise RunnerError("choose one evidence input mode")
    evidence = (
        load_evidence(sys.stdin)
        if args.evidence_stdin
        else load_evidence(env_name=args.evidence_env)
        if args.evidence_env
        else load_evidence(path=args.evidence_file)
        if args.evidence_file
        else {}
    )
    client = GoRouteClient(root, env)
    if args.review_context_only:
        contexts: list[dict[str, Any]] = []
        for row in targets:
            place_id = str(row.get("id") or "").strip()
            if not place_id:
                raise RunnerError("selected CSV row is missing id")
            current = client.get_place(place_id)
            if str(current.get("title") or current.get("name") or "").strip() != str(row.get("name") or "").strip():
                raise RunnerError("CSV/API title mismatch")
            if not addresses_match(row.get("address"), current.get("address")):
                raise RunnerError("CSV/API address mismatch")
            raw_reviews = client.get_place_reviews(place_id, max_pages=args.review_max_pages)
            review_context = build_review_context(raw_reviews)
            cached_item = evidence.get(place_id) if place_id in evidence else load_cached_evidence(root, place_id)
            cached_references = normalize_references(cached_item.get("references")) if cached_item else []
            save_context_document(
                root,
                place_id,
                build_context_document(
                    place_id,
                    row,
                    current,
                    review_context,
                    cached_item,
                    cached_references,
                    str(current.get("descriptions") or "").strip() or None,
                    None,
                    "review-context-only",
                ),
            )
            output_review_context = review_context if args.print_review_context else {
                key: review_context[key]
                for key in ("fetched_count", "usable_count", "context_count", "sentiment_counts")
            }
            contexts.append(
                {
                    "id": place_id,
                    "name": str(row.get("name") or "").strip(),
                    "review_context": output_review_context,
                    "context_file": str(context_file_path(root, place_id)),
                }
            )
        print(json.dumps({"rows": contexts}, ensure_ascii=False, separators=(",", ":")))
        return 0
    results_path = root / "results.log"
    failed_path = root / "failed.log"
    existing_results = read_logs(results_path)
    summary = {
        "selected": len(targets),
        "skipped": 0,
        "updated": 0,
        "failed": 0,
        "preserved": 0,
        "regenerated": 0,
        "sources": 0,
        "review_context_rows": 0,
        "review_context_items": 0,
    }

    for row in targets:
        place_id = str(row.get("id") or "").strip()
        name = str(row.get("name") or "").strip()
        if not place_id:
            summary["failed"] += 1
            continue
        if not args.force and existing_results.get(place_id, "").split(" | ")[-1].startswith("OK"):
            summary["skipped"] += 1
            continue
        try:
            item = evidence.get(place_id) if place_id in evidence else load_cached_evidence(root, place_id)
            if item is None:
                raise RunnerError("missing evidence ledger for selected ID")
            current = client.get_place(place_id)
            if str(current.get("title") or current.get("name") or "").strip() != name:
                raise RunnerError("CSV/API title mismatch")
            if not addresses_match(row.get("address"), current.get("address")):
                raise RunnerError("CSV/API address mismatch")
            schema = current.get("attributeSchema") or []
            attributes = apply_attribute_updates(current.get("attributes"), schema, item)
            raw_reviews = client.get_place_reviews(place_id, max_pages=args.review_max_pages)
            review_context = build_review_context(raw_reviews)
            description, description_en, preserved = render_description(row, current, item, review_context, args.force)
            references = normalize_references(item.get("references"))
            payload = build_payload(current, attributes, description, description_en, references)
            save_context_document(
                root,
                place_id,
                build_context_document(
                    place_id,
                    row,
                    current,
                    review_context,
                    item,
                    references,
                    description,
                    description_en,
                    "update",
                ),
            )
            client.put_place(place_id, payload)
            upsert_log(results_path, place_id, f"{place_id} | {name} | OK")
            remove_log_id(failed_path, place_id)
            summary["updated"] += 1
            summary["preserved" if preserved else "regenerated"] += 1
            summary["sources"] += len(references)
            summary["review_context_rows"] += int(bool(review_context["reviews"]))
            summary["review_context_items"] += int(review_context["context_count"])
        except Exception as exc:  # keep one bad row from stopping the batch
            reason = sanitize_reason(str(exc))
            upsert_log(results_path, place_id, f"{place_id} | {name} | FAILED | {reason}")
            upsert_log(failed_path, place_id, f"{place_id} | {name} | {reason}")
            summary["failed"] += 1

    print(json.dumps(summary, ensure_ascii=False, sort_keys=True))
    return 0 if summary["failed"] == 0 else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RunnerError as exc:
        print(json.dumps({"error": sanitize_reason(str(exc))}, ensure_ascii=False))
        raise SystemExit(1)
