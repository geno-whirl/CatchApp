import type { Person } from "./person";

function find(person: Person, pattern: RegExp): string | undefined {
  const entry = Object.entries(person.contact).find(([key]) => pattern.test(key));
  return entry?.[1];
}

const digits = (phone: string) => phone.replace(/[^\d+]/g, "");

export interface ContactLinks {
  call?: string;
  sms?: string;
  whatsapp?: string;
  email?: string;
}

export function contactLinks(person: Person): ContactLinks {
  const phone = find(person, /^(phone|mobile|cell)/i);
  const whatsapp = find(person, /^whatsapp/i) ?? phone;
  const email = find(person, /^e-?mail/i);
  return {
    call: phone ? `tel:${digits(phone)}` : undefined,
    sms: phone ? `sms:${digits(phone)}` : undefined,
    // wa.me needs the full international number without "+" or spaces.
    whatsapp: whatsapp && /^\+/.test(whatsapp.trim()) ? `https://wa.me/${digits(whatsapp).replace(/^\+/, "")}` : undefined,
    email: email ? `mailto:${email}` : undefined,
  };
}

export function contactHref(key: string, value: string): string | undefined {
  const k = key.toLowerCase();
  if (/^https?:\/\//.test(value)) return value;
  if (k.includes("email") || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return `mailto:${value}`;
  if (/phone|mobile|cell/.test(k)) return `tel:${digits(value)}`;
  if (k === "instagram") return `https://www.instagram.com/${value.replace(/^@/, "")}`;
  if (k === "whatsapp" && /^\+/.test(value)) return `https://wa.me/${digits(value).replace(/^\+/, "")}`;
  return undefined;
}
