const http = require("node:http");
const fs = require("node:fs/promises");
const path = require("node:path");

const host = process.env.HOST || "127.0.0.1";
const port = Number(process.env.PORT || 8787);
const maxBodyBytes = 2 * 1024 * 1024;
const staleAfterSeconds = Number(process.env.STALE_AFTER_SECONDS || 120);
const warSpoilsResourceKey = String(process.env.WAR_SPOILS_RESOURCE_KEY || "FISH").trim() || "FISH";
const warSpoilsBaseAmountValue = Number(process.env.WAR_SPOILS_BASE_AMOUNT || 20);
const warSpoilsMaxAmountValue = Number(process.env.WAR_SPOILS_MAX_AMOUNT || 250);
const warSpoilsBaseAmount =
  Number.isFinite(warSpoilsBaseAmountValue) && warSpoilsBaseAmountValue > 0
    ? Math.floor(warSpoilsBaseAmountValue)
    : 20;
const warSpoilsMaxAmount =
  Number.isFinite(warSpoilsMaxAmountValue) && warSpoilsMaxAmountValue > 0
    ? Math.floor(warSpoilsMaxAmountValue)
    : 250;
const dataDir = process.env.DATA_DIR || __dirname;
const devEndpointsEnabled =
  process.env.ENABLE_DEV_ENDPOINTS === "1" ||
  (process.env.ENABLE_DEV_ENDPOINTS !== "0" && (host === "127.0.0.1" || host === "localhost"));
const outputFile = path.join(dataDir, "received_state.json");
const roomsFile = path.join(dataDir, "rooms_state.json");
const tradesFile = path.join(dataDir, "trades_state.json");
const warsFile = path.join(dataDir, "wars_state.json");
const warRequestsFile = path.join(dataDir, "war_requests_state.json");
const peaceRequestsFile = path.join(dataDir, "peace_requests_state.json");
const diplomacyFile = path.join(dataDir, "diplomacy_state.json");
const rooms = new Map();
const trades = new Map();
const wars = new Map();
const warRequests = new Map();
const peaceRequests = new Map();
const diplomacyRelations = new Map();
let tradeCounter = 0;
let warCounter = 0;
let warRequestCounter = 0;
let peaceRequestCounter = 0;

function readBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let length = 0;

    request.on("data", (chunk) => {
      length += chunk.length;
      if (length > maxBodyBytes) {
        reject(new Error("Request body is too large."));
        request.destroy();
        return;
      }
      chunks.push(chunk);
    });

    request.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    request.on("error", reject);
  });
}

function sendJson(response, statusCode, value) {
  const body = stringifyJsonAscii(value);
  response.writeHead(statusCode, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
  });
  response.end(body);
}

function stringifyJsonAscii(value) {
  return `${JSON.stringify(value, null, 2).replace(/[^\x00-\x7f]/g, (character) => {
    const hex = character.charCodeAt(0).toString(16).padStart(4, "0");
    return `\\u${hex}`;
  })}\n`;
}

