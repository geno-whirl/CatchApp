// The Contact Picker API (Chrome on Android) lets people choose contacts directly from
// their phone's address book. Other platforms import a .vcf export instead.

import { type Candidate, candidate } from "./types";

interface ContactAddress {
  city?: string;
  country?: string;
  region?: string;
}

interface PickedContact {
  name?: string[];
  tel?: string[];
  email?: string[];
  address?: ContactAddress[];
}

interface ContactsManager {
  select(props: string[], options: { multiple: boolean }): Promise<PickedContact[]>;
  getProperties(): Promise<string[]>;
}

function manager(): ContactsManager | undefined {
  return (navigator as Navigator & { contacts?: ContactsManager }).contacts;
}

export function canPickContacts(): boolean {
  return typeof navigator !== "undefined" && !!manager() && "ContactsManager" in window;
}

export async function pickContacts(): Promise<Candidate[]> {
  const contacts = manager()!;
  const supported = await contacts.getProperties();
  const props = ["name", "tel", "email", "address"].filter((p) => supported.includes(p));
  const picked = await contacts.select(props, { multiple: true });
  return picked
    .filter((p) => p.name?.[0]?.trim())
    .map((p) => {
      const c = candidate(p.name![0].trim(), "contacts");
      p.tel?.forEach((t, i) => (c.contact[i === 0 ? "phone" : `phone_${i + 1}`] = t));
      p.email?.forEach((e, i) => (c.contact[i === 0 ? "email" : `email_${i + 1}`] = e));
      const a = p.address?.[0];
      const place = [a?.city, a?.country || a?.region].filter(Boolean).join(", ");
      if (place) c.location = place;
      return c;
    });
}
