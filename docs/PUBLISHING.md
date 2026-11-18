# Publishing mods so they work in Patchbay Launcher

This guide is for authors of mods for [Patchbay](https://github.com/shaboogen-dev/Patchbay). Patchbay Launcher finds mods in GitHub releases on its own, lists them for users, installs them and keeps them up to date. That only works when the repository follows the rules below.

Reference repositories that work without any settings:
- [shaboogen-dev/Mods](https://github.com/shaboogen-dev/Mods) — many mods in one release,
- [polarity/bitwig-mods](https://github.com/polarity/bitwig-mods) — one release per mod.

Repositories that don't follow these rules are not supported. The launcher does not adapt to other layouts.

## Required

1. **One ZIP per mod, the same for every operating system.**
   File name: `<mod-id>-<version>.zip`, for example `add-automation-points-1.1.0-dev.2.zip`.
   - `<version>` is `MAJOR.MINOR.PATCH`, optionally followed by `-dev.N`, for example `1.1.0` or `1.1.0-dev.2`.
   - No operating system or other words in the name: **not** `…-1.0.0.macos.zip`, **not** `…-1.0.0-windows.zip`, **not** `…-1.0.0-platform.zip`.
   - Differences between operating systems and Bitwig versions are handled by Patchbay's maps. One package has to work on macOS, Windows and Linux.

2. **The ZIP is a package Patchbay accepts through Mods → Add Mod…**
   It contains `mod.json` with:
   - `mod.id` — exactly the `<mod-id>` in the file name,
   - `mod.version` — exactly the `<version>` in the file name,
   - `mod.name`, `mod.author`, `mod.description`,
   - `built_against.bitwig_version` (plus `platform` and `architecture`) — the Bitwig the mod was built for.

3. **The same `mod.json` in the repository, at `<mod-id>/mod.json` on the default branch.**
   The launcher reads the name, author, description and Bitwig compatibility from it before anyone downloads the package. Without it, users see no description and can't tell whether the mod works on their Bitwig.

4. **Packages as GitHub release assets** (Releases → Assets), in a published release (not a draft).
   - A release can hold many mods or one mod — both layouts work.
   - A new version of a mod is a new file with a higher version number. Don't replace a file under the same version.

5. **Dependencies in `requires`.**
   List every framework and mod your mod needs in `requires` in `mod.json`:
   ```json
   "requires": [ { "id": "action-menu-composition-framework", "min_version": "0.6.0-dev.2" } ]
   ```
   The launcher installs them for the user. Without this, users have to guess what to tick.

6. **A unique `mod.id`.**
   Don't publish your own copy of someone else's mod or framework under the same `mod.id`. If you need a framework, list it in `requires` and use its author's version.

7. **Don't change Bitwig's files.**
   A mod works through Patchbay only. Installers that patch `bitwig.jar` break Patchbay ("No installed map supports bitwig.jar").

8. **The mod's name starts with a category from the list: `Category: Name`.**
   For example `Arranger: Add Automation Points`. The category says where in Bitwig the mod works. Pick exactly one from the list, spelled exactly the same way (capital letters included):

   | Category | Where in Bitwig |
   |---|---|
   | `Arranger` | the timeline, tracks, track headers |
   | `Clip Launcher` | clips in the clip launcher |
   | `Piano Roll` | editing notes |
   | `Audio Editor` | editing audio clips |
   | `Automation` | automation |
   | `Mixer` | the mixer |
   | `Devices` | the device chain and device parameters |
   | `Browser` | the browser |
   | `Inspector` | the inspector |
   | `Transport` | transport, tempo |
   | `Interface` | panels, notifications, appearance |
   | `Commands` | commands and keyboard shortcuts |
   | `Project` | the project, export |
   | `Framework` | libraries other mods need |
   | `Other` | anything that fits none of the above |

   Don't make up your own categories or collection names. When no category fits, use `Other`. The launcher also shows a mod with a category outside the list under `Other`. If you think a category is missing, ask the author of Patchbay Launcher (Marius R) to add it: in the [Issues](https://github.com/arius77R/patchbay-launcher/issues) or on the Bitwig Discord in the #launcher channel.

## Recommended

- **A table in the release description:**
  ```
  | Mod | Download |
  |---|---|
  | Arranger: Add Automation Points | `add-automation-points-1.1.0-dev.2.zip` |
  ```
- **Bundles and installers** may sit in the release alongside the individual packages, not instead of them.
- **So the launcher finds the repository:** on the repository's page click ⚙ next to **About** and add one topic under **Topics**:
  - `patchbay-mod` for mods,
  - `patchbay-devices` for devices (.bwdevice, .bwmodulator, Grid modules),
  - `patchbay-themes` for themes (.bte).

  The repository then shows up in Patchbay Launcher (from version 0.9.6) under Sources → "Source list". Everyone sees who made it and what it holds there, and adds it with one click. Only public repositories are shown, not archived ones and not forks.
  You can also ask for the repository to be added to the list of recommended sources, [arius77R/bitwig-mods-list](https://github.com/arius77R/bitwig-mods-list), through its Issues or a pull request. Repositories on that list are marked "recommended" in the launcher.

## Checklist before a release

- [ ] Every mod has one ZIP `<mod-id>-<version>.zip`, with no operating system in the name.
- [ ] `mod.id` and `mod.version` inside the ZIP match the file name.
- [ ] `<mod-id>/mod.json` in the repository matches the one in the ZIP.
- [ ] Every mod's name starts with a category from the list (`Category: Name`).
- [ ] Every dependency is in `requires`.
- [ ] No `mod.id` repeats someone else's.
- [ ] The release is published (not a draft) and the packages are in Assets.
- [ ] The package can be added in Patchbay through Mods → Add Mod… and switched on with Load.
- [ ] The repository has the topic `patchbay-mod` (or `patchbay-devices` / `patchbay-themes`).

## Ready-made prompt for an AI agent (Claude, Codex…)

Copy this and paste it to the agent that prepares your release:

> Prepare a release of my Patchbay mod (or mods) that is compatible with Patchbay Launcher. Follow these rules without exception:
> 1. Each mod is one ZIP file, the same for every operating system, named `<mod-id>-<version>.zip`, where the version is `MAJOR.MINOR.PATCH` or `MAJOR.MINOR.PATCH-dev.N`. No operating system or other words in the file name.
> 2. The ZIP contains `mod.json`; `mod.id` and `mod.version` must be exactly as in the file name. Fill in `mod.name` as `Category: Name`, where Category is exactly one of: Arranger, Clip Launcher, Piano Roll, Audio Editor, Automation, Mixer, Devices, Browser, Inspector, Transport, Interface, Commands, Project, Framework, Other — no categories of your own. Also fill in `mod.author`, `mod.description` and `built_against`.
> 3. Save the same `mod.json` in the repository at `<mod-id>/mod.json` on the default branch.
> 4. List every dependency (frameworks, other mods) in `requires` with `id` and `min_version`. Don't publish copies of other authors' frameworks under their `mod.id`.
> 5. Create a published GitHub release (not a draft) and add each ZIP as a separate file in Assets. In the release description add a `| Mod | Download |` table with the mod name and the file name in backticks.
> 6. Don't change Bitwig's files and don't include installers that do.
> Finally check: file names, the categories in `mod.name`, `mod.id` and `mod.version` matching the names, `<mod-id>/mod.json` present in the repository, `requires`, release not a draft. List what you checked and anything that couldn't be met.

## How to check in the launcher

Sources → Mods → **+ Add** → type `owner/repository` → **Check**. The launcher should say "Mods found: N", and in the Mods list every mod should show a name, type, author, description and whether it works with the user's Bitwig. Leave "Which release files" empty — a repository that follows these rules doesn't need it.
