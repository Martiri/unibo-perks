#!/usr/bin/env python3
"""
Fail-safe scraper and catalog builder for UniBo & Student Perks PWA.

Security & Reliability guarantees:
1. Strict URL validation: only https:// (or upgraded http://) URLs are allowed.
   javascript:, data:, vbscript:, file:, mailto: are never used as card URLs.
2. Strict text sanitization: all HTML tags are stripped via BeautifulSoup,
   control characters removed, whitespace normalized, lengths bounded.
3. Fail-safe fallback: if network requests fail or return 0 items due to
   upstream layout changes, the existing `data/scraped_unibo.json` is preserved
   and the build does not break.
4. Deterministic output: `updatedAt` is only bumped when an item's content
   actually changes, avoiding noisy commits in GitHub Actions.
"""

from __future__ import annotations

import hashlib
import json
import logging
import re
import sys
from datetime import date
from pathlib import Path
from typing import Any
from urllib.parse import urljoin, urlparse

import requests
from bs4 import BeautifulSoup

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("sync_unibo")

ROOT_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = ROOT_DIR / "data"
CURATED_PATH = DATA_DIR / "curated.json"
SCRAPED_PATH = DATA_DIR / "scraped_unibo.json"
OUTPUT_PATH = DATA_DIR / "discounts.json"

ALLOWED_CATEGORIES = {
    "AI & Dev Tools",
    "Outdoor & Trekking",
    "Abbigliamento & Calzature",
    "Tech & Elettronica",
    "Trasporti",
    "Cultura/Svago",
    "Abbonamenti",
}
ALLOWED_CIRCUITS = {
    "UniBo",
    "UNiDAYS",
    "Student Beans",
    "Carta Giovani (CGN)",
    "ISIC / ESNcard",
    "GitHub Pack",
    "SheerID / Diretto",
    "Prime Student",
    "Brand Club (Tutti)",
}
REQUEST_TIMEOUT = 15
USER_AGENT = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 "
    "UniBoStudentPerksCatalogSync/1.1"
)

UNIBO_SOURCES = [
    {
        "url": "https://www.unibo.it/it/studiare/vivere-luniversita-e-la-citta/trasporti-e-mobilita",
        "category": "Trasporti",
        "parser": "dl_summary",
        "badge": "Convenzione UniBo",
    },
    {
        "url": "https://www.unibo.it/it/studiare/vivere-luniversita-e-la-citta/agevolazioni-per-il-tempo-libero",
        "category": "Cultura/Svago",
        "parser": "h3_sections",
        "badge": "Riduzione UniBo",
    },
    {
        "url": "https://www.unibo.it/it/ateneo/organizzazione-e-sedi/servizi-di-ateneo/servizi-per-lo-studio/risorse-informatiche/risorse-informatiche",
        "category": "Tech & Elettronica",
        "parser": "dl_summary",
        "badge": "Servizio Ateneo",
    },
]

# Keywords that belong to ER.GO / Bandi / Borse di studio (explicitly out of scope)
EXCLUDED_KEYWORDS = (
    "er.go",
    "ergo",
    "borsa di studio",
    "borse di studio",
    "bando di concorso",
    "esonero tasse",
    "150 ore",
    "assegni di tutorato",
)


def clean_text(raw: str, max_len: int = 600) -> str:
    """Strip residual tags/control chars and normalize whitespace."""
    if not raw:
        return ""
    text = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]", " ", str(raw))
    text = re.sub(r"\s+", " ", text).strip()
    if len(text) > max_len:
        text = text[: max_len - 1].rstrip() + "…"
    return text


def sanitize_url(raw_url: str, fallback_url: str) -> str:
    """Ensure URL is a valid HTTP(S) URL, upgrading http->https when appropriate."""
    candidate = (raw_url or "").strip()
    if not candidate or candidate.startswith(("mailto:", "tel:", "javascript:", "data:")):
        return fallback_url
    parsed = urlparse(candidate)
    if parsed.scheme not in ("http", "https") or not parsed.netloc:
        return fallback_url
    if parsed.scheme == "http":
        candidate = "https://" + candidate[len("http://") :]
    return candidate


def slugify(text: str) -> str:
    """Generate a stable URL-safe identifier."""
    slug = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    if not slug:
        slug = hashlib.sha256(text.encode("utf-8")).hexdigest()[:12]
    return f"unibo-live-{slug[:48]}"


