const relative = new Intl.RelativeTimeFormat("en", { numeric: "auto" });

/** How long ago a moment was, in words: "just now", "5 minutes ago", "yesterday". */
export function timeAgo(iso: string, now: Date): string {
  const seconds = (new Date(iso).getTime() - now.getTime()) / 1000;
  if (seconds > -60) return "just now";
  const minutes = Math.round(seconds / 60);
  if (minutes > -60) return relative.format(minutes, "minute");
  const hours = Math.round(minutes / 60);
  if (hours > -24) return relative.format(hours, "hour");
  return relative.format(Math.round(hours / 24), "day");
}
