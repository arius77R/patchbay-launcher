# Changelog

## 0.9.2

- Mods: the list no longer stays empty after installing Patchbay for the first time.
- GitHub answers are cached with their ETag: unchanged lists don't count against GitHub's hourly limit, and when GitHub can't be reached or is rate-limiting, the last list is shown instead of an empty one. A failed request is retried once.
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
