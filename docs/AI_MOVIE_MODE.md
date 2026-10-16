# AI Movie Mode — MVP

This branch does not modify production releases. PR CI produces **Gidscreen-Test**
(application ID suffix `.test`) which installs beside the production application.

## Phone-only setup

1. Open the PR in Chrome, open its successful **Build APK** check, then the run summary.
2. Under Artifacts download **Gidscreen-Test** while signed into GitHub. Extract the ZIP
   with Android Files and tap `app-debug.apk`. Permit installation from Files if asked.
3. Open **Gidscreen Test**, permit drawing over other apps and notifications.
4. Settings: enter your Gemini API key in the existing encrypted key editor. Select a
   Gemini model (default Gemini 2.5 Flash); Movie Mode does not route to Qwen.
5. Obtain your own **API Read Access Token** at https://www.themoviedb.org/settings/api
   and enter it under TMDB API Read Access Token. This is the bearer token, not API key.
   It is encrypted using Android Keystore in no-backup storage, never committed or logged.
   Do not send keys in chat or put them in GitHub Actions secrets for APK compilation.
6. Enable Movie Mode, read the cloud-processing disclosure, accept Android capture consent.
   Return to the movie within ten seconds. Three frames are sampled 2.5 seconds apart.
7. Pause manually and tap the bubble. A high confidence, unique title/year catalog match
   may be selected automatically. Otherwise choose a candidate or retry on a new scene.
   Percentages are **uncalibrated AI estimates**, not identification guarantees.
8. Ask a question. The overlay and keyboard are hidden briefly for a fresh frame. Gemini
   answers in the application language. Spoiler-free is on by default.
9. For a different film choose **New movie / retry**, close the panel and return to playback.
   This clears the active movie text history. Automatic film-change detection is phase 2.
10. **Stop capture** in the panel, Settings, notification or Android capture control stops
    projection. Start again with fresh consent. Ordinary mode uses its original selection flow.

Test APK signing keys are generated per CI run. Updating an older test APK may require
uninstalling **only Gidscreen Test** (losing that test app's local keys/history). Do not
uninstall production Gidscreen.

## Implementation and boundaries

- Existing MediaProjection holder and single virtual display are reused. No Accessibility,
  microphone, audio capture, DRM bypass or background auto-restart is added.
- Bounded three-frame recognition; no continuous polling or automatic API retries.
  The existing virtual-display surface is detached between Movie Mode frame requests;
  cached full-screen pixels are recycled, reducing idle frame conversion and memory use.
- JPEG images capped at 768px, quality 65, at most three per recognition and one per question.
  Frames live in memory only. Text history (last 200 messages) is separately stored atomically
  in no-backup storage; at most 12 bounded messages go to the model on each question.
- TMDB title/year lookup is cached for the active controller, capped at 30 queries. An exact
  catalog match validates existence, not whether the model recognized the correct film.
- Metadata includes cast and synopsis. In spoiler-free mode neither is provided to Q&A;
  only title/year, conversation and current frame are included. The system prompt prohibits
  inferring viewing progress or fabricating dialogue. Generative spoiler protection is not
  a guarantee; scene timeline/audio analysis belong to later phases.
- Dark frames produce an honest ambiguous error: protection cannot be distinguished from
  a dark scene. Some partially black protected videos cannot be detected reliably.
- All permitted screen pixels are eligible for cloud processing, including other apps.
  Close private content. Android's lock screen is skipped. The app does not pause playback.
- Rotation closes the movie panel; the bubble reopens it. Capture surface resizing follows
  the existing helper. Process death never resumes capture; text history remains and the
  movie must be identified again before answers are enabled.
- The TMDB attribution is shown in the panel. Before public distribution review current
  TMDB licensing/branding requirements (including logo) and commercial use terms.
- Real Gemini/TMDB calls and device behavior need phone testing with user-owned credentials.

## Regression checklist

- Ordinary mode: select region, confirm, chat opens immediately, answer and scrolling work.
- Refuse capture consent: no Movie Mode capture or network call.
- Stop during the ten-second delay, capture, lookup or answer: no late answer/continued capture.
- Revoke capture using Android control: service stops. Restart requests new consent.
- Low confidence, ambiguous title, unknown movie: no false confirmed state.
- Offline, invalid key, rate limit: visible error, no retry storm, manual retry works.
- Dark/secure video: no bypass; error/uncertainty is visible.
- Rotation and process death: no consent token reuse; separate text history is retained.
- New film: old chat context is cleared; normal chat is unchanged.
- Spoiler questions: with protection enabled, ask about an unseen twist; check refusal.
- Test on Android 10 and 14+; verify landscape, keyboard dismissal and notification stop.

CI runs `assembleDebug testDebugUnitTest -PsideBySideTest=true` and uploads test reports.
Local build may be unavailable in network-restricted environments; CI status is authoritative
for compilation/unit tests, not physical-device or live API success.
