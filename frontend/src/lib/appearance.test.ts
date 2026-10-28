import { describe, expect, it } from "vitest";
import { parseAccent, parseTheme } from "./appearance";

describe("parseTheme", () => {
  it("reads a saved choice and falls back to light", () => {
    expect(parseTheme("dark")).toBe("dark");
    expect(parseTheme("system")).toBe("system");
    expect(parseTheme(undefined)).toBe("light");
    expect(parseTheme("purple")).toBe("light");
  });
});

describe("parseAccent", () => {
  it("reads a saved accent and falls back to cobalt", () => {
    expect(parseAccent("rose")).toBe("rose");
    expect(parseAccent(undefined)).toBe("cobalt");
    expect(parseAccent("neon")).toBe("cobalt");
  });
});
