import {
  generateKeyPairSync,
  randomBytes,
  sign,
  verify,
} from "node:crypto";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const MINIMUM_NODE_MAJOR = 18;
const SCRIPT_DIRECTORY = dirname(fileURLToPath(import.meta.url));
const PROJECT_DIRECTORY = resolve(SCRIPT_DIRECTORY, "..", "..", "..", "..");
const POWERSHELL_SCRIPT = resolve(
  SCRIPT_DIRECTORY,
  "invoke-offline-windows-installer.ps1",
);

function requireSupportedRuntime() {
  const major = Number.parseInt(process.versions.node.split(".")[0], 10);
  if (!Number.isInteger(major) || major < MINIMUM_NODE_MAJOR) {
    throw new Error(`Node.js ${MINIMUM_NODE_MAJOR} or newer is required.`);
  }
  if (process.platform !== "win32") {
    throw new Error("The offline launcher MSI must be built on Windows.");
  }
}

function createSigningPair() {
  const pair = generateKeyPairSync("ed25519");
  const challenge = randomBytes(64);
  const signature = sign(null, challenge, pair.privateKey);
  if (!verify(null, challenge, pair.publicKey, signature)) {
    throw new Error("Ephemeral Ed25519 key-pair verification failed.");
  }
  challenge.fill(0);
  signature.fill(0);
  return {
    privateDer: pair.privateKey.export({ format: "der", type: "pkcs8" }),
    publicDer: pair.publicKey.export({ format: "der", type: "spki" }),
  };
}

function buildEnvironment(debugPair, feedPair) {
  return {
    ...process.env,
    ELECTRON_BUILDER_OFFLINE: "true",
    ML_BUILD_CHANNEL: "release",
    ML_BUILD_COHORT: randomBytes(32).toString("hex"),
    ML_DEBUG_BUNDLE_SIGNING_KEY_PKCS8_BASE64:
      debugPair.privateDer.toString("base64"),
    ML_DEBUG_BUNDLE_SIGNING_PUBLIC_KEY_X509_BASE64:
      debugPair.publicDer.toString("base64"),
    ML_FEED_SIGNING_KEY_PKCS8_B64: feedPair.privateDer.toString("base64"),
    ML_FEED_SIGNING_PUBLIC_KEY_X509_B64:
      feedPair.publicDer.toString("base64"),
    ML_RELEASE_PUBLISHED_AT: new Date().toISOString(),
  };
}

function clearSensitiveEnvironment(environment) {
  for (const name of [
    "ML_DEBUG_BUNDLE_SIGNING_KEY_PKCS8_BASE64",
    "ML_FEED_SIGNING_KEY_PKCS8_B64",
  ]) {
    environment[name] = "";
    delete environment[name];
  }
}

let debugPair;
let feedPair;
let childEnvironment;

try {
  requireSupportedRuntime();
  debugPair = createSigningPair();
  feedPair = createSigningPair();
  childEnvironment = buildEnvironment(debugPair, feedPair);
  console.log(
    "[Market Ledger 2040] Building a self-contained internal launcher MSI...",
  );
  const result = spawnSync(
    "powershell.exe",
    [
      "-NoLogo",
      "-NoProfile",
      "-NonInteractive",
      "-ExecutionPolicy",
      "Bypass",
      "-File",
      POWERSHELL_SCRIPT,
    ],
    {
      cwd: PROJECT_DIRECTORY,
      env: childEnvironment,
      stdio: "inherit",
      windowsHide: false,
    },
  );
  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    process.exitCode = result.status ?? 1;
  }
} catch (error) {
  const message = error instanceof Error ? error.message : String(error);
  console.error(`[Market Ledger 2040] ${message}`);
  process.exitCode = 1;
} finally {
  if (childEnvironment) {
    clearSensitiveEnvironment(childEnvironment);
  }
  if (debugPair) {
    debugPair.privateDer.fill(0);
    debugPair.publicDer.fill(0);
  }
  if (feedPair) {
    feedPair.privateDer.fill(0);
    feedPair.publicDer.fill(0);
  }
}