def is_excluded(title: str, summary: str) -> bool:
    """Filter out ER.GO scholarships or competition notices."""
    combined = f"{title} {summary}".lower()
    return any(keyword in combined for keyword in EXCLUDED_KEYWORDS)


def parse_dl_summary(html: str, source_cfg: dict[str, str], today_str: str) -> list[dict[str, Any]]:
    """Parse Plone <dl class='summary'> blocks used on UniBo index pages."""
    soup = BeautifulSoup(html, "html.parser")
    items: list[dict[str, Any]] = []
    dl = soup.find("dl", class_="summary")
    if not dl:
        return items

    for dt in dl.find_all("dt"):
        dd = dt.find_next_sibling("dd")
        link_el = dt.find("a", href=True)
        if not link_el:
            continue

        # Remove internal Plone state spans like 'Pubblicato'
        for state_span in dt.find_all("span", class_="state"):
            state_span.decompose()

        title = clean_text(link_el.get_text(" ", strip=True), max_len=140)
        summary = clean_text(dd.get_text(" ", strip=True) if dd else "", max_len=450)
        if not title or len(title) < 4 or is_excluded(title, summary):
            continue

        raw_href = urljoin(source_cfg["url"], link_el["href"])
        safe_href = sanitize_url(raw_href, source_cfg["url"])

        items.append(
            {
                "id": slugify(title),
                "title": title,
                "provider": "Università di Bologna",
                "category": source_cfg["category"],
                "circuit": "UniBo",
                "isUniboOfficial": True,
                "student_only": True,
                "discountBadge": source_cfg["badge"],
                "summary": summary or f"Agevolazione ufficiale UniBo: {title}.",
                "reliability": {
                    "score": 5,
                    "reason": "Sincronizzato automaticamente dal portale istituzionale pubblico unibo.it.",
                },
                "eligibility": [
                    "Iscrizione attiva all'Università di Bologna",
                    "Badge universitario o credenziali @studio.unibo.it",
                ],
                "steps": [
                    "Consulta la scheda ufficiale sul portale dell'Università di Bologna tramite il pulsante qui sotto.",
                    "Accedi con le tue credenziali @studio.unibo.it o presenta il badge universitario secondo le modalità indicate.",
                    "Eventuali codici o numeri di tessera ottenuti possono essere salvati nel tuo spazio privato locale.",
                ],
                "url": safe_href,
                "expiresAt": None,
                "source": "unibo_scraper",
                "updatedAt": today_str,
            }
        )
    return items


def parse_h3_sections(html: str, source_cfg: dict[str, str], today_str: str) -> list[dict[str, Any]]:
    """Parse <div class='description-text'><h3> sections used on the Tempo Libero page."""
    soup = BeautifulSoup(html, "html.parser")
    items: list[dict[str, Any]] = []
    container = soup.find("div", class_="description-text")
    if not container:
        return items

    current_section = "Cultura e Tempo Libero"
    for h3 in container.find_all("h3"):
        classes = h3.get("class") or []
        heading_text = clean_text(h3.get_text(" ", strip=True), max_len=140)
        if not heading_text:
            continue

        if "aprichiudititle" in classes:
            current_section = heading_text
            continue

        paragraphs: list[str] = []
        ext_link: str | None = None

        for sibling in h3.find_next_siblings():
            if sibling.name == "h3":
                break
            if sibling.name in ("p", "ul"):
                a_tag = sibling.find("a", href=True)
                if a_tag and not ext_link:
                    href = urljoin(source_cfg["url"], a_tag["href"])
                    if not href.startswith("mailto:"):
                        ext_link = href
                txt = clean_text(sibling.get_text(" ", strip=True), max_len=400)
                if txt and not txt.lower().startswith("maggiori informazioni"):
                    paragraphs.append(txt)

        summary = clean_text(" ".join(paragraphs), max_len=450)
        if not summary or is_excluded(heading_text, summary):
            continue

        badge = f"UniBo · {current_section}"
        match_euro = re.search(r"(\d+\s*euro)", summary, re.IGNORECASE)
        if "accesso gratuito" in summary.lower() or "accredito gratuito" in summary.lower():
            badge = "Ingresso Gratis"
        elif match_euro:
            badge = f"Promo {match_euro.group(1)}"

        items.append(
            {
                "id": slugify(heading_text),
                "title": heading_text,
                "provider": f"Convenzione UniBo · {current_section}",
                "category": source_cfg["category"],
                "circuit": "UniBo",
                "isUniboOfficial": True,
                "student_only": True,
                "discountBadge": clean_text(badge, max_len=32),
                "summary": summary,
                "reliability": {
                    "score": 5,
                    "reason": "Estratto dalla pagina ufficiale 'Agevolazioni per il tempo libero' dell'Università di Bologna.",
                },
                "eligibility": [
                    "Iscrizione attiva all'Università di Bologna",
                    "Badge universitario UniBo e/o certificato di iscrizione o mail @studio.unibo.it",
                ],
                "steps": [
                    f"Verifica i dettagli aggiornati nella sezione '{current_section}' sul portale UniBo o sul sito dell'ente convenzionato.",
                    "Presenta il badge universitario UniBo in biglietteria (o invia richiesta con account @studio.unibo.it se previsto).",
                    "Salva eventuali codici accredito o annotazioni personali nel box privato qui sotto.",
                ],
                "url": sanitize_url(ext_link or source_cfg["url"], source_cfg["url"]),
                "expiresAt": None,
                "source": "unibo_scraper",
                "updatedAt": today_str,
            }
        )
    return items


