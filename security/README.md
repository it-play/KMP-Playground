# Debug bundle trust inputs

`debug-bundle-challenge.dat` is the canonical, non-secret pairing challenge shared by the
host and the signed built-in debug bundle. Keep exactly four LF-terminated bracketed groups;
each group contains exactly three unique 1000-character `[A-Za-z0-9_-]` fragments separated
by `&^`.

The DAT is an additional build-pairing check, not a cryptographic secret. Ed25519 signatures
and the host-pinned public key are the trust root.

Build channels are selected with `ML_BUILD_CHANNEL=dev|release` (default: `dev`).
Development builds generate an ephemeral Ed25519 key under `debugModBundle/build/trust` and
generate a CSPRNG build cohort unless `ML_BUILD_COHORT` is supplied.

GitHub repository variables (public verification material):

- `ML_DEBUG_BUNDLE_SIGNING_PUBLIC_KEY_X509_BASE64`: Ed25519 X.509 public key
- `ML_FEED_SIGNING_PUBLIC_KEY_X509_B64`: stable-feed Ed25519 X.509 public key
- `ML_WINDOWS_SIGNING_CERT_SHA256`: uppercase SHA-256 fingerprint of the Authenticode certificate
- `ML_WINDOWS_SIGNING_TRUST_MODE`: `internal-self-signed` or `public-ca`

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
- `ML_RELEASE_PUBLISHED_AT`: the release publication time as an ISO-8601 instant
- `ML_WINDOWS_SIGNING_CERT_SHA1`: thumbprint of the imported Authenticode code-signing certificate

The Windows release workflow additionally imports its non-exportable signing certificate from the
`ML_WINDOWS_SIGNING_CERT_PFX_BASE64` and `ML_WINDOWS_SIGNING_CERT_PASSWORD` environment secrets.
It signs and independently verifies both the launcher executable and MSI before publishing.

The current development configuration uses `internal-self-signed`. CI checks the pinned
certificate fingerprint, temporarily trusts that exact certificate only while verifying its own
artifacts, and removes it from the runner afterward. This proves artifact integrity inside CI but
does not make the publisher publicly trusted on a fresh Windows PC. Replace the PFX with a
CA-issued Authenticode certificate and set `ML_WINDOWS_SIGNING_TRUST_MODE=public-ca` before a
public release that must display a trusted publisher identity.

Production Ed25519 private keys are exposed only to the single release-build process environment
and read inside non-cacheable task actions. They are never written to a GitHub command file,
declared as Gradle inputs, printed, copied into an output, or materialized by the release build.
The stable release assembler independently checks the debug signature, pairing DAT, public key,
channel, host version, and cohort embedded in the actual game and debug ZIP files before it signs
their hashes. Before applying its monotonic version gate, CI also verifies the currently published
feed/signature pair with the configured Ed25519 public key.

Run `buildWindowsRelease` on Windows to produce the launcher MSI and the five-file signed release
closure under `build/release`. Game, debug-bundle, and inventory asset names include `appVersion`;
the detached feed signature is published immediately before the feed, which is the channel commit
point.