const server = http.createServer(async (request, response) => {
  const url = new URL(request.url, `http://${request.headers.host || "localhost"}`);

  try {
    if (request.method === "GET" && url.pathname === "/api/health") {
      sendJson(response, 200, {
        ok: true,
        serverTime: new Date().toISOString(),
        host,
        port,
        rooms: rooms.size,
        trades: trades.size,
        wars: wars.size,
        warRequests: warRequests.size,
        peaceRequests: peaceRequests.size,
        diplomacyRelations: diplomacyRelations.size,
        staleAfterSeconds,
        devEndpointsEnabled,
      });
      return;
    }

    if (url.pathname.startsWith("/api/dev/") && !devEndpointsEnabled) {
      sendJson(response, 403, {
        ok: false,
        error: "Development endpoints are disabled. Set ENABLE_DEV_ENDPOINTS=1 to enable them.",
      });
      return;
    }

    if (request.method === "POST" && url.pathname === "/api/dev/friend") {
      const body = await readBody(request);
      const payload = body.trim() ? JSON.parse(body) : {};
      const receivedAt = new Date().toISOString();
      const friendPayload = createDevFriendPayload(payload, receivedAt);
      const room = updateRoom(friendPayload, receivedAt);
      await persistRooms(receivedAt);
      sendJson(response, 200, {
        ok: true,
        receivedAt,
        friend_state: friendState(friendPayload.roomCode, normalizeKey(payload.localPlayerName, "player")),
        roomPlayerCount: room.players.length,
      });
      return;
    }

    if (request.method === "POST" && url.pathname === "/api/dev/inbox_trade") {
      const body = await readBody(request);
      const payload = body.trim() ? JSON.parse(body) : {};
      const trade = createDevInboxTrade(payload);
      await persistTrades(new Date().toISOString());
      sendJson(response, 200, { ok: true, trade });
      return;
    }

    if (request.method === "POST" && url.pathname === "/api/dev/war_request") {
      const body = await readBody(request);
      const payload = body.trim() ? JSON.parse(body) : {};
      const warRequest = createWarRequest({
        requestId: payload.requestId,
        roomCode: normalizeKey(payload.roomCode, "local"),
        fromPlayer: normalizeKey(payload.fromPlayer, "friend"),
        toPlayer: normalizeKey(payload.toPlayer, normalizeKey(payload.localPlayerName, "player")),
        source: "dev-war-request",
      });
      await persistWarRequests(new Date().toISOString());
      sendJson(response, 200, { ok: true, warRequest });
      return;
    }

    if (request.method === "POST" && url.pathname === "/api/dev/reset") {
      const now = new Date().toISOString();
      rooms.clear();
      trades.clear();
      wars.clear();
      warRequests.clear();
      peaceRequests.clear();
      diplomacyRelations.clear();
      tradeCounter = 0;
      warCounter = 0;
      warRequestCounter = 0;
      peaceRequestCounter = 0;
      await persistRooms(now);
      await persistTrades(now);
      await persistWars(now);
      await persistWarRequests(now);
      await persistPeaceRequests(now);
      await persistDiplomacy(now);
      sendJson(response, 200, { ok: true, resetAt: now });
      return;
    }

    if (request.method === "GET" && url.pathname === "/api/rooms") {
      sendJson(response, 200, { ok: true, rooms: roomSummaries() });
      return;
    }

    if (request.method === "GET" && url.pathname === "/api/inbox") {
      sendJson(response, 200, {
        ok: true,
        trades: inboxTrades(url.searchParams.get("roomCode"), url.searchParams.get("playerName")),
      });
      return;
    }

    if (request.method === "GET" && url.pathname === "/api/trades") {
      sendJson(response, 200, {
        ok: true,
        trades: [...trades.values()],
      });
      return;
    }

    if (request.method === "GET" && url.pathname === "/api/war_reports") {
      sendJson(response, 200, {
        ok: true,
        wars: warReports(url.searchParams.get("roomCode"), url.searchParams.get("playerName")),
      });
      return;
    }

    if (request.method === "GET" && url.pathname === "/api/war_status") {
      const roomCode = url.searchParams.get("roomCode");
      const playerName = url.searchParams.get("playerName");
      sendJson(response, 200, {
        ok: true,
        wars: warReports(roomCode, playerName),
        warRequests: playerWarRequests(roomCode, playerName),
        peaceRequests: playerPeaceRequests(roomCode, playerName),
        diplomacy: diplomacyStatus(roomCode, playerName, url.searchParams.get("friendName")),
      });
      return;
    }

    if (request.method === "GET" && url.pathname === "/api/diplomacy_status") {
      sendJson(response, 200, diplomacyStatus(
        url.searchParams.get("roomCode"),
        url.searchParams.get("playerName"),
        url.searchParams.get("friendName")
      ));
      return;
    }

    if (request.method === "GET" && url.pathname === "/api/wars") {
      sendJson(response, 200, {
        ok: true,
        wars: [...wars.values()],
      });
      return;
    }

    if (request.method === "GET" && url.pathname === "/api/war_requests") {
      sendJson(response, 200, {
        ok: true,
        warRequests: playerWarRequests(url.searchParams.get("roomCode"), url.searchParams.get("playerName")),
      });
      return;
    }

    if (request.method === "GET" && url.pathname === "/api/peace_requests") {
      sendJson(response, 200, {
        ok: true,
        peaceRequests: playerPeaceRequests(url.searchParams.get("roomCode"), url.searchParams.get("playerName")),
      });
      return;
    }

    if (request.method === "GET" && url.pathname === "/api/friend_state") {
      sendJson(response, 200, {
        ok: true,
        friend_state: friendState(url.searchParams.get("roomCode"), url.searchParams.get("playerName")),
      });
      return;
    }

    if (request.method === "GET" && url.pathname === "/api/room_status") {
      sendJson(response, 200, roomStatus(url.searchParams.get("roomCode"), url.searchParams.get("playerName")));
      return;
    }

    if (request.method === "GET" && url.pathname.startsWith("/api/rooms/")) {
      const roomCode = decodeURIComponent(url.pathname.slice("/api/rooms/".length));
      const room = rooms.get(roomCode);
      if (!room) {
        sendJson(response, 404, { ok: false, error: `Room not found: ${roomCode}` });
        return;
      }
      sendJson(response, 200, { ok: true, room });
      return;
    }

    if (request.method === "POST" && url.pathname === "/api/trade") {
      const body = await readBody(request);
      const payload = JSON.parse(body);
      const trade = createTrade(payload);
      await persistTrades(new Date().toISOString());
      sendJson(response, 200, { ok: true, trade });
      return;
    }

    if (request.method === "POST" && url.pathname === "/api/war") {
      const body = await readBody(request);
      const payload = JSON.parse(body);
      const war = createWar(payload);
      const now = war.createdAt || new Date().toISOString();
      await persistWars(now);
      await persistTrades(now);
      await persistDiplomacy(now);
      sendJson(response, 200, { ok: true, war });
      return;
    }

    if (request.method === "POST" && url.pathname === "/api/war_request") {
      const body = await readBody(request);
      const payload = JSON.parse(body);
      const warRequest = createWarRequest(payload);
      await persistWarRequests(new Date().toISOString());
      sendJson(response, 200, { ok: true, warRequest });
      return;
    }

    if (request.method === "POST" && url.pathname === "/api/peace_request") {
      const body = await readBody(request);
      const payload = JSON.parse(body);
      const peaceRequest = createPeaceRequest(payload);
      await persistPeaceRequests(new Date().toISOString());
      sendJson(response, 200, { ok: true, peaceRequest });
      return;
    }

    if (request.method === "POST" && url.pathname.startsWith("/api/war_request/") && url.pathname.endsWith("/accept")) {
      const requestId = decodeURIComponent(url.pathname.slice("/api/war_request/".length, -"/accept".length));
      const body = await readBody(request);
      const payload = body.trim() ? JSON.parse(body) : {};
      const result = acceptWarRequest(requestId, payload);
      const now = result.war?.createdAt || new Date().toISOString();
      await persistWarRequests(now);
      await persistWars(now);
      await persistTrades(now);
      await persistDiplomacy(now);
      sendJson(response, 200, result);
      return;
    }

    if (request.method === "POST" && url.pathname.startsWith("/api/war_request/") && url.pathname.endsWith("/decline")) {
      const requestId = decodeURIComponent(url.pathname.slice("/api/war_request/".length, -"/decline".length));
      const body = await readBody(request);
      const payload = body.trim() ? JSON.parse(body) : {};
      const result = declineWarRequest(requestId, payload);
      await persistWarRequests(result.updatedAt || new Date().toISOString());
      sendJson(response, 200, result);
      return;
    }

    if (request.method === "POST" && url.pathname.startsWith("/api/peace_request/") && url.pathname.endsWith("/accept")) {
      const requestId = decodeURIComponent(url.pathname.slice("/api/peace_request/".length, -"/accept".length));
      const body = await readBody(request);
      const payload = body.trim() ? JSON.parse(body) : {};
      const result = acceptPeaceRequest(requestId, payload);
      const now = result.updatedAt || new Date().toISOString();
      await persistPeaceRequests(now);
      await persistDiplomacy(now);
      sendJson(response, 200, result);
      return;
    }

    if (request.method === "POST" && url.pathname.startsWith("/api/peace_request/") && url.pathname.endsWith("/decline")) {
      const requestId = decodeURIComponent(url.pathname.slice("/api/peace_request/".length, -"/decline".length));
      const body = await readBody(request);
      const payload = body.trim() ? JSON.parse(body) : {};
      const result = declinePeaceRequest(requestId, payload);
      await persistPeaceRequests(result.updatedAt || new Date().toISOString());
      sendJson(response, 200, result);
      return;
    }

    if (request.method === "POST" && url.pathname.startsWith("/api/trade/") && url.pathname.endsWith("/claim")) {
      const tradeId = decodeURIComponent(url.pathname.slice("/api/trade/".length, -"/claim".length));
      const body = await readBody(request);
      const payload = body.trim() ? JSON.parse(body) : {};
      const result = claimTrade(tradeId, payload);
      await persistTrades(result.claimedAt || new Date().toISOString());
      sendJson(response, 200, result);
      return;
    }

    if (request.method !== "POST" || url.pathname !== "/api/state") {
      sendJson(response, 404, {
        ok: false,
        error: "Use GET /api/health, POST /api/state, GET /api/room_status, GET /api/friend_state, GET /api/inbox, GET /api/trades, GET /api/war_status, GET /api/diplomacy_status, GET /api/war_reports, GET /api/war_requests, GET /api/peace_requests, GET /api/wars, POST /api/trade, POST /api/war, POST /api/war_request, POST /api/war_request/{requestId}/accept, POST /api/war_request/{requestId}/decline, POST /api/peace_request, POST /api/peace_request/{requestId}/accept, POST /api/peace_request/{requestId}/decline, POST /api/trade/{tradeId}/claim, GET /api/rooms, GET /api/rooms/{roomCode}, POST /api/dev/friend, POST /api/dev/inbox_trade, POST /api/dev/war_request, or POST /api/dev/reset",
      });
      return;
    }

    const body = await readBody(request);
    const payload = JSON.parse(body);
    validatePayload(payload);
    const receivedAt = new Date().toISOString();
    const room = updateRoom(payload, receivedAt);

    await fs.mkdir(dataDir, { recursive: true });
    await fs.writeFile(
      outputFile,
      stringifyJsonAscii({ receivedAt, payload }),
      "ascii"
    );
    await persistRooms(receivedAt);

    console.log(
      `[${receivedAt}] ${payload.playerName || "unknown"} room=${payload.roomCode || "unknown"} save=${
        payload.cityState?.saveId || "unknown"
      } source=${payload.cityState?.saveIdSource || "unknown"} pop=${payload.cityState?.populationTotal ?? "unknown"}`
    );

    sendJson(response, 200, {
      ok: true,
      receivedAt,
      playerName: payload.playerName || null,
      roomCode: payload.roomCode || null,
      saveId: payload.cityState?.saveId || null,
      saveIdSource: payload.cityState?.saveIdSource || null,
      roomPlayerCount: room.players.length,
      roomReady: room.players.length >= 2,
    });
  } catch (error) {
    console.error(error);
    sendJson(response, 400, { ok: false, error: error.message });
  }
});

start().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

