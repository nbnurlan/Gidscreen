# Selection-first floating chat and smart scrolling

Based on main commit 5c51ce2, including the already merged persistent selection and Qwen changes.

## Existing implementation reused

WindowManager bubble/chat/selection windows, MediaProjection, foreground service, crop pipeline, confirm/reset/cancel UI, chat journal with old images, window/draft preferences, model selection, permission denial and rotation recovery are retained. No permissions or dependencies are added.

## Changes

- Bubble click (also when docked) opens selection immediately. Drag behavior is unchanged.
- Bubble remains available above the chat as its own WindowManager window.
- A second selection temporarily hides the same attached chat view; it never clears the conversation or creates another session.
- After confirmation, the existing crop and AI pipeline runs; the chat is shown when the result or error is available. Cancel restores the existing view and bubble.
- Remove the obsolete Select action and callback from the chat, including the unused in-app callback and label.
- Message IDs, not loading-state changes, trigger scrolling. Explicit user questions/captures resume following; incoming AI messages respect whether the reader has scrolled away. The last message's actual LazyColumn index is used, with loading/errors at the end under a stable key.
- New long replies align at their beginning. A viewport reduction keeps short current messages visible while preserving old-history/long-answer reading positions. The overlay uses SOFT_INPUT_ADJUST_RESIZE so the input and list fit above the keyboard.
- Default for unsaved model choice is Gemini 2.5 Flash, as requested. Existing saved choices (including Qwen) remain unchanged.

## Automated verification

ChatAutoScrollTest exercises following at the end, long-answer start, reading old messages during an incoming reply/status removal, explicit new questions/captures, and reduced viewports. OverlayRegressionTest checks bubble → selection without opening chat, second selection retaining chat/crops/window parameters, cancel restoration and busy requests. ModelDefaultTest checks the default and saved selection. Existing persistence, selection confirmation, update and screenshot tests remain in the build.

Local Gradle cannot download the distribution under the execution environment's network restriction. The PR's GitHub Actions run is the build/test authority; runtime capture and keyboard checks below require an Android device.

## Device checklist

- With a browser visible, tap the expanded and docked bubble: selection opens with no chat first.
- Draw → Confirm: only the crop is analyzed; chat appears with the answer after completion.
- Type a draft, move/resize chat and select a model. Tap bubble → second region → Confirm. Verify the same history, old crops, draft, model and window bounds survive.
- Reset selection does not capture/upload; Cancel restores the same chat and bubble.
- Scroll upward to old messages while AI is working: reply arrival must preserve the reader's position. At the bottom, the new answer becomes visible.
- Send a question from old history: jump to that turn. Request another capture: follow the new turn.
- For an answer longer than the chat window, its beginning is visible first.
- Open/close the keyboard with short and long messages; input stays visible and old-history reading is not forcibly moved. Also test landscape and resize.
- Deny renewed projection consent: history stays intact. Rotate during selection/capture: redraw selection with valid coordinates.
- Kill/reopen/grant fresh projection consent: saved conversation, images, draft, model and chat bounds restore. Interrupted AI requests are not automatically resent.
- Check Xiaomi battery restrictions and overlay Z order on a real device; automated tests do not emulate the device compositor or IME.
