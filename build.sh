#!/bin/bash
# Builds Patchbay Launcher into build/:
#   build/launcher.jar                              the app itself (Java 21, Swing)
#   build/Patchbay-Launcher-<version>-macos.zip     Patchbay Launcher.app
#   build/Patchbay-Launcher-<version>-linux.tar.gz  patchbay-launcher script + app/launcher.jar
#   build/Patchbay-Launcher-<version>-windows.zip   Patchbay Launcher.exe + app/launcher.jar
#                                                   (only when MinGW-w64 is installed)
# The nicer macOS disk image is made on a Mac with macos/build-dmg.command, after this script.
set -euo pipefail
cd "$(dirname "$0")"

VERSION=$(sed -n 's/.*VERSION = "\(.*\)";.*/\1/p' src/PatchbayLauncher.java)
OUT=build
rm -rf "$OUT"
mkdir -p "$OUT/classes"

echo "Patchbay Launcher $VERSION"

# 1. launcher.jar
javac --release 21 -d "$OUT/classes" src/*.java
cp res/icon-128.png res/icon-256.png "$OUT/classes/"
for f in discord.png github.png kofi.png; do [ -f "res/$f" ] && cp "res/$f" "$OUT/classes/"; done
mkdir -p "$OUT/classes/fonts" && cp res/fonts/* "$OUT/classes/fonts/"
jar cfm "$OUT/launcher.jar" manifest.txt -C "$OUT/classes" .
echo "  launcher.jar"

# 2. macOS app
APP="$OUT/mac/Patchbay Launcher.app"
mkdir -p "$OUT/mac"
cp -R "macos/app/Patchbay Launcher.app" "$OUT/mac/"
cp "$OUT/launcher.jar" "$APP/Contents/Resources/launcher.jar"
chmod +x "$APP/Contents/MacOS/patchbay-launcher"
(cd "$OUT/mac" && zip -qry -X "../Patchbay-Launcher-$VERSION-macos.zip" "Patchbay Launcher.app")
echo "  Patchbay-Launcher-$VERSION-macos.zip"

# 3. Linux: a folder with the start script, packed as .tar.gz (keeps the script runnable)
LIN="$OUT/linux/Patchbay-Launcher"
mkdir -p "$LIN/app"
cp linux/patchbay-launcher linux/README.txt "$LIN/"
cp res/icon-256.png "$LIN/patchbay-launcher.png"
cp "$OUT/launcher.jar" "$LIN/app/launcher.jar"
chmod 755 "$LIN/patchbay-launcher"
tar -C "$OUT/linux" --owner=0 --group=0 -czf "$OUT/Patchbay-Launcher-$VERSION-linux.tar.gz" Patchbay-Launcher 2>/dev/null \
  || tar -C "$OUT/linux" -czf "$OUT/Patchbay-Launcher-$VERSION-linux.tar.gz" Patchbay-Launcher
echo "  Patchbay-Launcher-$VERSION-linux.tar.gz"

# 4. Windows .exe (cross-compiled with MinGW-w64)
if command -v x86_64-w64-mingw32-gcc >/dev/null 2>&1; then
  WIN="$OUT/win/Patchbay Launcher"
  mkdir -p "$WIN/app"
  (cd windows && x86_64-w64-mingw32-windres resources.rc -O coff -o "../$OUT/win/resources.o")
  x86_64-w64-mingw32-gcc -municode -mwindows -O2 -s windows/launcher.c "$OUT/win/resources.o" -o "$WIN/Patchbay Launcher.exe"
  cp "$OUT/launcher.jar" "$WIN/app/launcher.jar"
  cp windows/README.txt windows/PRZECZYTAJ.txt "$WIN/"
  (cd "$OUT/win" && zip -qr -X "../Patchbay-Launcher-$VERSION-windows.zip" "Patchbay Launcher")
  echo "  Patchbay-Launcher-$VERSION-windows.zip"
else
  echo "  (Windows skipped: x86_64-w64-mingw32-gcc not found)"
fi
