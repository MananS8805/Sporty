"""TheSportsDB (free key) - fills the gap for cricket fixtures, which ESPN lacks."""
import difflib
from datetime import datetime, timezone
from typing import Optional

import httpx

from ..cache import TTLCache
from ..http import get_json
from ..schemas import Fixture, League, Team, TeamRef

BASE = "https://www.thesportsdb.com/api/v1/json/3"
# ESPN league id -> TheSportsDB league id. Extend as leagues are verified.
LEAGUE_MAP = {"cricket:8048": "4460"}  # Indian Premier League


def match_team(name: Optional[str], teams: list[Team]) -> Optional[Team]:
    if not name:
        return None
    best, best_ratio = None, 0.0
    for t in teams:
        for cand in filter(None, (t.name, t.short_name)):
            r = difflib.SequenceMatcher(None, name.lower(), cand.lower()).ratio()
            if r > best_ratio:
                best, best_ratio = t, r
    return best if best_ratio >= 0.75 else None


def parse_event(ev: dict, league: League, teams: list[Team]) -> Optional[Fixture]:
    try:
        date = datetime.fromisoformat(ev["strTimestamp"]).replace(tzinfo=timezone.utc)
    except (KeyError, TypeError, ValueError):
        return None

    def ref(name: Optional[str]) -> Optional[TeamRef]:
        if not name:
            return None
        t = match_team(name, teams)
        if t:
            return TeamRef(id=t.id, name=t.name, abbreviation=t.abbreviation, logo=t.logo)
        return TeamRef(id=name, name=name)

    done = ev.get("strStatus") in ("Match Finished", "FT") or bool(ev.get("strResult"))
    return Fixture(id=f"tsdb-{ev.get('idEvent')}", league_id=league.id, league_name=league.name, date=date,
                   status="final" if done else "scheduled", status_detail=ev.get("strResult") or ev.get("strStatus"),
                   title=ev.get("strEvent") or "", home=ref(ev.get("strHomeTeam")), away=ref(ev.get("strAwayTeam")),
                   home_score=ev.get("intHomeScore"), away_score=ev.get("intAwayScore"), venue=ev.get("strVenue"))


class TsdbProvider:
    def __init__(self, client: httpx.AsyncClient, cache: TTLCache):
        self.client = client
        self.cache = cache

    def supports(self, league_id: str) -> bool:
        return league_id in LEAGUE_MAP

    async def fixtures(self, league: League, teams: list[Team]) -> list[Fixture]:
        tid = LEAGUE_MAP[league.id]

        async def one(kind: str):
            data = await self.cache.get_or_set(
                f"tsdb:{kind}:{tid}", 600, lambda: get_json(self.client, f"{BASE}/{kind}.php", {"id": tid}))
            return data.get("events") or []

        events = [*await one("eventspastleague"), *await one("eventsnextleague")]
        out = [f for f in (parse_event(e, league, teams) for e in events) if f]
        return sorted(out, key=lambda f: f.date)
