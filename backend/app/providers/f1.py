"""Formula 1 via Jolpica-F1 (the Ergast successor). 'Teams' are constructors."""
from datetime import datetime, timedelta, timezone
from typing import Optional

import httpx

from ..cache import TTLCache
from ..http import get_json
from ..schemas import (Fixture, League, Session, StandingGroup, StandingRow,
                       Team, TeamRef)

BASE = "https://api.jolpi.ca/ergast/f1"
F1_LEAGUE = League(id="f1:f1", sport="f1", slug="f1", name="Formula 1", abbreviation="F1")

# Jolpica has no branding data; keep team colours here for themed UI.
COLORS = {
    "ferrari": "DC0000", "mercedes": "27F4D2", "red_bull": "3671C6", "mclaren": "FF8000",
    "aston_martin": "229971", "alpine": "0093CC", "williams": "64C4FF", "rb": "6692FF",
    "haas": "B6BABD", "audi": "F50537", "cadillac": "9AA0A6",
}
SESSION_KEYS = [("FirstPractice", "Practice 1"), ("SecondPractice", "Practice 2"),
                ("ThirdPractice", "Practice 3"), ("SprintQualifying", "Sprint Qualifying"),
                ("SprintShootout", "Sprint Shootout"), ("Sprint", "Sprint"), ("Qualifying", "Qualifying")]
RACE_DURATION = timedelta(hours=2, minutes=30)


def _dt(date: str, time: Optional[str]) -> datetime:
    stamp = f"{date}T{time}" if time else f"{date}T12:00:00Z"
    return datetime.fromisoformat(stamp.replace("Z", "+00:00"))


def parse_race(race: dict, season: str, now: datetime) -> Fixture:
    start = _dt(race["date"], race.get("time"))
    sessions = [Session(name=label, date=_dt(race[k]["date"], race[k].get("time")))
                for k, label in SESSION_KEYS if k in race]
    sessions.sort(key=lambda s: s.date)
    status = "final" if now > start + RACE_DURATION else "live" if now >= start else "scheduled"
    c = race.get("Circuit", {})
    loc = c.get("Location", {})
    venue = ", ".join(x for x in (c.get("circuitName"), loc.get("locality"), loc.get("country")) if x)
    return Fixture(id=f"f1-{season}-{race['round']}", league_id=F1_LEAGUE.id, league_name=F1_LEAGUE.name,
                   date=start, status=status, title=race["raceName"], venue=venue, sessions=sessions)


class F1Provider:
    def __init__(self, client: httpx.AsyncClient, cache: TTLCache):
        self.client = client
        self.cache = cache

    async def _get(self, path: str, ttl: int):
        return await self.cache.get_or_set(
            f"f1:{path}", ttl, lambda: get_json(self.client, f"{BASE}/{path}.json", {"limit": 100}))

    async def teams(self) -> list[Team]:
        data = await self._get("current/constructors", 86400)
        return [Team(id=c["constructorId"], name=c["name"], league_id=F1_LEAGUE.id,
                     color=COLORS.get(c["constructorId"]))
                for c in data["MRData"]["ConstructorTable"]["Constructors"]]

    async def standings(self) -> list[StandingGroup]:
        cons = await self._get("current/constructorStandings", 600)
        drivers = await self._get("current/driverStandings", 600)
        groups = []
        lists = cons["MRData"]["StandingsTable"]["StandingsLists"]
        rows = [StandingRow(rank=int(s["position"]), points=float(s["points"]), wins=int(s["wins"]),
                            team=TeamRef(id=s["Constructor"]["constructorId"], name=s["Constructor"]["name"]))
                for s in (lists[0]["ConstructorStandings"] if lists else [])]
        groups.append(StandingGroup(name="Constructors", rows=rows))
        lists = drivers["MRData"]["StandingsTable"]["StandingsLists"]
        rows = [StandingRow(rank=int(s["position"]), points=float(s["points"]), wins=int(s["wins"]),
                            subtitle=s["Constructors"][0]["name"] if s.get("Constructors") else None,
                            team=TeamRef(id=s["Driver"]["driverId"],
                                         name=f'{s["Driver"]["givenName"]} {s["Driver"]["familyName"]}',
                                         abbreviation=s["Driver"].get("code")))
                for s in (lists[0]["DriverStandings"] if lists else [])]
        groups.append(StandingGroup(name="Drivers", rows=rows))
        return groups

    async def fixtures(self, team_id: Optional[str] = None) -> list[Fixture]:
        data = await self._get("current", 900)
        races = data["MRData"]["RaceTable"]
        now = datetime.now(timezone.utc)
        fixtures = [parse_race(r, races["season"], now) for r in races["Races"]]
        if team_id:  # attach how this constructor did in finished races
            res = await self._get(f"current/constructors/{team_id}/results", 900)
            by_round = {r["round"]: r for r in res["MRData"]["RaceTable"]["Races"]}
            for f in fixtures:
                race = by_round.get(f.id.rsplit("-", 1)[1])
                if race:
                    f.summary = " · ".join(f'P{x["position"]} {x["Driver"]["familyName"]}' for x in race["Results"])
        return fixtures