async function start() {
  await fs.mkdir(dataDir, { recursive: true });
  await loadRooms();
  await loadTrades();
  await loadWars();
  await loadWarRequests();
  await loadPeaceRequests();
  await loadDiplomacy();

  server.listen(port, host, () => {
    console.log(`Syx Duo Realm test server listening at http://${host}:${port}/api/state`);
    console.log(`Writing latest payload to ${outputFile}`);
    console.log(`Writing room snapshots to ${roomsFile}`);
    console.log(`Writing trade snapshots to ${tradesFile}`);
    console.log(`Writing war snapshots to ${warsFile}`);
    console.log(`Writing war request snapshots to ${warRequestsFile}`);
    console.log(`Writing peace request snapshots to ${peaceRequestsFile}`);
    console.log(`Writing diplomacy snapshots to ${diplomacyFile}`);
    console.log(`Room freshness window is ${staleAfterSeconds} seconds`);
    console.log(`Development endpoints are ${devEndpointsEnabled ? "enabled" : "disabled"}`);
    console.log(`Loaded ${rooms.size} room(s), ${trades.size} trade(s), ${wars.size} war(s), ${warRequests.size} war request(s), ${peaceRequests.size} peace request(s), and ${diplomacyRelations.size} diplomacy relation(s) from disk`);
  });
}

function validatePayload(payload) {
  if (!payload || typeof payload !== "object") {
    throw new Error("Payload must be a JSON object.");
  }
  if (!payload.cityState || typeof payload.cityState !== "object") {
    throw new Error("Payload must include cityState.");
  }
  if (!payload.cityState.saveId) {
    throw new Error("cityState.saveId is required.");
  }
}

function updateRoom(payload, receivedAt) {
  const roomCode = normalizeKey(payload.roomCode, "local");
  const playerName = normalizeKey(payload.playerName, "player");
  const saveId = String(payload.cityState.saveId);
  const playerKey = `${playerName}:${saveId}`;

  let room = rooms.get(roomCode);
  if (!room) {
    room = {
      roomCode,
      createdAt: receivedAt,
      updatedAt: receivedAt,
      players: [],
    };
    rooms.set(roomCode, room);
  }

  room.updatedAt = receivedAt;

  const playerState = {
    playerKey,
    playerName,
    saveId,
    saveIdSource: payload.cityState.saveIdSource || null,
    receivedAt,
    sentAt: payload.sentAt || null,
    worldSeed: payload.cityState.worldSeed ?? null,
    protocolVersion: payload.protocolVersion ?? null,
    modVersion: payload.modVersion || null,
    gameVersion: payload.gameVersion || null,
    playerFactionName: payload.cityState.playerFactionName || null,
    populationTotal: payload.cityState.populationTotal ?? null,
    activeNpcFactionsCount: payload.cityState.activeNpcFactionsCount ?? null,
    playerRealmRegionsCount: payload.cityState.playerRealmRegionsCount ?? null,
    cityState: payload.cityState,
  };

  const existingIndex = room.players.findIndex((player) => player.playerKey === playerKey);
  if (existingIndex >= 0) {
    room.players[existingIndex] = playerState;
  } else {
    room.players.push(playerState);
  }

  room.players.sort((left, right) => left.playerKey.localeCompare(right.playerKey));
  return room;
}

function roomSummaries() {
  return [...rooms.values()].map((room) => {
    const players = latestPlayersByName(room.players);
    return {
      roomCode: room.roomCode,
      createdAt: room.createdAt,
      updatedAt: room.updatedAt,
      playerCount: players.length,
      rawStateCount: room.players.length,
      ready: players.length >= 2,
      players: players.map((player) => ({
      playerKey: player.playerKey,
      playerName: player.playerName,
      saveId: player.saveId,
      saveIdSource: player.saveIdSource,
      receivedAt: player.receivedAt,
      sentAt: player.sentAt,
      worldSeed: player.worldSeed,
      protocolVersion: player.protocolVersion,
      modVersion: player.modVersion,
      gameVersion: player.gameVersion,
      playerFactionName: player.playerFactionName,
      populationTotal: player.populationTotal,
      activeNpcFactionsCount: player.activeNpcFactionsCount,
      playerRealmRegionsCount: player.playerRealmRegionsCount,
      })),
    };
  });
}

function roomSnapshots() {
  return [...rooms.values()];
}

function normalizeKey(value, fallback) {
  const text = value == null ? "" : String(value).trim();
  return text || fallback;
}

function createDevFriendPayload(payload, now) {
  const playerName = normalizeKey(payload.playerName || payload.friendName, "friend");
  const roomCode = normalizeKey(payload.roomCode, "local");
  const saveId = normalizeKey(payload.saveId, `${playerName}_dev_save`);
  const worldSeed = numberOrNull(payload.worldSeed) ?? 222222;
  const populationTotal = numberOrNull(payload.populationTotal) ?? 320;
  const playerRealmRegionsCount = numberOrNull(payload.playerRealmRegionsCount) ?? 2;
  const activeNpcFactionsCount = numberOrNull(payload.activeNpcFactionsCount) ?? 20;
  const playerFactionName = normalizeKey(payload.playerFactionName, `${playerName} Realm`);
  const offensivePower = numberOrNull(payload.offensivePower) ?? Math.round(populationTotal + playerRealmRegionsCount * 140);
  const dominantRaceKey = normalizeKey(payload.dominantRace, "HUMAN");
  const populationByRace = payload.populationByRace && typeof payload.populationByRace === "object"
    ? payload.populationByRace
    : {
        [dominantRaceKey]: {
          name: dominantRaceKey,
          count: populationTotal,
        },
      };
  const resources = payload.resources && typeof payload.resources === "object"
    ? payload.resources
    : {
        FISH: {
          name: "Fish",
          amount: numberOrNull(payload.fish) ?? 250,
        },
        BREAD: {
          name: "Bread",
          amount: numberOrNull(payload.bread) ?? 120,
        },
        _WOOD: {
          name: "Wood",
          amount: numberOrNull(payload.wood) ?? 300,
        },
      };

  return {
    modName: "Syx Duo Realm Dev Friend",
    protocolVersion: numberOrNull(payload.protocolVersion) ?? 1,
    modVersion: normalizeKey(payload.modVersion, "dev-friend"),
    gameVersion: normalizeKey(payload.gameVersion, "V71"),
    playerName,
    roomCode,
    sentAt: now,
    cityState: {
      saveId,
      saveIdSource: "devFriend",
      worldSeed,
      playerFactionName,
      populationTotal,
      populationByRace,
      resources,
      militaryProfile: {
        offensivePower,
        fieldArmyPower: Math.max(0, offensivePower - playerRealmRegionsCount * 100),
        armyCount: numberOrNull(payload.armyCount) ?? 1,
        capitalMilitaryPower: playerRealmRegionsCount * 100,
        realmRegions: playerRealmRegionsCount,
        source: "devFriend",
      },
      activeNpcFactionsCount,
      playerRealmRegionsCount,
      timestamp: now,
    },
  };
}

function createDevInboxTrade(payload) {
  const nowId = Date.now();
  return createTrade({
    tradeId: normalizeKey(payload.tradeId, `dev_trade_${nowId}_${++tradeCounter}`),
    roomCode: normalizeKey(payload.roomCode, "local"),
    toPlayer: normalizeKey(payload.toPlayer, "player"),
    fromPlayer: normalizeKey(payload.fromPlayer, "friend"),
    resourceKey: normalizeKey(payload.resourceKey, "FISH"),
    amount: numberOrNull(payload.amount) ?? 25,
    availableAtSend: numberOrNull(payload.availableAtSend),
    source: "dev-inbox",
  });
}

