# Market Ledger 2040 Launcher

This module is a plain Kotlin/JVM and Swing application. It deliberately has no dependency on
the game module: the MSI contains only the launcher and its jlink runtime. The launcher obtains
the signed game and debug-bundle release assets at runtime.

Before any network access, the launcher creates `mods`, `resources`, and `saves` under the user
data root, plus its version, staging, download, quarantine, state, and runtime-cache directories
under the local data root. It then verifies the signed feed, artifact hashes, exact game inventory,
build cohort, and debug bundle before atomically activating an installation.

Release packaging requires `ML_FEED_SIGNING_PUBLIC_KEY_X509_B64`, containing the Base64 X.509
encoding of the stable-feed Ed25519 public key. Gradle validates it and generates the classpath
resource below without placing key material in source control. A normal `dev` build generates an
ephemeral public key so that local `check` and IDE builds do not need release secrets; a `release`
build fails closed when the configured key is absent.

```text
src/launcherApp/build/generated/release-resources/
  market-ledger/release/stable-feed-public-key.b64
  market-ledger/release/minimum-game-version.txt
```

For offline development, an optional complete, signed release may be placed under
`src/launcherApp/src/main/resources/bundled-release/`. The expected feed filenames are
`market-ledger-stable-feed.json` and `market-ledger-stable-feed.json.sig`; artifact URLs in that
feed use `classpath:/bundled-release/<asset-name>`. A bundled release is only a fallback after the
remote stable feed fails, and it is verified with the same embedded release key.
