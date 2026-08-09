# Play Console store listing — File Storm

Everything here is ready to paste. Character counts are Play's limits.

---

## App name (30)

```
File Storm: File Manager
```

`File Storm` alone is fine too if you would rather keep it clean — the longer
form only exists because "file manager" in the title helps people find it.

---

## Short description (80)

```
Move, sort and encrypt your files. Fast, private, and works entirely offline.
```

Alternatives, same limit:

```
A beautiful file manager with bulk transfers, duplicate finder and encryption.
```

```
Organise photos by month, find duplicates, and lock folders behind a password.
```

---

## Full description (4000)

```
File Storm is a fast, private file manager for Android. It has no ads, no
accounts, and no internet permission at all — nothing you do can leave your
device, because the app has no way to send it.

MOVE FILES WITHOUT GUESSWORK
Copy or move thousands of files and actually see what is happening: what is
transferring, how fast, how much is left, what failed and why. Transfers keep
running when you leave the app, and survive the screen turning off.

FIND WHAT IS EATING YOUR STORAGE
A dashboard that answers the real question. See used and free space broken down
by file type, the biggest files on your device, which folders are holding the
most, and how much you added each month. Tap anything to go straight to it.

RECLAIM SPACE
One card gathers everything you can get back: the trash, duplicate copies,
empty folders and zero-byte files, each with the action that frees it.

FIND DUPLICATES ANYWHERE
Compare two folders, or sweep your entire device. Files match on name, size and
type, with an optional byte-for-byte check that proves they are identical
before anything is deleted. Each set keeps its oldest copy and only the extras
can be removed, so you can never delete every copy of something.

ENCRYPT A FOLDER
Lock any folder behind a passphrase. Files are encrypted with AES-256, and
their names, dates and folder structure are hidden too — then restored exactly
when you decrypt. Take a single file back out without unlocking the rest.
Your passphrase is never stored and never leaves the device, and a recovery
code is shown once at setup in case you forget it. There is no backdoor.

ORGANISE BY MONTH
Sort photos and videos from anywhere into MonthYear folders automatically, or
run a job across two source folders. Dates that went missing can be recovered
from filenames — including WhatsApp and camera names — for photos and videos
alike, with a preview before anything is written.

BUILT-IN VIEWERS
Photos and video with pinch zoom, double-tap zoom and swipe between items.
Video can float in a window while you carry on browsing. A full music player
with a queue, shuffle, repeat, playback speed and lock-screen controls, so
audio keeps playing while you work.

ARCHIVES
Open zip, jar, apk, tar, tar.gz and gz archives, see what is inside before
unpacking, and extract single files or the lot. Compress anything into a zip.

MADE TO LOOK GOOD
Light, dark and three colour themes including Blossom and Sakura, eleven accent
colours, five loading animations, a choice of app icons and even the app's own
name on your home screen. Grid, gallery, list, mosaic and month-grouped views,
with pinch to resize.

SAFE BY DEFAULT
Deleted files go to a recoverable trash. Anything encrypted is verified before
the original is removed. Long jobs can be stopped at any moment without leaving
things half-done. Lock the whole app, or individual folders, behind your
fingerprint.

PERMISSIONS
All files access is required because File Storm is a file manager — it exists
to see and change your files. Notifications are used for progress on long jobs
and for playback controls. Biometrics are checked by Android; the app never
sees your fingerprint.

No internet permission. No analytics. No accounts. Nothing is collected and
nothing is shared.
```

---

## Release notes — "What's new" (500)

```
• Encrypt any folder with a passphrase — names, dates and structure hidden and
  restored exactly. Take one file back out without unlocking the rest.
• Built-in music player with lock-screen controls, and floating video.
• Open archives and extract single files.
• Settings reorganised, with search.
• Much smaller download.
```

---

## Categorisation

| Field | Value |
|---|---|
| App category | Tools |
| Tags | File manager, Productivity, Utilities |
| Contact email | shahabusaid.mm@gmail.com |
| Website | *(repo or GitHub Pages URL)* |
| Privacy policy | *(hosted PRIVACY.md URL — required)* |

## Content rating questionnaire

Answer **no** to everything — violence, sexuality, language, controlled
substances, gambling, user-generated content, user interaction, location
sharing, personal information sharing. Expect **Everyone / PEGI 3**.

The only question worth pausing on: some questionnaires ask whether the app
lets users share content. File Storm hands a file to another app when you tap
Share; it does not itself transmit anything. Answer honestly if the question
mentions in-app sharing between users — it does not do that.