function createTrade(payload) {
  if (!payload || typeof payload !== "object") {
    throw new Error("Trade payload must be a JSON object.");
  }
  if (!payload.resourceKey) {
    throw new Error("resourceKey is required.");
  }
  const amount = Number(payload.amount);
  if (!Number.isFinite(amount) || amount <= 0) {
    throw new Error("amount must be a positive number.");
  }

  const now = new Date().toISOString();
  const tradeId = normalizeKey(payload.tradeId, `trade_${Date.now()}_${++tradeCounter}`);
  let trade = trades.get(tradeId);
  if (trade) {
    return trade;
  }

  const toPlayer = payload.toPlayer == null ? null : normalizeKey(payload.toPlayer, "player");
  const fromPlayer = normalizeKey(payload.fromPlayer, "server");
  if (toPlayer && toPlayer === fromPlayer) {
    throw new Error("toPlayer must be different from fromPlayer.");
  }

  trade = {
    tradeId,
    roomCode: normalizeKey(payload.roomCode, "local"),
    toPlayer,
    fromPlayer,
    resourceKey: normalizeKey(payload.resourceKey, ""),
    amount: Math.floor(amount),
    source: normalizeKey(payload.source, "manual"),
    warId: stringOrNull(payload.warId),
    availableAtSend: numberOrNull(payload.availableAtSend),
    createdAt: now,
    claimed: false,
    claimedAt: null,
    claimedBy: null,
  };
  trades.set(tradeId, trade);
  console.log(
    `[${now}] trade created id=${trade.tradeId} room=${trade.roomCode} to=${trade.toPlayer || "*"} from=${trade.fromPlayer} ${trade.resourceKey} x${trade.amount}`
  );
  return trade;
}

function createWar(payload) {
  if (!payload || typeof payload !== "object") {
    throw new Error("War payload must be a JSON object.");
  }

  const roomCode = normalizeKey(payload.roomCode, "local");
  const attacker = normalizeKey(payload.attacker, "player");
  const defender = normalizeKey(payload.defender, "");
  if (!defender) {
    throw new Error("defender is required.");
  }
  if (attacker === defender) {
    throw new Error("defender must be different from attacker.");
  }

  const attackerState = latestPlayerState(roomCode, attacker);
  const defenderState = latestPlayerState(roomCode, defender);
  if (!attackerState) {
    throw new Error(`No latest state for attacker: ${attacker}`);
  }
  if (!defenderState) {
    throw new Error(`No latest state for defender: ${defender}`);
  }
  requireFreshPlayerState(attackerState, "attacker", attacker);
  requireFreshPlayerState(defenderState, "defender", defender);

  const now = new Date().toISOString();
  const warId = normalizeKey(payload.warId, `war_${Date.now()}_${++warCounter}`);
  let war = wars.get(warId);
  if (war) {
    return war;
  }

  const attackerScore = warScore(attackerState);
  const defenderScore = warScore(defenderState);
  const totalPower = attackerScore + defenderScore;
  const attackerPowerBalance = attackerScore / totalPower;
  const defenderPowerBalance = defenderScore / totalPower;
  const attackerRoll = Math.max(1, Math.round(attackerScore * warMultiplier(`${warId}:${attacker}`)));
  const defenderRoll = Math.max(1, Math.round(defenderScore * warMultiplier(`${warId}:${defender}`)));
  const attackerWins = attackerRoll >= defenderRoll;
  const winner = attackerWins ? attacker : defender;
  const loser = attackerWins ? defender : attacker;
  const margin = Math.abs(attackerRoll - defenderRoll);
  const attackerLossRate = autoLossRate(attackerWins ? attackerPowerBalance : 1 - defenderPowerBalance);
  const defenderLossRate = autoLossRate(attackerWins ? 1 - attackerPowerBalance : defenderPowerBalance);
  const attackerEstimatedLosses = estimatedLosses(attackerState, attackerLossRate);
  const defenderEstimatedLosses = estimatedLosses(defenderState, defenderLossRate);

  war = {
    warId,
    requestId: normalizeKey(payload.requestId, ""),
    roomCode,
    attacker,
    defender,
    winner,
    loser,
    attackerScore,
    defenderScore,
    attackerRoll,
    defenderRoll,
    attackerPowerBalance,
    defenderPowerBalance,
    attackerLossRate,
    defenderLossRate,
    attackerEstimatedLosses,
    defenderEstimatedLosses,
    margin,
    source: normalizeKey(payload.source, "manual"),
    createdAt: now,
    summary: `${winner} defeated ${loser} by ${margin} strength. Estimated losses: ${attacker} ${attackerEstimatedLosses}, ${defender} ${defenderEstimatedLosses}.`,
  };

  const spoils = createWarSpoilsTrade(war);
  war.spoilsTradeId = spoils.tradeId;
  war.spoilsResourceKey = spoils.resourceKey;
  war.spoilsAmount = spoils.amount;

  wars.set(warId, war);
  setDiplomacyRelation(roomCode, attacker, defender, "WAR", now, warId, normalizeKey(payload.source, "manual"));
  console.log(
    `[${now}] war resolved id=${war.warId} room=${war.roomCode} ${war.attacker}(${war.attackerRoll}) vs ${war.defender}(${war.defenderRoll}) winner=${war.winner} spoils=${spoils.resourceKey}x${spoils.amount}`
  );
  return war;
}

function createWarSpoilsTrade(war) {
  return createTrade({
    tradeId: `war_spoils_${war.warId}`,
    roomCode: war.roomCode,
    toPlayer: war.winner,
    fromPlayer: war.loser,
    resourceKey: warSpoilsResourceKey,
    amount: warSpoilsAmount(war),
    source: "war-spoils",
    warId: war.warId,
  });
}

function warSpoilsAmount(war) {
  const winnerScore = war.winner === war.attacker ? war.attackerScore : war.defenderScore;
  const strengthComponent = Math.floor(Math.sqrt(Math.max(1, numberOrNull(winnerScore) ?? 1)));
  const marginComponent = Math.floor(Math.max(0, numberOrNull(war.margin) ?? 0) / 25);
  return Math.max(1, Math.min(warSpoilsMaxAmount, warSpoilsBaseAmount + strengthComponent + marginComponent));
}

function createWarRequest(payload) {
  if (!payload || typeof payload !== "object") {
    throw new Error("War request payload must be a JSON object.");
  }

  const roomCode = normalizeKey(payload.roomCode, "local");
  const fromPlayer = normalizeKey(payload.fromPlayer || payload.attacker, "player");
  const toPlayer = normalizeKey(payload.toPlayer || payload.defender, "");
  if (!toPlayer) {
    throw new Error("toPlayer is required.");
  }
  if (fromPlayer === toPlayer) {
    throw new Error("toPlayer must be different from fromPlayer.");
  }

  const requestId = normalizeKey(payload.requestId, `war_req_${Date.now()}_${++warRequestCounter}`);
  let warRequest = warRequests.get(requestId);
  if (warRequest) {
    return warRequest;
  }

  const duplicate = [...warRequests.values()].find((entry) =>
    entry.roomCode === roomCode &&
    entry.fromPlayer === fromPlayer &&
    entry.toPlayer === toPlayer &&
    entry.status === "PENDING"
  );
  if (duplicate) {
    return duplicate;
  }

  const now = new Date().toISOString();
  warRequest = {
    requestId,
    roomCode,
    fromPlayer,
    toPlayer,
    status: "PENDING",
    source: normalizeKey(payload.source, "manual"),
    createdAt: now,
    updatedAt: now,
    resolvedWarId: null,
    note: normalizeKey(payload.note, ""),
  };
  warRequests.set(requestId, warRequest);
  console.log(`[${now}] war request created id=${requestId} room=${roomCode} from=${fromPlayer} to=${toPlayer}`);
  return warRequest;
}

function acceptWarRequest(requestId, payload) {
  const warRequest = warRequests.get(requestId);
  if (!warRequest) {
    throw new Error(`War request not found: ${requestId}`);
  }
  if (warRequest.status === "ACCEPTED" && warRequest.resolvedWarId) {
    return { ok: true, alreadyAccepted: true, warRequest, war: wars.get(warRequest.resolvedWarId) || null };
  }
  if (warRequest.status !== "PENDING") {
    throw new Error(`War request is not pending: ${warRequest.status}`);
  }

  const acceptedBy = normalizeKey(payload.playerName || payload.acceptedBy, warRequest.toPlayer);
  if (acceptedBy !== warRequest.toPlayer) {
    throw new Error(`Only ${warRequest.toPlayer} can accept this war request.`);
  }

  const war = createWar({
    warId: `war_from_${requestId}`,
    requestId,
    roomCode: warRequest.roomCode,
    attacker: warRequest.fromPlayer,
    defender: warRequest.toPlayer,
    source: "war-request",
  });

  const now = war.createdAt || new Date().toISOString();
  warRequest.status = "ACCEPTED";
  warRequest.updatedAt = now;
  warRequest.resolvedWarId = war.warId;
  console.log(`[${now}] war request accepted id=${requestId} war=${war.warId}`);
  return { ok: true, alreadyAccepted: false, warRequest, war };
}

