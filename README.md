# Syx Duo Realm

Experimental / playtest Songs of Syx V71 Java mod for duo asynchronous macro
multiplayer. This is an unofficial community mod, not an official multiplayer
mode, and it should be tested on disposable saves first.

Phase 1-5 MVP for a Songs of Syx V71 Java mod. It exports the local city state
to `city_state.json`, posts the same state to a local/LAN HTTP endpoint, shows
room readiness for friend testing, sends and claims async trade packages,
persists local test-server rooms/trades, includes solo dev endpoints for testing
without a second player, and can manually create one friend shadow NPC faction
for an async macro multiplayer prototype. It also includes non-destructive
async war requests, server-side diplomacy relations, server-side auto reports,
winner-only war-spoils inbox packages, soft freshness checks for async war, and
friend population/resources projected into the native NPC shell with automatic
background refresh.

Normal export/sync and room polling do not modify the save. Sending a trade now
deducts reservable stockpile resources through the game's resource pickup path,
records a local sent-trade ledger, and refunds as scattered resources near the
throne if the HTTP send fails. Trade claim only places scattered resources near
the throne when you click the trade button. War requests and reports are
server-only and do not modify the save. Shadow faction mode does change one
world-map region through
the game's faction APIs, and includes a manual cleanup button.

## Project Status

- Current mod version: `0.3.1`
- Game target: Songs of Syx `V71`
- Distribution: Steam Workshop for players, GitHub for source and server tools
- License: MIT
- Networking model: HTTP server over localhost/LAN/Tailscale
- Steam friend invite / Steam P2P: not implemented

This repository intentionally excludes generated jars, release zips, SteamCMD
cache, local server state, save data, and personal connection config.

## Build

Run once after a game update:

```powershell
$env:JAVA_HOME = 'C:\Path\To\jdk-21'
.\mvnw.cmd validate
```

Build the mod:

```powershell
$env:JAVA_HOME = 'C:\Path\To\jdk-21'
.\mvnw.cmd package
```

Install into the Songs of Syx user mods folder:

```powershell
$env:JAVA_HOME = 'C:\Path\To\jdk-21'
.\mvnw.cmd install
```

`JAVA_HOME` must point to a JDK 21 with `javac`. The Songs of Syx bundled
`jre` can run the game, but it is not enough to compile the mod.

The installed layout should be:

```text
%APPDATA%\songsofsyx\mods\Syx Duo Realm\_Info.txt
%APPDATA%\songsofsyx\mods\Syx Duo Realm\V71\script\Syx Duo Realm.jar
```

## In-Game Verification

1. Open the Songs of Syx launcher.
2. Enable `Syx Duo Realm`.
3. Load or start a city.
4. Find the collapsible `DUO >` button in the top UI bar.
5. Hover `DUO >` to see the control map.
6. Click `DUO >` to expand the controls.
7. Click `D` to force an immediate export.
8. Click `R` to force a room readiness refresh.
9. Click `DUO <` to collapse the controls again.
10. Wait at least 30 real-time seconds and confirm the file timestamp updates.

Expected export path:

```text
%APPDATA%\songsofsyx\saves\profile\Syx Duo Realm\city_state.json
```

Expected config path:

```text
%APPDATA%\songsofsyx\saves\profile\Syx Duo Realm\syx_duo_realm.json
```

The config is created automatically on first run:

```json
{
  "serverUrl": "http://localhost:8787/api/state",
  "playerName": "your-windows-user",
  "roomCode": "local",
  "syncIntervalSeconds": 30,
  "tradeOfferResourceKey": "FISH",
  "tradeOfferAmount": 25,
  "tradeOfferToPlayer": ""
}
```

Expected JSON fields:

```text
saveId
saveIdSource
worldSeed
playerFactionName
populationTotal
populationByRace
resources
militaryProfile
activeNpcFactionsCount
playerRealmRegionsCount
timestamp
```

`saveId` prefers the real save file name when the game exposes it to the script.
If that is not available, it falls back to a stable local/LAN identity:

```text
{roomCode}_{playerName}_seed_{worldSeed}
```

`saveIdSource` describes where the value came from:

```text
savePath
fallbackWorldSeed
fallbackConfig
```

## HTTP Sync

Every `syncIntervalSeconds`, the mod:

