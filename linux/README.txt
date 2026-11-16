Patchbay Launcher 0.9.3 for Linux (beta)
========================================

Installs, updates and starts Patchbay (the mod loader for Bitwig Studio) and
downloads mods and devices. Unofficial: not made by Bitwig GmbH or Patchbay's authors.

Linux support is new: Patchbay's own Linux build is a pre-release for testers
too. Please report what works and what doesn't (see Support below).

Requirements
- Linux x64
- Bitwig Studio (6.0 – 6.1.4, what Patchbay for Linux supports) installed from bitwig.com (.deb, in /opt/bitwig-studio).
  The launcher and Patchbay run on the Java that comes with it.
- Bitwig from Flathub keeps its Java inside the Flatpak, where the launcher can't
  reach it. Then install a Java 21 runtime with desktop support (for example
  openjdk-21-jre, not "headless"), or set PATCHBAY_LAUNCHER_JAVA to one.

Install
1. Unpack the archive anywhere, for example into your home folder:
     tar -xzf Patchbay-Launcher-0.9.3-linux.tar.gz
2. Start it: double-click "patchbay-launcher" in the folder (choose "Run" if
   asked), or in a terminal:
     ./Patchbay-Launcher/patchbay-launcher
3. The first time, the launcher offers to add itself to the applications menu.
4. In the Patchbay section pick a version and click "Install".

Where things are
- Patchbay, mods and devices: ~/.local/share/PatchbayLauncher
- The launcher's log: ~/.local/share/PatchbayLauncher/logs/launcher.log
- Patchbay's own settings: ~/.config/bml
- Menu entry: ~/.local/share/applications/patchbay-launcher.desktop

Uninstall
- Delete the Patchbay-Launcher folder, ~/.local/share/PatchbayLauncher and the
  menu entry above.

Good to know
- Mods only work when Bitwig is started from the Patchbay window with Load.
  Bitwig opened the usual way starts without mods.
- Mods and devices are code that runs inside Bitwig. Only add sources you trust.

Support
- Patchbay Launcher is an unofficial project made and supported by Marius R,
  separate from Patchbay. Please report problems with the launcher at
  https://github.com/arius77R/patchbay-launcher/issues
  (or "Report a problem" in the app), not to the authors of Patchbay.
