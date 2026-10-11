import { useEffect, useMemo, useState } from "react";
import { Icon, type IconName } from "./components/Icon";
import { Toaster } from "./components/Toaster";
import { buildAgenda } from "./lib/priorities";
import { setBadge } from "./pwa";
import { EditMarkdown } from "./screens/EditMarkdown";
import { Import } from "./screens/Import";
import { People } from "./screens/People";
import { PersonForm } from "./screens/PersonForm";
import { PersonPage } from "./screens/PersonPage";
import { Settings } from "./screens/Settings";
import { Today } from "./screens/Today";
import { Welcome } from "./screens/Welcome";
import { engine, usePeople, useSync, useToday } from "./store";

function useHash(): string {
  const [hash, setHash] = useState(() => window.location.hash || "#/");
  useEffect(() => {
    const onChange = () => {
      setHash(window.location.hash || "#/");
      window.scrollTo(0, 0);
    };
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);
  return hash;
}

/** Sync whenever the app comes back to the foreground or the network returns. */
function useAutoSync() {
  useEffect(() => {
    const sync = () => document.visibilityState === "visible" && void engine.sync();
    document.addEventListener("visibilitychange", sync);
    window.addEventListener("online", sync);
    const timer = setInterval(sync, 5 * 60 * 1000);
    return () => {
      document.removeEventListener("visibilitychange", sync);
      window.removeEventListener("online", sync);
      clearInterval(timer);
    };
  }, []);
}

const TABS: { href: string; label: string; icon: IconName; match: RegExp }[] = [
  { href: "#/", label: "Today", icon: "today", match: /^#\/$/ },
  { href: "#/people", label: "People", icon: "people", match: /^#\/people/ },
  { href: "#/new", label: "Add", icon: "plus", match: /^#\/new/ },
  { href: "#/import", label: "Import", icon: "import", match: /^#\/import/ },
  { href: "#/settings", label: "Settings", icon: "settings", match: /^#\/settings/ },
];

function SyncDot() {
  const { status, pending, config } = useSync();
  if (!config) return null;
  const label =
    status === "syncing" ? "Syncing…" : status === "offline" ? `Offline${pending ? ` · ${pending} waiting` : ""}` : status === "error" ? "Sync problem" : "Saved";
  return (
    <a href="#/settings" className={`sync-dot sync-${status}`} title={label}>
      <span className="dot" />
      <span className="small">{label}</span>
    </a>
  );
}

export function App() {
  const hash = useHash();
  const { ready, config } = useSync();
  const { people } = usePeople();
  const today = useToday();
  useAutoSync();

  const badgeCount = useMemo(
    () => buildAgenda(people, today).suggestions.filter((s) => s.reasons.some((r) => r.kind === "overdue" || (r.kind === "birthday" && r.days === 0))).length,
    [people, today],
  );
  useEffect(() => setBadge(badgeCount), [badgeCount]);

  if (!ready) return <div className="splash" />;
  if (!config) {
    return (
      <div className="app">
        <Welcome />
        <Toaster />
      </div>
    );
  }

  const m = /^#\/people\/([^/]+)(?:\/(edit|markdown))?$/.exec(hash);
  const person = m ? people.find((p) => p.id === decodeURIComponent(m[1])) : undefined;

  let screen;
  if (m) {
    screen = !person ? (
      <div className="empty muted">
        <p>That person isn't here (yet). If you just synced, give it a second.</p>
        <a className="button" href="#/people">All people</a>
      </div>
    ) : m[2] === "edit" ? (
      <PersonForm key={person.id} person={person} />
    ) : m[2] === "markdown" ? (
      <EditMarkdown key={person.id} person={person} />
    ) : (
      <PersonPage person={person} />
    );
  } else if (hash.startsWith("#/people")) screen = <People />;
  else if (hash.startsWith("#/new")) screen = <PersonForm />;
  else if (hash.startsWith("#/import")) screen = <Import />;
  else if (hash.startsWith("#/settings")) screen = <Settings />;
  else screen = <Today />;

  return (
    <div className="app">
      <header className="topbar">
        <a href="#/" className="brand">CatchUp</a>
        <SyncDot />
      </header>
      <main>{screen}</main>
      <nav className="tabbar" aria-label="Main">
        {TABS.map((tab) => (
          <a key={tab.href} href={tab.href} className={tab.match.test(hash) ? "active" : ""}>
            <Icon name={tab.icon} size={22} />
            <span>{tab.label}</span>
          </a>
        ))}
      </nav>
      <Toaster />
    </div>
  );
}
