# Publishing ReelOff on Google Play (internal testing)

Installing through Google Play avoids the "App not installed to protect you" block that Play
Protect applies to sideloaded apps that use Accessibility. Internal testing is available as soon
as the app is uploaded, without review delays, for up to 100 testers.

## 1. Create a Play Console developer account (one time)

1. Go to https://play.google.com/console/signup and sign in with your Google account.
2. Choose **Yourself** (personal account), pay the one-time US$25 fee, and complete identity
   verification. Verification can take from a few hours to a few days.
3. Verify a phone number and the email address when asked.

## 2. Create an upload key (one time, on your computer)

You need Java installed (it provides `keytool`). Run:

```bash
keytool -genkeypair -v -keystore reeloff-upload.jks -alias reeloff \
  -keyalg RSA -keysize 2048 -validity 10000
```

Pick a password and remember it. Keep `reeloff-upload.jks` safe and **never commit it**. If
you lose it, Google can reset the upload key, but that takes time.

## 3. Give the key to GitHub Actions (one time)

Encode the key file as text:

- macOS/Linux: `base64 -w0 reeloff-upload.jks > key.txt` (on macOS: `base64 -i reeloff-upload.jks -o key.txt`)
- Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("reeloff-upload.jks")) > key.txt`

In GitHub, go to **ShivamPratap16/ReelOff → Settings → Secrets and variables → Actions → New
repository secret** and add four secrets:

| Name | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the whole content of `key.txt` |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `reeloff` |
| `KEY_PASSWORD` | the key password (same as the keystore password unless you chose another) |

Push any commit or re-run the **Android CI** workflow. The **ReelOff-apk** artifact will now
also contain **`ReelOff-play.aab`**, the file Google Play needs.

## 4. Create the app in Play Console

1. **Create app** → name `ReelOff`, default language, **App**, **Free**, accept the declarations.
2. **Test and release → Testing → Internal testing → Create new release.**
3. Accept **Play App Signing** (Google keeps the real signing key; yours is only the upload key).
4. Upload `ReelOff-play.aab`, add release notes, **Save → Review release → Start rollout**.
5. Open the **Testers** tab, create an email list with your own Gmail address, and save.
6. Copy the **join on the web** link, open it on your phone, accept the invite, and install
   ReelOff from the Play Store page it opens.

Play may ask you to finish parts of **App content** before the release can roll out, even for
internal testing:

- **Privacy policy:** use
  `https://github.com/ShivamPratap16/ReelOff/blob/main/docs/PRIVACY_POLICY.md`
  (after this branch is merged into `main`; until then use the branch URL).
- **App access:** all functionality is available without special access.
- **Ads:** no ads.
- **Content rating:** fill in the questionnaire (utility app, no user content).
- **Target audience:** 18+ (or 13+), not designed for children.
- **Data safety:** no data collected, no data shared.
- **Sensitive permissions → Accessibility API:** declare ReelOff's use. Suggested text:

  > ReelOff uses the AccessibilityService API to detect when short-form video screens (YouTube
  > Shorts, Instagram Reels, Facebook Reels, Snapchat Spotlight, TikTok) or a feed the user chose
  > to limit appear in apps the user selected, and to press Back or Home to close them. It reads
  > only view identifiers, content descriptions, the selected tab and browser address bars. No
  > data is collected or shared, and the app has no internet permission.

  Google asks for a short **video** (a YouTube link is fine) showing the in-app disclosure, the
  user turning on the service, and a Short being closed. Record it with the phone's screen
  recorder.

## 5. Publishing to everyone (later)

New personal developer accounts must run a **closed test with at least 12 testers for 14 days**
before production access is granted. After that, create a production release from the same
`.aab`. Every later push builds a new `.aab` with a higher version code automatically.
