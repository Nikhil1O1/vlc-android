"""MCP server exposing VLC for Android (music mode) to AI assistants."""

import json
from typing import Any, Literal

from mcp.server.fastmcp import FastMCP

from .client import VlcClient, VlcError

mcp = FastMCP(
    "vlc-music",
    instructions=(
        "Controls the VLC music player on the user's Android phone. Typical flow to build a playlist: "
        "search_library or list_tracks to find track ids, create_playlist with those ids, then play_music. "
        "Tracks have these fields: id, title, artist, album, genre, year, durationMs, playCount, favorite. "
        "Many files have no genre tag, so prefer artist/album/year/playCount filters when genre comes back empty. "
        "Ask the user before deleting playlists."
    ),
)

_client: VlcClient | None = None


def _vlc() -> VlcClient:
    global _client
    if _client is None:
        _client = VlcClient.from_env()
    return _client


def _call(method: str, path: str, *, params: dict | None = None, body: dict | None = None) -> str:
    try:
        return json.dumps(_vlc().request(method, path, params=params, body=body), ensure_ascii=False)
    except VlcError as e:
        # Returned as the tool result (not raised) so the assistant can read and react to it.
        return json.dumps({"error": str(e)})


# ---------- now playing and transport ----------

@mcp.tool()
def now_playing() -> str:
    """What is playing right now: track, position, volume, shuffle/repeat, queue size and index."""
    return _call("GET", "/status")


@mcp.tool()
def player_control(
    action: Literal["play", "pause", "toggle", "next", "previous", "stop", "seek", "shuffle", "repeat", "volume", "speed"],
    value: float | None = None,
) -> str:
    """Control playback.

    seek: value = position in milliseconds. volume: value = 0-100. speed: value = 0.25-4.
    repeat: value = 0 off, 1 repeat one, 2 repeat all. shuffle toggles shuffle.
    """
    return _call("POST", "/player", body={"action": action, **({} if value is None else {"value": value})})


# ---------- queue ----------

@mcp.tool()
def get_queue() -> str:
    """The current play queue with the index of the track playing."""
    return _call("GET", "/queue")


@mcp.tool()
def play_music(
    media_ids: list[int] | None = None,
    album_id: int | None = None,
    artist_id: int | None = None,
    genre_id: int | None = None,
    playlist_id: int | None = None,
    mode: Literal["replace", "next", "append"] = "replace",
    shuffle: bool = False,
) -> str:
    """Play or queue music. Give one or more of media_ids, album_id, artist_id, genre_id, playlist_id.

    mode: replace = start playing these now, next = play after the current track, append = add to the end.
    """
    body: dict[str, Any] = {"mode": mode, "shuffle": shuffle}
    for key, value in (("mediaIds", media_ids), ("albumId", album_id), ("artistId", artist_id),
                       ("genreId", genre_id), ("playlistId", playlist_id)):
        if value is not None:
            body[key] = value
    return _call("POST", "/queue/play", body=body)


@mcp.tool()
def queue_remove(index: int) -> str:
    """Remove the track at this queue index (see get_queue)."""
    return _call("DELETE", f"/queue/{int(index)}")


@mcp.tool()
def queue_move(from_index: int, to_index: int) -> str:
    """Move a queued track from one queue index to another."""
    return _call("POST", "/queue/move", body={"from": from_index, "to": to_index})


# ---------- library ----------

@mcp.tool()
def search_library(query: str, limit: int = 25) -> str:
    """Search tracks (by title), albums, artists, genres and playlists. For all of an artist's songs use list_tracks(artist=...)."""
    return _call("GET", "/search", params={"q": query, "limit": limit})


