# Patchbay Launcher guide

The same guide is in the launcher (Guide button on the bottom bar), in English, Polish, German and French.

Patchbay Launcher installs and starts Patchbay (BML) and gets mods, devices and themes for Bitwig Studio, with no Terminal. It sorts the lists into categories and favourites, lets you switch things off and uninstall them, adds new Sources and warns about mods their authors removed or withdrew.

An unofficial project by Marius R, not affiliated with Bitwig GmbH or the author of Patchbay.

## Patchbay

In the Patchbay tab you install, update and switch Patchbay versions. Your mods and themes move to the new version by themselves.

- **Restore Patchbay's mods:** the button shows when a mod that came with Patchbay is missing, e.g. after a framework was uninstalled. The launcher gets the same version again and adds only the missing mods, switched on. Nothing is overwritten. Bitwig and the Patchbay window must be closed.

## Mods

In Mods you choose, install, update, switch off and uninstall mods for Patchbay.

- **Side panel:** All, Favourites and each category with its icon and number of mods. A click filters the list. « folds the panel to icons, » opens it again.
- **Favourites:** click the star beside a mod. Favourites are in the settings backup.
- **Category:** comes from the mod's name ("Category: Name"). A mod without a category from the list goes under Other.
- **Show:** All, Compatible, Installed, Updates, Incompatible, Removed from repo and Not in sources. To get rid of all mods not in sources at once: Show → Not in sources, "Tick all", then "Uninstall".
- **Install:** tick mods and click "Install ticked". "Tick all" ticks the mods to install or update. When the list has nothing to install (e.g. Show → Not in sources or Removed from repo), it ticks all installed ones, except mods from Patchbay. Ticks stay when you change the category, Show or Search, so you can pick mods from several categories and install them together.
- **Switch off / Switch on:** the mod stays on disk, Patchbay doesn't load it. Tick one or more and use the button under the list.
- **Uninstall:** removes the ticked mods from disk, after you confirm. The launcher warns when another switched-on mod needs it.
- "Switch off" and "Uninstall" are only under the list. They stay grey until a mod has a tick.

Switching off and uninstalling need Bitwig and the Patchbay window closed.

**States worth knowing:**

| State | What it means | What to do |
| --- | --- | --- |
| from Patchbay | The mod came with Patchbay (frameworks: Actions and Menus, Browser, Themes, Nitro Devices and others). Other mods, devices and themes need it. | Keep it. It is updated with Patchbay. |
| removed | The author removed the mod from the repository. It still works, but won't get updates. | Keep it or uninstall it. |
| withdrawn | The author withdrew the mod because of problems. The reason is in the details. The launcher asks once whether to switch it off. | Best switch it off or uninstall it. |
| not in sources | No source you have switched on offers it: it was added by hand, or its repository is gone. | Keep it or uninstall it. |
| incompatible | It doesn't work with your Bitwig. | Wait for a new version. |

## Devices

Devices gets Bitwig device files (.bwdevice, .bwmodulator, Grid modules) and registers them with Patchbay.

- **Build:** the launcher picks it for your Bitwig. You can change it in the box at the top left.
- **Side panel:** each type has its own icon (Audio FX, Instrument, Note FX, Note generator, Modulator, Grid module). The star adds to Favourites.
- **Download:** tick and click "Download ticked".
- **Switch off / Switch on:** the file stays on disk with .off added, and Bitwig doesn't show it. The buttons are under the list and act on the ticked devices.
- **Uninstall:** removes the file from disk.
- **removed / withdrawn:** as in Mods. A file its repository no longer has stays in the list, so it can be uninstalled.

Changes need Bitwig closed.

## Files no longer in the repository

The launcher also shows mods and devices you have on disk that no source offers any more:

- **removed:** the author removed them from the repository;
- **not in sources:** they were added by hand, or the source is gone.

They still work, but won't get updates. If you don't want them:

**Mods**

1. Quit Bitwig and the Patchbay window.
2. Show → Removed from repo or Not in sources. To pick only some, type a name in Search.
3. Tick them, or click "Tick all".
4. Click "Uninstall" under the list and confirm. The launcher lists what it removes and warns if another mod needs something.

