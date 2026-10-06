# Persistent floating-chat region selection

## Audit and architecture

Existing code already implements WindowManager application overlays, a draggable/resizable Compose chat, transparent rectangle/lasso/circle selection, a mediaProjection foreground service, a reusable VirtualDisplay/ImageReader, cropping, and GeminiService.continueChat with previous messages and images. These components are retained.

Gaps addressed: explicit confirmation and retry, a labelled Select action, chat reopening from the bubble, consent renewal from the chat, durable history including hidden image messages, draft/window restoration, rotation invalidation, and rejecting stale screen frames.

Flow: existing chat → Select → transparent selection overlay → drag → Confirm → temporarily hide overlays → fresh frame → crop → existing multi-turn AI call → append answer to the same chat. Retry clears just the selection; Cancel restores the same attached chat view. The chat is temporarily invisible during selection/capture so it cannot obscure selected pixels; it is not destroyed or replaced.

MediaProjection necessarily receives display frames locally. Only the confirmed crop is sent to the model or persisted. No Accessibility, OCR dependency, audio, storage permission, or new AI provider is needed: the existing vision model accepts text/image/UI crops directly. Android 14+ consent requests default-display capture because global overlay coordinates cannot map safely to an arbitrary app-sharing window.

ChatSessionStore writes cropped PNGs followed by an atomic JSON journal under noBackupFilesDir. It retains message IDs, timestamps, sender, hidden/visible status and images. Interrupted requests are shown as interrupted, never automatically resent. Clear Chat also removes saved crops. Window bounds, visibility and draft use private preferences. Selection coordinates are deliberately discarded on rotation/process death; users must select again. A dead process requires a fresh user start and Android capture consent; no background consent bypass or automatic request replay is attempted. START_NOT_STICKY avoids trying to restart a projection foreground service with a lost token.

## Permissions

Already declared: SYSTEM_ALERT_WINDOW (user grant), FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PROJECTION, INTERNET, and POST_NOTIFICATIONS on Android 13+. Android's MediaProjection consent is additionally required per projection session. No manifest permissions are added. Existing installation/vibration permissions are unrelated and unchanged. The consent-only Activity is non-exported.

## Implementation sequence and affected files

1. LassoSelectionContent.kt: rectangle default, confirm/reset/cancel, preserve existing drawing modes.
2. FloatingChatDialogContent.kt: labelled Select action and optional draft persistence callbacks, preserving in-app callers.
3. LassoOverlayService.kt: reuse current chat, save/restore history and window state, renew consent, reserve captures against duplicate taps, invalidate selection on rotation.
4. ChatSessionStore.kt (new): atomic private journal and cropped image persistence.
5. ScreenCapturePermissionActivity.kt (new), AndroidManifest.xml, MediaProjectionHolder.kt and MainActivity.kt: consent renewal/default-display selection using existing service.
6. ScreenCaptureHelper.kt: use frame production timestamps, request a fresh frame using the existing display surface, and reject stale fallback frames.
7. values/values-uz/values-ru strings.xml: action and recovery messages.
8. ChatSessionStoreTest.kt and SelectionConfirmationTest.kt (new): persistence, clear/delete, confirm/reset/cancel regressions. Existing OverlayRegressionTest remains unchanged.

## Verification

Local `git diff --check` passed. Local Gradle execution was blocked before compilation by network access to services.gradle.org. GitHub Actions run 37501266676 passed APK build and unit tests for the initial patch. A successful build does not establish real-device overlay behavior. The follow-up journal recovery test also needs CI verification.

Manual device checklist (Android 10/Redmi and Android 14–16):

- Start the service with overlay/capture consent; open a browser, tap the bubble, enter a draft.
- Select: background app remains visible; keyboard and chat do not obscure the region. Drag in all directions, including near status/navigation bars and cutouts.
- Finger release does not upload. Reset clears selection. Cancel returns to the same chat with draft/history intact.
- Confirm a text region, image and UI control. Inspect crop thumbnail: no selection border, chat, adjacent content or stale frame. Repeat on a completely static page.
- Select twice in one conversation, then ask a follow-up referring to the first crop; earlier image context remains available.
- Repeat rapid confirmation/follow-up taps; only one request is queued.
- Rotate before confirmation and during capture: old coordinates are discarded and no wrong crop is uploaded. Rotate during AI analysis: result returns to the current chat, resized within screen bounds.
- Stop projection using system UI or lock the screen; Select requests consent again. Deny consent: current conversation survives. Grant: select again without opening a new conversation.
- Kill the process during AI analysis. Reopen app and grant capture consent: history, saved crops, draft and chat bounds restore; interrupted request is reported and not silently replayed.
- Clear Chat, stop/start service: no old messages/crops return.
- Test offline/model failure, full storage, and protected/FLAG_SECURE screens. Protected content cannot be captured; do not attempt to bypass Android protections.
- Test repeated selections for memory pressure and Xiaomi battery restrictions. Android may terminate overlays; continuous survival after force-stop is not guaranteed.

Official platform reference: https://developer.android.com/media/grow/media-projection
