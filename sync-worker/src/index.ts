export interface Env {
  ROOMS: DurableObjectNamespace;
}

type WatchTogetherRole = "host" | "member";

type WatchTogetherRoomState = {
  roomCode: string;
  detailPageUrl: string;
  title: string;
  episodeIndex: number;
  positionMs: number;
  durationMs: number;
  playbackRate: number;
  paused: boolean;
  updatedAtMs: number;
  memberCount: number;
};

type RoomRecord = {
  roomCode: string;
  hostToken: string;
  expiresAt: number;
  state: WatchTogetherRoomState;
};

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type"
};

const roomCodePattern = /^\d{6}$/;
const roomTtlMs = 2 * 60 * 60 * 1000;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders });
    }

    const url = new URL(request.url);
    const parts = url.pathname.split("/").filter(Boolean);

    if (request.method === "POST" && url.pathname === "/rooms") {
      return createRoom(request, env);
    }

    if (parts[0] === "rooms" && roomCodePattern.test(parts[1] ?? "")) {
      const roomCode = parts[1];
      const stub = env.ROOMS.getByName(roomCode);
      return stub.fetch(request);
    }

    return json({ error: "not_found" }, 404);
  }
};

async function createRoom(request: Request, env: Env): Promise<Response> {
  const payload = await request.json() as Record<string, unknown>;
  for (let attempts = 0; attempts < 20; attempts++) {
    const roomCode = generateRoomCode();
    const stub = env.ROOMS.getByName(roomCode);
    const response = await stub.fetch("https://room.local/create", {
      method: "POST",
      body: JSON.stringify({ ...payload, roomCode }),
      headers: { "Content-Type": "application/json" }
    });
    if (response.status !== 409) {
      return withCors(response);
    }
  }
  return json({ error: "room_code_exhausted" }, 503);
}

function generateRoomCode(): string {
  const bytes = new Uint32Array(1);
  crypto.getRandomValues(bytes);
  return String(bytes[0] % 1_000_000).padStart(6, "0");
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json; charset=utf-8"
    }
  });
}

function withCors(response: Response): Response {
  const headers = new Headers(response.headers);
  Object.entries(corsHeaders).forEach(([key, value]) => headers.set(key, value));
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers
  });
}

export class WatchTogetherRoom {
  constructor(
    private readonly state: DurableObjectState,
    private readonly env: Env
  ) {}

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const parts = url.pathname.split("/").filter(Boolean);

    if (request.method === "POST" && url.pathname === "/create") {
      return this.create(request);
    }

    const record = await this.getLiveRecord();
    if (!record) {
      return json({ error: "room_not_found" }, 404);
    }

    if (request.method === "GET" && parts.length === 2) {
      return json(this.withMemberCount(record).state);
    }

    if (request.method === "POST" && parts[2] === "state") {
      return this.updateHostState(request, record);
    }

    if (request.method === "GET" && parts[2] === "ws") {
      return this.acceptWebSocket(request, record);
    }

