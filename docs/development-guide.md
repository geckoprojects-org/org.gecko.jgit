# Development Guide

Working notes for `org.gecko.jgit`. The **Change Log** below is kept in reverse
chronological order — the most recent change is always at the top.

## Building & testing

- Build + test everything: `./gradlew build`
- Test only the OSGi test bundle: `./gradlew :org.gecko.jgit.test:test`
- Requires **JDK 17** on `PATH` (the Gradle 7.6 wrapper fails under newer JDKs
  with "Unsupported class file major version …"). Source/target is Java 11.
- Tests are OSGi integration tests (Felix + `org.osgi.test`); they run the real
  `GitServiceImpl` against a throw-away **local** repo created under `testRepo/`
  by the `GitRepositoryService` helper.

## Change Log

### 2026-07-22 — SSH backend migrated JSch → Apache MINA sshd (issue #2)

Replaced the legacy JCraft **JSch** SSH backend with JGit's Apache **MINA sshd**
backend (`org.eclipse.jgit.ssh.apache`), so OpenSSH-format and **ed25519** keys work
(JSch 0.1.55 only parsed classic RSA PEM).

**Status:** implemented and verified — full OSGi test suite green under JDK 17
(`GitServiceTest` ×3, `GitAnonymousTransportTest`, and the new `GitSshTransportTest` ×4).
Changes are on branch `snapshot` and **not yet committed**; the working tree also carries
the earlier in-flight gecko→fennec `cnf/` migration (jgit `7.1`→`7.7`).

**Code.** `GitServiceImpl` no longer subclasses `JschConfigSessionFactory`. It builds an
`SshdSessionFactory` via `SshdSessionFactoryBuilder`:
- the configured `privateKey` is supplied as a lazily-loaded identity path
  (`setDefaultIdentities`) so MINA/BouncyCastle parse whatever key format it is;
- the passphrase comes from a small `KeyPasswordProvider` (`ConfiguredKeyPasswordProvider`)
  that returns `GitConfig.privateKeyPassphrase()` and never retries;
- the factory is built **only when a private key is configured**, attached only to real
  `SshTransport`s, and `close()`d in `deactivate()`;
- `SshdSessionFactoryBuilder` does **not** default the home/ssh directory — `build()` NPEs
  on a null home dir — so both are set explicitly (`user.home` + `~/.ssh`, with a fallback
  when `user.home` is unset, as in a bare OSGi launcher); the home dir must be absolute.

**`isRemote()` now recognizes `ssh://`.** It previously matched only `git`/`https`
prefixes, so an `ssh://host:port/path` URL fell through to the local `FileRepositoryBuilder`
branch and failed activation with *"One of setGitDir or setWorkTree must be called."* The
old scp-style `git@host:…` config masked this (it starts with `git`), but scp syntax can't
carry a port. `ssh` was added to the remote-scheme check.

**New config option `GitConfig.knownHosts()`** (optional, defaults to empty → MINA's
`~/.ssh/known_hosts`). MINA verifies the server host key by default; a headless deployment
must point this at a provisioned `known_hosts` or SSH fetches are rejected. Wired into the
Configurator JSON as `$[env:SSH_KNOWN_HOSTS;…]`.

