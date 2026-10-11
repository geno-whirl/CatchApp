// React bindings for the sync engine, plus app-wide helpers.

import { useMemo, useSyncExternalStore } from "react";
import { toIsoDate } from "./lib/dates";
import type { Op } from "./lib/ops";
import { parseFiles } from "./lib/people";
import { SyncEngine } from "./lib/storage/sync";

export const engine = new SyncEngine();
void engine.init();

export function useSync() {
  return useSyncExternalStore(engine.subscribe, engine.getState);
}

export function usePeople() {
  const { files } = useSync();
  return useMemo(() => parseFiles(files), [files]);
}

/** Today's date, stable for the whole day. */
export function useToday(): Date {
  const key = toIsoDate(new Date());
  return useMemo(() => {
    const [y, m, d] = key.split("-").map(Number);
    return new Date(y, m - 1, d);
  }, [key]);
}

export function apply(...ops: Op[]): Promise<string[]> {
  return engine.apply(...ops);
}

const examples = import.meta.glob("/examples/people/*.md", {
  query: "?raw",
  import: "default",
  eager: true,
}) as Record<string, string>;

export function exampleOps(): Op[] {
  return Object.entries(examples).map(([path, content]) => ({
    type: "create" as const,
    path: path.replace(/^\/examples\//, ""),
    content,
    ifExists: "skip" as const,
  }));
}

/** Saves a file to the device (works in the browser and in installed apps). */
export function download(name: string, content: BlobPart, type: string) {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

// Toasts: short confirmations ("Logged call with Maya").
type ToastListener = (message: string) => void;
const toastListeners = new Set<ToastListener>();

export function toast(message: string) {
  for (const listener of toastListeners) listener(message);
}

export function onToast(listener: ToastListener): () => void {
  toastListeners.add(listener);
  return () => toastListeners.delete(listener);
}