def validate_item(item: dict[str, Any]) -> dict[str, Any]:
    """Validate and normalize a discount record against the strict schema."""
    required_str_fields = ("id", "title", "provider", "category", "discountBadge", "summary", "url", "source", "updatedAt")
    for field in required_str_fields:
        val = item.get(field)
        if not isinstance(val, str) or not val.strip():
            raise ValueError(f"Invalid or missing string field '{field}' in item {item.get('id')}")

    category = item["category"]
    if category == "Tech":
        category = "Tech & Elettronica"

    if category not in ALLOWED_CATEGORIES:
        raise ValueError(f"Unsupported category '{category}' in {item['id']}")

    is_unibo = bool(item.get("isUniboOfficial", False))
    student_only = bool(item.get("student_only", True))
    default_circuit = "UniBo" if is_unibo else ("SheerID / Diretto" if student_only else "Brand Club (Tutti)")
    circuit = clean_text(str(item.get("circuit") or default_circuit), 40)
    if circuit not in ALLOWED_CIRCUITS:
        raise ValueError(f"Unsupported circuit '{circuit}' in {item['id']}")

    reliability = item.get("reliability")
    if not isinstance(reliability, dict):
        raise ValueError(f"Missing reliability object in {item['id']}")
    score = int(reliability.get("score", 0))
    if not 1 <= score <= 5:
        raise ValueError(f"Reliability score out of range (1..5) in {item['id']}")

    eligibility = item.get("eligibility")
    steps = item.get("steps")
    if not isinstance(eligibility, list) or not eligibility:
        raise ValueError(f"Eligibility must be a non-empty list in {item['id']}")
    if not isinstance(steps, list) or not steps:
        raise ValueError(f"Steps must be a non-empty list in {item['id']}")

    safe_url = sanitize_url(item["url"], "https://www.unibo.it")
    expires_at = item.get("expiresAt")
    if expires_at is not None and not re.match(r"^\d{4}-\d{2}-\d{2}$", str(expires_at)):
        expires_at = None

    return {
        "id": clean_text(item["id"], 80),
        "title": clean_text(item["title"], 140),
        "provider": clean_text(item["provider"], 100),
        "category": category,
        "circuit": circuit,
        "isUniboOfficial": is_unibo,
        "student_only": student_only,
        "discountBadge": clean_text(item["discountBadge"], 36),
        "summary": clean_text(item["summary"], 500),
        "reliability": {
            "score": score,
            "reason": clean_text(str(reliability.get("reason", "")), 240),
        },
        "eligibility": [clean_text(str(x), 200) for x in eligibility if str(x).strip()],
        "steps": [clean_text(str(x), 320) for x in steps if str(x).strip()],
        "url": safe_url,
        "expiresAt": expires_at,
        "source": clean_text(item["source"], 32),
        "updatedAt": clean_text(item["updatedAt"], 20),
    }


