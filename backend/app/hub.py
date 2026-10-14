"""Service layer: routes every request to the right provider so the API (and the
Android app) never needs to know which upstream a league comes from."""
import asyncio
from pathlib import Path
from typing import Optional

import httpx

from .cache import TTLCache
from .providers.espn import EspnProvider
from .providers.f1 import F1_LEAGUE, F1Provider
from .providers.tsdb import TsdbProvider
from .schemas import (Feed, Fixture, League, NewsItem, StandingGroup, StandingRow,
                      Team, TeamOverview)

# F1 news comes from ESPN's racing section.
_F1_NEWS = League(id="racing:f1", sport="racing", slug="f1", name="Formula 1")


class NotFound(Exception):
    pass


def parse_follow(key: str) -> tuple[str, str]:
    """'soccer:eng.1:360' -> ('soccer:eng.1', '360')"""
    league_id, sep, team_id = key.rpartition(":")
    if not sep or ":" not in league_id or not team_id:
        raise ValueError(f"invalid follow key: {key!r} (expected '<sport>:<league>:<team>')")
    return league_id, team_id


def split_fixtures(fixtures: list[Fixture], upcoming_n: int = 5, recent_n: int = 5):
    live = [f for f in fixtures if f.status == "live"]
    upcoming = sorted((f for f in fixtures if f.status == "scheduled"), key=lambda f: f.date)[:upcoming_n]
    recent = sorted((f for f in fixtures if f.status == "final"), key=lambda f: f.date, reverse=True)[:recent_n]
    return live, upcoming, recent


class Hub:
    def __init__(self, client: httpx.AsyncClient, cache_dir: Path):
        self.cache = TTLCache()
        self.espn = EspnProvider(client, self.cache, cache_dir)
        self.f1 = F1Provider(client, self.cache)
        self.tsdb = TsdbProvider(client, self.cache)

    # ---- leagues ----------------------------------------------------------
    async def leagues(self, sport: Optional[str] = None, q: Optional[str] = None) -> list[League]:
        found = [F1_LEAGUE, *sorted((await self.espn.leagues()).values(), key=lambda l: (l.sport, l.name))]
        if sport:
            found = [l for l in found if l.sport == sport]
        if q:
            found = [l for l in found if q.lower() in l.name.lower() or q.lower() == (l.abbreviation or "").lower()]
        return found

    async def league(self, league_id: str) -> League:
        if league_id == F1_LEAGUE.id:
            return F1_LEAGUE
        league = (await self.espn.leagues()).get(league_id)
        if league is None:
            raise NotFound(f"unknown league {league_id!r}")
        return league

    # ---- data -------------------------------------------------------------
    async def teams(self, league: League) -> list[Team]:
        return await (self.f1.teams() if league.sport == "f1" else self.espn.teams(league))

    async def standings(self, league: League) -> list[StandingGroup]:
        return await (self.f1.standings() if league.sport == "f1" else self.espn.standings(league))

    async def fixtures(self, league: League, team_id: Optional[str] = None) -> list[Fixture]:
        if league.sport == "f1":
            return await self.f1.fixtures(team_id)
        if self.tsdb.supports(league.id):
            fixtures = await self.tsdb.fixtures(league, await self.teams(league))
            if team_id:
                fixtures = [f for f in fixtures if team_id in (f.home and f.home.id, f.away and f.away.id)]
            return fixtures
        if team_id:
            return await self.espn.team_schedule(league, team_id)
        return await self.espn.scoreboard(league, days_back=3, days_ahead=14)

    async def news(self, league: League, team_id: Optional[str] = None, limit: int = 15) -> list[NewsItem]:
        if league.sport == "f1":
            items = await self.espn.news(_F1_NEWS, limit=40)
            for i in items:
                i.league_id = league.id
            if team_id:  # ESPN has no per-constructor feed; filter by name mentions
                names = {t.name.lower() for t in await self.f1.teams() if t.id == team_id}
                hits = [i for i in items if any(n in f"{i.headline} {i.description}".lower() for n in names)]
                items = hits or items
            return items[:limit]
        return await self.espn.news(league, team_id, limit)

    # ---- composed views ---------------------------------------------------
    async def overview(self, league_id: str, team_id: str) -> TeamOverview:
        league = await self.league(league_id)
        teams = await self.teams(league)
        team = next((t for t in teams if t.id == team_id), None)
        if team is None:
            raise NotFound(f"unknown team {team_id!r} in {league_id!r}")

        fixtures, standings, news = await asyncio.gather(
            self.fixtures(league, team_id), self.standings(league), self.news(league, team_id, 8),
            return_exceptions=True)  # one failing source should not blank the whole screen
        overview = TeamOverview(team=team)
        if not isinstance(fixtures, BaseException):
            overview.live, overview.upcoming, overview.recent = split_fixtures(fixtures)
        if not isinstance(standings, BaseException):
            for group in standings:
                row = next((r for r in group.rows if r.team.id == team_id), None)
                if row:
                    overview.standing, overview.standing_group = row, group.name
                    break
        if not isinstance(news, BaseException):
            overview.news = news
        return overview

    async def feed(self, follows: list[str]) -> Feed:
        keys = [parse_follow(f) for f in follows]
        results = await asyncio.gather(*(self.overview(l, t) for l, t in keys), return_exceptions=True)
        live: dict[str, Fixture] = {}
        upcoming: dict[str, Fixture] = {}
        recent: dict[str, Fixture] = {}
        news: dict[str, NewsItem] = {}
        errors = []
        for (league_id, team_id), res in zip(keys, results):
            if isinstance(res, BaseException):
                errors.append(f"{league_id}:{team_id}: {res}")
                continue
            for bucket, items in ((live, res.live), (upcoming, res.upcoming), (recent, res.recent)):
                bucket.update({f.id: f for f in items})  # dedupe when two followed teams meet
            news.update({n.id: n for n in res.news})
        return Feed(
            live=sorted(live.values(), key=lambda f: f.date),
            upcoming=sorted(upcoming.values(), key=lambda f: f.date)[:30],
            recent=sorted(recent.values(), key=lambda f: f.date, reverse=True)[:20],
            news=sorted(news.values(), key=lambda n: n.published.timestamp() if n.published else 0,
                        reverse=True)[:40],
            errors=errors)
