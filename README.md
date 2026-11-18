# Shout

Android soundboard and megaphone for a Bluetooth speaker. Put the speaker outside the car (or
under it), sit in the cab with your phone or tablet, and fire off sound effects or talk through
the speaker. Built for festival nonsense.

## What it does (v0.2.0)

**Soundboard.** The main screen is a grid of your sounds, each one a big button with the name you
gave it. Tap a button to play it through the speaker; tap it again to stop it. Sounds can overlap.
A **Stop all** button shows up while anything is playing.

**The bottom bar** is always on screen and has three buttons:

| Button | What it does |
|---|---|
| **Edit sounds** | Switches the board into edit mode (button turns green and says **Done**). |
| **Megaphone** | Toggle. Tap once: your mic goes live through the speaker and stays live. Tap again: off. |
| **Hold to talk** | Push to talk. Live only while your finger is on it. |

**Edit mode** (tap **Edit sounds**):

- **Add from files**: pick any sound file on the device (MP3, WAV, M4A, AAC, OGG, FLAC, OPUS...)
  and give it a name. **To add a batch**, long-press one file in the picker, tap the others, then
  **Select**: each becomes a button named after its file (`air_horn.mp3` becomes "air horn"), and
  you can rename them later. Every file is shown in the picker; if you pick something that isn't a sound,
  Shout says so. Shout keeps its own copy, so the button still works if you move or delete the
  original, and files from Downloads, Google Drive or a Files app all work.
- **Record new**: tap to record from the mic, tap again to stop, name it. It's saved inside the app.
- **Rename**: tap a sound. **Remove**: tap its red X.
- **Size slider** (zoom icons at the top): smaller buttons fit more on screen. The board scrolls
  like an app drawer once there are more sounds than fit.
- Tap **Done** to go back to playing.

**Megaphone settings** (slider icon, top right): mic volume (0 to 300%) and an echo effect.

**Top bar** shows the connected Bluetooth speaker's name, or "Phone speaker" if none is connected,
so you know where the sound is going before you hit anything.

## Setup

1. Install the APK from [Releases](https://github.com/FaradayMicrosystems/shout/releases)
   (allow "install unknown apps" for your browser or Files app when asked).
2. Pair the Bluetooth speaker in Android's Bluetooth settings, as for music.
3. Open Shout. The top bar should show the speaker's name.
4. The first time you use Megaphone or Record, allow microphone access.
5. Use the phone's volume keys for overall speaker loudness.

Works on phones and tablets, Android 8.0 or newer, portrait or landscape.

## Things to know

- **Bluetooth delay.** Bluetooth speakers add about 0.15 to 0.3 seconds of delay on their own. The
  app's side is kept as short as it can be (low-latency audio mode, tiny buffers), but you will hear
  yourself slightly late. Fine for shouting at people, odd if you try to sing along with yourself.
- **Feedback squeal.** If the speaker is close to the phone, the mic hears the speaker and howls.
  Keep it outside the cab, or turn the Megaphone volume down.
- **Hold to talk** keeps the mic warm (silent) for 4 seconds after you let go, so a quick second
  press doesn't cut off your first word while the speaker wakes up.
- The screen stays on while the Megaphone or a recording is running. Megaphone and Record can't run
  at the same time, so starting a recording turns the Megaphone off.

## Building

Push to `main` or `experimental` and GitHub Actions builds the APK and publishes it as a release,
the same way as Truckie:

- `main` publishes the stable release `v<version>` with `shout-v<version>.apk`.
- `experimental` publishes `v<version>e+<build>` pre-releases; the
  [experimental](https://github.com/FaradayMicrosystems/shout/releases/tag/experimental) page always
  has the newest one.

The version lives in `baseVersionName` in `app/build.gradle.kts` (X.Y.Z, see `CHANGELOG.md`).

**Signing.** This repo is public, so no key is committed. Until you add the repository secrets
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`, each build uses a throwaway
debug key, which means you must uninstall the old version before installing a newer one.

Locally: open the folder in Android Studio, or run `./gradlew :app:assembleDebug` (JDK 17, Android SDK 35).

## Code map

| File | What's in it |
|---|---|
| `MainActivity.kt` | All the screens: soundboard grid, edit mode, bottom bar, dialogs. |
| `SoundStore.kt` | The saved list of sounds (name + file location). |
| `Player.kt` | Plays sounds on the media stream, which Android sends to the Bluetooth speaker. |
| `Recorder.kt` | Records the mic to `.m4a`. |
| `Megaphone.kt` | Live mic to speaker: `AudioRecord` + `AudioTrack` in low-latency mode, volume, echo. |
