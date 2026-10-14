from contextlib import asynccontextmanager
from pathlib import Path
from typing import Optional

import httpx
from fastapi import FastAPI, HTTPException, Query, Request

from .hub import Hub, NotFound, parse_follow
from .http import make_client
from .schemas import (Feed, Fixture, League, NewsItem, StandingGroup, Team,
                      TeamOverview)

CACHE_DIR = Path(__file__).resolve().parent.parent / ".cache"


@asynccontextmanager
async def lifespan(app: FastAPI):
    async with make_client() as client:
        app.state.hub = Hub(client, CACHE_DIR)
        yield


app = FastAPI(title="Sports Hub API", version="0.1.0", lifespan=lifespan)


@app.exception_handler(NotFound)
async def _not_found(_: Request, exc: NotFound):
    from fastapi.responses import JSONResponse
    return JSONResponse(status_code=404, content={"detail": str(exc)})


@app.exception_handler(httpx.HTTPError)
async def _upstream(_: Request, exc: httpx.HTTPError):
    from fastapi.responses import JSONResponse
    return JSONResponse(status_code=502, content={"detail": f"upstream data source failed: {exc!r}"})


def hub(request: Request) -> Hub:
    return request.app.state.hub


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.get("/leagues", response_model=list[League])
async def leagues(request: Request, sport: Optional[str] = None, q: Optional[str] = None):
    return await hub(request).leagues(sport, q)


@app.get("/leagues/{league_id}/teams", response_model=list[Team])
async def teams(request: Request, league_id: str):
    h = hub(request)
    return await h.teams(await h.league(league_id))


@app.get("/leagues/{league_id}/standings", response_model=list[StandingGroup])
async def standings(request: Request, league_id: str):
    h = hub(request)
    return await h.standings(await h.league(league_id))


@app.get("/leagues/{league_id}/fixtures", response_model=list[Fixture])
async def fixtures(request: Request, league_id: str, team_id: Optional[str] = None):
    h = hub(request)
    return await h.fixtures(await h.league(league_id), team_id)


@app.get("/leagues/{league_id}/news", response_model=list[NewsItem])
async def news(request: Request, league_id: str, team_id: Optional[str] = None,
               limit: int = Query(15, ge=1, le=50)):
    h = hub(request)
    return await h.news(await h.league(league_id), team_id, limit)


@app.get("/leagues/{league_id}/teams/{team_id}", response_model=TeamOverview)
async def team_overview(request: Request, league_id: str, team_id: str):
    return await hub(request).overview(league_id, team_id)


@app.get("/feed", response_model=Feed)
async def feed(request: Request, follow: list[str] = Query(..., description="<sport>:<league>:<team>, repeatable")):
    try:
        for f in follow:
            parse_follow(f)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc))
    return await hub(request).feed(follow)