    return json({ error: "not_found" }, 404);
  }

  async webSocketMessage(ws: WebSocket, message: string | ArrayBuffer): Promise<void> {
    if (typeof message !== "string") {
      return;
    }
    const attachment = ws.deserializeAttachment() as { role?: WatchTogetherRole } | undefined;
    const record = await this.getLiveRecord();
    if (!record) {
      ws.close(4004, "room_not_found");
      return;
    }
    let parsed: unknown;
    try {
      parsed = JSON.parse(message);
    } catch {
      ws.send(JSON.stringify({ type: "error", message: "invalid_json" }));
      return;
    }
    const envelope = parsed as { type?: string; state?: WatchTogetherRoomState; hostToken?: string };
    if (envelope.type !== "host_state") {
      return;
    }
    if (attachment?.role !== "host" || envelope.hostToken !== record.hostToken || !envelope.state) {
      ws.send(JSON.stringify({ type: "error", message: "host_required" }));
      return;
    }
    const updated = this.normalizeRecord(record, envelope.state);
    await this.putRecord(updated);
    this.broadcast({ type: "room_state", state: this.withMemberCount(updated).state });
  }

  async webSocketClose(): Promise<void> {
    const record = await this.getLiveRecord();
    if (record) {
      this.broadcast({ type: "member_count", memberCount: this.memberCount() });
    }
  }

  private async create(request: Request): Promise<Response> {
    const existing = await this.state.storage.get<RoomRecord>("room");
    if (existing && existing.expiresAt > Date.now()) {
      return json({ error: "room_exists" }, 409);
    }

    const payload = await request.json() as Omit<WatchTogetherRoomState, "memberCount">;
    const roomCode = payload.roomCode;
    const now = Date.now();
    const record: RoomRecord = {
      roomCode,
      hostToken: crypto.randomUUID(),
      expiresAt: now + roomTtlMs,
      state: {
        roomCode,
        detailPageUrl: payload.detailPageUrl,
        title: payload.title,
        episodeIndex: payload.episodeIndex,
        positionMs: payload.positionMs,
        durationMs: payload.durationMs,
        playbackRate: payload.playbackRate,
        paused: payload.paused,
        updatedAtMs: payload.updatedAtMs || now,
        memberCount: 1
      }
    };
    await this.putRecord(record);
    return json({
      roomCode: record.roomCode,
      hostToken: record.hostToken,
      expiresAt: record.expiresAt,
      state: this.withMemberCount(record).state
    });
  }

  private async updateHostState(request: Request, record: RoomRecord): Promise<Response> {
    const payload = await request.json() as { hostToken?: string; state?: WatchTogetherRoomState };
    if (payload.hostToken !== record.hostToken || !payload.state) {
      return json({ error: "host_required" }, 403);
    }
    const updated = this.normalizeRecord(record, payload.state);
    await this.putRecord(updated);
    this.broadcast({ type: "room_state", state: this.withMemberCount(updated).state });
    return json(this.withMemberCount(updated).state);
  }

  private acceptWebSocket(request: Request, record: RoomRecord): Response {
    if (request.headers.get("Upgrade") !== "websocket") {
      return new Response("Expected WebSocket", { status: 426 });
    }
    const url = new URL(request.url);
    const role = url.searchParams.get("role") as WatchTogetherRole | null;
    const token = url.searchParams.get("token");
    if (role !== "host" && role !== "member") {
      return json({ error: "invalid_role" }, 400);
    }
    if (role === "host" && token !== record.hostToken) {
      return json({ error: "host_required" }, 403);
    }
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    server.serializeAttachment({ role });
    this.state.acceptWebSocket(server);
    server.send(JSON.stringify({ type: "room_state", state: this.withMemberCount(record).state }));
    this.broadcast({ type: "member_count", memberCount: this.memberCount() });
    return new Response(null, { status: 101, webSocket: client });
  }

  private async getLiveRecord(): Promise<RoomRecord | null> {
    const record = await this.state.storage.get<RoomRecord>("room");
    if (!record) {
      return null;
    }
    if (record.expiresAt <= Date.now()) {
      await this.state.storage.delete("room");
      this.state.getWebSockets().forEach((socket) => socket.close(4004, "room_expired"));
      return null;
    }
    return record;
  }

  private async putRecord(record: RoomRecord): Promise<void> {
    await this.state.storage.put("room", record);
  }

  private normalizeRecord(record: RoomRecord, state: WatchTogetherRoomState): RoomRecord {
    return {
      ...record,
      expiresAt: Date.now() + roomTtlMs,
      state: {
        ...state,
        roomCode: record.roomCode,
        memberCount: this.memberCount()
      }
    };
  }

  private withMemberCount(record: RoomRecord): RoomRecord {
    return {
      ...record,
      state: {
        ...record.state,
        memberCount: this.memberCount()
      }
    };
  }

  private memberCount(): number {
    return Math.max(1, this.state.getWebSockets().length);
  }

  private broadcast(data: unknown): void {
    const message = JSON.stringify(data);
    this.state.getWebSockets().forEach((socket) => {
      try {
        socket.send(message);
      } catch {
        socket.close(1011, "send_failed");
      }
    });
  }
}