1. Collects the city state from the live game.
2. Writes `city_state.json`.
3. Starts a background HTTP POST to `serverUrl`.

The HTTP payload is an envelope:

```text
modName
protocolVersion
modVersion
gameVersion
playerName
roomCode
sentAt
cityState
```

Network failures are caught in the background thread. They update the `D`
button, print to the game log, and show a temporary in-game time-box notice.
They do not throw back into `update` or `render`.

## Room Readiness

The server exposes a room-readiness endpoint for friend testing:

```text
GET /api/room_status?roomCode=local&playerName=player
```

The mod polls this endpoint in the background. Expand `DUO >` and use the `R`
button for a manual refresh:

```text
R --    no status yet
R ...   request in progress
R 0/2   no fresh players in room
R 1/2   only one fresh player
R OK    you and at least one friend are fresh
R OLD   a friend was seen, but their state is stale
R ERR   request failed
```

By default, a player is fresh for 120 seconds after their last `/api/state`
sync. Override this in the test server with:

```powershell
$env:STALE_AFTER_SECONDS = "180"
node .\tools\syx-duo-test-server\server.js
```

### Time-Speed Policy

The first usable version does not force Songs of Syx's local time-speed controls
to match between two clients. Instead, the server treats each `/api/state` upload
as a snapshot and only considers that snapshot fresh for the configured freshness
window.

War resolution requires both the attacker and defender states to be fresh. If
either player has paused too long, closed the game, or stopped syncing, the
server rejects the war with a clear stale-state error instead of resolving from
old data. Trade inboxes and shadow display can still be inspected while stale,
but they should be refreshed before important actions.

This keeps the mod async and stable while avoiding the biggest time-desync bug:
one client fighting against another client's outdated population, army, or
resource state.

## Async Trade Claim MVP

The server exposes a local/LAN trade inbox:

```text
GET  /api/inbox?roomCode=local&playerName=player
POST /api/trade
POST /api/trade/{tradeId}/claim
```

Each inbox trade has:

```text
tradeId
resourceKey
amount
fromPlayer
```

### Sending Trades

Expand `DUO >` and use the `SND` button to create an outgoing trade package
from the config fields:

```json
{
  "tradeOfferResourceKey": "FISH",
  "tradeOfferAmount": 25,
  "tradeOfferToPlayer": ""
}
```

If `tradeOfferToPlayer` is blank, the mod uses the first friend detected by the
`R` status endpoint. Clicking `SND`:

1. Resolves `tradeOfferResourceKey` with `RESOURCES.map().tryGet(...)`.
2. Checks reservable stockpile availability with `STOCKPILE.tally().amountReservable.get(res)`.
3. Deducts the offered amount with the game's resource removal path.
4. Saves the local send record to `sent_trades.json`.
5. POSTs `/api/trade` in a background HTTP thread using a client-generated trade id.

If the HTTP request fails or the server rejects the trade, the deducted amount is
placed back near the throne as scattered resources and the local ledger is marked
`REFUNDED`. If the game exits while a request is still in flight, check
`sent_trades.json` for any record that remains `DEDUCTED`.

The mod polls `/api/inbox` in the background. Pending trades appear in the
`D` button hover text. When at least one trade is pending, the button label
changes to `T selected/total`. The selected trade is marked with `>` in the
hover list.

The expanded controls also include an `N` button:

```text
N --   no pending trade
N 1/3  selected trade 1 of 3
```

Click `N` to cycle through pending trade packages. Click `T` to claim the
currently selected package.

Claim behavior:

1. Resolve `resourceKey` with `RESOURCES.map().tryGet(...)`.
2. Place scattered resources near `THRONE.coo()` with
   `SETT.THINGS().resources.createPrecise(tx, ty, res, amount)`.
3. Save the trade id locally.
4. POST `/api/trade/{tradeId}/claim`.

The mod does not write directly to stockpile tally.

Local claimed-trade state:

```text
%APPDATA%\songsofsyx\saves\profile\Syx Duo Realm\claimed_trades.json
```

Local sent-trade state:

```text
%APPDATA%\songsofsyx\saves\profile\Syx Duo Realm\sent_trades.json
```

Server-side claim is idempotent. Re-posting the same claim returns success with
`alreadyClaimed: true`.

## Async War Request MVP

The server exposes non-destructive async war endpoints:

