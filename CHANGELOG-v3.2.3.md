# SubGen Native v3.2.3 — Samsung My Files audio selection

Audio selection now first opens Samsung My Files using an audio-only
`ACTION_GET_CONTENT` request, rather than relying on the system document
picker to honor an initial Audio root.

- Applies to Carrier, Primary Affirmation, Secondary Affirmation and NeuroMod input.
- Requests one openable audio file from `com.sec.android.app.myfiles`.
- If My Files is absent or cannot be launched, uses the generic audio-import
  picker, allowing external picker apps; the document picker is a final fallback.
- Handles a single returned URI in either `data` or `ClipData`.
- Selected audio is still copied into the app's existing private source storage.
- No new storage permission, package visibility query or audio-engine change.
- Keeps all v3.2.2 High Quality options and caches.

The target is Samsung's **Select audio file** screen, including its Recent files
tab. The actual appearance and initial tab are controlled by the installed
My Files version and require Samsung device acceptance testing.

Package: `com.subgen.nativeapp.stable` · version code 17 · `3.2.3-samsung-audio-picker`.
