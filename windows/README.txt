Patchbay Launcher 0.9.3 for Windows
===================================

Installs, updates and starts Patchbay (the mod loader for Bitwig Studio) and
downloads mods and devices. Unofficial: not made by Bitwig GmbH or Patchbay's authors.

Requirements
- Windows 10 or 11 (64-bit)
- Bitwig Studio installed (the launcher runs on the Java that comes with it)

First start
1. Unpack the WHOLE ZIP file into an ordinary folder, e.g. C:\Patchbay Launcher.
   Don't start the program from the ZIP preview.
2. Double-click "Patchbay Launcher.exe".
3. Windows may show "Windows protected your PC". The program isn't signed with
   a certificate. Click "More info", then "Run anyway". This is needed once.
4. In the Patchbay section pick a version and click "Install".

Where things are
- Patchbay, mods and devices: %LOCALAPPDATA%\PatchbayLauncher
- Patchbay's own settings: %LOCALAPPDATA%\BML
- Start log: %LOCALAPPDATA%\PatchbayLauncher\logs\launcher.log

Good to know
- Mods only work when Bitwig is started from the Patchbay window with Load.
  Bitwig opened the usual way starts without mods.
- Mods and devices are code that runs inside Bitwig. Only add sources you trust.

Support
- Patchbay Launcher is an unofficial project made and supported by Marius R,
  separate from Patchbay. Please report problems with the launcher at
  https://github.com/arius77R/patchbay-launcher/issues
  (or "Report a problem" in the app), not to the authors of Patchbay.