```text
GET  /api/war_status?roomCode=local&playerName=player
GET  /api/diplomacy_status?roomCode=local&playerName=player&friendName=friend
GET  /api/war_requests?roomCode=local&playerName=player
GET  /api/war_reports?roomCode=local&playerName=player
GET  /api/wars
POST /api/war_request
POST /api/war_request/{requestId}/accept
POST /api/war_request/{requestId}/decline
POST /api/war
```

Expand `DUO >` and use the `WAR` button:

```text
WAR       no request or report yet
WAR ...   request in progress
WAR IN    incoming request; click to accept and resolve
WAR OUT   outgoing request; waiting for the friend to accept
WAR ON    server relation is WAR; click to resolve another controlled battle
WAR W     latest report was a win
WAR L     latest report was a loss
WAR ERR   request failed
PCE       send a peace request while server relation is WAR
PCE IN    incoming peace request; click to accept
PCE OUT   outgoing peace request; waiting for the friend to accept
PCE OK    no active server war with the target
```

If an incoming request exists, clicking `WAR` accepts it and the server resolves
one battle report. Otherwise, clicking `WAR` sends a pending request to the
detected friend. Once the server relation between the two players is `WAR`,
clicking `WAR` resolves another controlled battle report instead of sending a
duplicate request. Direct `POST /api/war` still exists for manual/dev testing.

The server relation is stored separately from the local original-game diplomacy
arrays. The mod displays the relation, but does not write it into `DIP.WAR()` by
default. This keeps the friend NPC shell from being taken over by local AI wars
or unsynchronized conquest.

The server also rejects direct war resolution when either player's latest upload
is stale. In practice, both clients should show `R OK` before accepting or
resolving wars.

The exported `city_state.json` now includes a `militaryProfile` block collected
from the live game:

```text
FACTIONS.player().offensivePower()
AD.power().get(player)
player.armies().all().size()
RD.MILITARY().power.getD(player.capitolRegion())
player.realm().regions()
```

The server uses `militaryProfile.offensivePower` first. If an old client has no
military profile, it falls back to the rough macro strength formula:

```text
population + realmRegions * 100 + activeNpcFactions * 5 + sqrt(resources) * 8
```

It then applies a deterministic small variance, chooses a winner, stores the
report in `wars_state.json`, and estimates losses with the same broad idea as
the game's auto resolver: power balance drives how severe each side's losses
are. This phase does not modify resources, armies, citizens, regions, faction
ownership, or the save.

### War Spoils Prototype

When the server creates a new war report, it also creates one deterministic
inbox trade for the winner:

```text
tradeId: war_spoils_{warId}
source: war-spoils
toPlayer: winner
fromPlayer: loser
resourceKey: FISH by default
amount: based on winner strength and victory margin
```

This does not deduct anything from the loser and does not write directly to
either player's stockpile. It only reuses the already-tested trade inbox path:
the winning client sees a pending `T` package and claiming it places scattered
resources near the throne through the existing `createPrecise` dropper.

Server knobs for local tests:

```powershell
$env:WAR_SPOILS_RESOURCE_KEY = "FISH"
$env:WAR_SPOILS_BASE_AMOUNT = "20"
$env:WAR_SPOILS_MAX_AMOUNT = "250"
node .\tools\syx-duo-test-server\server.js
```

War state:

```text
tools\syx-duo-test-server\wars_state.json
tools\syx-duo-test-server\war_requests_state.json
tools\syx-duo-test-server\peace_requests_state.json
tools\syx-duo-test-server\diplomacy_state.json
tools\syx-duo-test-server\trades_state.json
```

## Controlled Friend NPC Shell

The server exposes the latest state for one other player in the same room:

```text
GET /api/friend_state?roomCode=local&playerName=player
```

Expand `DUO >` and use the `SHD` button:

```text
SHD --   no shadow binding
SHD ...  fetching friend_state
SHD ON   one shadow faction is active
SHD ERR  fetch or apply failed
```

Click `SHD` when no shadow is active:

1. Fetch `friend_state` in a background HTTP thread.
2. On the game update thread, find an empty active world region.
3. Choose the friend's dominant uploaded race when possible.
4. Activate one inactive NPC faction with `FACTIONS.activateNext(region, race, false)`.
5. Rename the faction and region to the friend's `playerName`.
6. Project uploaded race population into the bound region through `RD.RACES()`.
7. Project uploaded resource totals into the NPC stockpile through `TR.get(resource)` and `faction.res(...)`.
8. Save the binding outside the game save.
9. Use that binding as the default target for `WAR` and `SND` when
   `tradeOfferToPlayer` is blank.

