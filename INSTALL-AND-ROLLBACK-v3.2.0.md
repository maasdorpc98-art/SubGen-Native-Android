# Installation, update and rollback — v3.2.0

## Available test build

The debug-signed APK uses `com.subgen.nativeapp.stable.hypertest`, versionCode 14. It installs alongside the stable app. It is not an in-place update and uses its own scripts, settings and model directory.

1. Keep the stable app installed.
2. Install the v3.2 test APK and open it.
3. In Hypnosis, install the original FP32 model if needed, paste a script, create and export as before.
4. Open the optional HYPER tools to inspect cues, structure and acoustics.
5. After a render, measure the WAV. For loop work, audition both repeated junctions and optionally create a three-cycle QA copy.
6. Review notes and approvals are optional production records, separate from ordinary export.

The model and arm64 runtime requirements are unchanged. This test APK does not share the existing stable app's private model/cache or settings. Use a short script before a long device test.

## Stable update candidate

The release candidate retains `com.subgen.nativeapp.stable`, versionCode 14. It is UNSIGNED and not directly installable. The original private stable release signer is not included in the source or this deliverable.

A maintainer with the original keystore must align/sign the release candidate using that same key, verify its certificate digest against the installed v3.1.3 APK, inspect package/version and then install over it. Do not create a replacement key or use the test certificate for the stable package.

The CI signing kit includes the unsigned candidate, apksigner and zipalign. Keep private signing material out of the public repository and reports.

## Rollback

For the separate test build, uninstall only the `.hypertest` app. The existing stable app is unaffected. Export wanted test WAVs/reports before removing it.

For a later correctly signed stable upgrade, Android normally rejects an ordinary downgrade from versionCode 14 to 13. Do not uninstall the stable app merely to downgrade: that erases its private data. Preserve exports and use a forward versionCode rollback build signed by the original key, or follow an explicitly reviewed device backup/recovery procedure.

## Device acceptance still required

Verify Studio mixing/presets, Hypnosis playback/export, NeuroModulation A/B/A+B, optional panel controls, long rendering, foreground/background transitions, settings retention and loop listening on the intended Samsung. No physical-device install, upgrade or listening pass is claimed here.
