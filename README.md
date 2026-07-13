<p align="center">
  <img src="src/main/resources/assets/mommymods/icon.png" alt="MommyMods" width="128">
</p>

<h1 align="center">MommyMods 26.1.2</h1>

<p align="center">
  <a href="https://github.com/Zytrm/MommyMod/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/Zytrm/MommyMod/build.yml?style=for-the-badge&logo=github" alt="Build"></a>
  <a href="https://github.com/Zytrm/MommyMod/releases"><img src="https://img.shields.io/github/downloads/Zytrm/MommyMod/total?style=for-the-badge&logo=github" alt="Downloads"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/Zytrm/MommyMod?style=for-the-badge" alt="License"></a>
</p>

> Compact fishing utilities and practical client tools for Hypixel SkyBlock, built with Fabric and Kotlin.

---

## Installation

1. Install [Fabric for Minecraft 26.1.2](https://fabricmc.net/use/installer/).
2. Install [Fabric API](https://modrinth.com/mod/fabric-api/versions?g=26.1.2) and [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin).
3. Download the latest MommyMods `.jar` from [Releases](https://github.com/Zytrm/MommyMod/releases).
4. Place all three `.jar` files in your `.minecraft/mods` folder.
5. Launch Minecraft with the Fabric profile and type `/mm` in chat.

## Features

- **Hide Fishing Line** — hides the line between your rod and bobber without hiding the bobber.
- **LouderCatch** — plays a configurable alert exactly when a fish is ready to reel, with volume up to 20x.
- **FishingPartyHelper** — checks Fishing 45, Silver Trophy Hunter, Looting V, Bloodshot belt, and Jawbus eligibility when players join.
- **Jawbus Finder** — shows a compact alert only when a non-party player dies to Lord Jawbus in your lobby.
- **Looting V Message** — sends one configurable reminder when you spawn a Jawbus.
- **Aura Player** — plays YouTube searches and links, SoundCloud, supported direct media URLs, playlists, and local files inside Minecraft without an account.
- **Party Commands** — provides configurable party-only helpers, starting with a hotbar-based Looting V check.
- **ClickGUI** — controls the menu accent, feature sorting, click sounds, HUD positions, and UI reset tools.

## Configuration

Open the compact MommyMods menu with any of these commands:

```text
/mm
/mommymods
/mommy mods
```

Left-click a feature to toggle it. Right-click a configurable feature to open its options. Settings are saved to `config/mommymods.json`.

Aura Player is in the compact **Misc** category. Right-click it to control volume, playlist autoplay, and the now-playing HUD. It uses public media playback and never asks for an account. Use its player screen or these commands:

```text
/mmplay
/mmplay <URL or search>
/mmmedia play <URL or search>
/mmmedia pause|next|previous|stop
/mmmedia shuffle|repeat
/mmmedia seek <seconds>
/mmmedia volume <0-100>
```

ClickGUI is always available under **Dev**. Right-click it to change the accent and sorting, toggle click sounds, open the draggable HUD editor, or reset the UI settings.

Party Commands is under **Misc**. Its Looting V Check uses `/lootingv` by default; right-click the feature to enable or rename the command. It refreshes the current party, checks Hyperion and Flaming Flay in each member's latest hotbar data, and sends one compact `[MM]` result through `/pc`. Unavailable inventory data is reported as `Unknown`, never as `No`.

Spotify links open in the desktop app or browser because Spotify does not provide playable audio streams to the embedded player.

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

- Fishing features activate only on `hypixel.net` and its subdomains.
- LouderCatch follows the local hook lifecycle and confirms the associated `!!!` fishing timer marker before alerting.
- FishingPartyHelper uses a narrow readiness service and falls back to visible in-game gear when profile data is unavailable. Unknown values are never treated as failures for auto-kick.
- Remote Looting V party checks use the latest public SkyBlock hotbar data available through the readiness service. The local player's nine hotbar slots are inspected directly.
- A player can Jawbus only with Fishing 45 or higher and Silver Trophy Hunter.
- The belt check distinguishes no equipped Gillsplash/Finwave from a relevant belt without Bloodshot.
- Jawbus Finder matches the exact skull-prefixed `☠ <player> was killed by Lord Jawbus.` line. Party joins, departures, transfers, party chat, and `/party list` role lines keep the exclusion list current.
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

The distributable JAR is written to `build/libs/MommyMods-<version>.jar`.

## Contributions

Issues and pull requests are welcome. Keep changes focused, test against Minecraft 26.1.2, and include a clear description of user-facing behavior.

## License

MommyMods is licensed under the [MIT License](LICENSE).
