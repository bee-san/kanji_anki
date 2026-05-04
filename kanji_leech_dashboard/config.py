from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Mapping

DEFAULT_ANKICONNECT_URL = "http://127.0.0.1:8765"
DEFAULT_MODEL_NAMES = ("Kiku",)
DEFAULT_EXPRESSION_FIELD = "Expression"
DEFAULT_READING_FIELD = "Reading"
DEFAULT_MEANING_FIELD = "Meaning"
DEFAULT_MATURE_DAYS = 21
DEFAULT_KANJI_SUPPORT_THRESHOLD = 3
DEFAULT_JITEN_CACHE_TTL_HOURS = 24
DEFAULT_JITEN_REQUEST_TIMEOUT_SECONDS = 20
DEFAULT_POLLING_ENABLED = False
DEFAULT_POLLING_INTERVAL_SECONDS = 300


class ConfigValidationError(ValueError):
    def __init__(self, messages: list[str]) -> None:
        super().__init__("\n".join(messages))
        self.messages = messages


@dataclass(frozen=True)
class AppSettings:
    ankiconnect_url: str = DEFAULT_ANKICONNECT_URL
    model_names: tuple[str, ...] = DEFAULT_MODEL_NAMES
    expression_field: str = DEFAULT_EXPRESSION_FIELD
    reading_field: str = DEFAULT_READING_FIELD
    meaning_field: str = DEFAULT_MEANING_FIELD
    mature_days: int = DEFAULT_MATURE_DAYS
    kanji_dashboard_mature_support_threshold: int = DEFAULT_KANJI_SUPPORT_THRESHOLD
    jiten_cache_ttl_hours: int = DEFAULT_JITEN_CACHE_TTL_HOURS
    jiten_request_timeout_seconds: int = DEFAULT_JITEN_REQUEST_TIMEOUT_SECONDS
    polling_enabled: bool = DEFAULT_POLLING_ENABLED
    polling_interval_seconds: int = DEFAULT_POLLING_INTERVAL_SECONDS

    def to_dict(self) -> dict[str, Any]:
        return {
            "ankiConnectUrl": self.ankiconnect_url,
            "noteModels": list(self.model_names),
            "expressionField": self.expression_field,
            "readingField": self.reading_field,
            "meaningField": self.meaning_field,
            "matureDays": self.mature_days,
            "kanjiSupportThreshold": self.kanji_dashboard_mature_support_threshold,
            "jitenCacheTtlHours": self.jiten_cache_ttl_hours,
            "jitenRequestTimeoutSeconds": self.jiten_request_timeout_seconds,
            "pollingEnabled": self.polling_enabled,
            "pollingIntervalSeconds": self.polling_interval_seconds,
        }

    @property
    def effective_mature_query(self) -> str:
        return build_default_mature_query(self.model_names, self.mature_days)


AddonConfig = AppSettings


def build_default_mature_query(
    model_names: tuple[str, ...],
    mature_days: int,
) -> str:
    model_query = _build_model_query(model_names)
    base_query = f"prop:ivl>={mature_days} -is:suspended"
    if not model_query:
        return base_query
    return f"({model_query}) {base_query}"


def parse_config(raw: Mapping[str, Any] | None) -> AppSettings:
    raw = raw or {}
    errors: list[str] = []

    model_names = _coerce_model_names(
        raw.get("noteModels", raw.get("modelNames")),
        errors,
    )
    expression_field = _clean_string(
        raw.get("expressionField"),
        DEFAULT_EXPRESSION_FIELD,
    )
    reading_field = _clean_string(
        raw.get("readingField"),
        DEFAULT_READING_FIELD,
    )
    meaning_field = _clean_string(
        raw.get("meaningField"),
        DEFAULT_MEANING_FIELD,
    )
    ankiconnect_url = _clean_string(
        raw.get("ankiConnectUrl", raw.get("ankiconnectUrl")),
        DEFAULT_ANKICONNECT_URL,
    )
    mature_days = _coerce_positive_int(
        raw.get("matureDays"),
        DEFAULT_MATURE_DAYS,
        "matureDays",
        errors,
    )
    kanji_support_threshold = _coerce_positive_int(
        raw.get(
            "kanjiSupportThreshold",
            raw.get("kanjiDashboardMatureSupportThreshold"),
        ),
        DEFAULT_KANJI_SUPPORT_THRESHOLD,
        "kanjiSupportThreshold",
        errors,
    )
    jiten_cache_ttl_hours = _coerce_positive_int(
        raw.get("jitenCacheTtlHours"),
        DEFAULT_JITEN_CACHE_TTL_HOURS,
        "jitenCacheTtlHours",
        errors,
    )
    jiten_request_timeout_seconds = _coerce_positive_int(
        raw.get("jitenRequestTimeoutSeconds"),
        DEFAULT_JITEN_REQUEST_TIMEOUT_SECONDS,
        "jitenRequestTimeoutSeconds",
        errors,
    )
    polling_interval_seconds = _coerce_positive_int(
        raw.get("pollingIntervalSeconds"),
        DEFAULT_POLLING_INTERVAL_SECONDS,
        "pollingIntervalSeconds",
        errors,
    )
    polling_enabled = bool(raw.get("pollingEnabled", DEFAULT_POLLING_ENABLED))

    if not ankiconnect_url:
        errors.append("ankiConnectUrl must be a non-empty URL.")
    if not expression_field:
        errors.append("expressionField must be a non-empty string.")
    if not reading_field:
        errors.append("readingField must be a non-empty string.")
    if not meaning_field:
        errors.append("meaningField must be a non-empty string.")

    if errors:
        raise ConfigValidationError(errors)

    return AppSettings(
        ankiconnect_url=ankiconnect_url,
        model_names=model_names,
        expression_field=expression_field,
        reading_field=reading_field,
        meaning_field=meaning_field,
        mature_days=mature_days,
        kanji_dashboard_mature_support_threshold=kanji_support_threshold,
        jiten_cache_ttl_hours=jiten_cache_ttl_hours,
        jiten_request_timeout_seconds=jiten_request_timeout_seconds,
        polling_enabled=polling_enabled,
        polling_interval_seconds=polling_interval_seconds,
    )


def _coerce_model_names(value: Any, errors: list[str]) -> tuple[str, ...]:
    if value in (None, ""):
        return DEFAULT_MODEL_NAMES
    if not isinstance(value, (list, tuple)):
        errors.append("noteModels must be a list of note type names.")
        return DEFAULT_MODEL_NAMES

    names: list[str] = []
    for entry in value:
        cleaned = _clean_string(entry)
        if cleaned:
            names.append(cleaned)

    if not names:
        errors.append("noteModels must include at least one note type name.")
        return DEFAULT_MODEL_NAMES
    return tuple(names)


def _clean_string(value: Any, default: str = "") -> str:
    if value is None:
        return default
    if isinstance(value, str):
        return value.strip()
    return str(value).strip()


def _coerce_positive_int(
    value: Any,
    default: int,
    field_name: str,
    errors: list[str],
) -> int:
    if value in (None, ""):
        return default
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        errors.append(f"{field_name} must be a positive integer.")
        return default
    if parsed <= 0:
        errors.append(f"{field_name} must be a positive integer.")
        return default
    return parsed


def _build_model_query(model_names: tuple[str, ...]) -> str:
    parts = [f'note:"{name}"' for name in model_names if name]
    return " or ".join(parts)