## Data safety form

- Does your app collect or share any of the required user data types? **No**
- Is all user data encrypted in transit? **N/A — no data is transmitted**
- Do you provide a way to delete data? **N/A — no data is collected**

This is unusually simple because the app has no `INTERNET` permission at all.

## Declarations you must complete

**All files access (MANAGE_EXTERNAL_STORAGE).** File managers are a permitted
category, so this is winnable — but write the justification concretely.
Suggested wording:

```
File Storm is a general-purpose file manager. Its core functions — browsing,
moving, copying, renaming, compressing, extracting, organising and encrypting
files across the whole of shared storage — require access to files the app did
not create and cannot know about in advance. The Storage Access Framework
cannot support bulk operations across arbitrary folders, month-based
organisation of an entire library, duplicate detection across the device, or
folder-level encryption. No file content is transmitted; the app has no
internet permission.
```

**Encryption export compliance.** The app uses AES via the Android platform's
standard cryptography, which is the exempt case, but the declaration still has
to be filled in.

**Foreground services.** Declare `dataSync` for transfers, organising and
encryption, and `mediaPlayback` for the audio player. Justification: these jobs
are user-initiated, can run for many minutes, and must survive the app being
backgrounded.

---

# Screenshots

**Captured.** Thirteen are in [`store/screenshots/`](store/screenshots), taken
from the running app on an API 36 emulator at 1344 × 2992. Every one is a real
frame — nothing is mocked up or retouched.

| File | Screen |
|---|---|
| `01-dashboard.png` | Storage donut, category breakdown, Monthly Footprint |
| `02-vault.png` | Vault unlocked — 27 encrypted files with thumbnails |
| `03-encrypting.png` | Encryption in progress: speed, ETA, per-file bar, counters |
| `04-duplicates.png` | Whole-storage sweep results, contents verified, sortable |
| `05-gallery-month.png` | Mosaic view grouped by month |
| `06-largest-folders.png` | Largest Folders with the treemap chart |
| `07-themes.png` | Appearance with Blossom applied |
| `08-music.png` | Music player with queue and playback speed |
| `09-settings-hub.png` | Settings hub with search and live summaries |
| `10-settings-dashboard.png` | Dashboard cards reordered and toggled |
| `11-context-menu.png` | Folder actions, including Encrypt and Compress |
| `12-blossom-dashboard.png` | The dashboard in Blossom |
| `13-vault-settings.png` | Vault options — verification, speed, auto-lock |

## Which eight to upload

Play allows at most eight phone screenshots, and the first two carry most of
the weight. Recommended order:

1. `01-dashboard.png`
2. `02-vault.png`
3. `03-encrypting.png`
4. `04-duplicates.png`
5. `06-largest-folders.png`
6. `05-gallery-month.png`
7. `08-music.png`
8. `07-themes.png`

`09`–`13` are spares — swap `12-blossom-dashboard.png` in for `07` if you would
rather lead the theming story with the whole app than with the settings page.

**Note on the sample data.** The device was loaded with real photographs and
real video clips from Pexels, plus generated audio tones, so the thumbnails,
charts and month grouping all show genuine content. The Pexels License permits
this use and requires no attribution. Unsplash was the first choice but it
serves a challenge page to anything scripted, so nothing could be fetched from
it.

If you would rather ship screenshots from your own phone, the same walkthrough
works — just avoid filenames you would not want public. The vault shots are
safe by nature; the browser ones are not.

## What Play needs

- **Phone:** 2–8 screenshots. 16:9 or 9:16, each side 320–3840 px. A modern
  phone's own resolution is fine.
- **Feature graphic:** 1024 × 500 PNG or JPEG, no transparency. **Required.**
- **App icon:** 512 × 512 PNG, 32-bit with alpha. Use the Glass icon.
- Tablet screenshots are optional but improve placement.

## Re-capturing on your own device

With the phone connected and USB debugging on:

```bash
adb exec-out screencap -p > 01-dashboard.png
```

Take each shot, then repeat with the next filename.

## Feature graphic

I could not generate this either; there is no image tooling available here. It
needs to be a designed 1024 × 500 banner, not a screenshot.

What would suit the app: the Glass icon's indigo-to-rose gradient as the
background, "File Storm" in large white text, and a short line beneath such as
*"Organise, find and encrypt — entirely offline."* Any of Canva, Figma or
Photopea will do it in a few minutes from that description.
