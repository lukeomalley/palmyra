# Navigation sound verification

The current sample is Kenney drop_001 ("Pop"), the first Pop in the audition list.
It is a 23,374-byte stereo PCM WAV: 5,824 frames at 44,100 Hz, approximately
132 ms. The source and CC0 license are in CREDITS.md. The playback engine is
unchanged. Pop-specific startup timing and PSS have not been remeasured.

A one-shot worker constructs SoundPool away from the UI thread. The sample loads
asynchronously; navigation skips sound until ready. Nothing queues or retries.
Only one stream can play. The player requests no audio focus, reads Android's
sound-effects preference on resume, and releases its resources on pause.
Initialization completed after pause is discarded and released.

## Initial Tiny tick emulator measurements

The following measurements used the previous 1,102-byte click_002 sample
("Tiny tick"), a 12 ms mono PCM WAV. They are historical engine measurements.

Measured on the arm64 API 31 Android TV emulator with system sound effects enabled.
Each build used three cold launches, followed by the same idle frame, CPU and PSS
capture from scripts/metrics.py. These are emulator observations, not TV estimates.

| Measurement | Before sound | With sound |
| --- | --- | --- |
| Cold launches, ms | 147, 142, 126 | 123, 148, 166 |
| Median cold start, ms | 142 | 148 |
| Idle frames in 10 seconds | 0 | 0 |
| Idle CPU | 0% | 0% |
| PSS, KiB | 28,999 | 30,697 |

The startup sample ranges overlap. Three samples do not establish a small timing
regression or improvement. The audio engine adds about 1.7 MiB of foreground PSS.
An initial implementation constructed SoundPool on the UI thread and measured a
207 ms median; that implementation was replaced before delivery.

The signed release APK grew from 10,138,525 to 10,142,277 bytes, including audio,
code, credits and signing overhead. DEX grew from 63,464 to 65,632 bytes. Both
remain inside the existing budgets. No runtime dependency or permission was added.

Unit and rendering tests cover navigation boundaries, silent Home and Art Mode,
system mute, asynchronous loading, load failure, stale callbacks and pause/resume.
All 35 unit/rendering tests and screenshot comparisons pass. Release emulator smoke
passes navigation, app return, process death, Settings, All apps and Art Mode.
The MediaSession smoke fixture passes all 18 checks, including transport controls,
access revocation and producer death, with zero idle frames in 10 seconds. This
fixture has no real Spotify account or audio playback.

Raw measurements live locally under build/tick-performance; verification logs live
under build/verify. The emulator reports a regular AudioTrack fallback because its
audio server denies fast output. No sound latency measurement is claimed.

The emulator runs without host audio output. Physical TV loudness, audio latency,
and performance have not been measured for this change.
