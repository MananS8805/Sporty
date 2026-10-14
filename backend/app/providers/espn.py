"""ESPN public (unofficial) API adapter. One adapter serves ~220 soccer leagues,
IPL/cricket, and the US sports, because ESPN uses the same URL shape for all."""
import asyncio
import json
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Optional

import httpx

from ..cache import TTLCache
from ..http import get_json
from ..schemas import (Fixture, League, NewsItem, StandingGroup, StandingRow,
                       Team, TeamRef)

SITE = "https://site.api.espn.com/apis/site/v2/sports"
SITE_V2 = "https://site.api.espn.com/apis/v2/sports"
CORE = "https://sports.core.api.espn.com/v2/sports"

# Sports whose league lists we discover. Soccer alone has ~220 leagues.
DISCOVER_SPORTS = ["soccer", "basketball", "football", "baseball", "hockey", "rugby"]
LEAGUE_CACHE_TTL = 7 * 24 * 3600

# ESPN's core API cannot list cricket leagues, so these ids (verified against the
# scoreboard endpoint) are seeded by hand. Add more ids here as needed.
CRICKET_LEAGUES = {
    "8048": ("Indian Premier League", "IPL"), "8044": ("Big Bash League", "BBL"),
    "8039": ("World Cup", None), "8604": ("ICC Men's T20 World Cup", "T20 WC"),
    "8043": ("Sheffield Shield", None), "8050": ("Ranji Trophy", None),
    "8052": ("County Championship Division One", None),
}


def _parse_dt(value: Optional[str]) -> Optional[datetime]:
    if not value:
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(timezone.utc)
    except ValueError:
        return None


def _logo(obj: dict) -> Optional[str]:
    if obj.get("logo"):
        return obj["logo"]
    logos = obj.get("logos") or []
    return logos[0].get("href") if logos else None


def team_ref(team: dict) -> TeamRef:
    return TeamRef(
        id=str(team.get("id")),
        name=team.get("displayName") or team.get("name") or team.get("shortDisplayName") or "?",
        abbreviation=team.get("abbreviation"),
        logo=_logo(team),
    )


def _score(value: Any) -> Optional[str]:
    if isinstance(value, dict):
        value = value.get("displayValue", value.get("value"))
    if value is None or value == "":
        return None
    if isinstance(value, float) and value.is_integer():
        value = int(value)
    return str(value)


_STATE = {"pre": "scheduled", "in": "live", "post": "final"}


def parse_event(ev: dict, league: League) -> Optional[Fixture]:
    comps = ev.get("competitions") or []
    comp = comps[0] if comps else {}
    competitors = comp.get("competitors") or []
    home = next((c for c in competitors if c.get("homeAway") == "home"), None)
    away = next((c for c in competitors if c.get("homeAway") == "away"), None)
    if home is None and away is None and len(competitors) == 2:  # cricket/neutral venue
        home, away = competitors
    date = _parse_dt(ev.get("date") or comp.get("date"))
    if date is None:
        return None

    status = comp.get("status") or ev.get("status") or {}
    stype = status.get("type") or {}
    state = _STATE.get(stype.get("state"), "scheduled")
    if stype.get("name") in ("STATUS_POSTPONED", "STATUS_CANCELED", "STATUS_SUSPENDED"):
        state = "postponed"
    venue = (comp.get("venue") or ev.get("venue") or {}).get("fullName")

    return Fixture(
        id=str(ev.get("id")),
        league_id=league.id,
        league_name=league.name,
        date=date,
        status=state,
        status_detail=stype.get("shortDetail") or stype.get("description"),
        title=ev.get("shortName") or ev.get("name") or "",
        home=team_ref(home["team"]) if home and home.get("team") else None,
        away=team_ref(away["team"]) if away and away.get("team") else None,
        home_score=_score(home.get("score")) if home else None,
        away_score=_score(away.get("score")) if away else None,
        venue=venue,
    )


