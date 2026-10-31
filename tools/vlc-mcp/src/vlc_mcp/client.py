"""HTTP client for the phone's /api/v1, with the self-signed certificate pinned."""

import hashlib
import os
import ssl
from typing import Any
from urllib.parse import urlparse

import httpx


class VlcError(Exception):
    """An error reported by the phone (or a connection problem), with a message meant for the AI."""


def _normalize_fingerprint(value: str) -> str:
    return value.replace(":", "").replace(" ", "").lower()


class VlcClient:
    def __init__(self, url: str, token: str, cert_sha256: str):
        parsed = urlparse(url)
        if parsed.scheme != "https" or not parsed.hostname:
            raise VlcError("VLC_URL must look like https://<phone-ip>:<port>")
        self._base = f"https://{parsed.hostname}:{parsed.port or 443}/api/v1"
        self._host, self._port = parsed.hostname, parsed.port or 443
        self._token = token
        self._pin = _normalize_fingerprint(cert_sha256)
        self._http: httpx.Client | None = None

    @classmethod
    def from_env(cls) -> "VlcClient":
        missing = [k for k in ("VLC_URL", "VLC_TOKEN", "VLC_CERT_SHA256") if not os.environ.get(k)]
        if missing:
            raise VlcError(
                "Missing environment variables: " + ", ".join(missing) +
                ". In the app open Settings > Remote access > AI access tokens > Create token and copy the three values."
            )
        return cls(os.environ["VLC_URL"], os.environ["VLC_TOKEN"], os.environ["VLC_CERT_SHA256"])

    def _connect(self) -> httpx.Client:
        if self._http is not None:
            return self._http
        try:
            pem = ssl.get_server_certificate((self._host, self._port), timeout=8)
        except OSError as e:
            raise VlcError(
                f"Cannot reach the phone at {self._host}:{self._port} ({e}). Check that it is on the same network, "
                "that Remote Access is enabled in the app, and that the port in VLC_URL is current."
            ) from e
        der = ssl.PEM_cert_to_DER_cert(pem)
        if hashlib.sha256(der).hexdigest() != self._pin:
            raise VlcError(
                "The phone's certificate does not match VLC_CERT_SHA256. Refusing to connect. "
                "If you reinstalled the app, create a new token and update the settings."
            )
        # Trust exactly the pinned certificate; the hostname is an IP so name checking is off.
        context = ssl.create_default_context(cadata=pem)
        context.check_hostname = False
        self._http = httpx.Client(verify=context, timeout=20, headers={"Authorization": f"Bearer {self._token}"})
        return self._http

    def request(self, method: str, path: str, *, params: dict | None = None, body: dict | None = None) -> Any:
        client = self._connect()
        clean = {k: v for k, v in (params or {}).items() if v is not None}
        try:
            response = client.request(method, self._base + path, params=clean or None, json=body)
        except httpx.HTTPError as e:
            self._http = None
            raise VlcError(f"Request to the phone failed: {e}") from e
        try:
            data = response.json()
        except ValueError:
            data = {}
        if response.status_code == 401:
            raise VlcError("The phone rejected the token (401). It may have been revoked: create a new token in the app.")
        if response.status_code >= 400:
            raise VlcError(data.get("error") or f"The phone answered HTTP {response.status_code}")
        return data
