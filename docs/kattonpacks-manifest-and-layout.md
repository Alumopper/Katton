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
  "targets": {
    "server": true,
    "client": true
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
- `targets.server`: whether pack scripts should be loaded in server environment.
- `targets.client`: whether pack scripts should be loaded in client environment.

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
