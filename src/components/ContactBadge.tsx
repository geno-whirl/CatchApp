import type { ContactStatus } from "../lib/status";

const LABELS: Record<ContactStatus, string | null> = {
  ok: null,
  "due-soon": "due soon",
  overdue: "overdue",
  unknown: null,
};

export function ContactBadge({ status }: { status: ContactStatus }) {
  const label = LABELS[status];
  return label ? <span className={`badge badge-${status}`}>{label}</span> : null;
}
