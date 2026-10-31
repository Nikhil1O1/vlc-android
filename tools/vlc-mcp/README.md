# vlc-mcp

An MCP server that lets Claude Desktop, Claude Code or any MCP client manage the music on your phone:
what is playing, the queue, searching the library, and creating and editing playlists.
It talks to the Remote Access API built into this fork of VLC for Android over HTTPS.

## Set up

1. On the phone: Settings > Remote access > turn it on, then **AI access tokens > Create token**.
   Copy the three lines it shows (the token is shown only once).
2. The phone and your computer must be on the same network (or both on Tailscale).
3. Add the server to your client.

Claude Code:

```sh
claude mcp add vlc \
  -e VLC_URL=https://192.168.1.42:8443 \
  -e VLC_TOKEN=vlc_xxxxxxxx \
  -e VLC_CERT_SHA256=C1:EC:70:... \
  -- uv run --directory /path/to/vlc-android/tools/vlc-mcp vlc-mcp
```

Claude Desktop (`claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "vlc": {
      "command": "uv",
      "args": ["run", "--directory", "/path/to/vlc-android/tools/vlc-mcp", "vlc-mcp"],
      "env": {
        "VLC_URL": "https://192.168.1.42:8443",
        "VLC_TOKEN": "vlc_xxxxxxxx",
        "VLC_CERT_SHA256": "C1:EC:70:..."
      }
    }
  }
}
```

The phone's certificate is self-signed, so the helper pins it: it refuses to connect if the certificate's
SHA-256 fingerprint differs from `VLC_CERT_SHA256`. The port can change when the server restarts; if the
helper cannot connect, check the port shown in the app and update `VLC_URL`.

## Try it

"Make a playlist called Road Trip with songs by Arijit Singh and put it on shuffle."

Deleting a playlist needs `confirm=true`, which the assistant is told to use only after you agree.
