# Installation, update and rollback — v3.2.0

## Stable in-place update

The release APK retains `com.subgen.nativeapp.stable`, increases versionCode 13 to 14, and uses the same existing stable certificate as v3.1.3.

1. Keep your existing SubGen app installed. Do not uninstall it.
2. Open the signed v3.2 APK and accept Android's update prompt.
3. Open Hypnosis, paste your script and use the existing Create/Export workflow.
4. Optional: open **HYPER AUDIT & PRODUCTION TOOLS** in the Hypnosis output panel.
5. After rendering, measure the WAV. For loop work, audition both repeated junctions. Three-cycle export and professional approvals remain optional.

The existing app-private model, settings, scripts, audio and speech cache are not intentionally cleared or migrated. Same package/signature/update identity is verified; physical Samsung installation and data retention still require device acceptance.

If Android reports a package/signature conflict, stop rather than uninstalling. Compare the installed version/certificate with the identity manifest first.

## Developer debug build

CI also creates `com.subgen.nativeapp.stable.hypertest`, labelled **SubGen v3.2 Test**. It installs alongside stable with separate model/data storage. It is not the primary stable update deliverable.

## Signing provenance

The original private backup was recovered and used locally. Its certificate SHA-256 matches v3.1.3:

`37c823a64caae27f705c6e1b2736ba2b6081f3b144e96a9337defd85f1131f9e`

The private key and passphrase are excluded from source, CI, reports and delivery packages. No replacement stable key was generated.

## Rollback

Android ordinarily rejects downgrade from versionCode 14 to 13. Do not uninstall stable merely to downgrade: that erases private data. Preserve wanted WAV/report exports and use a forward versionCode rollback build signed with the original key, or a separately reviewed device backup/recovery procedure.

For a `.hypertest` install, uninstall only that separate test app after exporting wanted test data; stable is unaffected.

## Device acceptance still required

Verify Studio presets/mixing, Hypnosis playback/export, NeuroModulation A/B/A+B, optional panel interactions, long rendering, background/foreground transitions, settings retention and loop listening on the intended Samsung. No physical-device install, upgrade or listening pass is claimed here.
