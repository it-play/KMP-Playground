# Debug bundle trust model

`debug-bundle-challenge.dat` is the canonical, non-secret pairing challenge shared by the
host and the signed built-in debug bundle. Keep exactly four LF-terminated bracketed groups;
each group contains exactly three unique 1000-character `[A-Za-z0-9_-]` fragments separated
by `&^`.

The DAT is an additional build-pairing check, not a cryptographic secret. Ed25519 signatures
and the host-pinned public key are the trust root.

Build channels are selected with `ML_BUILD_CHANNEL=dev|release` (default: `dev`).
Development builds generate an ephemeral Ed25519 key under `composeApp/src/debugModBundle/build/trust` and
generate a CSPRNG build cohort unless `ML_BUILD_COHORT` is supplied.

GitHub repository variables (public verification material):

- `ML_DEBUG_BUNDLE_SIGNING_PUBLIC_KEY_X509_BASE64`: Ed25519 X.509 public key
- `ML_FEED_SIGNING_PUBLIC_KEY_X509_B64`: stable-feed Ed25519 X.509 public key
- `ML_WINDOWS_SIGNING_CERT_SHA256`: uppercase SHA-256 fingerprint of the project-owned signing certificate

The `market-ledger-stable` GitHub environment contains only secrets:

- `ML_DEBUG_BUNDLE_SIGNING_KEY_PKCS8_BASE64`: debug-bundle Ed25519 PKCS#8 private key
- `ML_FEED_SIGNING_KEY_PKCS8_B64`: stable-feed Ed25519 PKCS#8 private key
- `ML_WINDOWS_SIGNING_CERT_PFX_BASE64`: Authenticode certificate and private key in PFX form
- `ML_WINDOWS_SIGNING_CERT_PASSWORD`: PFX password

Release builds receive the following environment variables from those GitHub settings or generate
them once per build:

- `ML_BUILD_CHANNEL=release`
- `ML_BUILD_COHORT`: exactly 64 lowercase hexadecimal characters, generated once per CI build
- `ML_DEBUG_BUNDLE_SIGNING_KEY_PKCS8_BASE64`: Ed25519 PKCS#8 private key
- `ML_DEBUG_BUNDLE_SIGNING_PUBLIC_KEY_X509_BASE64`: matching repository public-key variable
- `ML_FEED_SIGNING_KEY_PKCS8_B64`: stable-feed Ed25519 PKCS#8 private key
- `ML_FEED_SIGNING_PUBLIC_KEY_X509_B64`: matching repository public-key variable
- `ML_RELEASE_PUBLISHED_AT`: the offline package timestamp stored in the feed as an ISO-8601 instant
- `ML_WINDOWS_SIGNING_CERT_SHA1`: thumbprint of the imported Authenticode code-signing certificate

The Windows packaging workflow additionally imports its non-exportable signing certificate from the
`ML_WINDOWS_SIGNING_CERT_PFX_BASE64` and `ML_WINDOWS_SIGNING_CERT_PASSWORD` environment secrets.
It signs and independently verifies both the launcher executable and MSI before uploading the MSI
as an Actions artifact.

Stable Windows packaging uses one project-owned self-signed certificate. CI requires its pinned
SHA-256 fingerprint, temporarily adds that exact public certificate to the runner trust store,
verifies the launcher executable and MSI, and removes the certificate afterward. No external
certificate authority, timestamp service, trust-mode switch, or certificate migration path is part
of this project.

Production Ed25519 private keys are exposed only to the single release-build process environment
and read inside non-cacheable task actions. They are never written to a GitHub command file,
declared as Gradle inputs, printed, copied into an output, or materialized by the release build.
The release assembler independently checks the debug signature, pairing DAT, public key, channel,
host version, and cohort embedded in the actual game and debug ZIP files before it signs their
hashes. The resulting feed uses only `/bundled-release/...` embedded resource paths.

The launcher MSI embeds the exact five-file signed closure from `build/release`. On first launch it
verifies the feed signature, artifact hashes, inventory, build cohort, and debug bundle before
installing the game body. It does not contact or require a deployment server. For a local internal
build, run `build-windows-installer.bat`; it generates ephemeral signing inputs and cleans them after
the MSI and launcher signatures have been verified.
