// Keeps a local copy of the data plus a queue ("outbox") of edits not yet saved to the
// source. The app always shows cache + outbox, so it works offline and feels instant.

import { del, get, set } from "idb-keyval";
import { applyOp, applyOps, commitMessage, type Files, type Op } from "../ops";
import { type Backend, BackendError, type Cache, contents, DeviceBackend, type SourceConfig } from "./backend";
import { GitHubBackend } from "./github";

const KEYS = {
  config: "catchup:config",
  cache: "catchup:cache",
  outbox: "catchup:outbox",
  syncedAt: "catchup:synced-at",
};

export type SyncStatus = "idle" | "syncing" | "offline" | "error";

export interface SyncState {
  ready: boolean;
  config: SourceConfig | null;
  files: Files;
  pending: number;
  status: SyncStatus;
  error?: string;
  syncedAt?: number;
}

export function createBackend(config: SourceConfig): Backend {
  return config.kind === "github" ? new GitHubBackend(config) : new DeviceBackend();
}

export class SyncEngine {
  private config: SourceConfig | null = null;
  private backend: Backend | null = null;
  private cache: Cache = {};
  private outbox: Op[] = [];
  private status: SyncStatus = "idle";
  private error?: string;
  private syncedAt?: number;
  private ready = false;
  private running: Promise<void> | null = null;
  private queued: Promise<void> | null = null;
  private listeners = new Set<() => void>();
  private snapshot: SyncState;

  constructor(private readonly makeBackend: (config: SourceConfig) => Backend = createBackend) {
    this.snapshot = this.buildSnapshot();
  }

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  getState = (): SyncState => this.snapshot;

  private buildSnapshot(): SyncState {
    return {
      ready: this.ready,
      config: this.config,
      files: applyOps(contents(this.cache), this.outbox),
      pending: this.outbox.length,
      status: this.status,
      error: this.error,
      syncedAt: this.syncedAt,
    };
  }

  private emit() {
    this.snapshot = this.buildSnapshot();
    for (const listener of this.listeners) listener();
  }

  async init(): Promise<void> {
    const [config, cache, outbox, syncedAt] = await Promise.all([
      get<SourceConfig>(KEYS.config),
      get<Cache>(KEYS.cache),
      get<Op[]>(KEYS.outbox),
      get<number>(KEYS.syncedAt),
    ]);
    this.config = config ?? null;
    this.backend = config ? this.makeBackend(config) : null;
    this.cache = cache ?? {};
    this.outbox = outbox ?? [];
    this.syncedAt = syncedAt;
    this.ready = true;
    this.emit();
    if (this.config) void this.sync();
  }

  /**
   * Switches where data lives. With `bringData`, people currently in the app are
   * copied to the new source (existing files there are left alone).
   */
  async configure(config: SourceConfig, { bringData = false } = {}): Promise<void> {
    await this.running;
    const carried: Op[] = bringData
      ? Object.entries(this.snapshot.files).map(([path, content]) => ({
          type: "create" as const,
          path,
          content,
          ifExists: "skip" as const,
        }))
      : [];
    this.config = config;
    this.backend = this.makeBackend(config);
    this.cache = {};
    this.outbox = carried;
    this.syncedAt = undefined;
    this.error = undefined;
    this.status = "idle";
    await Promise.all([
      set(KEYS.config, config),
      set(KEYS.cache, this.cache),
      set(KEYS.outbox, this.outbox),
      del(KEYS.syncedAt),
    ]);
    this.emit();
    await this.sync();
  }

  async reset(): Promise<void> {
    await this.running;
    this.config = null;
    this.backend = null;
    this.cache = {};
    this.outbox = [];
    this.syncedAt = undefined;
    this.error = undefined;
    this.status = "idle";
    await Promise.all(Object.values(KEYS).map((k) => del(k)));
    this.emit();
  }

  /** Queues edits, updates the UI immediately, and saves in the background. */
  async apply(...ops: Op[]): Promise<string[]> {
    let files = this.snapshot.files;
    const paths = ops.map((op) => {
      const result = applyOp(files, op);
      files = result.files;
      return result.path;
    });
    this.outbox = [...this.outbox, ...ops];
    await set(KEYS.outbox, this.outbox);
    this.emit();
    void this.sync();
    return paths;
  }

  /** Saves queued edits, then fetches the latest files. Safe to call any time. */
  sync(): Promise<void> {
    if (this.running) {
      // Run once more after the current sync, so edits made meanwhile are included.
      this.queued ??= this.running.then(() => {
        this.queued = null;
        return this.sync();
      });
      return this.queued;
    }
    this.running = this.run().finally(() => {
      this.running = null;
    });
    return this.running;
  }

  private async run(): Promise<void> {
    const backend = this.backend;
    if (!backend) return;
    this.status = "syncing";
    this.emit();
    try {
      const sending = this.outbox;
      if (sending.length > 0) {
        this.cache = await backend.push(sending, this.cache, commitMessage(sending));
        // Keep anything queued while we were saving.
        this.outbox = this.outbox.slice(sending.length);
        await Promise.all([set(KEYS.cache, this.cache), set(KEYS.outbox, this.outbox)]);
      }
      this.cache = await backend.pull(this.cache);
      this.syncedAt = Date.now();
      await Promise.all([set(KEYS.cache, this.cache), set(KEYS.syncedAt, this.syncedAt)]);
      this.status = "idle";
      this.error = undefined;
    } catch (e) {
      const offline = e instanceof BackendError && e.status === undefined;
      this.status = offline ? "offline" : "error";
      this.error = e instanceof Error ? e.message : String(e);
    }
    this.emit();
  }
}
