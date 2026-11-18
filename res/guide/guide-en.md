Patchbay Launcher installs and starts Patchbay (BML) and gets mods, devices and themes for Bitwig Studio, with no Terminal. Version 0.9.6 sorts the lists into categories and favourites, lets you switch things off and uninstall them, adds new Sources and warns about mods their authors removed or withdrew.

An unofficial project by Marius R, not affiliated with Bitwig GmbH or the author of Patchbay.

## Mods

In Mods you choose, install, update, switch off and uninstall mods for Patchbay.

- **Side panel:** All, Favourites and each category with its icon and number of mods. A click filters the list. « folds the panel to icons, » opens it again.
- **Favourites:** click the star beside a mod. Favourites are in the settings backup.
- **Category:** comes from the mod's name ("Category: Name"). A mod without a category from the list goes under Other.
- **Show:** All, Compatible, Installed, Updates, Incompatible, Removed from repo and Not in sources. To get rid of all mods not in sources at once: Show → Not in sources, "Tick all", then "Uninstall".
- **Install:** tick mods and click "Install ticked". "Tick all" ticks only the ones to install or update.
- **Switch off / Switch on:** the mod stays on disk, Patchbay doesn't load it. Tick several and use the button under the list.
- **Uninstall:** removes the mod from disk, after you confirm. The launcher warns when another switched-on mod needs it.

Switching off and uninstalling need Bitwig and the Patchbay window closed.

**States worth knowing:**

| State | What it means | What to do |
| --- | --- | --- |
| removed | The author removed the mod from the repository. It still works, but won't get updates. | Keep it or uninstall it. |
| withdrawn | The author withdrew the mod because of problems. The reason is in the details. The launcher asks once whether to switch it off. | Best switch it off or uninstall it. |
| not in sources | No source you have switched on offers it: it came with Patchbay, was added by hand, or its repository is gone. | Keep it or uninstall it. |
| incompatible | It doesn't work with your Bitwig. | Wait for a new version. |

## Devices

Devices gets Bitwig device files (.bwdevice, .bwmodulator, Grid modules) and registers them with Patchbay.

- **Build:** the launcher picks it for your Bitwig. You can change it in the box at the top left.
- **Side panel:** each type has its own icon (Audio FX, Instrument, Note FX, Note generator, Modulator, Grid module). The star adds to Favourites.
- **Download:** tick and click "Download ticked".
- **Switch off / Switch on:** the file stays on disk with .off added, and Bitwig doesn't show it.
- **Uninstall:** removes the file from disk.
- **removed / withdrawn:** as in Mods. A file its repository no longer has stays in the list, so it can be uninstalled.

Changes need Bitwig closed.

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
- **Clear the GitHub memory** (Settings): when the launcher shows something its repository no longer has. It deletes the kept lists and fetches everything again. Installed things stay.
- **Window:** the first time it fits the screen, then it opens where and as big as it was when you closed it.

## For authors of mods, devices and themes

The full rules are in [PUBLISHING](https://github.com/arius77R/patchbay-launcher/blob/main/docs/PUBLISHING.md). The main points:

1. **One ZIP per mod** for every system: `<mod-id>-<version>.zip` in a release, and `<mod-id>/mod.json` in the repository.
2. **The mod's name starts with a category** from the fixed list, e.g. "Arranger: Add Automation Points".
3. **A repository topic,** so the launcher finds it: ⚙ next to About → Topics → `patchbay-mod`, `patchbay-devices` or `patchbay-themes`.
4. **The recommended list:** ask for your repository to be added to [arius77R/bitwig-mods-list](https://github.com/arius77R/bitwig-mods-list) through Issues or a pull request.
5. **Withdrawing because of problems:** a `withdrawn.json` at the top of the repository, with the mod's `id` or the device's file name and the reason. The launcher shows "withdrawn" and asks the user once whether to switch it off.
6. **Simply deleting** the `<mod-id>/` folder is enough when a mod just comes to an end. Users see "removed".
