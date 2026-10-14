import asyncio
import time
from urllib.parse import urlparse

import httpx

_HEADERS = {"User-Agent": "SportsHub/0.1 (personal project)", "Accept": "application/json"}

# Free public APIs rate-limit by IP and will block a burst, so every host gets a
# concurrency cap and a pause between calls.
_DEFAULT_LIMIT = 4
_HOST_LIMITS = {"sports.core.api.espn.com": 4, "site.api.espn.com": 4, "api.jolpi.ca": 3}
_MIN_GAP = 0.15  # seconds between request starts per host
BLOCK_COOLDOWN = 300  # stop calling a host this long after it returns 403/429

_sems: dict[str, asyncio.Semaphore] = {}
_last_call: dict[str, float] = {}
_blocked_until: dict[str, float] = {}


class UpstreamBlocked(httpx.HTTPError):
    """The host told us to back off; we fail fast instead of hammering it."""


def make_client() -> httpx.AsyncClient:
    return httpx.AsyncClient(timeout=15, headers=_HEADERS, follow_redirects=True)


def _block(host: str) -> None:
    _blocked_until[host] = time.monotonic() + BLOCK_COOLDOWN


async def get_json(client: httpx.AsyncClient, url: str, params: dict | None = None, retries: int = 2):
    host = urlparse(url).netloc
    if _blocked_until.get(host, 0) > time.monotonic():
        raise UpstreamBlocked(f"{host} is cooling down after a rate-limit response")
    sem = _sems.setdefault(host, asyncio.Semaphore(_HOST_LIMITS.get(host, _DEFAULT_LIMIT)))

    last: Exception | None = None
    for attempt in range(retries + 1):
        try:
            async with sem:
                wait = _last_call.get(host, 0) + _MIN_GAP - time.monotonic()
                if wait > 0:
                    await asyncio.sleep(wait)
                _last_call[host] = time.monotonic()
                resp = await client.get(url, params=params)
            if resp.status_code in (403, 429):
                _block(host)
                raise UpstreamBlocked(f"{host} returned {resp.status_code}")
            resp.raise_for_status()
            return resp.json()
        except UpstreamBlocked:
            raise
        except httpx.HTTPStatusError as exc:
            if exc.response.status_code < 500:
                raise  # other 4xx will not get better on retry
            last = exc
        except (httpx.TransportError, ValueError) as exc:
            last = exc
        await asyncio.sleep(0.5 * (attempt + 1))
    assert last is not None
    raise last
