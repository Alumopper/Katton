# Katton Packs Layout and Manifest

## 1. Directory Layout

Global:
- `<gameDir>/kattonpacks/<packFolder>/manifest.json`
- `<gameDir>/kattonpacks/<packFolder>/**/*.kt`

World:
- `<worldDir>/kattonpacks/<packFolder>/manifest.json`
- `<worldDir>/kattonpacks/<packFolder>/**/*.kt`

Server-transferred cache on client:
- `<gameDir>/serverpacks/<sha256(serverAddress)>/<base64(syncId)>/manifest.json`
- `<gameDir>/serverpacks/<sha256(serverAddress)>/<base64(syncId)>/**/*.kt`

## 2. Manifest Example

```json
{
  "id": "example_pack",
  "name": "Example Pack",
  "version": "1.0.0",
  "description": "Example scripts for Katton pack system",
  "authors": ["YourName"],
  "enabled": true,
  "clientSync": true,
  "signature": {
    "algorithm": "Ed25519",
    "keyId": "example-server-key",
    "publicKey": "base64-x509-public-key",
    "signature": "base64-signature"
  }
}
```

## 3. Manifest Field Meaning

- `id`: stable logical ID of the pack.
- `name`: display name in UI.
- `version`: human-readable version string.
- `description`: description in UI.
- `authors`: optional string array.
- `enabled`: default enabled state if no local state file exists.
- `clientSync`: whether this pack should be sent to multiplayer clients during Katton server sync. Defaults to `true` for compatibility.
- `signature`: recommended for remote client-synced packs. Uses Ed25519 and signs the pack's canonical content digest.

Side behavior:
- Runtime side-specific execution is still decided by function annotations (`@ServerScriptEntrypoint`, `@ClientScriptEntrypoint`).
- `clientSync` only controls whether the server includes the pack in the client download/sync snapshot.
- Use `"clientSync": false` for pure server-side packs that do not contain client entrypoints or client-required registry/rendering code.

Signature behavior:
- Signed client-synced packs are verified before they are written to the client's `serverpacks` cache.
- Unsigned client-synced packs remain compatible, but they rely only on the blocking trust prompt and do not have tamper-evident author verification.
- The signed payload includes a Katton signature format version, the pack `syncId`, pack scope, the manifest JSON with `signature` removed, and all synced file relative paths plus bytes in sorted order.
- `publicKey` is an X.509-encoded Ed25519 public key in Base64. After the user trusts a server/key, Katton stores the trusted public key in `<gameDir>/.katton/remote-script-trust.json`.
- If a trusted `keyId` later presents a different embedded public key, verification fails and the remote scripts are rejected.

## 4. State File

Katton writes a per-pack local state file when user toggles enable/disable in GUI:

- `.kattonpack.state.json`

Structure:

```json
{
  "enabled": false
}
```

Priority:
- If state file exists, it overrides `manifest.json` `enabled`.
- If state file is absent, `manifest.json` `enabled` is used.

## 5. Hash Input

Current hash calculation uses:
- raw `manifest.json` string bytes (UTF-8)
- sorted script relative path bytes
- script file bytes

Algorithm: SHA-256 (hex lowercase).
