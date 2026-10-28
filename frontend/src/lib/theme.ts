import { cookies } from "next/headers";
import { parseAccent, parseTheme, type Accent, type Theme } from "./appearance";

export type { Accent, Theme } from "./appearance";

/**
 * The visitor's theme: light unless they picked dark or "system". The choice is kept in a cookie, so
 * the server draws the page in the right colors and there's no flash of the wrong theme.
 */
export async function getTheme(): Promise<Theme> {
  return parseTheme((await cookies()).get("theme")?.value);
}

/** The visitor's accent color, cobalt unless they picked another. */
export async function getAccent(): Promise<Accent> {
  return parseAccent((await cookies()).get("accent")?.value);
}
