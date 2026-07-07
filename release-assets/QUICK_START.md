# Syx Duo Realm Quick Start

This package keeps the HTTP test server because it is easy to inspect and
debug, but hides most manual setup behind two launchers.

## Host

1. Install/subscribe to the mod.
2. Run `HOST_START.bat`.
3. Copy the printed `SYXDUO|...` join code to your friend.
4. Keep the server window open while playing.

The host script automatically writes the host config to:

```text
%APPDATA%\songsofsyx\saves\profile\Syx Duo Realm\syx_duo_realm.json
```

It also starts the local test server on TCP port `8787`.

## Friend

1. Install/subscribe to the mod.
2. Run `FRIEND_JOIN.bat`.
3. Paste the host's `SYXDUO|...` join code.
4. Start Songs of Syx V71, enable the mod, and load a test save.

## In Game

Both players should:

1. Use separate test saves first.
2. Expand `DUO >`.
3. Wait for `D OK`.
4. Use `R` until both sides show `R OK`.
5. Use `SHD`, `SND`, `T/N`, `WAR`, and `PCE` after room status is fresh.

## Remote Testing

For friends outside your LAN, use Tailscale. The host script auto-detects a
Tailscale IPv4 address when the `tailscale` command is available.

The test server has no authentication. Do not expose it directly to the public
internet.
