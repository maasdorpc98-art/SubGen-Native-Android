# Original Subliminal Studio native visual parity lock

The `design-manifest.json` control inventory is extracted from the original Subliminal + Hypnosis v1.1 APK that the user chose as the visual specification. The reference PNG images are 393 CSS px viewport captures with 2× pixel ratio, not proposed new art. They are for comparison only. The application **must not include the reference HTML** or use `WebView`, and must not replace the original slider ranges, defaults, labels, visuals or interaction semantics.

Acceptance requires:

1. Inventory parity for all active original Subliminal controls, including 28 distinct range definitions and unchanged min/max/step/defaults, plus buttons/selects/checkboxes.
2. Native Android layout at the same Galaxy S21 viewport compared section by section with the original reference.
3. Hit targets, two master balances, linked volumes, and original preset-to-slider synchronization functionally verified, not just drawn.
4. Original Hypnosis controls and all four voice cards visually verified.
5. No release labelled 'pixel-exact' or 'full parity' until on-device visual and interaction comparisons pass. Reference screenshots are historical and may differ based on Android font metrics and status bar.
6. Previously released native v2.2 is not accepted as visually matched.

This is an engineering baseline, not a completed app release.