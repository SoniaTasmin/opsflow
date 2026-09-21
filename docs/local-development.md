# Local development

## Required tooling

| Tool | Version | Check |
|---|---|---|
| JDK | 21 (LTS) | `java -version` |
| Node | ≥ 24.15.0 | `node -v` |
| Docker | with the Compose v2 plugin | `docker compose version` |
| Make | any | `make --version` |

`make preflight` checks all of these at once and names what is missing.

Gradle is **not** required: the repository ships a pinned wrapper (`backend/gradlew`). The
Gradle distribution is checksum-pinned in `backend/gradle/wrapper/gradle-wrapper.properties`, so a
swapped or corrupted download fails the build rather than executing.

### Installing a JDK

The toolchain is declared in `backend/build.gradle.kts`, so Gradle will use whichever Java 21
installation it can find.

**Debian / Ubuntu / WSL:**

```bash
sudo apt update && sudo apt install -y openjdk-21-jdk
```

**Without root**, install a user-local Temurin 21 and put it on your path:

```bash
mkdir -p ~/.local/jdks && cd ~/.local/jdks
curl -L -o temurin21.tar.gz \
  "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
tar xzf temurin21.tar.gz && rm temurin21.tar.gz
# then add to ~/.bashrc:
#   export JAVA_HOME="$HOME/.local/jdks/$(ls ~/.local/jdks | head -1)"
#   export PATH="$JAVA_HOME/bin:$PATH"
```

### Installing the Docker Compose v2 plugin

```bash
sudo apt install -y docker-compose-v2
```

Or, without root, as a Docker CLI plugin in your home directory:

```bash
mkdir -p ~/.docker/cli-plugins
curl -SL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64" \
  -o ~/.docker/cli-plugins/docker-compose
chmod +x ~/.docker/cli-plugins/docker-compose
```

On WSL with Docker Desktop, enable integration for this distribution in
*Docker Desktop → Settings → Resources → WSL Integration* instead.

## Authenticating locally

`make up` starts Keycloak with the `opsflow` realm imported from
`deploy/keycloak/realm-opsflow.json`. The admin console is at `http://localhost:8081`
(admin/admin).

Four users exist, all with password `password`:

| User | Realm roles | Use for |
|---|---|---|
| `reporter` | `USER` | The ordinary case |
| `reviewer` | `USER`, `REVIEWER` | Review authority |
| `admin` | `USER`, `ADMIN` | Platform administration |
| `noroles` | *(none)* | Proving that authorization fails closed |

To get a token with curl:

```bash
TOKEN=$(curl -s -X POST \
  "http://localhost:8081/realms/opsflow/protocol/openid-connect/token" \
  -d client_id=opsflow-dev-cli -d grant_type=password \
  -d username=reporter -d password=password | jq -r .access_token)

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/me | jq
```

**The realm ships two development-only clients.** `opsflow-dev-cli` enables the password grant so
a token can be obtained without a browser, and `opsflow-wrong-audience-cli` issues tokens without
this API's audience so an integration test can prove they are rejected. Neither belongs in a
deployed realm. `opsflow-frontend` — the client the Angular application uses — is configured
exactly as it would be deployed: Authorization Code with PKCE, no password grant, exact redirect
URIs.

## Daily loop

```bash
make up      # PostgreSQL in Docker
             # backend and frontend run from your IDE against it
```

Run the backend with the `local` Spring profile, and the frontend with `npm start`.

`make down` stops the stack and keeps your data. `make clean` deletes the data volume and all
build output.

## Configuration

`make up` copies `.env.example` to `.env` on first run. `.env` is git-ignored and must never be
committed. Nothing in it is a production value — deployed environments supply every setting
through the environment.

## End-to-end tests

The Playwright specs in `frontend/e2e/` drive the real Keycloak login page, so they need the
whole stack up: `make up`, the backend on 8080, and the frontend on 4200. Serve the production
build rather than `ng serve` — the dev server's file watcher is unreliable on Windows-mounted
volumes and will happily serve a stale bundle.

```bash
cd frontend
npm run build
npm run e2e:serve &          # static server with SPA fallback on :4200
```

Then run the specs. Browser system libraries need root, so the official image is the reliable
route:

```bash
docker run --rm --network host --ipc=host \
  -v "$PWD:/work" -w /work \
  -e PLAYWRIGHT_BROWSERS_PATH=/ms-playwright -e HOME=/tmp \
  mcr.microsoft.com/playwright:v1.63.0-noble npx playwright test
```

With the libraries installed locally, `npx playwright install --with-deps chromium && npm run e2e`
does the same thing.

## Running checks

```bash
make test     # backend (unit + ArchUnit) and frontend tests
make lint     # Spotless, ESLint, Prettier
make format   # auto-fix formatting in both projects
make build    # full build of both projects
```

CI runs exactly these, so a green `make test && make lint` locally means a green pipeline.

## Conventions

- **Commits** follow [Conventional Commits](https://www.conventionalcommits.org/):
  `feat(submission): add reject command with mandatory reason`. The scope is the module. The body
  explains *why*; the diff already shows what.
- **Branches** are short-lived and named `feat/…`, `fix/…`, `chore/…` or `docs/…`. Rebase on
  `main` before opening a pull request.
- **`main` must build and run after every merge.** Each milestone leaves the repository working.
