import { applyOps, type Files, type Op } from "../ops";

export interface CachedFile {
  content: string;
  /** Git blob SHA when the file came from a repository; used to skip re-downloads. */
  sha?: string;
}

export type Cache = Record<string, CachedFile>;

export type SourceConfig =
  | { kind: "device" }
  | {
      kind: "github";
      owner: string;
      repo: string;
      branch: string;
      /** Optional folder inside the repo that contains `people/`, e.g. `catchup`. */
      root?: string;
      token: string;
    };

export interface Backend {
  /** Returns the current files, reusing cached content where it hasn't changed. */
  pull(cache: Cache): Promise<Cache>;
  /** Applies ops on top of the latest remote files and saves them. */
  push(ops: Op[], cache: Cache, message: string): Promise<Cache>;
}

export class BackendError extends Error {
  constructor(
    message: string,
    readonly status?: number,
  ) {
    super(message);
  }
}

export function contents(cache: Cache): Files {
  return Object.fromEntries(Object.entries(cache).map(([path, f]) => [path, f.content]));
}

/** "This device": the cache is the data. */
export class DeviceBackend implements Backend {
  async pull(cache: Cache): Promise<Cache> {
    return cache;
  }

  async push(ops: Op[], cache: Cache): Promise<Cache> {
    const files = applyOps(contents(cache), ops);
    return Object.fromEntries(Object.entries(files).map(([path, content]) => [path, { content }]));
  }
}
