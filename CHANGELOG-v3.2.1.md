# SubGen Native v3.2.1 — Open Audio Directly

All audio-import buttons now request the system file picker's Audio category
as their initial location instead of inheriting an arbitrary folder.

- Studio: Carrier (shared by Standard and 8D), Primary Affirmation and Secondary Affirmation.
- NeuroMod: Choose / Replace Affirmation Audio.
- Audio-only filtering and the original import callbacks are retained.
- Script and JSON import/export pickers retain their existing behavior.
- Uses the native Storage Access Framework; no broad storage permission added.
- Package: `com.subgen.nativeapp.stable`; version code: 15.

Android chooses the actual picker implementation. If the system cannot resolve
the Audio category, it uses its default location. Opening behavior on Samsung
One UI requires device acceptance testing.

The change is limited to picker navigation and release metadata. Kokoro,
Voice Direction, HYPER analysis, Studio DSP, presets and NeuroMod A/B are unchanged.

Platform references:
- https://developer.android.com/reference/android/provider/DocumentsContract#EXTRA_INITIAL_URI
- https://android.googlesource.com/platform/packages/apps/DocumentsUI/+/refs/heads/main/src/com/android/documentsui/picker/ActionHandler.java
- https://android.googlesource.com/platform/packages/apps/DocumentsUI/+/refs/heads/main/src/com/android/documentsui/base/Providers.java
