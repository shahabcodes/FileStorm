# File Storm — Privacy Policy

**Last updated:** 9 August 2026
**Developer:** shahabkodes
**Contact:** shahabusaid.mm@gmail.com

## The short version

File Storm does not collect anything, does not send anything anywhere, and has
no way to do either. The app has no internet permission at all. Everything it
does happens on your device.

There are no accounts, no analytics, no advertising, no crash reporting and no
third-party SDKs that phone home.

## What the app can access, and why

**Your files.** File Storm is a file manager, so it needs to see and change the
files on your device. On Android this requires the *All files access*
permission (`MANAGE_EXTERNAL_STORAGE`). It is used to browse, move, copy,
rename, compress, extract, encrypt and delete files at your instruction.

Files are read and written **only on your device**. They are never uploaded,
never copied off the device by the app, and never shown to anyone else.

**Notifications.** Used to show progress for long jobs — transfers, folder
organising, encryption — so they can continue while the app is in the
background, and to show playback controls for audio.

**Biometrics.** If you turn on the app lock or lock a folder, your fingerprint
or face is checked by Android itself. File Storm never sees, stores or
transmits biometric data; Android only tells the app whether the check passed.

## The encrypted vault

If you encrypt a folder, File Storm derives a key from the passphrase you
choose and encrypts each file with AES-256-GCM.

- Your passphrase and recovery code are **never stored anywhere** and never
  leave the device.
- The key that opens a vault is held in memory only while the vault is
  unlocked, and is discarded when it locks.
- There is no backdoor, no master key and no recovery service. If you lose both
  your passphrase and your recovery code, nobody — including the developer —
  can open those files.

Files you open from a vault are decrypted into the app's own private storage so
they can be viewed, and are deleted when the vault locks.

## Diagnostic logs

If you turn on vault logging in Settings, File Storm writes a log file to its
own private storage to help trace a problem. The log **never contains** your
passphrase, your recovery code, any encryption key, or the contents of any
file. Filenames are excluded by default and replaced with a short code; you can
include them deliberately if you need to identify a specific file.

The log stays on your device. It is only shared if you choose to share it, and
you can clear it at any time from Settings.

## Data sent off the device

None. The app cannot make network connections.

When you use **Share** or open a file in another app, Android hands that file
to whichever app you pick. What happens to it then is governed by that app's
privacy policy, not this one.

## Children

File Storm is a general-purpose utility and is not directed at children. It
collects no personal information from anyone, of any age.

## Changes

If this policy changes, the updated version will be published here and the date
at the top will change.

## Contact

Questions about this policy: **shahabusaid.mm@gmail.com**