**Dependencies** (in `cnf/central.mvn`; buildpath swapped `ssh.jsch`+`jsch` → `ssh.apache`):
`ssh.apache:7.7` mandatorily imports MINA `[2.17.1,2.18.0)`, BouncyCastle `[1.84,2.0)`,
and slf4j (already present). Added: `sshd-osgi` + `sshd-sftp` `2.17.1` (jgit imports
`org.apache.sshd.sftp.*`, which `sshd-osgi` does **not** carry), and
`bcprov`/`bcpkix`/`bcutil` `1.84` (MINA uses `bcpkix`'s `openssl`/`pkcs` to parse
classic-PEM keys via BouncyCastle's `PEMParser`). `test.bndrun` `-runbundles` swapped
accordingly and jgit core bumped `7.1`→`7.7`. The JSch bundles (`ssh.jsch`,
`org.apache.servicemix.bundles.jsch`) and the unused `org.eclipse.jgit.http.server` were
dropped from `central.mvn`. `org.gecko.jgit.config/launch.bndrun` was migrated the same way.

> **Resolution-vs-runtime trap for `launch.bndrun`.** jgit's `ssh.apache` only *mandatorily*
> imports `org.bouncycastle.jce.provider` (in `bcprov`); MINA's imports of `bcpkix`/`bcutil`
> packages are **optional**, so the bnd resolver drops them from `-runbundles` on every
> re-resolve. But `bcpkix` is needed **at runtime** to parse classic-PEM keys
> (`-----BEGIN RSA PRIVATE KEY-----`) — the very format legacy JSch supported. To stop the
> resolver from omitting them, `bcpkix`/`bcutil` are pinned via `-runrequires`
> (`bnd.identity;id='bcpkix'` / `id='bcutil'`) in `launch.bndrun`. `test.bndrun` lists them
> directly in its hand-maintained `-runbundles`, which is why the PEM key test passes.

**Test.** `GitSshTransportTest` starts an in-process MINA `SshServer` on an ephemeral port
(generated host key, accept-any pubkey) serving git-upload-pack through JGit's `UploadPack`,
then drives the real `GitServiceImpl` at `ssh://…` via `ConfigurationAdmin` with a
`known_hosts` written after startup. It runs four throw-away key fixtures under
`org.gecko.jgit.test/testkeys/`: ed25519 (OpenSSH), RSA (OpenSSH), RSA (classic PEM —
regression guard for the one format JSch handled), and a passphrase-protected ed25519 key.
No new test dependency — the server side ships inside `sshd-osgi`, already in `-runbundles`.

> Runtime note (applies to any OSGi deployment, not just the test): `ssh.apache`
> references `javax.security.auth.*` (e.g. `DestroyFailedException` while wiping key
> material) with **no** `Import-Package` — it expects the package on the boot classpath.
> Felix boot-delegates only `java.*`, so `javax.*` must be added to
> `org.osgi.framework.bootdelegation`. The catch in `test.bndrun`: the `enableOSGi-Test`
> (fennec) library *already* sets bootdelegation — to `org.mockito…` — through its
> `test-runproperties` macro, and bootdelegation is a **single** property, so a separate
> `-runproperties` entry can't add to it (the merge just picks one value). The fix is to
> **override the `test-runproperties` macro** so the one bootdelegation value carries both:
> `org.osgi.framework.bootdelegation='org.mockito.internal.creation.bytebuddy.inject,javax.*'`.
> Without it, activation resolves and the SSH handshake starts, then fails with
> `ClassNotFoundException: javax.security.auth.DestroyFailedException` the moment a key is
> destroyed. (A production deployment needs `javax.*` in its framework bootdelegation too.)
>
> SPI-Fly turned out **not** to be needed: MINA registers the BouncyCastle provider by
> class name and `bcprov`/`bcpkix` are wired, so ed25519/RSA keys load without the extender.
> If that ever regresses (key-load / "no such algorithm" errors), the lever is to add
> `org.apache.aries.spifly.dynamic.framework.extension` (already in `central.mvn`) to
> `-runbundles`.

### 2026-07-22 — `GitService` transport & fetch fixes

Two related fixes in `GitServiceImpl`, prompted by the model.atlas
`management.git` integration work (see that project's `PLAN.md`, "G8 IN PROGRESS"
findings).

**Status:** implemented and verified — full test suite green under JDK 17
(`GitServiceTest` ×3 + `GitAnonymousTransportTest`). Changes are on branch
`snapshot` and **not yet committed**; the working tree also carries an unrelated,
in-flight gecko→fennec migration in `cnf/` (`build.bnd`, `central.mvn`, `cnf/ext/`)
that predates this work.

**1. SSH-only transport → support `git://` and `https://` (issue #1).**
`activate()` previously installed a `TransportConfigCallback` that
*unconditionally* cast every JGit `Transport` to `SshTransport`, so any anonymous
`git://` (`TransportGitAnon`) or `https://` (`TransportHttp`) remote failed with a
`ClassCastException`. The callback now only attaches the SSH session factory when
the transport actually is an `SshTransport` (and a `privateKey` is configured);
other transports fetch with JGit's default transport.

Regression test: `GitAnonymousTransportTest` serves a repo in-process over `git://`
via JGit's own `org.eclipse.jgit.transport.Daemon` (ephemeral port, no external git
binary or container) and drives the real `GitServiceImpl` at it through
`ConfigurationAdmin`. Before the fix the service failed to activate
(`ClassCastException` on `TransportGitAnon`) and `waitForService` returned null.

> Note: the earlier commit `0f7231d` ("disabled ssh sessionFactory, when key is
> null") did **not** address this — it only null-guarded `addIdentity` inside the
> JSch factory. The transport cast remained unconditional until this change.

**2. Unconditional fetch broke local repos (issue #3, regression from `0e62d82`).**
`activate()` ran a `fetch` with `setRemote(config.repo())` for *every* repo. For a
local on-disk repo `config.repo()` is a filesystem path, not a resolvable remote,
so activation threw `InvalidRemoteException: Invalid remote: <path>` — which is
why all of `GitServiceTest` had been red. The regression came from `0e62d82`,
which hoisted the fetch out of the `if (isRemote())` branch to make `fetchCmd` a
reusable field for the new public `fetch()` method. Fixes:
- The `fetchCmd` setup + `call()` now live **inside** the `if (isRemote())`
  branch. A local `FileRepository` already carries its objects on disk, so it is
  never fetched.
- The public `fetch()` is null-safe: when the service was created against a local
  repo (`fetchCmd == null`) it logs at `INFO` ("Skipping fetch for local repo …")
  and returns instead of throwing/NPE-ing.

Result: `GitServiceTest` (`testLog`, `testFiles`, `testLoad`) passes. The
"Skipping fetch" line only appears if something actually calls `fetch()` on a
local-repo service — the current tests don't.

**Follow-up (issue #2):** migrating the SSH backend from JSch to Apache MINA sshd so
OpenSSH-format and ed25519 keys work — **done**, see the entry above (2026-07-22, at the
top of this log).
