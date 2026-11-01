import Link from "next/link";
import type { Theme } from "@/lib/theme";
import { AuthForm } from "./auth-form";
import { Brand } from "./brand";
import { BankIcon, LockIcon, SyncIcon, TrendIcon } from "./icons";
import { ThemeToggle } from "./theme-toggle";

const features = [
  {
    icon: BankIcon,
    title: "Every account in one place",
    text: "Checking, savings and credit cards from any bank Plaid supports.",
  },
  {
    icon: SyncIcon,
    title: "Syncs on its own",
    text: "New transactions come in every day without you doing anything.",
  },
  {
    icon: TrendIcon,
    title: "Insights you can trust",
    text: "Claude writes the summary. The app calculates every number in it.",
  },
];

/** Screen A1: what LedgerSync does on the left, the sign-in form on the right. */
export function SignInScreen({ theme, unavailable }: { theme: Theme; unavailable: boolean }) {
  return (
    <div className="flex min-h-screen">
      <aside className="hidden w-[min(600px,45%)] shrink-0 flex-col gap-10 border-r border-line bg-surface px-14 py-10 lg:flex">
        <Brand />
        <div className="flex grow flex-col justify-center gap-7">
          <div className="flex flex-col gap-3">
            <h1 className="max-w-[16ch] text-4xl leading-tight font-semibold tracking-tight">
              All your accounts, one clear picture.
            </h1>
            <p className="max-w-[44ch] text-base leading-relaxed text-ink-2">
              LedgerSync links your bank accounts and credit cards, keeps them in sync on its own,
              and tells you what changed.
            </p>
          </div>
          <ul className="flex flex-col gap-4">
            {features.map(({ icon: Icon, title, text }) => (
              <li key={title} className="flex items-start gap-3.5">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-muted text-accent">
                  <Icon className="size-[18px]" />
                </span>
                <div className="flex flex-col gap-0.5">
                  <span className="text-[15px] font-semibold">{title}</span>
                  <span className="text-sm text-ink-2">{text}</span>
                </div>
              </li>
            ))}
          </ul>
          <div className="flex max-w-[440px] flex-col gap-2 rounded-xl border border-line bg-page px-[18px] py-4">
            <div className="flex items-center justify-between gap-3">
              <span className="text-[13px] font-semibold">What changed this month</span>
              <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] font-medium text-muted-ink">
                AI summary
              </span>
            </div>
            <span className="text-[15px]">
              Dining is up <strong className="font-semibold">38%</strong> from last month.
            </span>
            <span className="text-xs text-ink-3">Example from the demo account</span>
          </div>
        </div>
      </aside>

      <div className="flex min-w-0 grow flex-col px-4 pt-3 pb-10 sm:px-8 lg:pt-6">
        <header className="flex items-center justify-between gap-3 lg:justify-end">
          <div className="lg:hidden">
            <Brand />
          </div>
          <ThemeToggle initialTheme={theme} />
        </header>

        <main className="flex grow flex-col items-center gap-4 py-6 lg:justify-center">
          <div className="flex w-full max-w-[420px] flex-col gap-1.5 lg:hidden">
            <h1 className="text-[26px] leading-tight font-semibold tracking-tight">
              All your accounts, one clear picture.
            </h1>
            <p className="text-[15px] text-ink-2">
              Sign in to see your balances, spending and what changed.
            </p>
          </div>
          {unavailable && (
            <p
              role="status"
              className="w-full max-w-[420px] rounded-lg border border-line bg-surface px-4 py-3 text-sm text-ink-2"
            >
              LedgerSync can’t reach its server right now, so signing in may not work. Try again in
              a minute.
            </p>
          )}
          <AuthForm />
          <p className="flex w-full max-w-[420px] items-start gap-2 text-[13px] text-ink-3">
            <LockIcon className="mt-0.5 size-4 shrink-0" />
            <span>
              Bank logins happen in Plaid’s window. LedgerSync never sees your bank password.{" "}
              <Link href="/privacy" className="font-medium text-accent hover:underline">
                How LedgerSync handles your data
              </Link>
            </span>
          </p>
        </main>
      </div>
    </div>
  );
}
