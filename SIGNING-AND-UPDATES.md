# Sub Gen native Android — stable in-place updates

Starting with v3.0.8, the update-series application ID is
`com.subgen.nativeapp.stable`. Future versions must keep this ID, increase
`versionCode`, and be signed with the **same private release key**.

The private signing key is not committed to this public repository. GitHub
Actions builds an **unsigned** release APK and uploads the offline signing
kit (unsigned APK, zipalign, apksigner). The initial stable APK is signed
offline with a private keystore and verified. The user should back up the
keystore and its separate passphrase in a private secure location.

For a future update:
1. Increment `versionCode` and set a new `versionName`; keep the stable ID.
2. Run the tests and `:app:assembleRelease` in CI.
3. Download the unsigned signing kit and sign with the *same* private key.
4. Run `apksigner verify --verbose --print-certs`, compare certificate digest
   to the previous stable APK, and inspect the APK package and version.
5. Install the newly signed APK over the previous stable version. The app
   keeps its private files, settings, speech cache and installed model.

Do not publish or commit the private keystore or password.
Do not use GitHub Actions' random debug signing certificate for updates.

## Migration warning

v3.0.7 and earlier test APKs used other package IDs and per-run debug signing
keys. They cannot be replaced in place by the stable v3.0.8 release. v3.0.8
installs beside them as the new stable baseline. Later stable builds can update
it in place if signed with the same private key.
