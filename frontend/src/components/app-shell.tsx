import Link from "next/link";
import type { ReactNode } from "react";
import type { User } from "@/lib/api";
import type { Theme } from "@/lib/theme";
import { Brand } from "./brand";
import { BankIcon, GridIcon, ListIcon, SettingsIcon } from "./icons";
import { SignOutButton } from "./sign-out-button";
import { ThemeToggle } from "./theme-toggle";

const sections = [
  { id: "dashboard", href: "/", label: "Dashboard", Icon: GridIcon },
  { id: "transactions", href: "/transactions", label: "Transactions", Icon: ListIcon },
  { id: "accounts", href: "/accounts", label: "Accounts", Icon: BankIcon },
  { id: "settings", href: "/settings", label: "Settings", Icon: SettingsIcon },
] as const;

export type Section = (typeof sections)[number]["id"];

/**
 * The signed-in app's frame: a sidebar on wide screens, and on phones a top bar with the sections in
 * a row underneath.
 */
export function AppShell({
  user,
  theme,
  current,
  children,
}: {
  user: User;
  theme: Theme;
  current: Section;
  children: ReactNode;
}) {
  return (
    <div className="flex min-h-screen flex-col lg:flex-row">
      <aside className="flex flex-col gap-3 border-b border-line bg-surface px-4 pt-3 pb-2 lg:sticky lg:top-0 lg:h-screen lg:w-60 lg:shrink-0 lg:gap-6 lg:border-r lg:border-b-0 lg:px-3 lg:py-5">
        <div className="flex items-center justify-between gap-3 lg:px-2">
          <Brand />
          <div className="flex items-center gap-2 lg:hidden">
            <ThemeToggle initialTheme={theme} />
            <SignOutButton />
          </div>
        </div>
        <nav
          aria-label="Sections"
          className="-mx-1 flex gap-1 overflow-x-auto px-1 lg:mx-0 lg:flex-col lg:overflow-visible lg:px-0"
        >
          {sections.map(({ id, href, label, Icon }) => (
            <Link
              key={id}
              href={href}
              aria-current={current === id ? "page" : undefined}
              className="group flex h-10 shrink-0 items-center gap-2.5 rounded-lg px-3 text-sm font-medium whitespace-nowrap text-ink-2 hover:bg-muted hover:text-ink aria-[current=page]:bg-muted aria-[current=page]:text-ink"
            >
              <Icon className="size-[18px] group-aria-[current=page]:text-accent" />
              {label}
            </Link>
          ))}
        </nav>
        <div className="mt-auto hidden flex-col gap-3 lg:flex">
          <div className="flex min-w-0 flex-col rounded-xl border border-line px-3 py-2.5">
            <span className="truncate text-sm font-semibold">{user.name}</span>
            <span className="truncate text-xs text-ink-3">{user.email}</span>
          </div>
          <div className="flex items-center gap-2">
            <ThemeToggle initialTheme={theme} />
            <SignOutButton />
          </div>
        </div>
      </aside>
      <main className="mx-auto flex w-full max-w-6xl min-w-0 flex-col gap-8 px-4 py-8 sm:px-6 lg:py-10">
        {children}
      </main>
    </div>
  );
}

/** A page's title row, with room for a button on the right. */
export function PageTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <h1 className="text-[28px] leading-tight font-semibold tracking-tight">{children}</h1>
      {action}
    </div>
  );
}

/** Shown instead of a page's content when the backend couldn't be reached. */
export function LoadError({ what }: { what: string }) {
  return (
    <p
      role="status"
      className="rounded-lg border border-line bg-surface px-4 py-3 text-sm text-ink-2"
    >
      Couldn’t load your {what} right now. Reload the page to try again.
    </p>
  );
}
