import asyncio
from datetime import datetime, timezone

import pytest

from app.cache import TTLCache
from app.hub import Hub, parse_follow, split_fixtures
from app.providers.espn import parse_event, parse_news, parse_standings
from app.providers.f1 import parse_race
from app.providers.tsdb import match_team, parse_event as tsdb_event
from app.schemas import Fixture, League, Team, TeamOverview

LEAGUE = League(id="soccer:eng.1", sport="soccer", slug="eng.1", name="Premier League")


def event(state="post", name="STATUS_FULL_TIME", home_score="2", away_score="1"):
    return {
        "id": "1", "date": "2026-10-10T16:30Z", "shortName": "TOT @ MUN",
        "competitions": [{
            "status": {"type": {"state": state, "name": name, "shortDetail": "FT"}},
            "venue": {"fullName": "Old Trafford"},
            "competitors": [
                {"homeAway": "home", "score": {"displayValue": home_score}, "team": {"id": "360", "displayName": "Manchester United"}},
                {"homeAway": "away", "score": home_score and away_score, "team": {"id": "367", "displayName": "Tottenham", "logos": [{"href": "x.png"}]}},
            ],
        }],
    }


@pytest.mark.parametrize("state,name,expected", [
    ("pre", "STATUS_SCHEDULED", "scheduled"), ("in", "STATUS_IN_PROGRESS", "live"),
    ("post", "STATUS_FULL_TIME", "final"), ("pre", "STATUS_POSTPONED", "postponed")])
def test_parse_event_status(state, name, expected):
    assert parse_event(event(state, name), LEAGUE).status == expected


def test_parse_event_teams_scores_and_logo():
    f = parse_event(event(), LEAGUE)
    assert (f.home.name, f.away.name) == ("Manchester United", "Tottenham")
    assert (f.home_score, f.away_score) == ("2", "1")  # handles dict and plain-string scores
    assert f.away.logo == "x.png" and f.venue == "Old Trafford"


def test_parse_event_without_date_is_dropped():
    ev = event()
    del ev["date"]
    assert parse_event(ev, LEAGUE) is None


def test_parse_event_neutral_venue_has_no_homeaway():
    ev = event()
    for c in ev["competitions"][0]["competitors"]:
        del c["homeAway"]
    f = parse_event(ev, LEAGUE)
    assert f.home.id == "360" and f.away.id == "367"


def entry(team_id, name, rank, points, **extra):
    stats = [{"name": "rank", "value": rank}, {"name": "points", "value": points},
             {"name": "gamesPlayed", "value": 6}, {"name": "wins", "value": 5},
             {"name": "ties", "value": 1}, {"name": "losses", "value": 0},
             {"name": "pointDifferential", "value": 11}]
    stats += [{"name": k, "value": v} for k, v in extra.items()]
    return {"team": {"id": team_id, "displayName": name}, "stats": stats}


def test_parse_standings_sorted_with_extras():
    data = {"children": [{"name": "League", "standings": {"entries": [
        entry("2", "B", 2, 10), entry("1", "A", 1, 12, netRunRate=0.5)]}}]}
    [group] = parse_standings(data)
    assert [r.team.name for r in group.rows] == ["A", "B"]
    top = group.rows[0]
    assert (top.played, top.wins, top.draws, top.losses, top.points, top.goal_diff) == (6, 5, 1, 0, 12, 11)
    assert top.extra == {"netRunRate": 0.5}


def test_parse_standings_nested_conferences():
    data = {"children": [{"name": "East", "children": [{"name": "Atlantic", "standings": {"entries": [entry("1", "A", 1, 3)]}}]},
                         {"name": "West", "standings": {"entries": [entry("2", "B", 1, 3)]}}]}
    assert [g.name for g in parse_standings(data)] == ["Atlantic", "West"]


def test_parse_news():
    items = parse_news({"articles": [{"id": 7, "headline": "H", "published": "2026-10-10T10:00:00Z",
                                      "links": {"web": {"href": "http://a"}}, "images": [{"url": "i.png"}]}]}, "x:y")
    assert items[0].id == "7" and items[0].url == "http://a" and items[0].image == "i.png"
    assert items[0].published == datetime(2026, 10, 10, 10, tzinfo=timezone.utc)