function declineWarRequest(requestId, payload) {
  const warRequest = warRequests.get(requestId);
  if (!warRequest) {
    throw new Error(`War request not found: ${requestId}`);
  }
  if (warRequest.status !== "PENDING") {
    return { ok: true, alreadyFinal: true, warRequest };
  }

  const declinedBy = normalizeKey(payload.playerName || payload.declinedBy, warRequest.toPlayer);
  if (declinedBy !== warRequest.toPlayer) {
    throw new Error(`Only ${warRequest.toPlayer} can decline this war request.`);
  }

  const now = new Date().toISOString();
  warRequest.status = "DECLINED";
  warRequest.updatedAt = now;
  warRequest.note = normalizeKey(payload.note, "Declined.");
  console.log(`[${now}] war request declined id=${requestId} by=${declinedBy}`);
  return { ok: true, alreadyFinal: false, updatedAt: now, warRequest };
}

function createPeaceRequest(payload) {
  if (!payload || typeof payload !== "object") {
    throw new Error("Peace request payload must be a JSON object.");
  }

  const roomCode = normalizeKey(payload.roomCode, "local");
  const fromPlayer = normalizeKey(payload.fromPlayer, "player");
  const toPlayer = normalizeKey(payload.toPlayer, "");
  if (!toPlayer) {
    throw new Error("toPlayer is required.");
  }
  if (fromPlayer === toPlayer) {
    throw new Error("toPlayer must be different from fromPlayer.");
  }

  const relation = relationBetween(roomCode, fromPlayer, toPlayer);
  if (relation.status !== "WAR") {
    throw new Error(`Cannot request peace while relation is ${relation.status}.`);
  }

  const requestId = normalizeKey(payload.requestId, `peace_req_${Date.now()}_${++peaceRequestCounter}`);
  let peaceRequest = peaceRequests.get(requestId);
  if (peaceRequest) {
    return peaceRequest;
  }

  const duplicate = [...peaceRequests.values()].find((entry) =>
    entry.roomCode === roomCode &&
    entry.fromPlayer === fromPlayer &&
    entry.toPlayer === toPlayer &&
    entry.status === "PENDING"
  );
  if (duplicate) {
    return duplicate;
  }

  const now = new Date().toISOString();
  peaceRequest = {
    requestId,
    roomCode,
    fromPlayer,
    toPlayer,
    requestedStatus: normalizeDiplomacyStatus(payload.requestedStatus) || "PEACE",
    status: "PENDING",
    source: normalizeKey(payload.source, "manual"),
    createdAt: now,
    updatedAt: now,
    note: normalizeKey(payload.note, ""),
  };
  peaceRequests.set(requestId, peaceRequest);
  console.log(`[${now}] peace request created id=${requestId} room=${roomCode} from=${fromPlayer} to=${toPlayer}`);
  return peaceRequest;
}

function acceptPeaceRequest(requestId, payload) {
  const peaceRequest = peaceRequests.get(requestId);
  if (!peaceRequest) {
    throw new Error(`Peace request not found: ${requestId}`);
  }
  if (peaceRequest.status === "ACCEPTED") {
    return {
      ok: true,
      alreadyAccepted: true,
      updatedAt: peaceRequest.updatedAt,
      peaceRequest,
      relation: relationBetween(peaceRequest.roomCode, peaceRequest.fromPlayer, peaceRequest.toPlayer),
    };
  }
  if (peaceRequest.status !== "PENDING") {
    throw new Error(`Peace request is not pending: ${peaceRequest.status}`);
  }

  const acceptedBy = normalizeKey(payload.playerName || payload.acceptedBy, peaceRequest.toPlayer);
  if (acceptedBy !== peaceRequest.toPlayer) {
    throw new Error(`Only ${peaceRequest.toPlayer} can accept this peace request.`);
  }

  const now = new Date().toISOString();
  const relation = setDiplomacyRelation(
    peaceRequest.roomCode,
    peaceRequest.fromPlayer,
    peaceRequest.toPlayer,
    peaceRequest.requestedStatus || "PEACE",
    now,
    relationBetween(peaceRequest.roomCode, peaceRequest.fromPlayer, peaceRequest.toPlayer).lastWarId,
    "peace-request"
  );
  peaceRequest.status = "ACCEPTED";
  peaceRequest.updatedAt = now;
  console.log(`[${now}] peace request accepted id=${requestId} relation=${relation.status}`);
  return { ok: true, alreadyAccepted: false, updatedAt: now, peaceRequest, relation };
}

function declinePeaceRequest(requestId, payload) {
  const peaceRequest = peaceRequests.get(requestId);
  if (!peaceRequest) {
    throw new Error(`Peace request not found: ${requestId}`);
  }
  if (peaceRequest.status !== "PENDING") {
    return { ok: true, alreadyFinal: true, updatedAt: peaceRequest.updatedAt, peaceRequest };
  }

  const declinedBy = normalizeKey(payload.playerName || payload.declinedBy, peaceRequest.toPlayer);
  if (declinedBy !== peaceRequest.toPlayer) {
    throw new Error(`Only ${peaceRequest.toPlayer} can decline this peace request.`);
  }

  const now = new Date().toISOString();
  peaceRequest.status = "DECLINED";
  peaceRequest.updatedAt = now;
  peaceRequest.note = normalizeKey(payload.note, "Declined.");
  console.log(`[${now}] peace request declined id=${requestId} by=${declinedBy}`);
  return { ok: true, alreadyFinal: false, updatedAt: now, peaceRequest };
}

function playerWarRequests(roomCode, playerName) {
  const room = normalizeKey(roomCode, "local");
  const player = normalizeKey(playerName, "player");
  return [...warRequests.values()]
    .filter((request) => request.roomCode === room)
    .filter((request) => request.fromPlayer === player || request.toPlayer === player)
    .sort((left, right) => String(right.updatedAt).localeCompare(String(left.updatedAt)));
}

function playerPeaceRequests(roomCode, playerName) {
  const room = normalizeKey(roomCode, "local");
  const player = normalizeKey(playerName, "player");
  return [...peaceRequests.values()]
    .filter((request) => request.roomCode === room)
    .filter((request) => request.fromPlayer === player || request.toPlayer === player)
    .sort((left, right) => String(right.updatedAt).localeCompare(String(left.updatedAt)));
}

function warReports(roomCode, playerName) {
  const room = normalizeKey(roomCode, "local");
  const player = normalizeKey(playerName, "player");
  return [...wars.values()]
    .filter((war) => war.roomCode === room)
    .filter((war) => war.attacker === player || war.defender === player)
    .sort((left, right) => String(right.createdAt).localeCompare(String(left.createdAt)));
}

function diplomacyStatus(roomCode, playerName, friendName) {
  const room = normalizeKey(roomCode, "local");
  const player = normalizeKey(playerName, "player");
  const relations = [...diplomacyRelations.values()]
    .filter((relation) => relation.roomCode === room)
    .filter((relation) => relation.playerA === player || relation.playerB === player)
    .sort((left, right) => String(right.updatedAt).localeCompare(String(left.updatedAt)));

  const friend = normalizeKey(friendName, "");
  const targetRelation = friend ? relationBetween(room, player, friend) : null;
  return {
    ok: true,
    roomCode: room,
    playerName: player,
    friendName: friend || null,
    targetRelation,
    relations,
    peaceRequests: playerPeaceRequests(room, player),
  };
}

