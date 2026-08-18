# Market Ledger 2040 Launcher

This module is a plain Kotlin/JVM and Swing application. It deliberately has no compile-time
dependency on the game module. The MSI contains the launcher, its jlink runtime, and one complete
five-file signed game release under the launcher classpath at `/bundled-release/`.

At startup, the launcher creates `mods`, `resources`, and `saves` under the user data root, plus
its version, staging, artifact-cache, quarantine, state, and runtime-cache directories under the local
data root. It then verifies the embedded feed, artifact hashes, exact game inventory, build cohort,
and debug bundle before atomically activating an installation. It does not require a server or a
GitHub Release.

Release packaging uses `ML_FEED_SIGNING_PUBLIC_KEY_X509_B64`, containing the Base64 X.509 encoding
of the feed's Ed25519 public key. Gradle validates it and generates the classpath resource below
without placing private key material in source control. A normal `dev` compile generates an
ephemeral public key so local compilation and IDE use do not require release inputs.

```text
composeApp/src/launcherApp/build/generated/release-resources/
  market-ledger/release/stable-feed-public-key.b64
  market-ledger/release/minimum-game-version.txt
```

## One-command internal MSI

Run `build-windows-installer.bat` from the repository root on Windows. JDK 21, Node.js 18 or newer,
and `npx` must be available. No GitHub environment, GitHub Release, external certificate authority,
PFX, or preconfigured signing secret is needed.

The Node build entrypoint creates two independent ephemeral Ed25519 key pairs and one random build
cohort in memory. The private key values are passed only through the child build process environment.
PowerShell creates a non-exportable, short-lived self-signed code-signing certificate in the current
user's certificate store, temporarily trusts it, builds the release, verifies the MSI and packaged
launcher signatures, and removes the certificate, private key, environment values, and temporary
certificate file in `finally`.

Gradle creates and verifies exactly these five release files and copies them into generated launcher
resources:

```text
composeApp/src/launcherApp/build/generated/bundled-release-resources/bundled-release/
  market-ledger-game-<version>-windows-x64.zip
  market-ledger-debug-<version>-windows-x64.zip
  market-ledger-game-<version>-windows-x64.inventory.json
  market-ledger-stable-feed.json
  market-ledger-stable-feed.json.sig
```

All three artifact entries in the signed feed use the embedded resource path
`/bundled-release/<asset-name>`. The generated MSI is under
`composeApp/src/launcherApp/build/compose/binaries/main/msi/`.
