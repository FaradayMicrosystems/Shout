# Changelog

Versions follow X.Y.Z: X for primary releases, Y for user-visible changes and features, Z for minor
fixes and looks (colours, design, logo). Experimental builds carry the letter e (experimental);
stable releases carry none. Stable releases come from `main`. Work in progress lands on `experimental`
first as `vX.Y.Ze+<build>` until Aurora calls a full release.

## v0.2.0 — 2026-11-18

- **Add many sounds at once.** In Edit sounds > Add from files, long-press a file, tap the rest,
  then Select. Each one is named after its file (rename any of them later). Files that aren't
  playable sounds are skipped and Shout says how many. Picking a single file still asks for a name.

## v0.1.1 — 2026-11-18

- **MP3 and WAV files can be added.** The file picker no longer hides files: many phones label
  downloaded MP3/WAV files as generic data, which the old "audio only" filter greyed out. Shout now
  checks the file itself after you pick it and tells you if it can't play it.
- Added sounds are copied into Shout, so they keep working if the original is moved or deleted,
  and files from Google Drive or other apps work too.

## v0.1.0 — 2026-11-18

First version.

- **Soundboard:** your own named sounds as big buttons. Tap to play through the Bluetooth speaker,
  tap again to stop. Several can play at once; **Stop all** appears while anything is playing.
- **Edit sounds** (bottom bar): add sounds from your files, record new ones, rename (tap) or remove
  (red X), and resize the buttons with the zoom slider. The board scrolls when it's full.
- **Megaphone** (bottom bar): a toggle that keeps the mic live, and **Hold to talk** that's live only
  while held. Volume (up to 300%) and echo under the slider icon, top right.
- Bluetooth status in the top bar shows which speaker the sound is going to.