If the shadow binding is already active and `apply` receives a fresh
`friend_state`, the mod refreshes the same native NPC shell instead of creating a
second one. Once a binding is active, the mod also refreshes that same shell in
the background on the normal sync interval. Successful background refreshes are
silent; failures are logged and surfaced through the `SHD` hover/status.

Click `SHD ON` again to remove the shadow faction with `FACTIONS.remove(...)`
and clear the binding file.

Local shadow binding:

```text
%APPDATA%\songsofsyx\saves\profile\Syx Duo Realm\shadow_binding.json
```

This is now the preferred local representation for a friend: an original game
NPC shell for map/faction UI, with server-authoritative trade and war logic.
The mod reads and displays local shell details such as faction index, region,
player stance, local offensive power, region military power, and region
population. It also shows the latest server-uploaded friend military profile.

The goal is for the friend to be inspectable through original faction/map UI:
name, region, race population, military hints, and stockpile goods come from the
latest uploaded state. The first version still keeps conquest, peace, and war
reports server-authoritative; it does not let local AI freely rewrite the shared
async relationship.

The created NPC faction still uses the game's normal NPC data generation. The
mod does not hand war results to the original AI yet, and it does not mirror
server war state into `DIP.WAR()` by default. This avoids uncontrolled local AI
wars, expansion, or region conquest while still letting the friend appear as a
normal faction in the game's world/faction UI.

## Local Test Server

Run the zero-dependency Node.js server:

```powershell
node .\tools\syx-duo-test-server\server.js
```

For LAN testing, bind to all interfaces:

```powershell
$env:HOST = "0.0.0.0"
node .\tools\syx-duo-test-server\server.js
```

## First-Version Startup Checklist

For a normal local/LAN test:

1. Start the server before launching or loading the save.
2. Make sure both clients use the same `roomCode` and different `playerName`.
3. Point both configs at the same server URL.
4. Use a disposable save for shadow-faction testing.
5. Load the save, expand `DUO >`, and wait for `D OK`.
6. Use `R` until both clients show a fresh room state.
7. Click `SHD` once to create the friend NPC shell.
8. Use original world/faction UI to inspect the friend faction.
9. Use `SND`, `T/N`, `WAR`, and `PCE` for async actions.

The room should show fresh before important actions. If a player has not synced
recently, trade display may still work, but war resolution will reject stale
state.

On the host machine, allow inbound TCP traffic for port `8787` in Windows
Firewall if your friend cannot connect. The friend should set their config to
the host LAN IP, for example:

```json
{
  "serverUrl": "http://192.168.1.25:8787/api/state",
  "playerName": "friend",
  "roomCode": "local",
  "syncIntervalSeconds": 30
}
```

Both players must use the same `roomCode` and different `playerName` values.
When both games have synced within the fresh window, both clients should show
`R OK`.

## Remote Friend Test

If the friend is not on the same LAN, the lowest-friction remote test is a
private mesh VPN. The recommended path is Tailscale:

1. Both players install Tailscale and join the same tailnet.
2. The host runs the Syx Duo Realm test server on all interfaces:

```powershell
$env:HOST = "0.0.0.0"
node .\tools\syx-duo-test-server\server.js
```

3. The host finds their Tailscale IPv4 address:

```powershell
tailscale ip -4
```

4. Both players set `serverUrl` in
`%APPDATA%\songsofsyx\saves\profile\Syx Duo Realm\syx_duo_realm.json`:

```json
{
  "serverUrl": "http://100.x.y.z:8787/api/state",
  "roomCode": "duo-test",
  "playerName": "your-unique-name",
  "syncIntervalSeconds": 30
}
```

5. The host verifies from their own machine:

```powershell
Invoke-RestMethod "http://100.x.y.z:8787/api/health"
```

6. The friend verifies the same URL from their machine before launching the
game.

Avoid public tunnels for the first real test unless you must. The test server is
intentionally simple and has no authentication. If you do use a tunnel, disable
development endpoints with `ENABLE_DEV_ENDPOINTS=0`, use a random room code, and
stop the server when the test ends.