def _collect_standing_nodes(node: dict, out: list[dict]) -> None:
    """Standings are nested differently per sport (league -> conference -> division)."""
    if (node.get("standings") or {}).get("entries"):
        out.append(node)
    for child in node.get("children") or []:
        _collect_standing_nodes(child, out)


def _stat_map(entry: dict) -> dict[str, float]:
    stats: dict[str, float] = {}
    for s in entry.get("stats") or []:
        value = s.get("value")
        if value is None:
            try:
                value = float(str(s.get("displayValue")).replace("+", ""))
            except ValueError:
                continue
        if isinstance(value, (int, float)):
            stats[s.get("name")] = float(value)
    return stats


def _int(v: Optional[float]) -> Optional[int]:
    return None if v is None else int(v)


def parse_standings(data: dict) -> list[StandingGroup]:
    nodes: list[dict] = []
    _collect_standing_nodes(data, nodes)
    groups = []
    for node in nodes:
        rows = []
        for idx, entry in enumerate(node["standings"]["entries"], start=1):
            st = _stat_map(entry)
            # Cricket names its stats differently; fold them onto the common ones.
            for cricket, common in (("matchesPlayed", "gamesPlayed"), ("matchesWon", "wins"),
                                    ("matchesLost", "losses"), ("matchPoints", "points"),
                                    ("noresult", "ties")):
                if cricket in st and common not in st:
                    st[common] = st.pop(cricket)
            if "netrr" in st:
                st["netRunRate"] = st.pop("netrr")
            known = {"rank", "gamesPlayed", "wins", "ties", "draws", "losses", "points", "pointDifferential"}
            rows.append(StandingRow(
                rank=int(st.get("rank", idx)),
                team=team_ref(entry["team"]),
                played=_int(st.get("gamesPlayed")),
                wins=_int(st.get("wins")),
                draws=_int(st.get("ties", st.get("draws"))),
                losses=_int(st.get("losses")),
                points=st.get("points"),
                goal_diff=st.get("pointDifferential"),
                extra={k: v for k, v in st.items() if k not in known},
            ))
        rows.sort(key=lambda r: r.rank)
        groups.append(StandingGroup(name=node.get("name") or node.get("abbreviation") or "Standings", rows=rows))
    return groups


def parse_news(data: dict, league_id: str) -> list[NewsItem]:
    items = []
    for a in data.get("articles") or []:
        link = ((a.get("links") or {}).get("web") or {}).get("href")
        images = a.get("images") or []
        items.append(NewsItem(
            id=str(a.get("id") or a.get("dataSourceIdentifier") or a.get("headline")),
            headline=a.get("headline") or "",
            description=a.get("description"),
            published=_parse_dt(a.get("published")),
            url=link,
            image=images[0].get("url") if images else None,
            league_id=league_id,
        ))
    return items


