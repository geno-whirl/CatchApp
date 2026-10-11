// Turns picked files (including zips) into text files the parsers understand.
// Zips are filtered by name so photos and videos in exports are never decompressed.

import { unzip } from "fflate";
import type { ImportFile } from "./types";

const RELEVANT = [
  /\.vcf$/i,
  /\.txt$/i,
  /(^|\/)(your_)?friends(_\d+)?\.json$/i,
  /(^|\/)following(_\d+)?\.json$/i,
  /(^|\/)followers(_\d+)?\.json$/i,
  /(^|\/)message_\d+\.json$/i,
  /(^|\/)profile_information\.json$/i,
  /(^|\/)personal_information\.json$/i,
];

export function isRelevant(name: string): boolean {
  if (/(^|\/)(__MACOSX|\.)/.test(name)) return false;
  return RELEVANT.some((re) => re.test(name));
}

function unzipAsync(data: Uint8Array): Promise<Record<string, Uint8Array>> {
  return new Promise((resolve, reject) =>
    unzip(data, { filter: (f) => isRelevant(f.name) }, (err, out) => (err ? reject(err) : resolve(out))),
  );
}

export async function readImportFiles(picked: File[]): Promise<ImportFile[]> {
  const out: ImportFile[] = [];
  const decoder = new TextDecoder();
  for (const file of picked) {
    if (/\.zip$/i.test(file.name)) {
      const entries = await unzipAsync(new Uint8Array(await file.arrayBuffer()));
      for (const [name, bytes] of Object.entries(entries)) {
        out.push({ name: `${file.name}/${name}`, text: decoder.decode(bytes) });
      }
    } else {
      out.push({ name: file.name, text: await file.text() });
    }
  }
  return out;
}
