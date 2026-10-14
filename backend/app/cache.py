import asyncio
import time
from typing import Any, Awaitable, Callable


class TTLCache:
    """In-memory async TTL cache. Serves stale data if the upstream call fails,
    so a flaky free API degrades to old data instead of an error."""

    def __init__(self) -> None:
        self._data: dict[str, tuple[float, Any]] = {}
        self._locks: dict[str, asyncio.Lock] = {}

    async def get_or_set(self, key: str, ttl: float, factory: Callable[[], Awaitable[Any]]) -> Any:
        hit = self._data.get(key)
        if hit and hit[0] > time.monotonic():
            return hit[1]
        async with self._locks.setdefault(key, asyncio.Lock()):
            hit = self._data.get(key)
            if hit and hit[0] > time.monotonic():
                return hit[1]
            try:
                value = await factory()
            except Exception:
                if hit:
                    return hit[1]
                raise
            self._data[key] = (time.monotonic() + ttl, value)
            return value
