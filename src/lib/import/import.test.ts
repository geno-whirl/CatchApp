import { zipSync, strToU8 } from "fflate";
import { describe, expect, it } from "vitest";
import { parsePerson } from "../person";
import { readImportFiles } from "./files";
import { planImport } from "./match";
import { fixMojibake, parseMetaExport } from "./meta";
import { parseVCardDate, parseVCards } from "./vcard";
import { chatTitle, parseWhatsApp } from "./whatsapp";

describe("vCard", () => {
  it("reads an iPhone export", () => {
    const vcf = [
      "BEGIN:VCARD",
      "VERSION:3.0",
      "N:Patel;Maya;;;",
      "FN:Maya Patel",
      "NICKNAME:May",
      "ORG:Acme\\, Inc.;",
      "item1.TEL;type=CELL;type=VOICE;type=pref:+1 206 555 0142",
      "TEL;type=WORK;type=VOICE:+1 206 555 0100",
      "EMAIL;type=INTERNET;type=HOME;type=pref:maya@example.com",
      "item2.ADR;type=HOME;type=pref:;;1 Pine St;Seattle;WA;98101;United States",
      "BDAY;X-APPLE-OMIT-YEAR=1604:1604-10-19",
      "item3.X-ABRELATEDNAMES;type=pref:Arjun",
      "item3.X-ABLabel:_$!<Spouse>!$_",
      "X-SOCIALPROFILE;type=instagram;x-user=maya.climbs:x-apple:maya.climbs",
      "NOTE:Loves matcha.\\nAllergic to cats.",
      "END:VCARD",
      "BEGIN:VCARD",
      "VERSION:3.0",
      "N:;;;;",
      "END:VCARD",
    ].join("\r\n");
    const { candidates, notes } = parseVCards(vcf);
    expect(candidates).toHaveLength(1);
    expect(candidates[0]).toMatchObject({
      name: "Maya Patel",
      nickname: "May",
      birthday: "10-19",
      location: "Seattle, United States",
      contact: { phone: "+1 206 555 0142", phone_work: "+1 206 555 0100", email: "maya@example.com", instagram: "maya.climbs" },
      family: [{ name: "Arjun", relation: "spouse" }],
      notes: "Works at Acme, Inc.\n\nLoves matcha.\nAllergic to cats.",
    });
    expect(notes).toEqual(["Skipped 1 contact without a name."]);
  });

  it("reads Google and old Android (2.1, quoted-printable) exports", () => {
    const vcf = [
      "BEGIN:VCARD",
      "VERSION:2.1",
      "N;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Bront=C3=AB;Zo=C3=AB;;;",
      "FN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Zo=C3=AB Bront=C3=AB",
      "TEL;CELL:+44 7700 900123",
      "BDAY:19880304",
      "CATEGORIES:myContacts,Book Club",
      "END:VCARD",
      "BEGIN:VCARD",
      "VERSION:4.0",
      "FN:Sam",
      "BDAY:--0229",
      "RELATED;TYPE=child:text:Robin",
      "END:VCARD",
    ].join("\n");
    const { candidates } = parseVCards(vcf);
    expect(candidates[0]).toMatchObject({
      name: "Zoë Brontë",
      birthday: "1988-03-04",
      contact: { phone: "+44 7700 900123" },
      tags: ["book-club"],
    });
    expect(candidates[1]).toMatchObject({ name: "Sam", birthday: "02-29", family: [{ name: "Robin", relation: "child" }] });
  });

  it("parses vCard dates", () => {
    expect(parseVCardDate("1991-10-19T00:00:00Z")).toBe("1991-10-19");
    expect(parseVCardDate("--10-19")).toBe("10-19");
    expect(parseVCardDate("nonsense")).toBeUndefined();
  });
});

