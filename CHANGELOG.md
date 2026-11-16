# Changelog

## 0.9.4

- **Themes in the older `.json` format** from the Bitwig Theme Editor (most of the "awesome-bitwig-themes" list: Nord, GruvBit, Dracula, Macchiato, Cubitwig, Ghosty…) are listed too and turned into `.bte` when installed. A `.json` beside a `.bte` of the same name is left out (it's the same theme, older); other `.json` files (palettes, catalogues) are not listed.
- **List repositories:** a theme source whose repository has no themes but links to theme repositories in its README (like `Berikai/awesome-bitwig-themes`) brings in the themes of every repository it links to.
- Theme details show the format.
- **Bottom bar:** on the left a **sun / moon** switch for the light or dark look (light colours on warm off-white), on the right a button to the launcher's channel on the Bitwig Discord.
- Changing the look or the language keeps the window where it was, at the same size and on the same tab.
- **Copy and paste on macOS:** ⌘V, ⌘C, ⌘X and ⌘A work in every text field (pasting a repository link into Sources didn't); a right-click menu with Cut, Copy, Paste and Select all in every text field.
- A bigger app icon in the top bar, with the version under the name; the note about who makes the launcher sits at the bottom of the Patchbay page.
- Theme and device details scroll when they are taller than the window, so the buttons stay in view.

## 0.9.3

- **New look:** the sections are tabs in a bar across the top (more room for the lists), Bitwig's orange for what you can do, a teal "Start Patchbay", Inter and JetBrains Mono (bundled, SIL Open Font License), thin frames, status as coloured text with a mark.
- **List and details side by side, resizable:** the details take about a third of the window (more room for descriptions and previews in a bigger window); drag the divider between them to change it, double-click it to reset. The launcher remembers it for Mods, Devices and Themes.
- **The launcher updates itself:** when a newer Patchbay Launcher release is out, the Patchbay section shows it (with a red count on the tab and a notification); "Update the launcher" downloads this system's package from the release, checks its SHA-256, puts it in place of the installed copy and starts it again. Patchbay, mods and settings are not touched. Releases must keep the `Patchbay-Launcher-<version>-macos.zip`, `-windows.zip` and `-linux.tar.gz` files.
- **Sources:** built-in sources can now be changed and removed too (marked "built-in · changed"); "Restore default" undoes changes to one, "Restore built-in" brings them all back. Removing asks first; Patchbay always keeps at least one source. Nothing is picked when the section opens.
- **Updates as macOS shows them:** a red count on the Patchbay and Mods tabs, and an "Update available" bar; in Mods it lists what changed and a click shows only the mods with updates.
- **Linux (beta):** `Patchbay-Launcher-<version>-linux.tar.gz` with a start script that finds Bitwig's Java (`/opt/bitwig-studio/lib/jre`, or the Bitwig chosen in Patchbay), Patchbay's Linux build (`BML-*-linux.zip`), settings in `~/.config/bml`, data in `~/.local/share/PatchbayLauncher`, Bitwig's version from dpkg / rpm / Flatpak, notifications with `notify-send`, and an "Add to the applications menu" entry.
- Folders and web pages open with `xdg-open` where Java can't open them.
- **Mods work on your Bitwig?** The launcher checks each mod's `built_against` against the map packs in the installed Patchbay, the way Patchbay does: mods that can't run are marked "incompatible", can't be ticked, sit at the end of the list and say why; mods built for another Bitwig say Patchbay translates them. A **Show** filter: All, Compatible, Installed, Updates, Incompatible. "Install anyway" for a mod that won't run.
- The Bitwig chosen in Patchbay's settings is the one the launcher reads the version of (macOS and Windows); when the version can't be read, the Mods section asks for it.
- "Start Patchbay" is orange.
- **Themes section:** `.bte` themes for Patchbay's Framework: Themes, from [csabacsaba/bw6themes](https://github.com/csabacsaba/bw6themes) and any repository you add under Sources → Themes. Preview, install (checked against GitHub's file hashes) into `components/` of the active Patchbay, update, remove, and "Use this theme", which picks it in Patchbay's settings and switches Framework: Themes on. The active theme is shown at the top of the list.

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
