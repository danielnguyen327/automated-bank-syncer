import type { Metadata } from "next";
import Link from "next/link";
import type { ReactNode } from "react";
import { Brand } from "@/components/brand";
import { ThemeToggle } from "@/components/theme-toggle";
import { getTheme } from "@/lib/theme";

export const metadata: Metadata = { title: "Privacy" };

const linkClass = "font-medium text-accent hover:underline";

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-lg font-semibold tracking-tight text-ink">{title}</h2>
      {children}
    </section>
  );
}

function List({ children }: { children: ReactNode }) {
  return <ul className="flex list-disc flex-col gap-2 pl-5 marker:text-ink-3">{children}</ul>;
}

export default async function PrivacyPage() {
  const theme = await getTheme();

  return (
    <div className="flex min-h-screen flex-col">
      <header className="flex items-center justify-between gap-3 border-b border-line bg-surface px-4 py-3 sm:px-8">
        <Link href="/" aria-label="LedgerSync home">
          <Brand />
        </Link>
        <ThemeToggle key={theme} initialTheme={theme} />
      </header>

      <main className="mx-auto flex w-full max-w-2xl flex-col gap-10 px-4 py-10 text-[15px] leading-relaxed text-ink-2 sm:px-6">
        <div className="flex flex-col gap-2">
          <h1 className="text-[28px] leading-tight font-semibold tracking-tight text-ink">
            Privacy
          </h1>
          <p className="text-sm text-ink-3">Last updated November 1, 2026</p>
          <p>
            LedgerSync shows your bank accounts, credit cards and spending in one place. This page
            explains what it stores, who else handles it, and how to delete it.
          </p>
        </div>

        <Section title="Test banks only, for now">
          <p>
            LedgerSync connects only to Plaid’s Sandbox, which has made-up banks, accounts and
            transactions. No real bank data is stored. If that changes, this page will say so first.
          </p>
        </Section>

        <Section title="What LedgerSync stores">
          <List>
            <li>
              Your name, email and password. The password is saved only as a bcrypt hash, so no one
              can read it.
            </li>
            <li>
              For each bank you connect: the bank’s name, and each account’s name, type, last four
              digits and balance.
            </li>
            <li>
              Your transactions: date, description, merchant, amount and category. Also your daily
              balances, to draw your net worth over time.
            </li>
            <li>
              The key Plaid gives LedgerSync for each bank, encrypted with AES-256-GCM before it’s
              saved.
            </li>
            <li>When each sync ran and how many transactions it changed.</li>
          </List>
        </Section>

        <Section title="What LedgerSync never sees">
          <List>
            <li>
              Your bank username and password. You type them into Plaid’s window, not into
              LedgerSync.
            </li>
            <li>
              Full account or routing numbers. LedgerSync asks Plaid only for read-only transaction
              data, and it can’t move money.
            </li>
          </List>
        </Section>

        <Section title="How it’s used">
          <p>
            Only to show you your balances, transactions and spending, and to keep them up to date
            by syncing every few hours. LedgerSync doesn’t sell your data, show ads, or use
            analytics or tracking.
          </p>
        </Section>

        <Section title="Who else handles it">
          <List>
            <li>
              <strong className="font-semibold text-ink">Plaid</strong> connects to your bank and
              sends LedgerSync your accounts and transactions. Plaid’s{" "}
              <a
                href="https://plaid.com/legal/#end-user-privacy-policy"
                className={linkClass}
                target="_blank"
                rel="noreferrer"
              >
                End User Privacy Policy
              </a>{" "}
              explains what Plaid does with your data.
            </li>
            <li>
              <strong className="font-semibold text-ink">Railway</strong> runs LedgerSync’s server
              and database. Its server logs can include your IP address.
            </li>
          </List>
          <p>No one else gets your data.</p>
        </Section>

        <Section title="Cookies">
          <p>LedgerSync uses four cookies, all needed for the app to work:</p>
          <List>
            <li>
              <code className="font-mono text-[13px] text-ink">SESSION</code> keeps you signed in.
            </li>
            <li>
              <code className="font-mono text-[13px] text-ink">XSRF-TOKEN</code> stops other
              websites from acting as you.
            </li>
            <li>
              <code className="font-mono text-[13px] text-ink">theme</code> and{" "}
              <code className="font-mono text-[13px] text-ink">accent</code> remember your colors.
            </li>
          </List>
          <p>There are no tracking or advertising cookies.</p>
        </Section>

        <Section title="Deleting your data">
          <p>LedgerSync keeps your data until you delete it:</p>
          <List>
            <li>
              <strong className="font-semibold text-ink">Disconnect a bank</strong> on the Accounts
              page. Plaid is told to stop sharing it, and its accounts, transactions and balances
              are deleted.
            </li>
            <li>
              <strong className="font-semibold text-ink">Delete your account</strong> in Settings.
              Every bank is disconnected at Plaid, all your data is deleted, and every device is
              signed out.
            </li>
          </List>
        </Section>

        <Section title="Keeping it safe">
          <List>
            <li>Every connection to LedgerSync uses HTTPS.</li>
            <li>Passwords are hashed and Plaid keys are encrypted.</li>
            <li>
              LedgerSync’s server and database can only be reached from inside its private network,
              not from the internet.
            </li>
          </List>
        </Section>

        <Section title="Questions">
          <p>
            If this page changes, the date at the top will too. For questions, open an issue on{" "}
            <a
              href="https://github.com/danielnguyen327/automated-bank-syncer/issues"
              className={linkClass}
              target="_blank"
              rel="noreferrer"
            >
              GitHub
            </a>
            .
          </p>
        </Section>

        <Link href="/" className={`${linkClass} text-sm`}>
          ← Back to LedgerSync
        </Link>
      </main>
    </div>
  );
}