describe("WhatsApp", () => {
  it("reads iPhone exports (day first, seconds, invisible marks)", () => {
    const text = [
      "[03/02/2026, 09:15:02] Maya Patel: ‎Messages and calls are end-to-end encrypted.",
      "[03/02/2026, 09:15:02] Maya Patel: Hey!",
      "[13/09/2026, 21:40:10] Me Myself: See you soon",
      "multi-line continuation",
      "[14/09/2026, 08:00:00] Maya Patel: ‎image omitted",
    ].join("\n");
    const { candidates } = parseWhatsApp([{ name: "WhatsApp Chat - Maya Patel.zip/_chat.txt", text }]);
    expect(candidates).toEqual([
      expect.objectContaining({ name: "Maya Patel", lastContacted: "2026-09-14", detail: "4 WhatsApp messages" }),
    ]);
  });

  it("reads US Android exports (month first, AM/PM)", () => {
    const text = [
      "10/2/26, 9:05 PM - Messages and calls are end-to-end encrypted.",
      "10/2/26, 9:05 PM - +1 206 555 0199: Hi, it's Jo",
      "10/9/26, 12:30 AM - You: Hello",
    ].join("\n");
    const { candidates } = parseWhatsApp([{ name: "WhatsApp Chat with +1 206 555 0199.txt", text }]);
    expect(candidates).toEqual([
      expect.objectContaining({ name: "+1 206 555 0199", lastContacted: "2026-10-09", contact: { whatsapp: "+1 206 555 0199" } }),
    ]);
  });

  it("works out your own name across several chats and skips groups", () => {
    const chat = (other: string) => `01/10/2026, 10:00 - ${other}: hi\n02/10/2026, 11:00 - Alex Doe: hello`;
    const { candidates, notes } = parseWhatsApp([
      { name: "a.txt", text: chat("Sam Lee") },
      { name: "b.txt", text: chat("Robin Roe") },
      { name: "group.txt", text: `${chat("Sam Lee")}\n03/10/2026, 12:00 - Robin Roe: yo` },
    ]);
    expect(candidates.map((c) => c.name)).toEqual(["Sam Lee", "Robin Roe"]);
    expect(notes).toEqual(["Skipped group chat."]);
  });

  it("finds the chat name in localised file names", () => {
    expect(chatTitle("WhatsApp-Chat mit Jonas Weber.txt")).toBe("Jonas Weber");
    expect(chatTitle("Chat de WhatsApp con Ana.txt")).toBe("Ana");
    expect(chatTitle("notes.txt")).toBeUndefined();
  });
});

describe("Facebook & Instagram", () => {
  const thread = (participants: string[], times: number[]) =>
    JSON.stringify({ participants: participants.map((name) => ({ name })), messages: times.map((t) => ({ timestamp_ms: t, sender_name: participants[0] })) });

  it("fixes Meta's broken UTF-8", () => {
    expect(fixMojibake("ZoÃ«")).toBe("Zoë");
    expect(fixMojibake("Zoë 🎂")).toBe("Zoë 🎂");
  });

  it("reads Facebook friends and 1:1 Messenger threads", () => {
    const { candidates, notes } = parseMetaExport(
      [
        { name: "fb.zip/connections/friends/your_friends.json", text: JSON.stringify({ friends_v2: [{ name: "ZoÃ« BrontÃ«", timestamp: 1356998400 }] }) },
        { name: "fb.zip/profile_information/profile_information.json", text: JSON.stringify({ profile_v2: { name: { full_name: "Alex Doe" } } }) },
        { name: "fb.zip/messages/inbox/zoebronte_123/message_1.json", text: thread(["Zoë Brontë", "Alex Doe"], [Date.UTC(2026, 8, 1, 12), Date.UTC(2026, 9, 2, 12)]) },
        { name: "fb.zip/messages/inbox/group_1/message_1.json", text: thread(["A", "B", "Alex Doe"], [1]) },
      ],
      "facebook",
    );
    expect(candidates).toEqual([
      expect.objectContaining({ name: "Zoë Brontë", howWeMet: "Facebook friends since 2013." }),
      expect.objectContaining({ name: "Zoë Brontë", lastContacted: "2026-10-02", detail: "2 Messenger messages" }),
    ]);
    expect(notes).toEqual(["Skipped 1 group conversation."]);
  });

  it("uses the thread folder to tell who's who", () => {
    const { candidates } = parseMetaExport(
      [{ name: "inbox/samlee_99/message_1.json", text: thread(["Sam Lee", "Alex Doe"], [Date.UTC(2026, 0, 5, 12)]) }],
      "facebook",
    );
    expect(candidates.map((c) => c.name)).toEqual(["Sam Lee"]);
  });

  it("reads Instagram mutual follows", () => {
    const following = { relationships_following: [
      { title: "maya.climbs", string_list_data: [{ href: "https://www.instagram.com/_u/maya.climbs", timestamp: 1 }] },
      { title: "", string_list_data: [{ href: "https://www.instagram.com/celebrity", value: "celebrity", timestamp: 1 }] },
    ] };
    const followers = [{ title: "", string_list_data: [{ href: "https://www.instagram.com/maya.climbs", value: "maya.climbs", timestamp: 1 }] }];
    const { candidates } = parseMetaExport(
      [
        { name: "ig/connections/followers_and_following/following.json", text: JSON.stringify(following) },
        { name: "ig/connections/followers_and_following/followers_1.json", text: JSON.stringify(followers) },
      ],
      "instagram",
    );
    expect(candidates).toEqual([
      expect.objectContaining({ name: "maya.climbs", contact: { instagram: "https://www.instagram.com/_u/maya.climbs" }, detail: "Mutual follow" }),
    ]);
  });
});

