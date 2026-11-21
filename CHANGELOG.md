# Changelog

## 0.9.7

- **Fixed: the launcher couldn't update itself on Windows.** It closed, reopened and was still the old version: Windows wouldn't rename the launcher's folder while the update script was working inside it. The script now works from the folder above, tries again for a few seconds if something holds the folder, and if the update still fails the launcher says why on its next start and links the release page. Thanks to Alban1806 for the report (#2).
- **Ticks add up across categories:** in Mods and Devices, ticks stay when you change the category, Show or Search, so you can pick from several categories and install them together. Tick all adds the visible ones to the ticked; Untick all removes only those. Thanks to rjsmith for the idea (#1).

**On Windows with 0.9.4–0.9.6:** please update by hand this once (download the Windows ZIP and replace the launcher's folder; settings, Patchbay and mods stay). From 0.9.7 on, updates work from the launcher.

## 0.9.6

- **Guide in the launcher:** a Guide button on the bottom bar; in English, Polish, German and French, works offline and always fits the version.
- **Sources in three tabs:** **Active** (the sources you use), **Source list** (GitHub repositories with the topic `patchbay-mod`, `patchbay-devices` or `patchbay-themes`) and **Recommended** (the list kept in `arius77R/bitwig-mods-list`).
- **Repository card:** a click on a repository shows its author, what it holds, last change, licence and stars, with **Add to active** and **Repository page**. A red count on the Sources tab says how many recommended ones are new.
- **Refresh on every Sources tab:** reads the sources again.
- **A source that no longer exists** on GitHub is marked "doesn't exist", with a button to the author's page. Nothing disappears by itself.
- **Mods and devices removed from their repository:** installed ones are marked "removed" (with a one-time notice) and are no longer offered for install.
- **Withdrawn by the author** (a `withdrawn.json` file): marked "withdrawn" with the reason, and the launcher offers once to switch them off.
- **Installed mods not in any source** show in the list as "not in sources". Show → Not in sources, Tick all and Uninstall remove them in three clicks.
- **Mods that came with Patchbay** (its frameworks) are marked "from Patchbay": kept out of Not in sources and Tick all, with a clear warning before switching one off or uninstalling it. **Restore Patchbay's mods** in the Patchbay tab puts back the missing ones from the same version; nothing is overwritten.
- **Side panel in Mods and Devices:** All, Favourites and the categories with icons and counts; a star marks favourites; favourites are kept in the settings backup.
- **Device type icons:** Audio FX, Instrument, Note FX, Note generator, Modulator, Grid module.
- **Switch off and uninstall several at once:** tick mods, devices or themes and use the buttons under the list; they stay grey until something is ticked. Confirmations list what changes and stay narrow, however many items there are.
- **Launcher page in the main window:** version, update, what's new, and system information with Copy. "Report a problem" adds the system information. A warning before starting Patchbay on a Bitwig its maps don't cover.
- **Settings → Clear the GitHub memory:** a last resort when the launcher still shows something a repository no longer has after Refresh; it fetches everything again, installed things stay.
- **The window** opens scaled to the screen, and after that where and as big as it was closed.
- **Fix:** a mod that is no longer in its repository isn't shown again from an old kept copy.
- **Fix:** a repository that keeps its mods' files elsewhere (no `<mod-id>/mod.json` folders) no longer makes all its mods look removed.

## 0.9.5

- **Search in Mods and Devices:** type part of a name to narrow the list; the type and the author are searched too ("audio fx", "polarity").
- **Settings:** a Settings button in the bottom bar (where the language choice was) opens a page with the language, the settings backup, the data folder and whose project this is. The Patchbay section keeps only Patchbay's versions.
- **Settings backup:** in Settings, "Save a backup…" writes the mods switched on, the chosen theme with the theme files, and the sources into one file; "Restore from a backup…" brings them back, for example on another computer.
- **Type column:** Mods and Devices both start with a Type column, then the name. A mod's type is the part of its name before the colon ("Arranger"), shown apart from the name. Each type has its own colour; neighbouring types never share one. In Mods the sort by category is now called Type, as in Devices.
- **Device author:** a device's details name its author (the owner of its repository).
- **Themes without a screenshot** (such as one added from a file) show a picture of their own colours.
- **After updating, Mods goes back to the list:** when Show was on Updates and nothing is left to update, it returns to the choice before (All by default) instead of an empty list.
- **Enter no longer starts Patchbay:** pressing Enter, for example in Search, did the same as clicking Start Patchbay. Now Patchbay starts only with a click.
- **When the launcher can't open its window** it says so, keeps the details in `logs/start-error.log`, and offers to copy them or show the folder, instead of closing without a word.

## 0.9.4

- **List repositories:** a theme source whose repository has no `.bte` themes but links to theme repositories in its README (like `Berikai/awesome-bitwig-themes`) brings in the `.bte` themes of every repository it links to. Only `.bte` files are themes: older Theme Editor `.json` themes were made for Bitwig 5 and change little in Bitwig 6, so they are not listed.
- **Themes from a file:** "Add a file…" in Themes adds a `.bte` file from this computer. It is added only when it has Bitwig 6's main colours; a theme for an older Bitwig or a file that isn't a theme is refused with the reason. Themes already in Patchbay's `components/` folder that come from no repository are listed as "local file".
- **Sorting:** Mods can be sorted by status, by category (the part of a mod's name before the colon) or newest first, and show when the available version was released; Devices by status, type or name. The choice is remembered.
- **One layout for Mods, Devices and Themes:** the title with the counts at the right of its line, then one row of controls that follows the divider: what to show and how to sort over the list, Tick all at the list's right edge, Refresh at the window's.
- **macOS, the launcher didn't open and said nothing** when Bitwig Studio wasn't at `/Applications/Bitwig Studio.app` and the Mac had an old Java: it started on that Java and closed at once. Now Bitwig Studio is found under any name and in any folder, only a Java 21 or later is used, and when there is none a window says so.
- **Bottom bar:** on the left a **sun / moon** switch for the light or dark look (light colours on warm off-white), on the right a button to the launcher's channel on the Bitwig Discord.
- Changing the look or the language keeps the window where it was, at the same size and on the same tab.
- **Copy and paste on macOS:** ⌘V, ⌘C, ⌘X and ⌘A work in every text field (pasting a repository link into Sources didn't); a right-click menu with Cut, Copy, Paste and Select all in every text field.
- Themes: a theme source that can't be read (GitHub limiting requests, a moved or private repository) is named under the list instead of being left out silently. A repository typed as `github.com/owner/name` without `https://` is accepted too.
- A bigger app icon in the top bar, with the version under the name. "Report a problem" (with a bug icon) and "Project page" moved to the bottom bar; hovering them says whose project this is. "Show the data folder" sits in the version line, and the language choice in the bottom bar beside the dark / light switch.
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
