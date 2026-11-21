"""ETags for the API's JSON answers: a repeated GET goes back as a bare 304.

The body is already built by the time Starlette hands it over, so the tag is its SHA-256 (the first 32
hex digits, as the streams' own has). Only 200 answers to GET are tagged, only when they are JSON, and
only when the endpoint did not forbid storing them. Clients with an HTTP cache (the Android app) then
revalidate without receiving the body again.
"""

from __future__ import annotations

import hashlib

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

_NO_STORE = "no-store"


class ETagMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):  # type: ignore[no-untyped-def]
        response = await call_next(request)
        if request.method != "GET" or response.status_code != 200:
            return response
        if "etag" in response.headers or "application/json" not in response.headers.get("content-type", ""):
            return response
        cache_control = response.headers.get("cache-control", "")
        if _NO_STORE in cache_control:
            return response
        body = b"".join([chunk async for chunk in response.body_iterator])
        etag = '"' + hashlib.sha256(body).hexdigest()[:32] + '"'
        headers = {
            key: value
            for key, value in response.headers.items()
            if key.lower() not in {"content-length", "etag", "cache-control"}
        }
        headers["ETag"] = etag
        headers["Cache-Control"] = cache_control or "private, no-cache"
        if etag in {tag.strip() for tag in request.headers.get("if-none-match", "").split(",")}:
            return Response(status_code=304, headers=headers)
        return Response(content=body, status_code=200, headers=headers, background=response.background)
