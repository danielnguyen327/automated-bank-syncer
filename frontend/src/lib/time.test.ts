import { describe, expect, it } from "vitest";
import { timeAgo } from "./time";

const now = new Date("2026-10-10T12:00:00Z");

describe("timeAgo", () => {
  it("says just now for the last minute", () => {
    expect(timeAgo("2026-10-10T11:59:30Z", now)).toBe("just now");
  });
  it("counts minutes, then hours", () => {
    expect(timeAgo("2026-10-10T11:55:00Z", now)).toBe("5 minutes ago");
    expect(timeAgo("2026-10-10T09:00:00Z", now)).toBe("3 hours ago");
  });
  it("says yesterday, then counts days", () => {
    expect(timeAgo("2026-10-09T10:00:00Z", now)).toBe("yesterday");
    expect(timeAgo("2026-10-06T12:00:00Z", now)).toBe("4 days ago");
  });
});