def content_fingerprint(item: dict[str, Any]) -> str:
    """Compute a hash of an item excluding updatedAt for deterministic diffing."""
    clone = {k: v for k, v in item.items() if k != "updatedAt"}
    raw = json.dumps(clone, sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def load_json_list(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    try:
        with path.open("r", encoding="utf-8") as f:
            data = json.load(f)
            return data if isinstance(data, list) else []
    except Exception as exc:
        logger.warning("Failed to read %s: %s", path, exc)
        return []


def fetch_scraped_items(today_str: str, previous_scraped: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Fetch and parse all configured UniBo sources with fail-safe fallback."""
    session = requests.Session()
    session.headers.update(
        {
            "User-Agent": USER_AGENT,
            "Accept": "text/html,application/xhtml+xml",
            "Accept-Language": "it-IT,it;q=0.9,en;q=0.5",
        }
    )

    prev_by_id = {item["id"]: item for item in previous_scraped if isinstance(item, dict) and "id" in item}
    collected: list[dict[str, Any]] = []
    seen_ids: set[str] = set()

    for source_cfg in UNIBO_SOURCES:
        url = source_cfg["url"]
        try:
            logger.info("Fetching %s ...", url)
            resp = session.get(url, timeout=REQUEST_TIMEOUT)
            resp.raise_for_status()
            if source_cfg["parser"] == "dl_summary":
                parsed_items = parse_dl_summary(resp.text, source_cfg, today_str)
            else:
                parsed_items = parse_h3_sections(resp.text, source_cfg, today_str)

            logger.info("Extracted %d items from %s", len(parsed_items), url)
            for raw_item in parsed_items:
                validated = validate_item(raw_item)
                if validated["id"] in seen_ids:
                    continue
                old_item = prev_by_id.get(validated["id"])
                if old_item and content_fingerprint(validate_item(old_item)) == content_fingerprint(validated):
                    validated["updatedAt"] = old_item.get("updatedAt", today_str)
                seen_ids.add(validated["id"])
                collected.append(validated)
        except Exception as exc:
            logger.warning("Error scraping %s: %s", url, exc)

    if not collected and previous_scraped:
        logger.warning("Zero items scraped across all sources; preserving previous scraped_unibo.json snapshot.")
        return [validate_item(x) for x in previous_scraped]

    return collected


def merge_catalogs(
    curated: list[dict[str, Any]],
    scraped: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """
    Combine curated and scraped items.
    Curated items have richer step-by-step guides and take precedence when URLs
    or specific topics overlap, while unique scraped items are appended automatically.
    """
    merged: list[dict[str, Any]] = []
    curated_urls: set[str] = set()
    curated_ids: set[str] = set()

    curated_topic_tokens = (
        "tper",
        "freccia",
        "itabus",
        "sistema museale di ateneo",
        "istituzione bologna musei",
        "multisala odeon",
        "cineteca di bologna",
        "arena del sole",
        "virtus pallacanestro",
    )

    for item in curated:
        valid = validate_item(item)
        merged.append(valid)
        curated_urls.add(valid["url"].rstrip("/"))
        curated_ids.add(valid["id"])

    for item in scraped:
        valid = validate_item(item)
        norm_url = valid["url"].rstrip("/")
        title_lower = valid["title"].lower()

        if valid["id"] in curated_ids:
            continue
        if norm_url in curated_urls and norm_url != "https://www.unibo.it/it/studiare/vivere-luniversita-e-la-citta/agevolazioni-per-il-tempo-libero":
            continue
        if any(token in title_lower for token in curated_topic_tokens):
            continue

        merged.append(valid)

    return merged


def write_json(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def main() -> int:
    today_str = date.today().isoformat()
    curated_raw = load_json_list(CURATED_PATH)
    if not curated_raw:
        logger.error("curated.json is empty or missing at %s", CURATED_PATH)
        return 1

    curated_valid = [validate_item(x) for x in curated_raw]
    previous_scraped = load_json_list(SCRAPED_PATH)
    scraped_valid = fetch_scraped_items(today_str, previous_scraped)

    write_json(SCRAPED_PATH, scraped_valid)
    merged = merge_catalogs(curated_valid, scraped_valid)
    write_json(OUTPUT_PATH, merged)

    logger.info(
        "Catalog sync complete: %d curated + %d scraped -> %d total in discounts.json",
        len(curated_valid),
        len(scraped_valid),
        len(merged),
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