To write server state somewhere else:

```powershell
$env:DATA_DIR = "C:\Temp\syx-duo-server"
node .\tools\syx-duo-test-server\server.js
```

The server listens on:

```text
http://localhost:8787/api/state
```

It writes the latest received payload to:

```text
tools\syx-duo-test-server\received_state.json
```

It also aggregates latest player states by `roomCode` and writes:

```text
tools\syx-duo-test-server\rooms_state.json
```

Trade state is written to:

```text
tools\syx-duo-test-server\trades_state.json
```

War report state is written to:

```text
tools\syx-duo-test-server\wars_state.json
```

War request state is written to:

```text
tools\syx-duo-test-server\war_requests_state.json
```

Server-side diplomacy relation state is written to:

```text
tools\syx-duo-test-server\diplomacy_state.json
```

Peace request state is written to:

```text
tools\syx-duo-test-server\peace_requests_state.json
```

On startup the server loads `rooms_state.json`, `trades_state.json`,
`wars_state.json`, `war_requests_state.json`, and `diplomacy_state.json` if they
exist, so room summaries, open trades, claimed trades, war requests, diplomacy
relations, and war reports survive a local test server restart.

Read endpoints:

```text
GET http://localhost:8787/api/health
GET http://localhost:8787/api/rooms
GET http://localhost:8787/api/rooms/local
GET http://localhost:8787/api/inbox?roomCode=local&playerName=player
GET http://localhost:8787/api/friend_state?roomCode=local&playerName=player
GET http://localhost:8787/api/room_status?roomCode=local&playerName=player
GET http://localhost:8787/api/trades
GET http://localhost:8787/api/war_status?roomCode=local&playerName=player
GET http://localhost:8787/api/diplomacy_status?roomCode=local&playerName=player&friendName=friend
GET http://localhost:8787/api/war_requests?roomCode=local&playerName=player
GET http://localhost:8787/api/peace_requests?roomCode=local&playerName=player
GET http://localhost:8787/api/war_reports?roomCode=local&playerName=player
GET http://localhost:8787/api/wars
POST http://localhost:8787/api/war
POST http://localhost:8787/api/war_request
POST http://localhost:8787/api/war_request/{requestId}/accept
POST http://localhost:8787/api/war_request/{requestId}/decline
POST http://localhost:8787/api/peace_request
POST http://localhost:8787/api/peace_request/{requestId}/accept
POST http://localhost:8787/api/peace_request/{requestId}/decline
```

Development endpoints are enabled automatically when the server binds to
`127.0.0.1` or `localhost`. If you bind to `0.0.0.0`, enable them explicitly
only for trusted testing:

```powershell
$env:ENABLE_DEV_ENDPOINTS = "1"
node .\tools\syx-duo-test-server\server.js
```

Solo test endpoints:

```text
POST http://localhost:8787/api/dev/friend
POST http://localhost:8787/api/dev/inbox_trade
POST http://localhost:8787/api/dev/war_request
POST http://localhost:8787/api/dev/reset
```

These endpoints are for local verification. Do not expose them on an untrusted
public server.

Recommended solo verification loop:

1. Start the test server.
2. Load the city, expand `DUO >`, click `D`, and wait for `/api/state`.
3. Run the solo friend simulation command below.
4. Expand `DUO >`, click `R`, and confirm room status becomes `R OK`.
5. Run the solo inbox trade simulation command below.
6. Wait for inbox polling, then expand `DUO >` and click `D` when it shows a pending trade.
7. Run the solo incoming war request command below.
8. Wait for war polling or click `WAR` after it shows `WAR IN` to accept and resolve the report.
9. After it refreshes to `WAR ON`, click `WAR` again to generate another controlled battle report.
10. Click `PCE` to send a peace request while the relation is `WAR`.
11. In solo testing, accept that peace request with PowerShell or a second client, then confirm `PCE OK`.
12. Use `SND` only on a disposable test save, because it deducts real city resources before posting.

PowerShell validation after the game syncs:

```powershell
$p = ".\tools\syx-duo-test-server\received_state.json"
$r = Get-Content $p -Raw | ConvertFrom-Json
$r.payload | Select-Object playerName, roomCode, sentAt
$r.payload.cityState | Select-Object saveId, saveIdSource, worldSeed, playerFactionName, populationTotal, activeNpcFactionsCount, playerRealmRegionsCount, timestamp
$r.payload.cityState.militaryProfile | Select-Object offensivePower, fieldArmyPower, armyCount, capitalMilitaryPower, realmRegions, source
```