class EspnProvider:
    def __init__(self, client: httpx.AsyncClient, cache: TTLCache, cache_dir: Path):
        self.client = client
        self.cache = cache
        self.league_file = cache_dir / "leagues.json"
        self._leagues: dict[str, League] | None = None
        self._lock = asyncio.Lock()

    # ---- league discovery -------------------------------------------------
    async def _discover_sport(self, sport: str, sem: asyncio.Semaphore) -> list[League]:
        try:
            listing = await get_json(self.client, f"{CORE}/{sport}/leagues", {"limit": 1000})
        except Exception:
            return []

        async def one(ref: str) -> Optional[League]:
            async with sem:
                try:
                    d = await get_json(self.client, ref.replace("http://", "https://"), retries=1)
                except Exception:
                    return None
            slug = d.get("slug") or str(d.get("id"))
            logos = d.get("logos") or []
            name = d.get("name") or d.get("shortName")
            if not name:
                return None
            return League(id=f"{sport}:{slug}", sport=sport, slug=slug, name=name,
                          abbreviation=d.get("abbreviation"),
                          logo=logos[0].get("href") if logos else None)

        refs = [i["$ref"] for i in listing.get("items", []) if "$ref" in i]
        return [l for l in await asyncio.gather(*(one(r) for r in refs)) if l]

    async def leagues(self) -> dict[str, League]:
        if self._leagues is not None:
            return self._leagues
        async with self._lock:
            if self._leagues is not None:
                return self._leagues
            if self.league_file.exists() and time.time() - self.league_file.stat().st_mtime < LEAGUE_CACHE_TTL:
                raw = json.loads(self.league_file.read_text(encoding="utf-8"))
                self._leagues = {l["id"]: League(**l) for l in raw}
                return self._leagues
            sem = asyncio.Semaphore(4)
            found: list[League] = [
                League(id=f"cricket:{i}", sport="cricket", slug=i, name=n, abbreviation=a)
                for i, (n, a) in CRICKET_LEAGUES.items()]
            for batch in await asyncio.gather(*(self._discover_sport(s, sem) for s in DISCOVER_SPORTS)):
                found.extend(batch)
            if found:  # do not cache an empty result from a network blip
                self.league_file.parent.mkdir(parents=True, exist_ok=True)
                self.league_file.write_text(json.dumps([l.model_dump() for l in found]), encoding="utf-8")
            self._leagues = {l.id: l for l in found}
            return self._leagues

    # ---- per-league data --------------------------------------------------
    async def standings(self, league: League) -> list[StandingGroup]:
        async def load():
            data = await get_json(self.client, f"{SITE_V2}/{league.sport}/{league.slug}/standings")
            return parse_standings(data)
        return await self.cache.get_or_set(f"st:{league.id}", 600, load)

    async def teams(self, league: League) -> list[Team]:
        async def load():
            try:
                data = await get_json(self.client, f"{SITE}/{league.sport}/{league.slug}/teams")
                raw = data["sports"][0]["leagues"][0]["teams"]
                return [Team(**team_ref(t["team"]).model_dump(), league_id=league.id,
                             short_name=t["team"].get("shortDisplayName"), color=t["team"].get("color"))
                        for t in raw]
            except Exception:
                # e.g. cricket has no /teams endpoint; the standings table lists every team
                return [Team(**r.team.model_dump(), league_id=league.id)
                        for g in await self.standings(league) for r in g.rows]
        return await self.cache.get_or_set(f"teams:{league.id}", 86400, load)

    async def scoreboard(self, league: League, days_back: int = 0, days_ahead: int = 7) -> list[Fixture]:
        today = datetime.now(timezone.utc).date()
        rng = f"{(today - timedelta(days=days_back)):%Y%m%d}-{(today + timedelta(days=days_ahead)):%Y%m%d}"

        async def load():
            data = await get_json(self.client, f"{SITE}/{league.sport}/{league.slug}/scoreboard",
                                  {"dates": rng, "limit": 300})
            return sorted(filter(None, (parse_event(e, league) for e in data.get("events", []))),
                          key=lambda f: f.date)
        return await self.cache.get_or_set(f"sb:{league.id}:{rng}", 60, load)

    async def team_schedule(self, league: League, team_id: str) -> list[Fixture]:
        """ESPN returns only played games by default; fixture=true returns the upcoming ones."""
        async def load():
            url = f"{SITE}/{league.sport}/{league.slug}/teams/{team_id}/schedule"
            played = await get_json(self.client, url)
            try:
                upcoming = await get_json(self.client, url, {"fixture": "true"})
            except httpx.HTTPError:
                upcoming = {}  # still show results if the upcoming list fails
            events = {e.get("id"): e for e in [*played.get("events", []), *upcoming.get("events", [])]}
            return sorted(filter(None, (parse_event(e, league) for e in events.values())), key=lambda f: f.date)
        return await self.cache.get_or_set(f"sched:{league.id}:{team_id}", 300, load)

    async def news(self, league: League, team_id: Optional[str] = None, limit: int = 15) -> list[NewsItem]:
        async def load():
            params: dict[str, Any] = {"limit": limit}
            if team_id:
                params["team"] = team_id
            data = await get_json(self.client, f"{SITE}/{league.sport}/{league.slug}/news", params)
            return parse_news(data, league.id)
        return await self.cache.get_or_set(f"news:{league.id}:{team_id}:{limit}", 600, load)
