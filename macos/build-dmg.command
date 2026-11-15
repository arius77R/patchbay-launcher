#!/bin/bash
# Builds the Patchbay Launcher disk image (DMG) with macOS's own tools: a window with a
# background, the app and an Applications shortcut side by side, and the app icon on the disk.
# Run ./build.sh first, then double-click this file in Finder. The result goes to build/.
#
# macOS may ask once whether Terminal may use files on a removable volume and control Finder
# (Finder lays out the window). Allow both.
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$(cd .. && pwd)"

SRC="$ROOT/build/mac"
DMG="$ROOT/macos/dmg"
[ -d "$SRC/Patchbay Launcher.app" ] || { echo "Run ./build.sh first."; exit 1; }
APP="Patchbay Launcher.app"
VERSION=$(/usr/libexec/PlistBuddy -c "Print CFBundleShortVersionString" "$SRC/$APP/Contents/Info.plist")
VOL="Patchbay Launcher $VERSION"
OUT="$ROOT/build/Patchbay-Launcher-$VERSION-macos.dmg"
WORK=$(mktemp -d /tmp/patchbay-dmg.XXXXXX)
MNT=""

finish() {
  if [ -n "$MNT" ] && [ -d "$MNT" ]; then hdiutil detach "$MNT" -force -quiet || true; fi
  rm -rf "$WORK"
}
trap finish EXIT

echo "Patchbay Launcher $VERSION: building the disk image…"

if [ -d "/Volumes/$VOL" ]; then
  echo "Ejecting the previously opened disk \"$VOL\"."
  hdiutil detach "/Volumes/$VOL" -force -quiet || true
fi

# 1. The disk's contents
STAGE="$WORK/stage"
mkdir -p "$STAGE/.background"
cp -R "$SRC/$APP" "$STAGE/"
chmod +x "$STAGE/$APP/Contents/MacOS/patchbay-launcher"
xattr -cr "$STAGE/$APP"
cp "$DMG/How to install.txt" "$STAGE/"
ln -s /Applications "$STAGE/Applications"
tiffutil -cathidpicheck "$DMG/bg.png" "$DMG/bg@2x.png" -out "$STAGE/.background/bg.tiff" >/dev/null

# 2. A writable image of it
hdiutil create -quiet -volname "$VOL" -srcfolder "$STAGE" -fs HFS+ -format UDRW -ov "$WORK/rw.dmg"
MNT=$(hdiutil attach -readwrite -noverify -noautoopen "$WORK/rw.dmg" | awk -F'\t' '/Apple_HFS/ {print $NF}')
[ -d "$MNT" ] || { echo "The image could not be mounted."; exit 1; }
VOLNAME=$(basename "$MNT")

# 3. The disk's own icon: the custom-icon flag on the volume's root
cp "$SRC/$APP/Contents/Resources/AppIcon.icns" "$MNT/.VolumeIcon.icns"
xattr -wx com.apple.FinderInfo 0000000000000000040000000000000000000000000000000000000000000000 "$MNT"

# 4. The window: size, background, icon places (the text files sit outside the visible area)
osascript <<EOF
tell application "Finder"
  tell disk "$VOLNAME"
    open
    set current view of container window to icon view
    set toolbar visible of container window to false
    set statusbar visible of container window to false
    set the bounds of container window to {200, 120, 860, 548}
    set opts to the icon view options of container window
    set arrangement of opts to not arranged
    set icon size of opts to 128
    set text size of opts to 13
    set background picture of opts to file ".background:bg.tiff"
    set position of item "$APP" of container window to {180, 190}
    set position of item "Applications" of container window to {480, 190}
    set position of item "How to install.txt" of container window to {330, 620}
    close
    open
    update without registering applications
    delay 2
    close
  end tell
end tell
EOF

sync
hdiutil detach "$MNT" -quiet
MNT=""

# 5. The final, compressed, read-only image
rm -f "$OUT"
hdiutil convert "$WORK/rw.dmg" -quiet -format UDZO -imagekey zlib-level=9 -o "$OUT"
echo
echo "Done: $OUT"
echo "You can close this window."
open -R "$OUT"
