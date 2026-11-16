# Changelog

## 0.9.3

- **Linux (beta):** `Patchbay-Launcher-<version>-linux.tar.gz` with a start script that finds Bitwig's Java (`/opt/bitwig-studio/lib/jre`, or the Bitwig chosen in Patchbay), Patchbay's Linux build (`BML-*-linux.zip`), settings in `~/.config/bml`, data in `~/.local/share/PatchbayLauncher`, Bitwig's version from dpkg / rpm / Flatpak, notifications with `notify-send`, and an "Add to the applications menu" entry.
- Folders and web pages open with `xdg-open` where Java can't open them.
- **Mods work on your Bitwig?** The launcher checks each mod's `built_against` against the map packs in the installed Patchbay, the way Patchbay does: mods that can't run are marked "not available", can't be ticked, sit at the end of the list and say why; mods built for another Bitwig say Patchbay translates them. A **Show** filter: all, work on my Bitwig, installed, updates, not available. "Install anyway" for a mod that won't run.
- The Bitwig chosen in Patchbay's settings is the one the launcher reads the version of (macOS and Windows); when the version can't be read, the Mods section asks for it.
- "Start Patchbay" is orange.

## 0.9.2

- Mods: the list no longer stays empty after installing Patchbay for the first time.
- GitHub answers are cached with their ETag: unchanged lists don't count against GitHub's hourly limit, and when GitHub can't be reached or is rate-limiting, the last list is shown instead of an empty one. A failed request is retried once.
- Mods: up to 500 releases per source are read (100 per page) instead of 30, so older mods no longer drop out of the catalogue.
- Mod details show which Bitwig version the mod was built for (`built_against` in its manifest).
- About card in the Patchbay section: the launcher is an unofficial project made and supported by Marius R; "Report a problem" opens this repository's Issues. The same note is in the README and the install notes.
- A refresh asked for while the list is loading (after changing sources, say) now runs when the first one ends.

## 0.9.1

- New app icon (patch panel with two cables).

## 0.9.0

- First version for Windows 10 / 11: `Patchbay Launcher.exe`, no console window, Start menu and desktop shortcuts.
- macOS disk image.

## 0.8.x

- New look in Patchbay's colours, sidebar, Sources section (Patchbay, Mods, Devices), app icon.

## 0.3 – 0.7

- Mods section with checkboxes, "Select all", details; English, Polish, German, French; Devices section (polarity/bitwig-devices); "Add to Dock".

## 0.2

- First version for macOS: installs, updates and starts Patchbay without Terminal.
