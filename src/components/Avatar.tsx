const HUES = [12, 32, 150, 190, 215, 262, 290, 330];

function hueFor(name: string): number {
  let hash = 0;
  for (const ch of name) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  return HUES[hash % HUES.length];
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/);
  const first = parts[0]?.[0] ?? "";
  const last = parts.length > 1 ? parts[parts.length - 1][0] : "";
  return (first + last).toUpperCase();
}

export function Avatar({ name, size = "md" }: { name: string; size?: "md" | "lg" }) {
  return (
    <div className={`avatar avatar-${size}`} style={{ "--hue": hueFor(name) } as React.CSSProperties} aria-hidden>
      {initials(name)}
    </div>
  );
}
