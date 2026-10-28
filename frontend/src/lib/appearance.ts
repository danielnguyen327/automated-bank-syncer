/** "system" follows the device's own light or dark setting. */
export const THEMES = ["light", "dark", "system"] as const;
export const ACCENTS = ["cobalt", "teal", "violet", "rose", "graphite"] as const;

export type Theme = (typeof THEMES)[number];
export type Accent = (typeof ACCENTS)[number];

/** Light unless the cookie says otherwise. Light is the default. */
export function parseTheme(value: string | undefined): Theme {
  return THEMES.find((theme) => theme === value) ?? "light";
}

export function parseAccent(value: string | undefined): Accent {
  return ACCENTS.find((accent) => accent === value) ?? "cobalt";
}

/** Switches the page's colors right away and remembers the choice for the next page load. */
export function applyTheme(theme: Theme) {
  if (theme === "system") delete document.documentElement.dataset.theme;
  else document.documentElement.dataset.theme = theme;
  remember("theme", theme);
}

export function applyAccent(accent: Accent) {
  document.documentElement.dataset.accent = accent;
  remember("accent", accent);
}

function remember(name: string, value: string) {
  document.cookie = `${name}=${value}; path=/; max-age=31536000; samesite=lax`;
}