describe("planImport", () => {
  const maya = parsePerson(
    "maya-patel",
    "---\nname: Maya Patel\ncontact:\n  phone: \"+1 206 555 0142\"\nlast_contacted: 2026-08-01\n---\n",
  ).person!;
  const files = { "people/maya-patel.md": "---\nname: Maya Patel\ncontact:\n  phone: \"+1 206 555 0142\"\nlast_contacted: 2026-08-01\n---\n" };

  it("merges duplicates, matches by name or phone, and only proposes real changes", () => {
    const items = planImport(
      [
        { name: "May P", source: "contacts", contact: { phone: "206-555-0142", email: "maya@example.com" }, family: [], tags: [], birthday: "1991-10-19" },
        { name: "Maya Patel", source: "whatsapp", contact: {}, family: [], tags: [], lastContacted: "2026-07-01" },
        { name: "Zoë Brontë", source: "facebook", contact: {}, family: [], tags: [], howWeMet: "Facebook friends since 2013." },
        { name: "zoe bronte", source: "facebook", contact: {}, family: [], tags: [], lastContacted: "2026-10-02" },
      ],
      [maya],
      files,
    );
    expect(items.map((i) => [i.candidate.name, i.match?.id, i.changes])).toEqual([
      ["May P", "maya-patel", ["birthday", "email"]],
      ["Maya Patel", "maya-patel", []],
      ["Zoë Brontë", undefined, ["new"]],
    ]);
    expect(items[1].op).toBeNull();
    const created = items[2].op!;
    expect(created).toMatchObject({ type: "create", path: "people/zoe-bronte.md" });
    const zoe = parsePerson("zoe-bronte", (created as { content: string }).content);
    expect(zoe.issues).toEqual([]);
    expect(zoe.person).toMatchObject({ howWeMet: "Facebook friends since 2013.", lastContacted: new Date(2026, 9, 2) });
  });
});

describe("readImportFiles", () => {
  it("only extracts relevant files from zips", async () => {
    const zip = zipSync({
      "connections/friends/your_friends.json": strToU8("{}"),
      "media/photo.jpg": new Uint8Array(1000),
      "__MACOSX/._chat.txt": strToU8("junk"),
      "_chat.txt": strToU8("hi"),
    });
    const files = await readImportFiles([new File([zip], "export.zip")]);
    expect(files.map((f) => f.name).sort()).toEqual(["export.zip/_chat.txt", "export.zip/connections/friends/your_friends.json"]);
  });
});