def test_parse_follow():
    assert parse_follow("soccer:eng.1:360") == ("soccer:eng.1", "360")
    assert parse_follow("f1:f1:red_bull") == ("f1:f1", "red_bull")
    for bad in ("360", "soccer:360", "soccer:eng.1:"):
        with pytest.raises(ValueError):
            parse_follow(bad)


def fx(id_, status, day):
    return Fixture(id=id_, league_id="l", league_name="L", date=datetime(2026, 10, day, tzinfo=timezone.utc),
                   status=status, title=id_)


def test_split_fixtures_orders_and_limits():
    fixtures = [fx("p1", "final", 1), fx("p2", "final", 2), fx("n2", "scheduled", 20), fx("n1", "scheduled", 15),
                fx("live", "live", 10)]
    live, upcoming, recent = split_fixtures(fixtures, upcoming_n=1, recent_n=5)
    assert [f.id for f in live] == ["live"]
    assert [f.id for f in upcoming] == ["n1"]  # soonest first
    assert [f.id for f in recent] == ["p2", "p1"]  # most recent first


def test_parse_race_status_and_sessions():
    race = {"round": "5", "raceName": "Test GP", "date": "2026-06-07", "time": "13:00:00Z",
            "Circuit": {"circuitName": "Circuit", "Location": {"locality": "Town", "country": "Land"}},
            "Qualifying": {"date": "2026-06-06", "time": "14:00:00Z"},
            "FirstPractice": {"date": "2026-06-05", "time": "11:30:00Z"}}
    before = parse_race(race, "2026", datetime(2026, 6, 1, tzinfo=timezone.utc))
    assert before.status == "scheduled" and [s.name for s in before.sessions] == ["Practice 1", "Qualifying"]
    assert before.venue == "Circuit, Town, Land"
    assert parse_race(race, "2026", datetime(2026, 6, 7, 14, tzinfo=timezone.utc)).status == "live"
    assert parse_race(race, "2026", datetime(2026, 6, 8, tzinfo=timezone.utc)).status == "final"


def test_tsdb_team_matching_handles_spelling_variants():
    teams = [Team(id="1", name="Royal Challengers Bengaluru", league_id="c"), Team(id="2", name="Gujarat Titans", league_id="c")]
    assert match_team("Royal Challengers Bangalore", teams).id == "1"
    assert match_team("Totally Different FC", teams) is None
    f = tsdb_event({"idEvent": "9", "strTimestamp": "2026-05-31T14:00:00", "strEvent": "GT vs RCB",
                    "strHomeTeam": "Gujarat Titans", "strAwayTeam": "Royal Challengers Bangalore",
                    "strStatus": "Match Finished", "strResult": "RCB won by 5 wickets"},
                   League(id="cricket:8048", sport="cricket", slug="8048", name="IPL"), teams)
    assert f.status == "final" and f.home.id == "2" and f.away.id == "1"


def test_cache_serves_stale_when_upstream_fails():
    async def run():
        cache, calls = TTLCache(), {"n": 0}

        async def factory():
            calls["n"] += 1
            if calls["n"] > 1:
                raise RuntimeError("upstream down")
            return "fresh"

        assert await cache.get_or_set("k", 0, factory) == "fresh"  # ttl 0 -> immediately stale
        assert await cache.get_or_set("k", 0, factory) == "fresh"  # failure falls back to stale
        with pytest.raises(RuntimeError):
            await cache.get_or_set("never-cached", 0, factory)
    asyncio.run(run())


def test_feed_dedupes_shared_fixtures_and_reports_errors():
    shared = fx("derby", "scheduled", 20)
    team = lambda i: Team(id=i, name=i, league_id="l")

    class FakeHub(Hub):
        def __init__(self):
            pass

        async def overview(self, league_id, team_id):
            if team_id == "bad":
                raise RuntimeError("boom")
            return TeamOverview(team=team(team_id), upcoming=[shared, fx(f"own-{team_id}", "scheduled", 25)])

    feed = asyncio.run(FakeHub().feed(["a:b:one", "a:b:two", "a:b:bad"]))
    assert [f.id for f in feed.upcoming] == ["derby", "own-one", "own-two"]
    assert len(feed.errors) == 1 and "bad" in feed.errors[0]
