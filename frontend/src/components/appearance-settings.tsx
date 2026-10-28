"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { ACCENTS, applyAccent, applyTheme, type Accent, type Theme } from "@/lib/appearance";

const themeLabels: Record<Theme, string> = { light: "Light", dark: "Dark", system: "System" };

/** Theme (light, dark, or follow the device) and accent color. Changes show right away. */
export function AppearanceSettings({ theme, accent }: { theme: Theme; accent: Accent }) {
  const router = useRouter();
  const [chosenTheme, setChosenTheme] = useState(theme);
  const [chosenAccent, setChosenAccent] = useState(accent);

  function pickTheme(next: Theme) {
    applyTheme(next);
    setChosenTheme(next);
    router.refresh(); // so the theme button in the sidebar matches
  }

  function pickAccent(next: Accent) {
    applyAccent(next);
    setChosenAccent(next);
  }

  return (
    <section
      aria-labelledby="appearance-title"
      className="flex max-w-2xl flex-col gap-4 rounded-2xl border border-line bg-surface p-5"
    >
      <h2 id="appearance-title" className="text-[15px] font-semibold">
        Appearance
      </h2>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-sm font-medium">Theme</p>
          <p className="text-xs text-ink-3">System follows your device’s setting.</p>
        </div>
        <div
          role="group"
          aria-label="Theme"
          className="flex gap-0.5 rounded-lg border border-field-line bg-muted p-0.5"
        >
          {(Object.keys(themeLabels) as Theme[]).map((option) => (
            <button
              key={option}
              type="button"
              aria-pressed={chosenTheme === option}
              onClick={() => pickTheme(option)}
              className="h-8 rounded-md px-3 text-[13px] font-medium text-ink-2 aria-pressed:bg-surface aria-pressed:text-ink aria-pressed:shadow-sm"
            >
              {themeLabels[option]}
            </button>
          ))}
        </div>
      </div>
      <div className="flex flex-col gap-3 border-t border-line pt-4">
        <div>
          <p className="text-sm font-medium">Accent color</p>
          <p className="text-xs text-ink-3">Used for buttons, charts and highlights.</p>
        </div>
        <div role="group" aria-label="Accent color" className="flex flex-wrap gap-2">
          {ACCENTS.map((option) => (
            <button
              key={option}
              type="button"
              aria-pressed={chosenAccent === option}
              onClick={() => pickAccent(option)}
              className="flex h-9 items-center gap-2 rounded-full border border-field-line bg-surface py-0 pr-3 pl-1.5 text-[13px] font-medium capitalize hover:bg-muted aria-pressed:border-ink aria-pressed:ring-1 aria-pressed:ring-ink"
            >
              <span
                data-swatch={option}
                aria-hidden="true"
                className="size-6 rounded-full bg-[linear-gradient(90deg,var(--accent-light)_50%,var(--accent-dark)_50%)]"
              />
              {option}
            </button>
          ))}
        </div>
      </div>
    </section>
  );
}
