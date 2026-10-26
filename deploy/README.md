# Stage 21: trial deployment preparation

These files prepare a single-server trial. No hosting provider, domain, SMTP account, public deployment, or tester invitations have been created. The local Docker application remains separate. Choose those services before following the server steps below.

## CI

`.github/workflows/ci.yml` runs on pushes, pull requests, and manual dispatch. It uses Java 21, Node 22, PostgreSQL 17, read-only repository permissions, immutable action commits, and time limits. Three jobs check Java tests/JAR packaging, frontend lint/types/unit tests/production build, and isolated Docker workflows including captured email and backup restoration. Test reports are retained for seven days. No production secrets or deployment access are needed.

After committing and pushing Stage 21, inspect **Actions → CI** on GitHub. Once the first run passes, make **Backend tests**, **Frontend checks**, and **Docker integration** required checks in the repository's branch rules if you want merges to depend on them. The local checks do not prove a hosted GitHub Actions run has occurred.

## Local email testing

Run `docker compose up --build -d --wait` from the repository root. Register at `http://127.0.0.1:3000/register`, then open the captured message at `http://127.0.0.1:8025`. Mailpit captures SMTP locally and does not forward email to real recipients. Its inbox and SMTP ports bind to loopback. Do not expose this development inbox to the Internet.

Open the verification link and press **Verify email**, then log in. For a forgotten password, use the login page's **Forgot password?** link, open the captured reset message, and set a new password. These local defaults do not need an email provider. If you run Java directly on the host, keep the Docker `db` and `mailpit` services running and start Java with the `dev` profile.

Accounts registered in Stage 20 retain their IDs, passwords, and requests, but must verify their email before their next login. Use **Resend verification email**. Pre-authentication demo accounts without a password remain unable to log in or claim themselves through recovery.

Verification links expire after 24 hours; reset links after 30 minutes. Resending replaces the earlier link for that purpose. Links are random 256-bit values, stored as SHA-256 hashes, carried in URL fragments, and removed from the address bar after the page reads them. Opening a link does not consume it; a CSRF-protected POST does. Password resets invalidate all earlier sessions on their next request and send a notification email. Recovery only works for verified accounts.

Mail requests receive the same HTTP response for unknown, legacy, and eligible accounts. A bounded background queue performs the lookup and SMTP work after the response/registration commit. Delivery failures are logged without addresses, credentials, or tokens; users can request another email. This queue is held in memory, so a restart can lose a queued email. It is suitable for this small trial; there is no claim of durable mail delivery.

Throttles count all attempts, including successful logins: 10 logins per normalized email per 15 minutes, 500 total logins per instance per 15 minutes, 3 recovery/resend requests per email per 15 minutes, 100 total mail requests per hour, 30 registrations per hour, and 100 token submissions per 15 minutes. HTTP 429 includes `Retry-After` in seconds. Counters are bounded, held in memory, reset on restart, and intended for one backend instance. They do not trust client-supplied forwarded IPs. Use a shared limiter before adding replicas. Sessions also live in memory and require a fresh login after a backend restart.

## Prepare the chosen server

Use a dedicated trial server with Docker Compose v2, a dedicated domain pointing to that server, and an SMTP service with a verified sender and STARTTLS credentials (normally port 587). Provide sufficient memory for Java plus the Next.js image build. Keep the trial database separate from the development database.

1. Copy the repository to the server and check out the reviewed commit.
2. Copy `deploy/.env.example` to `deploy/.env.trial`. Fill in a fresh database password, domain, certificate contact, sender, and SMTP credentials on that server. Keep it out of Git and restrict it to the server administrator (`chmod 600 deploy/.env.trial`).
3. Validate the configuration without printing secrets:

   ```bash
   docker compose --env-file deploy/.env.trial -f deploy/compose.trial.yml config --quiet
   ```

4. Allow inbound HTTPS/HTTP ports 443 and 80 on the chosen server. Keep PostgreSQL, the Java backend, and Next.js ports private. Review the domain and configuration before starting the public site.
5. When you are ready to publish:

   ```bash
   docker compose --env-file deploy/.env.trial -f deploy/compose.trial.yml up --build -d --wait
   docker compose --env-file deploy/.env.trial -f deploy/compose.trial.yml ps
   node scripts/check-health.mjs https://YOUR-TRIAL-HOST
   ```

The standalone `compose.trial.yml` uses the project name `local-delivery-trial`, its own volumes, HTTPS with automatic certificate renewal, Secure/HttpOnly/SameSite session cookies, SMTP STARTTLS, and bounded container logs. Only Caddy publishes ports. Swagger is disabled in this deployment. Search indexing is discouraged with `X-Robots-Tag`; this is not an access restriction, and people with the URL can register. Use fictional delivery details for the first trial.

Do not combine this file with the local Compose file. Do not copy the local database into the trial environment. Public TLS issuance and real email delivery must be verified on the selected host; local configuration validation cannot establish those results.

## Backups, restoration, and updates

From the repository root on the server:

```bash
COMPOSE_FILE=deploy/compose.trial.yml COMPOSE_ENV_FILES=deploy/.env.trial sh scripts/backup-database.sh
```

The script writes a PostgreSQL custom-format backup under `backups/`, with private file permissions, and refuses to overwrite a file. It uses the database credentials inside the container. Back up before each release and arrange a regular server backup schedule when hosting is selected. Store an encrypted copy away from the server and periodically test restoration. No backup schedule or off-site storage has been configured by this change.

Restore into a **new, empty database** for validation, leaving the active database alone:

```bash
export COMPOSE_FILE=deploy/compose.trial.yml
export COMPOSE_ENV_FILES=deploy/.env.trial
docker compose exec -T db sh -c 'createdb -U "$POSTGRES_USER" restore_check'
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d restore_check --no-owner --no-acl --exit-on-error' < backups/YOUR-BACKUP.dump
```

Inspect restored accounts and requests before planning any switch. The automated Docker test performs this restoration in a disposable project and compares every account/request field to the source. Never run `down --volumes` on the normal local or trial project unless you intend to remove its database.

For upgrades, take a backup, check out a reviewed commit with passing CI, and repeat `up --build -d --wait`. Flyway applies additive migrations and the readiness check waits for PostgreSQL access. Keep the previous application release and backup available; schema rollback requires a deliberate restore plan rather than blindly running an older application.

## Operational checks

- `GET /backend-api/health/ready` returns 200 only when the backend can query PostgreSQL; it returns a minimal 503 response on database failure.
- `node scripts/check-health.mjs https://YOUR-TRIAL-HOST` checks the login page and database readiness. Configure the chosen host's uptime monitor and alert recipient before inviting testers; no monitor has been created yet.
- `docker compose --env-file deploy/.env.trial -f deploy/compose.trial.yml logs --since 15m backend frontend gateway` shows application errors. Log files rotate at 10 MB with three files per service. Account email failures are reported without the message body or destination. The gateway does not log request URLs.
- After deployment, check registration, real email arrival, verification, reset, logout, mobile layouts, and the full delivery flow using the [trial checklist](TRIAL_CHECKLIST.md).

## References

Implementation follows [GitHub's Maven CI guide](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven), [Spring Boot SMTP configuration](https://docs.spring.io/spring-boot/reference/io/email.html), [OWASP password recovery guidance](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html), [Mailpit's Docker instructions](https://mailpit.axllent.org/docs/install/docker/), and [Caddy automatic HTTPS requirements](https://caddyserver.com/docs/automatic-https).