function relationBetween(roomCode, playerA, playerB) {
  const key = diplomacyKey(roomCode, playerA, playerB);
  return diplomacyRelations.get(key) || {
    relationId: key,
    roomCode: normalizeKey(roomCode, "local"),
    playerA: normalizeKey(playerA, "player"),
    playerB: normalizeKey(playerB, "friend"),
    status: "PEACE",
    source: "implicit",
    createdAt: null,
    updatedAt: null,
    lastWarId: null,
  };
}

function setDiplomacyRelation(roomCode, playerA, playerB, status, updatedAt, lastWarId, source) {
  const relationId = diplomacyKey(roomCode, playerA, playerB);
  const [left, right] = sortedPlayers(playerA, playerB);
  const existing = diplomacyRelations.get(relationId);
  const relation = {
    relationId,
    roomCode: normalizeKey(roomCode, "local"),
    playerA: left,
    playerB: right,
    status: normalizeDiplomacyStatus(status) || "PEACE",
    source: normalizeKey(source, "manual"),
    createdAt: existing?.createdAt || updatedAt,
    updatedAt,
    lastWarId: stringOrNull(lastWarId),
  };
  diplomacyRelations.set(relationId, relation);
  return relation;
}

function diplomacyKey(roomCode, playerA, playerB) {
  const [left, right] = sortedPlayers(playerA, playerB);
  return `${normalizeKey(roomCode, "local")}::${left}::${right}`;
}

function sortedPlayers(playerA, playerB) {
  const left = normalizeKey(playerA, "player");
  const right = normalizeKey(playerB, "friend");
  return [left, right].sort((a, b) => a.localeCompare(b));
}

function latestPlayerState(roomCode, playerName) {
  const room = rooms.get(normalizeKey(roomCode, "local"));
  if (!room) {
    return null;
  }

  const player = normalizeKey(playerName, "player");
  const states = room.players
    .filter((entry) => entry.playerName === player)
    .sort((left, right) => String(right.receivedAt).localeCompare(String(left.receivedAt)));
  return states[0] || null;
}

function warScore(playerState) {
  const profilePower = numberOrNull(playerState.cityState?.militaryProfile?.offensivePower);
  if (profilePower != null && profilePower > 0) {
    return Math.max(1, Math.round(profilePower));
  }

  const population = numberOrNull(playerState.populationTotal) ?? 0;
  const regions = numberOrNull(playerState.playerRealmRegionsCount) ?? 0;
  const npcFactions = numberOrNull(playerState.activeNpcFactionsCount) ?? 0;
  const resources = totalResourceAmount(playerState.cityState?.resources);
  return Math.max(1, Math.round(population + regions * 100 + npcFactions * 5 + Math.sqrt(resources) * 8));
}

function requireFreshPlayerState(playerState, role, playerName) {
  const seconds = secondsSince(playerState?.receivedAt, new Date());
  if (seconds == null || seconds > staleAfterSeconds) {
    throw new Error(
      `Cannot resolve war: ${role} ${playerName} state is stale. Last sync was ${
        seconds == null ? "unknown" : `${seconds}s`
      } ago; freshness window is ${staleAfterSeconds}s.`
    );
  }
}

function autoLossRate(powerBalance) {
  if (powerBalance < 0.5) {
    return 1.0;
  }
  return Math.max(0, Math.min(1, 1.0 - powerBalance));
}

function estimatedLosses(playerState, lossRate) {
  const militaryPower = numberOrNull(playerState.cityState?.militaryProfile?.offensivePower) ?? 0;
  const population = numberOrNull(playerState.populationTotal) ?? 0;
  const baseMen = militaryPower > 0 ? Math.sqrt(militaryPower) * 12 : population * 0.08;
  return Math.max(0, Math.round(baseMen * lossRate));
}

function totalResourceAmount(resources) {
  if (!resources || typeof resources !== "object") {
    return 0;
  }

  let total = 0;
  for (const value of Object.values(resources)) {
    if (value && typeof value === "object") {
      total += numberOrNull(value.amount) ?? 0;
    }
  }
  return total;
}

function warMultiplier(seed) {
  const hash = hashText(seed);
  return 0.85 + (hash % 31) / 100;
}

