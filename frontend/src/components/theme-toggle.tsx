"use client";

import { useRouter } from "next/navigation";
import { useState, useSyncExternalStore } from "react";
import { applyTheme, type Theme } from "@/lib/appearance";
import { MoonIcon, SunIcon } from "./icons";

const DEVICE_DARK = "(prefers-color-scheme: dark)";

function watchDevice(onChange: () => void) {
  const query = window.matchMedia(DEVICE_DARK);
  query.addEventListener("change", onChange);
  return () => query.removeEventListener("change", onChange);
}

/** The device's own setting. The server can't know it, so it assumes light until the browser takes over. */
function useDeviceTheme(): "light" | "dark" {
  return useSyncExternalStore(
    watchDevice,
    () => (window.matchMedia(DEVICE_DARK).matches ? "dark" : "light"),
    () => "light",
  );
}

/** Switches between light and dark. On "system", it starts from whatever the device shows. */
export function ThemeToggle({ initialTheme }: { initialTheme: Theme }) {
  const router = useRouter();
  const [theme, setTheme] = useState(initialTheme);
  const device = useDeviceTheme();
  const shown = theme === "system" ? device : theme;
  const other = shown === "dark" ? "light" : "dark";
  const label = `Switch to ${other} theme`;

  function toggle() {
    applyTheme(other);
    setTheme(other);
    router.refresh(); // so Settings shows the new choice too
  }

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={label}
      title={label}
      className="flex size-11 items-center justify-center rounded-lg border border-field-line bg-surface text-ink hover:bg-muted"
    >
      {shown === "dark" ? (
        <SunIcon className="size-[18px]" />
      ) : (
        <MoonIcon className="size-[18px]" />
      )}
    </button>
  );
}
