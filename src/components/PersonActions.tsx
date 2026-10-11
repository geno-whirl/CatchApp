import { useState } from "react";
import { contactLinks } from "../lib/contactLinks";
import type { Person } from "../lib/person";
import { toast } from "../store";
import { Icon, type IconName } from "./Icon";
import { LogSheet } from "./LogSheet";
import { ScheduleSheet } from "./ScheduleSheet";

function Action({ icon, label, href, onClick }: { icon: IconName; label: string; href?: string; onClick?: () => void }) {
  const body = (
    <>
      <Icon name={icon} />
      <span>{label}</span>
    </>
  );
  return href ? (
    <a className="action" href={href} target={href.startsWith("http") ? "_blank" : undefined} rel="noreferrer">
      {body}
    </a>
  ) : (
    <button className="action" onClick={onClick}>
      {body}
    </button>
  );
}

/** Reach out, then log it or plan the next one. */
export function PersonActions({ person, reach = true }: { person: Person; reach?: boolean }) {
  const [sheet, setSheet] = useState<"log" | "schedule" | null>(null);
  const links = contactLinks(person);
  const close = () => setSheet(null);

  return (
    <>
      <div className="actions">
        {reach && links.call && <Action icon="phone" label="Call" href={links.call} />}
        {reach && links.whatsapp && <Action icon="message" label="WhatsApp" href={links.whatsapp} />}
        {reach && !links.whatsapp && links.sms && <Action icon="message" label="Text" href={links.sms} />}
        {reach && links.email && !links.call && <Action icon="mail" label="Email" href={links.email} />}
        <Action icon="log" label="Log" onClick={() => setSheet("log")} />
        <Action icon="calendar" label="Plan" onClick={() => setSheet("schedule")} />
      </div>
      {sheet === "log" && <LogSheet person={person} onClose={close} onSaved={toast} />}
      {sheet === "schedule" && <ScheduleSheet person={person} onClose={close} onSaved={toast} />}
    </>
  );
}