@mcp.tool()
def list_tracks(
    query: str | None = None,
    artist: str | None = None,
    album: str | None = None,
    genre: str | None = None,
    year_from: int | None = None,
    year_to: int | None = None,
    favorites_only: bool = False,
    sort: Literal["title", "artist", "year", "playcount"] | None = None,
    limit: int = 50,
    offset: int = 0,
) -> str:
    """List library tracks with filters (text filters match substrings, ignoring case). Returns total and a page of tracks."""
    return _call("GET", "/tracks", params={
        "q": query, "artist": artist, "album": album, "genre": genre, "yearFrom": year_from, "yearTo": year_to,
        "favorite": "true" if favorites_only else None, "sort": sort, "limit": limit, "offset": offset,
    })


@mcp.tool()
def list_albums(limit: int = 50, offset: int = 0) -> str:
    """List albums (id, title, artist, year, trackCount)."""
    return _call("GET", "/albums", params={"limit": limit, "offset": offset})


@mcp.tool()
def list_artists(limit: int = 50, offset: int = 0) -> str:
    """List artists (id, title, albumCount)."""
    return _call("GET", "/artists", params={"limit": limit, "offset": offset})


@mcp.tool()
def list_genres(limit: int = 100, offset: int = 0) -> str:
    """List genres (id, title, trackCount). Often nearly empty because many files lack genre tags."""
    return _call("GET", "/genres", params={"limit": limit, "offset": offset})


@mcp.tool()
def set_favorite(media_id: int, favorite: bool = True) -> str:
    """Mark or unmark a track as favorite."""
    return _call("POST", "/favorite", body={"mediaId": media_id, "value": favorite})


# ---------- playlists ----------

@mcp.tool()
def list_playlists() -> str:
    """List playlists (id, title, trackCount)."""
    return _call("GET", "/playlists")


@mcp.tool()
def get_playlist(playlist_id: int) -> str:
    """The tracks of a playlist, each with its position."""
    return _call("GET", f"/playlists/{int(playlist_id)}")


@mcp.tool()
def create_playlist(name: str, track_ids: list[int] | None = None) -> str:
    """Create a playlist, optionally filled with these track ids. Fails if the name is already used."""
    return _call("POST", "/playlists", body={"name": name, **({"mediaIds": track_ids} if track_ids else {})})


@mcp.tool()
def rename_playlist(playlist_id: int, name: str) -> str:
    """Rename a playlist."""
    return _call("PATCH", f"/playlists/{int(playlist_id)}", body={"name": name})


@mcp.tool()
def delete_playlist(playlist_id: int, confirm: bool = False) -> str:
    """Permanently delete a playlist (its tracks stay in the library). Must be called with confirm=true after the user agrees."""
    if not confirm:
        return json.dumps({"error": "Not deleted. Ask the user to confirm, then call again with confirm=true."})
    return _call("DELETE", f"/playlists/{int(playlist_id)}")


@mcp.tool()
def add_to_playlist(
    playlist_id: int,
    track_ids: list[int] | None = None,
    album_id: int | None = None,
    artist_id: int | None = None,
    genre_id: int | None = None,
    position: int | None = None,
) -> str:
    """Add tracks (by id, or a whole album/artist/genre) to a playlist, at the end or at a position."""
    body: dict[str, Any] = {}
    for key, value in (("mediaIds", track_ids), ("albumId", album_id), ("artistId", artist_id),
                       ("genreId", genre_id), ("position", position)):
        if value is not None:
            body[key] = value
    return _call("POST", f"/playlists/{int(playlist_id)}/tracks", body=body)


@mcp.tool()
def remove_from_playlist(playlist_id: int, positions: list[int]) -> str:
    """Remove the tracks at these positions (0-based, see get_playlist)."""
    return _call("DELETE", f"/playlists/{int(playlist_id)}/tracks", body={"positions": positions})


@mcp.tool()
def reorder_playlist(playlist_id: int, from_position: int, to_position: int) -> str:
    """Move a track within a playlist from one position to another."""
    return _call("POST", f"/playlists/{int(playlist_id)}/move", body={"from": from_position, "to": to_position})


def main() -> None:
    mcp.run()


if __name__ == "__main__":
    main()