Room validation:

```powershell
$rooms = Invoke-RestMethod http://localhost:8787/api/rooms
$rooms.rooms | Select-Object roomCode, playerCount, ready, updatedAt

$room = Invoke-RestMethod http://localhost:8787/api/rooms/local
$room.room.players | Select-Object playerName, saveId, saveIdSource, populationTotal, worldSeed, receivedAt
```

Room readiness validation:

```powershell
$status = Invoke-RestMethod "http://localhost:8787/api/room_status?roomCode=local&playerName=player"
$status | Select-Object roomCode, playerCount, freshPlayerCount, ready, freshReady, friendPresent, friendFresh
$status.players | Select-Object playerName, fresh, secondsSinceReceived, saveId, populationTotal, modVersion, gameVersion
```

Solo friend simulation after your game has synced once:

```powershell
$cfgPath = "$env:APPDATA\songsofsyx\saves\profile\Syx Duo Realm\syx_duo_realm.json"
$cfg = Get-Content $cfgPath -Raw | ConvertFrom-Json

$friend = @{
  localPlayerName = $cfg.playerName
  playerName = "friend"
  roomCode = $cfg.roomCode
  saveId = "friend_dev_save"
  dominantRace = "GARTHIMI"
  populationTotal = 333
  populationByRace = @{
    GARTHIMI = @{
      name = "Garthimi"
      count = 333
    }
  }
  resources = @{
    FISH = @{
      name = "Fish"
      amount = 321
    }
    BREAD = @{
      name = "Bread"
      amount = 45
    }
    _WOOD = @{
      name = "Wood"
      amount = 777
    }
  }
  playerRealmRegionsCount = 3
  offensivePower = 850
} | ConvertTo-Json -Depth 8

Invoke-RestMethod -Method Post -Uri http://localhost:8787/api/dev/friend -ContentType "application/json" -Body $friend
Invoke-RestMethod "http://localhost:8787/api/room_status?roomCode=$($cfg.roomCode)&playerName=$($cfg.playerName)"
$friendState = Invoke-RestMethod "http://localhost:8787/api/friend_state?roomCode=$($cfg.roomCode)&playerName=$($cfg.playerName)"
$friendState.friend_state | Select-Object playerName, populationTotal, dominantRace, fresh, secondsSinceReceived
$friendState.friend_state.populationByRace.GARTHIMI
$friendState.friend_state.resources.FISH
```

After that, click `SHD` in game on a disposable save. Expected result:

1. The button changes to `SHD ON`.
2. The hover text shows the friend's population, dominant race, resource count,
   and freshness age.
3. The world/faction UI contains a faction named `friend`.
4. Opening that native faction/region view should show the projected population
   and goods rather than a random empty shell.

Solo inbox trade simulation:

```powershell
$cfgPath = "$env:APPDATA\songsofsyx\saves\profile\Syx Duo Realm\syx_duo_realm.json"
$cfg = Get-Content $cfgPath -Raw | ConvertFrom-Json

$trade = @{
  roomCode = $cfg.roomCode
  toPlayer = $cfg.playerName
  fromPlayer = "friend"
  resourceKey = "FISH"
  amount = 25
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://localhost:8787/api/dev/inbox_trade -ContentType "application/json" -Body $trade
Invoke-RestMethod "http://localhost:8787/api/inbox?roomCode=$($cfg.roomCode)&playerName=$($cfg.playerName)"
```

Solo incoming war request validation:

```powershell
$cfgPath = "$env:APPDATA\songsofsyx\saves\profile\Syx Duo Realm\syx_duo_realm.json"
$cfg = Get-Content $cfgPath -Raw | ConvertFrom-Json

$warRequest = @{
  roomCode = $cfg.roomCode
  fromPlayer = "friend"
  toPlayer = $cfg.playerName
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://localhost:8787/api/dev/war_request -ContentType "application/json" -Body $warRequest
Invoke-RestMethod "http://localhost:8787/api/war_status?roomCode=$($cfg.roomCode)&playerName=$($cfg.playerName)&friendName=friend"
```

After the in-game `WAR` button shows `WAR IN`, click it to accept and resolve.
Then validate the generated report:

