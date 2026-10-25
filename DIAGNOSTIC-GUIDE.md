# Sub Gen Native v3.0.1 · diagnostic APK candidate
This build installs alongside v3.0, without erasing v3.0 app data. It uses native Android Views, not a WebView.
Existing interface, audio DSP, slider specifications and preset assets are intentionally unchanged.
The diagnostic recorder stores crash and performance evidence privately on your phone. It does not upload telemetry.
To export evidence: open SUBLIMINAL DIAGNOSTICS > EXPORT REPORT; choose a location for SubGen-Diagnostic-Report.json.
Reproduce crashes using short sample files first. Reopen the diagnostic app after a crash, export JSON, and share the report.
Java/Kotlin exceptions include stack traces. Native faults, out-of-memory process kills and force stops may only leave an interrupted-job marker; logcat may still be required.
The APK is a test candidate. Green CI checks confirm compilation, signing and packaging but not device performance or pixel-perfect parity.