Mods from Patchbay don't appear in these views. If you tick one yourself, the launcher warns before removing it.

**Devices**

1. Quit Bitwig.
2. Sort by "Status": removed ones are at the top.
3. Tick them and click "Uninstall" under the list.

## Sources

Sources are the GitHub repositories the launcher gets Patchbay, mods, devices and themes from. There are three tabs:

| Tab | What it shows | What you can do |
| --- | --- | --- |
| Active | The sources you have: built-in and your own. | Switch on, switch off, edit, remove, add your own. |
| Source list | Repositories from the recommended list and those found on GitHub by the topic patchbay-mod, patchbay-devices or patchbay-themes. The ones you have are marked "active". | Click a repository and add it to the active ones. |
| Recommended | Repositories from the list arius77R/bitwig-mods-list that you don't have yet. The red number counts the new ones. | Click a repository and add it to the active ones. |

- **Repository card:** a click shows the author, the description, what it holds (e.g. "Mods: 7"), the last change, the licence and stars. "Add to active" works when there is something to get. "Repository page" opens it on GitHub.
- **Refresh:** on every tab, gets the sources, the recommended list and the topic search from GitHub again, and checks that the repositories still exist. A change to the list can take a few minutes to show.
- **doesn't exist:** a red badge beside a source in Active when its repository was deleted, made private or renamed. The source doesn't go by itself. "Edit" has "Author's page" and "Remove source".

GitHub allows 60 requests an hour from one address. When they run out, the launcher shows what it got last and marks nothing as removed.

## The launcher, reports and the window

- **The launcher's page:** click "Patchbay Launcher" at the top left. You see your version, "What's new" and the system information. When a new version is out, "Update" is there, and "New version" beside the version number in the top bar.
- **System information:** the versions of the launcher, the system, Java, Bitwig, Patchbay and the list of mods, with no paths or personal data. "Copy" puts it on the clipboard.
- **Report a problem** (bottom bar): opens a new issue on GitHub with the system information filled in. Add what happened and what should have happened.
- **Bitwig warning:** before "Start Patchbay" the launcher checks that Patchbay supports your Bitwig. If not, it shows the supported versions and asks: Cancel or Start anyway.
- **Window:** the first time it fits the screen, then it opens where and as big as it was when you closed it.

## When something doesn't add up

1. **Refresh:** in Mods, Devices, Themes or Sources. That's usually enough.
2. **Restore Patchbay's mods** (Patchbay tab), when mods, devices or themes stopped working after something was uninstalled.
3. **Clear the GitHub memory** (Settings). A last resort, when after "Refresh" the launcher still shows a mod, device or theme its repository no longer has, doesn't see a new version that is already on GitHub, or shows errors while getting lists. It deletes the kept lists and fetches everything again. Installed mods, devices, themes and settings stay. It uses part of GitHub's limit (60 requests an hour), so don't use it often. It doesn't remove files not in sources; "Uninstall" does that.
4. **Report a problem,** if none of this helped.

## For authors of mods, devices and themes

The full rules are in [PUBLISHING](https://github.com/arius77R/patchbay-launcher/blob/main/docs/PUBLISHING.md). The main points:

1. **One ZIP per mod** for every system: `<mod-id>-<version>.zip` in a release, and `<mod-id>/mod.json` in the repository.
2. **The mod's name starts with a category** from the fixed list, e.g. "Arranger: Add Automation Points".
3. **A repository topic,** so the launcher finds it: ⚙ next to About → Topics → `patchbay-mod`, `patchbay-devices` or `patchbay-themes`.
4. **The recommended list:** ask for your repository to be added to [arius77R/bitwig-mods-list](https://github.com/arius77R/bitwig-mods-list) through Issues or a pull request.
5. **Withdrawing because of problems:** a `withdrawn.json` at the top of the repository, with the mod's `id` or the device's file name and the reason. The launcher shows "withdrawn" and asks the user once whether to switch it off.
6. **Simply deleting** the `<mod-id>/` folder is enough when a mod just comes to an end. Users see "removed".
