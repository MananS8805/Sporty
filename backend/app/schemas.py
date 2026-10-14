"""Sport-agnostic models. Every provider maps its upstream data onto these."""
from datetime import datetime
from typing import Literal, Optional

from pydantic import BaseModel, Field

FixtureStatus = Literal["scheduled", "live", "final", "postponed"]


class League(BaseModel):
    id: str  # "<sport>:<slug>", e.g. "soccer:eng.1", "cricket:8048", "f1:f1"
    sport: str
    slug: str
    name: str
    abbreviation: Optional[str] = None
    logo: Optional[str] = None


class TeamRef(BaseModel):
    id: str
    name: str
    abbreviation: Optional[str] = None
    logo: Optional[str] = None


class Team(TeamRef):
    league_id: str
    short_name: Optional[str] = None
    color: Optional[str] = None  # hex without '#', for team-themed UI


class Session(BaseModel):
    name: str
    date: datetime


class Fixture(BaseModel):
    id: str
    league_id: str
    league_name: str
    date: datetime
    status: FixtureStatus
    status_detail: Optional[str] = None
    title: str
    home: Optional[TeamRef] = None  # None for non-head-to-head events (F1)
    away: Optional[TeamRef] = None
    home_score: Optional[str] = None
    away_score: Optional[str] = None
    venue: Optional[str] = None
    sessions: list[Session] = Field(default_factory=list)
    summary: Optional[str] = None  # e.g. a team's result in a race


class StandingRow(BaseModel):
    rank: int
    team: TeamRef
    subtitle: Optional[str] = None
    played: Optional[int] = None
    wins: Optional[int] = None
    draws: Optional[int] = None
    losses: Optional[int] = None
    points: Optional[float] = None
    goal_diff: Optional[float] = None
    extra: dict[str, float] = Field(default_factory=dict)


class StandingGroup(BaseModel):
    name: str
    rows: list[StandingRow]


class NewsItem(BaseModel):
    id: str
    headline: str
    description: Optional[str] = None
    published: Optional[datetime] = None
    url: Optional[str] = None
    image: Optional[str] = None
    league_id: Optional[str] = None


class TeamOverview(BaseModel):
    team: Team
    standing: Optional[StandingRow] = None
    standing_group: Optional[str] = None
    live: list[Fixture] = Field(default_factory=list)
    upcoming: list[Fixture] = Field(default_factory=list)
    recent: list[Fixture] = Field(default_factory=list)
    news: list[NewsItem] = Field(default_factory=list)


class Feed(BaseModel):
    live: list[Fixture]
    upcoming: list[Fixture]
    recent: list[Fixture]
    news: list[NewsItem]
    errors: list[str] = Field(default_factory=list)
