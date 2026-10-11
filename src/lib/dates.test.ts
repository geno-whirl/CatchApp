import { describe, expect, it } from "vitest";
import {
  cadenceToDays,
  daysUntilBirthday,
  formatDaysAgo,
  parseBirthday,
  parseDate,
  upcomingAge,
} from "./dates";

const day = (iso: string) => parseDate(iso)!;

describe("parseDate", () => {
  it("parses valid ISO dates and rejects impossible ones", () => {
    expect(parseDate("2026-02-28")?.getDate()).toBe(28);
    expect(parseDate("2026-02-29")).toBeNull();
    expect(parseDate("2026-13-01")).toBeNull();
    expect(parseDate("10/11/2026")).toBeNull();
  });
});

describe("parseBirthday", () => {
  it("accepts full dates and month-day only", () => {
    expect(parseBirthday("1991-10-19")).toEqual({ year: 1991, month: 10, day: 19 });
    expect(parseBirthday("06-22")).toEqual({ month: 6, day: 22 });
    expect(parseBirthday("--06-22")).toEqual({ month: 6, day: 22 });
    expect(parseBirthday("02-29")).toEqual({ month: 2, day: 29 });
    expect(parseBirthday("02-30")).toBeNull();
  });
});

describe("daysUntilBirthday", () => {
  const today = day("2026-10-11");

  it("counts forward, wrapping into next year", () => {
    expect(daysUntilBirthday({ month: 10, day: 11 }, today)).toBe(0);
    expect(daysUntilBirthday({ month: 10, day: 19 }, today)).toBe(8);
    expect(daysUntilBirthday({ month: 10, day: 10 }, today)).toBe(364);
  });

  it("celebrates Feb 29 on Feb 28 in non-leap years", () => {
    expect(daysUntilBirthday({ month: 2, day: 29 }, day("2027-02-27"))).toBe(1);
    expect(daysUntilBirthday({ month: 2, day: 29 }, day("2028-02-28"))).toBe(1);
  });

  it("works across daylight-saving changes", () => {
    expect(daysUntilBirthday({ month: 11, day: 2 }, day("2026-10-11"))).toBe(22);
    expect(daysUntilBirthday({ month: 4, day: 1 }, day("2026-03-01"))).toBe(31);
  });
});

describe("upcomingAge", () => {
  it("is the age they turn next, when the year is known", () => {
    expect(upcomingAge({ year: 1991, month: 10, day: 19 }, day("2026-10-11"))).toBe(35);
    expect(upcomingAge({ year: 1991, month: 1, day: 5 }, day("2026-10-11"))).toBe(36);
    expect(upcomingAge({ month: 1, day: 5 }, day("2026-10-11"))).toBeUndefined();
  });
});

describe("cadenceToDays", () => {
  it("understands names and shorthand", () => {
    expect(cadenceToDays("Monthly")).toBe(30);
    expect(cadenceToDays("6w")).toBe(42);
    expect(cadenceToDays("45d")).toBe(45);
    expect(cadenceToDays("2m")).toBe(60);
    expect(cadenceToDays("0d")).toBeNull();
    expect(cadenceToDays("sometimes")).toBeNull();
  });
});

describe("formatDaysAgo", () => {
  it("reads naturally", () => {
    expect(formatDaysAgo(0)).toBe("today");
    expect(formatDaysAgo(1)).toBe("yesterday");
    expect(formatDaysAgo(9)).toBe("9 days ago");
    expect(formatDaysAgo(21)).toBe("3 weeks ago");
    expect(formatDaysAgo(100)).toBe("3 months ago");
    expect(formatDaysAgo(400)).toBe("1 year ago");
  });
});
