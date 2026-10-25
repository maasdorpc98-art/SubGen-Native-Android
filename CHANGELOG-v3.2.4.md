# SubGen Native v3.2.4 — Clear ON/OFF controls

Every checkbox now displays its current ON or OFF state and updates immediately,
including changes made by code. The same state is supplied to accessibility
services on Android 11 and later; Android 10 retains the visible state label.

- High Quality in Studio, Hypnosis and NeuroMod shows ON/OFF plus the selected
  24-bit or standard 16-bit edition.
- Studio effect labels follow their actual setting, including restored and
  preset-loaded values. Removes an always-OFF mixer summary.
- NeuroMod Custom Frequency / Depth shows ON/OFF. Its frequency and depth labels
  show the effective 40 Hz / 90% reference when OFF and the slider values when ON.
  Moving a slider still turns Custom ON; switching OFF keeps custom values for reuse.
- Voice Direction and optional HYPER checkbox controls show ON/OFF as well.
  Classification identifiers remain independent from the displayed labels.
- Save Work already shows ON/OFF and retains that behavior.

Audio algorithms, format limits, settings keys, defaults and caches are unchanged.
Package: `com.subgen.nativeapp.stable` · version code 18 · `3.2.4-clear-switches`.

Compilation and regression checks are performed before distribution. Visual,
TalkBack and installation acceptance require an Android device.
