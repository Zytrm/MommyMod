<p align="center">
  <img src="src/main/resources/assets/mommymods/icon.png" alt="MommyMods" width="128">
</p>

<h1 align="center">MommyMods 26.1.2</h1>

<p align="center">
  <a href="https://github.com/Zytrm/MommyMod/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/Zytrm/MommyMod/build.yml?style=for-the-badge&logo=github" alt="Build"></a>
  <a href="https://github.com/Zytrm/MommyMod/releases"><img src="https://img.shields.io/github/downloads/Zytrm/MommyMod/total?style=for-the-badge&logo=github" alt="Downloads"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/Zytrm/MommyMod?style=for-the-badge" alt="License"></a>
</p>

> Focused fishing utilities for Hypixel SkyBlock, built with Fabric and Kotlin.

---

## Installation

1. Install [Fabric for Minecraft 26.1.2](https://fabricmc.net/use/installer/).
2. Install [Fabric API](https://modrinth.com/mod/fabric-api/versions?g=26.1.2) and [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin).
3. Install [NoammAddons](https://github.com/Noamm9/NoammAddons) 1.2.2 or newer. MommyMods uses its feature, event, and settings framework.
4. Download the latest MommyMods JAR from [Releases](https://github.com/Zytrm/MommyMod/releases) or a successful [Actions build](https://github.com/Zytrm/MommyMod/actions/workflows/build.yml).
5. Place the required JARs in `.minecraft/mods`, launch Minecraft, and type `/mm`.

## Features

- **Hide Fishing Line** — hides the line between your rod and bobber without hiding the bobber.
- **LouderCatch** — confirms the local ready-to-reel moment and plays a configurable alert at up to 20x volume.
- **FishingPartyHelper** — checks Fishing 45, Silver Trophy Hunter, Looting V, Bloodshot belt, and Jawbus eligibility when players join.
- **Jawbus Finder** — displays a compact ten-second alert when a non-party player dies to Jawbus in your lobby.
- **Looting V Message** — sends one configurable reminder when you spawn a Jawbus.

## Configuration

Open the compact settings menu with:

```text
/mm
/mommymods
/mommy mods
```

All MommyMods features live in the **Fishing** category. Left-click a feature to toggle it and right-click to open its options.

<details>
<summary><strong>Debug commands</strong></summary>

Debug tools are opt-in and never auto-kick or send the preview message.

```text
/mmcatchdebug
/mmpartydebug self
/mmpartydebug profile <player>
/mmpartydebug status
/mmpartydebug message
```

</details>

<details>
<summary><strong>Detection notes</strong></summary>

- Features activate only on Hypixel.
- LouderCatch tracks the local hook through casting, landing, waiting, bite confirmation, and reset. Cast and landing signals cannot play the alert.
- FishingPartyHelper uses the MommyMods readiness service and falls back to visible in-game gear when profile data is unavailable. Unknown values never trigger auto-kick.
- Jawbus eligibility requires Fishing 45 or higher and Silver Trophy Hunter.
- Belt output distinguishes no equipped Gillsplash/Finwave belt from a relevant belt without Bloodshot.
- Jawbus alerts and chat reminders use narrow Hypixel messages with per-event cooldowns to avoid duplicates.

</details>

## Building

Requires JDK 25.

```bash
./gradlew build
```

Windows PowerShell:

```powershell
./gradlew.bat build
```

The distributable JAR is written to `build/libs/`.

## Contributions

Issues and pull requests are welcome. Keep changes focused and test against Minecraft 26.1.2.

## License

MommyMods is licensed under the [MIT License](LICENSE).
