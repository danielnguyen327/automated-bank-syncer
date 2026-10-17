# LedgerSync

Links bank accounts and credit cards through Plaid, keeps transactions in sync on a schedule,
and uses Claude to categorize spending and explain what changed.

- `backend/`: Java 25 + Spring Boot, PostgreSQL (Flyway), Plaid, Claude
- `frontend/`: Next.js + TypeScript, which forwards `/api/*` to the backend

## Run it locally (needs Java 25, Node 24 and Docker)

```bash
cd backend && ./mvnw spring-boot:run   # starts Postgres automatically; API on :8080
cd frontend && npm install && npm run dev   # in a second terminal; web on :3000
```

## Checks

`cd backend && ./mvnw verify` · `cd frontend && npm run lint && npm run typecheck && npm test && npm run build`

## Roadmap

### Built

- [x] Email and password sign-in, with sessions stored in Postgres and CSRF protection
- [x] Connect and disconnect banks with Plaid Link; delete account
- [x] Transaction sync with Plaid's cursor: safe to retry, never creates duplicates

### Next

- [ ] Automatic sync every 3 hours, balance refresh, daily balance snapshots, sync history, reconnect detection
- [ ] Categories from Claude (it sees only merchant, amount and account type), with rules as a fallback
- [ ] Monthly insights: the app calculates every number, Claude writes the sentences
- [ ] Dashboard, appearance settings (theme and accent color), reconnect flow
- [ ] Google and GitHub sign-in, Playwright end-to-end tests, security checklist, privacy page
- [ ] Deploy

### Real banks, for the owner only

The public demo stays on Plaid's Sandbox, with made-up data. Real bank connections are only for the
owner's own accounts, so real data is never shown to anyone else.

**Plaid**

- [ ] Apply for Production access (use-case profile and security questionnaire)
- [ ] Register for banks that use OAuth sign-in, and add the OAuth redirect page to the frontend
- [ ] Store which Plaid environment each bank belongs to; use the Production client only for allowlisted
      accounts (`REAL_BANK_EMAILS`) and Sandbox for everyone else
- [ ] Verify the signature of every Plaid webhook, if webhooks are added

**Accounts and secrets**

- [ ] Two-factor login on GitHub, the hosting provider, the Plaid dashboard, the database host and email
- [ ] A new production encryption key and the Plaid Production secret, stored only in the host's settings
- [ ] Move the encryption key to a key management service and support key rotation (stored tokens
      already start with `v1:` for this)

**Network**

- [ ] Only the frontend is public; the backend and Postgres are reachable only on the private network
- [ ] HTTPS only, with HSTS, a Content-Security-Policy, and secure, HTTP-only cookies

**Sign-in**

- [ ] Limit and slow down repeated sign-in attempts
- [ ] Two-factor sign-in with an authenticator app or a passkey
- [ ] Email verification, and password reset with links that expire
- [ ] Ask for the password again before connecting a bank or deleting the account

**Already in place**

- [x] Read-only access: the Transactions product only, so no account or routing numbers and no moving money
- [x] Plaid access tokens encrypted with AES-256-GCM, never sent to the browser or written to logs
- [x] Every query is limited to the signed-in user's own data
- [x] Disconnect and Delete my account revoke access at Plaid
- [x] Passwords hashed with BCrypt

**Monitoring and upkeep**

- [ ] Dependabot, CodeQL and secret scanning on GitHub
- [ ] An audit log of sign-ins, bank connections, disconnections and account deletions
- [ ] Alerts on error spikes and repeated failed sign-ins
- [ ] Keep only what's needed, and delete old transactions on a schedule
- [ ] An incident plan: rotate the Plaid secret and the encryption key, then revoke every bank at Plaid