function hashText(value) {
  let hash = 2166136261;
  for (const character of String(value)) {
    hash ^= character.charCodeAt(0);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

function claimTrade(tradeId, payload) {
  const trade = trades.get(tradeId);
  if (!trade) {
    throw new Error(`Trade not found: ${tradeId}`);
  }

  if (trade.claimed) {
    return {
      ok: true,
      tradeId,
      alreadyClaimed: true,
      claimedAt: trade.claimedAt,
      claimedBy: trade.claimedBy,
    };
  }

  const claimedAt = new Date().toISOString();
  trade.claimed = true;
  trade.claimedAt = claimedAt;
  trade.claimedBy = normalizeKey(payload.playerName, "player");
  console.log(`[${claimedAt}] trade claimed id=${tradeId} by=${trade.claimedBy}`);
  return {
    ok: true,
    tradeId,
    alreadyClaimed: false,
    claimedAt,
    claimedBy: trade.claimedBy,
  };
}

function inboxTrades(roomCode, playerName) {
  const room = normalizeKey(roomCode, "local");
  const player = playerName == null ? null : normalizeKey(playerName, "player");

  return [...trades.values()]
    .filter((trade) => !trade.claimed)
    .filter((trade) => trade.roomCode === room)
    .filter((trade) => !trade.toPlayer || !player || trade.toPlayer === player)
    .map((trade) => ({
      tradeId: trade.tradeId,
      resourceKey: trade.resourceKey,
      amount: trade.amount,
      fromPlayer: trade.fromPlayer,
      source: trade.source,
      warId: trade.warId,
    }));
}

function friendState(roomCode, playerName) {
  const room = rooms.get(normalizeKey(roomCode, "local"));
  if (!room) {
    return null;
  }

  const player = normalizeKey(playerName, "player");
  const friend = latestPlayersByName(room.players).find((entry) => entry.playerName !== player);
  if (!friend) {
    return null;
  }

  const now = new Date();
  const seconds = secondsSince(friend.receivedAt, now);
  return {
    playerName: friend.playerName,
    saveId: friend.saveId,
    saveIdSource: friend.saveIdSource,
    worldSeed: friend.worldSeed,
    protocolVersion: friend.protocolVersion,
    modVersion: friend.modVersion,
    gameVersion: friend.gameVersion,
    playerFactionName: friend.playerFactionName,
    populationTotal: friend.populationTotal,
    populationByRace: friend.cityState?.populationByRace || {},
    dominantRace: dominantRace(friend.cityState?.populationByRace),
    resources: friend.cityState?.resources || {},
    activeNpcFactionsCount: friend.activeNpcFactionsCount,
    playerRealmRegionsCount: friend.playerRealmRegionsCount,
    militaryProfile: friend.cityState?.militaryProfile || null,
    receivedAt: friend.receivedAt,
    secondsSinceReceived: seconds,
    fresh: seconds != null && seconds <= staleAfterSeconds,
    staleAfterSeconds,
    sentAt: friend.sentAt,
  };
}

function dominantRace(populationByRace) {
  if (!populationByRace || typeof populationByRace !== "object") {
    return null;
  }

  let bestKey = null;
  let bestCount = -1;
  for (const [key, value] of Object.entries(populationByRace)) {
    const count = numberOrNull(value?.count) ?? 0;
    if (count > bestCount) {
      bestKey = key;
      bestCount = count;
    }
  }
  return bestKey;
}

function roomStatus(roomCode, playerName) {
  const now = new Date();
  const nowIso = now.toISOString();
  const roomKey = normalizeKey(roomCode, "local");
  const player = normalizeKey(playerName, "player");
  const room = rooms.get(roomKey);

  if (!room) {
    return {
      ok: true,
      serverTime: nowIso,
      roomCode: roomKey,
      playerName: player,
      staleAfterSeconds,
      exists: false,
      ready: false,
      freshReady: false,
      friendPresent: false,
      friendFresh: false,
      playerCount: 0,
      freshPlayerCount: 0,
      players: [],
      friend_state: null,
    };
  }

  const players = latestPlayersByName(room.players).map((entry) => roomStatusPlayer(entry, now));
  const freshPlayerCount = players.filter((entry) => entry.fresh).length;
  const friendPlayers = players.filter((entry) => entry.playerName !== player);
  const freshFriends = friendPlayers.filter((entry) => entry.fresh);

  return {
    ok: true,
    serverTime: nowIso,
    roomCode: room.roomCode,
    playerName: player,
    staleAfterSeconds,
    exists: true,
    createdAt: room.createdAt,
    updatedAt: room.updatedAt,
    ready: players.length >= 2,
    freshReady: freshPlayerCount >= 2 && freshFriends.length > 0,
    friendPresent: friendPlayers.length > 0,
    friendFresh: freshFriends.length > 0,
    playerCount: players.length,
    freshPlayerCount,
    players,
    friend_state: friendState(roomKey, player),
  };
}

function latestPlayersByName(players) {
  const latest = new Map();
  for (const player of players) {
    const previous = latest.get(player.playerName);
    if (!previous || String(player.receivedAt).localeCompare(String(previous.receivedAt)) > 0) {
      latest.set(player.playerName, player);
    }
  }
  return [...latest.values()].sort((left, right) => left.playerName.localeCompare(right.playerName));
}

function roomStatusPlayer(player, now) {
  const secondsSinceReceived = secondsSince(player.receivedAt, now);
  return {
    playerKey: player.playerKey,
    playerName: player.playerName,
    saveId: player.saveId,
    saveIdSource: player.saveIdSource,
    receivedAt: player.receivedAt,
    sentAt: player.sentAt,
    secondsSinceReceived,
    fresh: secondsSinceReceived != null && secondsSinceReceived <= staleAfterSeconds,
    worldSeed: player.worldSeed,
    protocolVersion: player.protocolVersion,
    modVersion: player.modVersion,
    gameVersion: player.gameVersion,
    playerFactionName: player.playerFactionName,
    populationTotal: player.populationTotal,
    activeNpcFactionsCount: player.activeNpcFactionsCount,
    playerRealmRegionsCount: player.playerRealmRegionsCount,
  };
}

function secondsSince(value, now) {
  if (!value) {
    return null;
  }

  const time = new Date(value);
  if (Number.isNaN(time.getTime())) {
    return null;
  }

  return Math.max(0, Math.floor((now.getTime() - time.getTime()) / 1000));
}

async function loadRooms() {
  const data = await readJsonFile(roomsFile);
  if (!data || !Array.isArray(data.rooms)) {
    return;
  }

  rooms.clear();
  for (const value of data.rooms) {
    const room = normalizeRoom(value);
    if (room) {
      rooms.set(room.roomCode, room);
    }
  }
}

async function loadTrades() {
  const data = await readJsonFile(tradesFile);
  if (!data || !Array.isArray(data.trades)) {
    return;
  }

  trades.clear();
  let loaded = 0;
  for (const value of data.trades) {
    const trade = normalizeTrade(value);
    if (trade) {
      trades.set(trade.tradeId, trade);
      loaded++;
    }
  }
  tradeCounter = loaded;
}

async function loadWars() {
  const data = await readJsonFile(warsFile);
  if (!data || !Array.isArray(data.wars)) {
    return;
  }

  wars.clear();
  let loaded = 0;
  for (const value of data.wars) {
    const war = normalizeWar(value);
    if (war) {
      wars.set(war.warId, war);
      loaded++;
    }
  }
  warCounter = loaded;
}

async function loadWarRequests() {
  const data = await readJsonFile(warRequestsFile);
  if (!data || !Array.isArray(data.warRequests)) {
    return;
  }

  warRequests.clear();
  let loaded = 0;
  for (const value of data.warRequests) {
    const warRequest = normalizeWarRequest(value);
    if (warRequest) {
      warRequests.set(warRequest.requestId, warRequest);
      loaded++;
    }
  }
  warRequestCounter = loaded;
}

async function loadPeaceRequests() {
  const data = await readJsonFile(peaceRequestsFile);
  if (!data || !Array.isArray(data.peaceRequests)) {
    return;
  }

  peaceRequests.clear();
  let loaded = 0;
  for (const value of data.peaceRequests) {
    const peaceRequest = normalizePeaceRequest(value);
    if (peaceRequest) {
      peaceRequests.set(peaceRequest.requestId, peaceRequest);
      loaded++;
    }
  }
  peaceRequestCounter = loaded;
}

async function loadDiplomacy() {
  const data = await readJsonFile(diplomacyFile);
  if (!data || !Array.isArray(data.relations)) {
    return;
  }

  diplomacyRelations.clear();
  for (const value of data.relations) {
    const relation = normalizeDiplomacyRelation(value);
    if (relation) {
      diplomacyRelations.set(relation.relationId, relation);
    }
  }
}

async function readJsonFile(file) {
  try {
    const text = await fs.readFile(file, "utf8");
    return JSON.parse(text);
  } catch (error) {
    if (error.code === "ENOENT") {
      return null;
    }
    console.error(`Could not load ${file}: ${error.message}`);
    return null;
  }
}

function normalizeRoom(value) {
  if (!value || typeof value !== "object") {
    return null;
  }

  const roomCode = normalizeKey(value.roomCode, "");
  if (!roomCode) {
    return null;
  }

  const room = {
    roomCode,
    createdAt: stringOrNull(value.createdAt) || new Date().toISOString(),
    updatedAt: stringOrNull(value.updatedAt) || stringOrNull(value.createdAt) || new Date().toISOString(),
    players: [],
  };

  if (Array.isArray(value.players)) {
    for (const player of value.players) {
      const normalized = normalizePlayerState(player);
      if (normalized) {
        room.players.push(normalized);
      }
    }
  }

  room.players.sort((left, right) => left.playerKey.localeCompare(right.playerKey));
  return room;
}

function normalizePlayerState(value) {
  if (!value || typeof value !== "object") {
    return null;
  }

  const playerName = normalizeKey(value.playerName, "");
  const saveId = normalizeKey(value.saveId || value.cityState?.saveId, "");
  if (!playerName || !saveId) {
    return null;
  }

  const playerKey = normalizeKey(value.playerKey, `${playerName}:${saveId}`);
  return {
    playerKey,
    playerName,
    saveId,
    saveIdSource: stringOrNull(value.saveIdSource || value.cityState?.saveIdSource),
    receivedAt: stringOrNull(value.receivedAt),
    sentAt: stringOrNull(value.sentAt),
    worldSeed: numberOrNull(value.worldSeed ?? value.cityState?.worldSeed),
    protocolVersion: numberOrNull(value.protocolVersion),
    modVersion: stringOrNull(value.modVersion),
    gameVersion: stringOrNull(value.gameVersion),
    playerFactionName: stringOrNull(value.playerFactionName || value.cityState?.playerFactionName),
    populationTotal: numberOrNull(value.populationTotal ?? value.cityState?.populationTotal),
    activeNpcFactionsCount: numberOrNull(value.activeNpcFactionsCount ?? value.cityState?.activeNpcFactionsCount),
    playerRealmRegionsCount: numberOrNull(value.playerRealmRegionsCount ?? value.cityState?.playerRealmRegionsCount),
    cityState: value.cityState && typeof value.cityState === "object" ? value.cityState : null,
  };
}

function normalizeTrade(value) {
  if (!value || typeof value !== "object") {
    return null;
  }

  const tradeId = normalizeKey(value.tradeId, "");
  const resourceKey = normalizeKey(value.resourceKey, "");
  const amount = Math.floor(Number(value.amount));
  if (!tradeId || !resourceKey || !Number.isFinite(amount) || amount <= 0) {
    return null;
  }

  return {
    tradeId,
    roomCode: normalizeKey(value.roomCode, "local"),
    toPlayer: value.toPlayer == null ? null : normalizeKey(value.toPlayer, "player"),
    fromPlayer: normalizeKey(value.fromPlayer, "server"),
    resourceKey,
    amount,
    source: normalizeKey(value.source, "manual"),
    warId: stringOrNull(value.warId),
    availableAtSend: numberOrNull(value.availableAtSend),
    createdAt: stringOrNull(value.createdAt) || new Date().toISOString(),
    claimed: Boolean(value.claimed),
    claimedAt: stringOrNull(value.claimedAt),
    claimedBy: stringOrNull(value.claimedBy),
  };
}

function normalizeWar(value) {
  if (!value || typeof value !== "object") {
    return null;
  }

  const warId = normalizeKey(value.warId, "");
  const attacker = normalizeKey(value.attacker, "");
  const defender = normalizeKey(value.defender, "");
  const winner = normalizeKey(value.winner, "");
  const loser = normalizeKey(value.loser, "");
  if (!warId || !attacker || !defender || !winner || !loser) {
    return null;
  }

  return {
    warId,
    requestId: stringOrNull(value.requestId) || "",
    roomCode: normalizeKey(value.roomCode, "local"),
    attacker,
    defender,
    winner,
    loser,
    attackerScore: numberOrNull(value.attackerScore) ?? 0,
    defenderScore: numberOrNull(value.defenderScore) ?? 0,
    attackerRoll: numberOrNull(value.attackerRoll) ?? 0,
    defenderRoll: numberOrNull(value.defenderRoll) ?? 0,
    attackerPowerBalance: numberOrNull(value.attackerPowerBalance) ?? 0,
    defenderPowerBalance: numberOrNull(value.defenderPowerBalance) ?? 0,
    attackerLossRate: numberOrNull(value.attackerLossRate) ?? 0,
    defenderLossRate: numberOrNull(value.defenderLossRate) ?? 0,
    attackerEstimatedLosses: numberOrNull(value.attackerEstimatedLosses) ?? 0,
    defenderEstimatedLosses: numberOrNull(value.defenderEstimatedLosses) ?? 0,
    margin: numberOrNull(value.margin) ?? 0,
    spoilsTradeId: stringOrNull(value.spoilsTradeId),
    spoilsResourceKey: stringOrNull(value.spoilsResourceKey),
    spoilsAmount: numberOrNull(value.spoilsAmount) ?? 0,
    source: normalizeKey(value.source, "manual"),
    createdAt: stringOrNull(value.createdAt) || new Date().toISOString(),
    summary: normalizeKey(value.summary, `${winner} defeated ${loser}.`),
  };
}

function normalizeWarRequest(value) {
  if (!value || typeof value !== "object") {
    return null;
  }

  const requestId = normalizeKey(value.requestId, "");
  const fromPlayer = normalizeKey(value.fromPlayer, "");
  const toPlayer = normalizeKey(value.toPlayer, "");
  const status = normalizeWarRequestStatus(value.status);
  if (!requestId || !fromPlayer || !toPlayer || !status) {
    return null;
  }

  return {
    requestId,
    roomCode: normalizeKey(value.roomCode, "local"),
    fromPlayer,
    toPlayer,
    status,
    source: normalizeKey(value.source, "manual"),
    createdAt: stringOrNull(value.createdAt) || new Date().toISOString(),
    updatedAt: stringOrNull(value.updatedAt) || stringOrNull(value.createdAt) || new Date().toISOString(),
    resolvedWarId: stringOrNull(value.resolvedWarId),
    note: stringOrNull(value.note) || "",
  };
}

function normalizeWarRequestStatus(value) {
  const status = normalizeKey(value, "").toUpperCase();
  return ["PENDING", "ACCEPTED", "DECLINED"].includes(status) ? status : null;
}

function normalizePeaceRequest(value) {
  if (!value || typeof value !== "object") {
    return null;
  }

  const requestId = normalizeKey(value.requestId, "");
  const fromPlayer = normalizeKey(value.fromPlayer, "");
  const toPlayer = normalizeKey(value.toPlayer, "");
  const status = normalizeWarRequestStatus(value.status);
  if (!requestId || !fromPlayer || !toPlayer || !status) {
    return null;
  }

  return {
    requestId,
    roomCode: normalizeKey(value.roomCode, "local"),
    fromPlayer,
    toPlayer,
    requestedStatus: normalizeDiplomacyStatus(value.requestedStatus) || "PEACE",
    status,
    source: normalizeKey(value.source, "manual"),
    createdAt: stringOrNull(value.createdAt) || new Date().toISOString(),
    updatedAt: stringOrNull(value.updatedAt) || stringOrNull(value.createdAt) || new Date().toISOString(),
    note: stringOrNull(value.note) || "",
  };
}

function normalizeDiplomacyRelation(value) {
  if (!value || typeof value !== "object") {
    return null;
  }

  const roomCode = normalizeKey(value.roomCode, "local");
  const playerA = normalizeKey(value.playerA, "");
  const playerB = normalizeKey(value.playerB, "");
  const status = normalizeDiplomacyStatus(value.status);
  if (!playerA || !playerB || !status || playerA === playerB) {
    return null;
  }

  const relationId = diplomacyKey(roomCode, playerA, playerB);
  const updatedAt = stringOrNull(value.updatedAt) || stringOrNull(value.createdAt) || new Date().toISOString();
  return {
    relationId,
    roomCode,
    playerA: sortedPlayers(playerA, playerB)[0],
    playerB: sortedPlayers(playerA, playerB)[1],
    status,
    source: normalizeKey(value.source, "loaded"),
    createdAt: stringOrNull(value.createdAt) || updatedAt,
    updatedAt,
    lastWarId: stringOrNull(value.lastWarId),
  };
}

function normalizeDiplomacyStatus(value) {
  const status = normalizeKey(value, "").toUpperCase();
  return ["PEACE", "WAR", "TRUCE"].includes(status) ? status : null;
}

function stringOrNull(value) {
  return value == null ? null : String(value);
}

function numberOrNull(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

async function persistRooms(updatedAt) {
  await fs.mkdir(dataDir, { recursive: true });
  await fs.writeFile(
    roomsFile,
    stringifyJsonAscii({ updatedAt, rooms: roomSnapshots() }),
    "ascii"
  );
}

async function persistTrades(updatedAt) {
  await fs.mkdir(dataDir, { recursive: true });
  await fs.writeFile(
    tradesFile,
    stringifyJsonAscii({ updatedAt, trades: [...trades.values()] }),
    "ascii"
  );
}

async function persistWars(updatedAt) {
  await fs.mkdir(dataDir, { recursive: true });
  await fs.writeFile(
    warsFile,
    stringifyJsonAscii({ updatedAt, wars: [...wars.values()] }),
    "ascii"
  );
}

async function persistWarRequests(updatedAt) {
  await fs.mkdir(dataDir, { recursive: true });
  await fs.writeFile(
    warRequestsFile,
    stringifyJsonAscii({ updatedAt, warRequests: [...warRequests.values()] }),
    "ascii"
  );
}

async function persistPeaceRequests(updatedAt) {
  await fs.mkdir(dataDir, { recursive: true });
  await fs.writeFile(
    peaceRequestsFile,
    stringifyJsonAscii({ updatedAt, peaceRequests: [...peaceRequests.values()] }),
    "ascii"
  );
}

async function persistDiplomacy(updatedAt) {
  await fs.mkdir(dataDir, { recursive: true });
  await fs.writeFile(
    diplomacyFile,
    stringifyJsonAscii({ updatedAt, relations: [...diplomacyRelations.values()] }),
    "ascii"
  );
}
