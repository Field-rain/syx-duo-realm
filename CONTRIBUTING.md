# Contributing

Syx Duo Realm is an experimental Songs of Syx V71 Java mod. Contributions are welcome, especially around V71/V72 compatibility, safer shadow-faction projection, server testing, UI polish, and documentation.

## Ground Rules

- Use disposable saves for testing.
- Do not commit personal server URLs, Tailscale IPs, Steam credentials, SteamCMD cache, local save data, or server state JSON files.
- Keep the mod non-destructive by default. Trade claim may place resource piles only after explicit user action; war reports should remain server-side unless a change is clearly documented and reversible.
- Prefer small, testable changes.

## Local Build

Use Java 21 and make sure `pom.xml` points to your Songs of Syx V71 install directory.

```powershell
$env:JAVA_HOME = "C:\Path\To\jdk-21"
.\mvnw.cmd install
```

## Server Smoke Test

```powershell
node --check .\tools\syx-duo-test-server\server.js
$env:HOST = "127.0.0.1"
$env:PORT = "8787"
node .\tools\syx-duo-test-server\server.js
```

Then use the endpoints documented in `README.md`.
