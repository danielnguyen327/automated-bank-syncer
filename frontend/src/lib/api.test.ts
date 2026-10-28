import { afterEach, describe, expect, it, vi } from "vitest";
import {
  ApiError,
  createLinkToken,
  describeRun,
  describeSync,
  describeSyncAll,
  disconnectBank,
  errorMessage,
  listTransactions,
  messageFor,
  readCookie,
  signIn,
  syncNewBank,
  transactionsQuery,
} from "./api";

describe("readCookie", () => {
  it("finds one cookie among several", () => {
    expect(readCookie("theme=dark; XSRF-TOKEN=abc-123; other=x", "XSRF-TOKEN")).toBe("abc-123");
  });
  it("returns undefined when the cookie is missing", () => {
    expect(readCookie("theme=dark", "XSRF-TOKEN")).toBeUndefined();
  });
});

describe("errorMessage", () => {
  it("uses the backend's problem detail", async () => {
    const response = Response.json({ detail: "Email or password is incorrect." }, { status: 401 });
    expect(await errorMessage(response)).toBe("Email or password is incorrect.");
  });
  it("falls back to a general message when there is no detail", async () => {
    const response = new Response("Internal Server Error", { status: 500 });
    expect(await errorMessage(response)).toBe("Something went wrong. Try again in a moment.");
  });
});

describe("signIn", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("gets a CSRF cookie first, then sends it back in a header", async () => {
    const browser = { cookie: "" };
    const fetchMock = vi.fn(async (path: string) => {
      if (path === "/api/auth/csrf") browser.cookie = "XSRF-TOKEN=abc-123";
      return new Response(null, { status: 204 });
    });
    vi.stubGlobal("document", browser);
    vi.stubGlobal("fetch", fetchMock);

    await signIn("sam@example.com", "correct horse battery");

    expect(fetchMock).toHaveBeenNthCalledWith(1, "/api/auth/csrf");
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      "/api/auth/login",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ "X-XSRF-TOKEN": "abc-123" }),
      }),
    );
  });

  it("throws the backend's message when sign-in fails", async () => {
    vi.stubGlobal("document", { cookie: "XSRF-TOKEN=abc-123" });
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({ detail: "Email or password is incorrect." }, { status: 401 }),
      ),
    );

    await expect(signIn("sam@example.com", "wrong")).rejects.toMatchObject({
      status: 401,
      message: "Email or password is incorrect.",
    });
  });
});

describe("bank requests", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("returns the link token the backend created", async () => {
    vi.stubGlobal("document", { cookie: "XSRF-TOKEN=abc-123" });
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ linkToken: "link-sandbox-42" })),
    );

    expect(await createLinkToken()).toBe("link-sandbox-42");
  });

  it("disconnects a bank with a DELETE that carries the CSRF token", async () => {
    vi.stubGlobal("document", { cookie: "XSRF-TOKEN=abc-123" });
    const fetchMock = vi.fn(async () => new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await disconnectBank("bank-1");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/banks/bank-1",
      expect.objectContaining({
        method: "DELETE",
        headers: expect.objectContaining({ "X-XSRF-TOKEN": "abc-123" }),
      }),
    );
  });
});

describe("messageFor", () => {
  it("shows the backend's sentence for an ApiError", () => {
    expect(messageFor(new ApiError(404, "That bank isn't connected."))).toBe(
      "That bank isn't connected.",
    );
  });
  it("explains a network failure in plain words", () => {
    expect(messageFor(new TypeError("Failed to fetch"))).toBe(
      "Can’t reach LedgerSync right now. Try again in a moment.",
    );
  });
});

describe("syncNewBank", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("tries again while Plaid is still gathering a new bank's transactions", async () => {
    vi.stubGlobal("document", { cookie: "XSRF-TOKEN=abc-123" });
    const notReady = { ready: false, added: 0, updated: 0, removed: 0 };
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(Response.json(notReady))
      .mockResolvedValueOnce(Response.json(notReady))
      .mockResolvedValueOnce(Response.json({ ready: true, added: 18, updated: 0, removed: 0 }));
    vi.stubGlobal("fetch", fetchMock);
    const pause = vi.fn(async () => {});

    const result = await syncNewBank("bank-1", pause);

    expect(result.added).toBe(18);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(pause).toHaveBeenCalledTimes(2);
  });
});

describe("describeSync", () => {
  it("lists only what changed", () => {
    expect(describeSync({ ready: true, added: 3, updated: 0, removed: 1 })).toBe(
      "Synced: 3 new, 1 removed.",
    );
  });
  it("says when nothing changed", () => {
    expect(describeSync({ ready: true, added: 0, updated: 0, removed: 0 })).toBe(
      "No new transactions.",
    );
  });
  it("explains when Plaid isn't ready yet", () => {
    expect(describeSync({ ready: false, added: 0, updated: 0, removed: 0 })).toBe(
      "Plaid is still gathering this bank’s transactions. Try again in a minute.",
    );
  });
});

describe("describeSyncAll", () => {
  const changed = { ready: true, added: 3, updated: 0, removed: 1 };
  const nothing = { ready: true, added: 0, updated: 0, removed: 0 };

  it("adds up the changes across banks", () => {
    expect(
      describeSyncAll([
        { bankId: "a", bankName: "Tartan Bank", result: changed, error: null },
        { bankId: "b", bankName: "First Platypus Bank", result: nothing, error: null },
      ]),
    ).toBe("Synced 2 banks: 3 new, 1 removed.");
  });
  it("names each bank that failed after what worked", () => {
    expect(
      describeSyncAll([
        {
          bankId: "a",
          bankName: "First Platypus Bank",
          result: null,
          error: "First Platypus Bank needs you to sign in again.",
        },
        { bankId: "b", bankName: "Tartan Bank", result: nothing, error: null },
      ]),
    ).toBe(
      "Synced 1 of 2 banks. No new transactions. First Platypus Bank needs you to sign in again.",
    );
  });
});

describe("describeRun", () => {
  const run = {
    startedAt: "2026-10-18T09:17:00Z",
    triggeredBy: "schedule",
    status: "succeeded",
    added: 0,
    updated: 0,
    removed: 0,
    error: null,
  };

  it("shows what changed, or that nothing did", () => {
    expect(describeRun({ ...run, added: 2, updated: 1 })).toBe("2 new, 1 updated");
    expect(describeRun(run)).toBe("No new transactions");
  });
  it("shows why a sync failed", () => {
    expect(
      describeRun({ ...run, status: "failed", error: "The bank needs you to sign in again." }),
    ).toBe("The bank needs you to sign in again.");
  });
});

describe("transactionsQuery", () => {
  it("leaves out filters that aren't set", () => {
    expect(transactionsQuery({ accountType: "", category: "", search: "  " }, 0)).toBe(
      "page=0&size=50",
    );
  });
  it("sends every filter that is set", () => {
    expect(
      transactionsQuery({ accountType: "credit", category: "Food & drink", search: " uber " }, 2),
    ).toBe("accountType=credit&category=Food+%26+drink&search=uber&page=2&size=50");
  });
});

describe("listTransactions", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("asks for the right page and reads the answer", async () => {
    const fetchMock = vi.fn(async () => Response.json({ transactions: [], hasMore: true }));
    vi.stubGlobal("fetch", fetchMock);

    const page = await listTransactions({ accountType: "", category: "", search: "" }, 1);

    expect(page.hasMore).toBe(true);
    expect(fetchMock).toHaveBeenCalledWith("/api/transactions?page=1&size=50");
  });
});
