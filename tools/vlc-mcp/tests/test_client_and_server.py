import hashlib
import json
import ssl

import httpx
import pytest

from vlc_mcp import client as client_mod
from vlc_mcp import server
from vlc_mcp.client import VlcClient, VlcError

DER = b"fake-der-bytes"
FINGERPRINT = hashlib.sha256(DER).hexdigest()


def make_client(handler, pin=FINGERPRINT):
    c = VlcClient("https://10.0.0.5:8443", "tok", pin)
    c._http = httpx.Client(transport=httpx.MockTransport(handler))
    return c


def test_url_must_be_https():
    with pytest.raises(VlcError):
        VlcClient("http://10.0.0.5:8080", "t", FINGERPRINT)


def test_fingerprint_with_colons_is_normalized():
    colon = ":".join(FINGERPRINT[i:i + 2].upper() for i in range(0, len(FINGERPRINT), 2))
    assert VlcClient("https://h:1", "t", colon)._pin == FINGERPRINT


def test_cert_pin_mismatch_refuses_connection(monkeypatch):
    monkeypatch.setattr(ssl, "get_server_certificate", lambda *a, **k: "PEM")
    monkeypatch.setattr(ssl, "PEM_cert_to_DER_cert", lambda pem: DER)
    c = VlcClient("https://10.0.0.5:8443", "tok", "0" * 64)
    with pytest.raises(VlcError, match="does not match"):
        c.request("GET", "/status")


def test_unreachable_phone_gives_helpful_error(monkeypatch):
    def boom(*a, **k):
        raise OSError("timed out")
    monkeypatch.setattr(ssl, "get_server_certificate", boom)
    with pytest.raises(VlcError, match="Cannot reach the phone"):
        VlcClient("https://10.0.0.5:8443", "tok", FINGERPRINT).request("GET", "/status")


def test_401_explains_revoked_token():
    c = make_client(lambda req: httpx.Response(401, json={}))
    with pytest.raises(VlcError, match="401"):
        c.request("GET", "/status")


def test_error_body_is_surfaced_and_none_params_dropped():
    seen = {}

    def handler(req):
        seen["url"] = str(req.url)
        return httpx.Response(400, json={"error": "bad thing"})

    c = make_client(handler)
    with pytest.raises(VlcError, match="bad thing"):
        c.request("GET", "/tracks", params={"q": "x", "genre": None})
    assert "genre" not in seen["url"] and "q=x" in seen["url"]


def test_bearer_token_sent():
    c = VlcClient("https://10.0.0.5:8443", "tok", FINGERPRINT)
    sent = {}

    def handler(req):
        sent.update(req.headers)
        return httpx.Response(200, json={"ok": True})

    c._http = httpx.Client(transport=httpx.MockTransport(handler), headers={"Authorization": "Bearer tok"})
    assert c.request("GET", "/status") == {"ok": True}
    assert sent["authorization"] == "Bearer tok"


def test_delete_playlist_requires_confirm(monkeypatch):
    calls = []
    monkeypatch.setattr(server, "_call", lambda *a, **k: calls.append(a) or "{}")
    out = json.loads(server.delete_playlist(5))
    assert "error" in out and calls == []
    server.delete_playlist(5, confirm=True)
    assert calls == [("DELETE", "/playlists/5")]
