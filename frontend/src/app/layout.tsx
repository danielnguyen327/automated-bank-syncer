import type { Metadata } from "next";
import { IBM_Plex_Mono, IBM_Plex_Sans } from "next/font/google";
import { getAccent, getTheme } from "@/lib/theme";
import "./globals.css";

const plexSans = IBM_Plex_Sans({
  variable: "--font-plex-sans",
  subsets: ["latin"],
  weight: ["400", "500", "600"],
});
const plexMono = IBM_Plex_Mono({
  variable: "--font-plex-mono",
  subsets: ["latin"],
  weight: ["400", "500"],
});

export const metadata: Metadata = {
  title: { default: "LedgerSync", template: "%s · LedgerSync" },
  description: "All your bank accounts and credit cards in one place, synced automatically.",
};

export default async function RootLayout({ children }: LayoutProps<"/">) {
  const [theme, accent] = await Promise.all([getTheme(), getAccent()]);
  return (
    <html
      lang="en"
      data-theme={theme === "system" ? undefined : theme}
      data-accent={accent}
      className={`${plexSans.variable} ${plexMono.variable} h-full antialiased`}
    >
      <body className="min-h-full font-sans">{children}</body>
    </html>
  );
}
