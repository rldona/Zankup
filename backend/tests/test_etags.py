from fastapi.testclient import TestClient
from starlette.applications import Starlette
from starlette.responses import JSONResponse
from starlette.routing import Route

from zankup.api.etags import ETagMiddleware


def _app() -> TestClient:
    async def answer(request):  # type: ignore[no-untyped-def]
        return JSONResponse({"a": 1})

    app = Starlette(routes=[Route("/x", answer)])
    app.add_middleware(ETagMiddleware)
    return TestClient(app)


def test_a_json_get_gets_an_etag_and_a_repeat_is_a_304() -> None:
    http = _app()
    first = http.get("/x")
    assert first.status_code == 200
    etag = first.headers["ETag"]
    assert etag.startswith('"') and etag.endswith('"')
    assert first.headers["Cache-Control"] == "private, no-cache"
    again = http.get("/x", headers={"If-None-Match": etag})
    assert again.status_code == 304
    assert not again.content


def test_a_different_body_gets_a_different_etag() -> None:
    async def one(request):  # type: ignore[no-untyped-def]
        return JSONResponse({"a": 1})

    async def two(request):  # type: ignore[no-untyped-def]
        return JSONResponse({"a": 2})

    app = Starlette(routes=[Route("/one", one), Route("/two", two)])
    app.add_middleware(ETagMiddleware)
    http = TestClient(app)
    assert http.get("/one").headers["ETag"] != http.get("/two").headers["ETag"]