```powershell
$status = Invoke-RestMethod "http://localhost:8787/api/war_status?roomCode=$($cfg.roomCode)&playerName=$($cfg.playerName)&friendName=friend"
$status.warRequests | Select-Object requestId, fromPlayer, toPlayer, status, resolvedWarId
$status.diplomacy.targetRelation | Select-Object status, playerA, playerB, updatedAt, lastWarId
$status.wars | Select-Object warId, attacker, defender, winner, spoilsTradeId, spoilsResourceKey, spoilsAmount, summary
```

When `targetRelation.status` is `WAR`, the in-game button should show `WAR ON`.
Clicking it creates a new server report and updates `targetRelation.lastWarId`.
If your player won, the same report also creates a claimable war-spoils inbox
package:

```powershell
Invoke-RestMethod "http://localhost:8787/api/inbox?roomCode=$($cfg.roomCode)&playerName=$($cfg.playerName)" |
  Select-Object -ExpandProperty trades |
  Where-Object source -eq "war-spoils" |
  Select-Object tradeId, resourceKey, amount, fromPlayer, source, warId
```

In game, wait for inbox polling or click `R`, then use `N` to select the
war-spoils package and `T` to claim it. If your player lost, check the friend's
inbox instead:

```powershell
Invoke-RestMethod "http://localhost:8787/api/inbox?roomCode=$($cfg.roomCode)&playerName=friend"
```

Solo peace request validation after the in-game `PCE` button sends a request:

```powershell
$status = Invoke-RestMethod "http://localhost:8787/api/war_status?roomCode=$($cfg.roomCode)&playerName=$($cfg.playerName)&friendName=friend"
$peace = $status.peaceRequests | Where-Object status -eq "PENDING" | Select-Object -First 1

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8787/api/peace_request/$($peace.requestId)/accept" `
  -ContentType "application/json" `
  -Body (@{ playerName = "friend" } | ConvertTo-Json)

$status = Invoke-RestMethod "http://localhost:8787/api/war_status?roomCode=$($cfg.roomCode)&playerName=$($cfg.playerName)&friendName=friend"
$status.diplomacy.targetRelation | Select-Object status, playerA, playerB, updatedAt, lastWarId
$status.peaceRequests | Select-Object requestId, fromPlayer, toPlayer, status, requestedStatus
```

Expected result: `targetRelation.status` becomes `PEACE`. After the next in-game
refresh, `PCE` should show `PCE OK`, and `WAR` should no longer show `WAR ON`.

For a two-player LAN test, set both clients to the same `roomCode` and different
`playerName` values. The room is considered ready when `playerCount` is at least
2.

Seed a test trade:

```powershell
$trade = @{
  roomCode = "local"
  toPlayer = "player"
  fromPlayer = "friend"
  resourceKey = "FISH"
  amount = 25
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://localhost:8787/api/trade -ContentType "application/json" -Body $trade
Invoke-RestMethod "http://localhost:8787/api/inbox?roomCode=local&playerName=player"
```

Seed a two-player room for the shadow faction prototype:

```powershell
$alice = @{
  playerName = "player"
  roomCode = "local"
  sentAt = (Get-Date).ToUniversalTime().ToString("o")
  cityState = @{
    saveId = "alice_save"
    saveIdSource = "manual"
    worldSeed = 111
    playerFactionName = "Alice Realm"
    populationTotal = 200
    activeNpcFactionsCount = 20
    playerRealmRegionsCount = 1
  }
} | ConvertTo-Json -Depth 6

$bob = @{
  playerName = "friend"
  roomCode = "local"
  sentAt = (Get-Date).ToUniversalTime().ToString("o")
  cityState = @{
    saveId = "bob_save"
    saveIdSource = "manual"
    worldSeed = 222
    playerFactionName = "Bob Realm"
    populationTotal = 300
    activeNpcFactionsCount = 20
    playerRealmRegionsCount = 2
  }
} | ConvertTo-Json -Depth 6

Invoke-RestMethod -Method Post -Uri http://localhost:8787/api/state -ContentType "application/json" -Body $alice
Invoke-RestMethod -Method Post -Uri http://localhost:8787/api/state -ContentType "application/json" -Body $bob
Invoke-RestMethod "http://localhost:8787/api/friend_state?roomCode=local&playerName=player"
```
