# Audio implementation verification

Verified on Windows with Java 25 and Gradle 9.3.0, 2026-09-09.

## Results

- Common tests on MC 26.1.2 and 26.2: 256 cases combined, 254 passed,
  2 skipped (the opt-in native test), no failures or errors.
- Audio-specific tests: 8 passed per version. Coverage includes all four
  codecs, 24-bit PCM/FLAC, downmixing, decode limits, queue progress, controls,
  immutable directory/ZIP/JAR content, hashes and signature payload changes.
- Separate `verifyAudioNative`: 1 passed per version, no skips. Uses an actual
  OpenAL device with muted output to check speed, pause, seek and stop.
- `verifyAudioExamples`: passed on both versions, including all three
  integration probe scripts.
- Fabric/NeoForge JAR and Paper shadow JAR builds: passed for both versions.
  Inspected all six `build/*-katton-0.5.0-build2+mc*.jar` artifacts:
  Fabric/NeoForge contain nested JLayer/jFLAC dependencies; Paper does not.
  License notices are included.
- `git diff --check`: passed; Git emitted line-ending normalization warnings.

## In-game probes

Probe sources are in `common/src/test/resources/audio-probe/`. They are test
scripts, not shipped gameplay: they stop their test game/server on completion.
Run only in a disposable game directory.

- Fabric 26.2 local client: `AUDIO_CLIENT_PROBE_PASS`, covering media-time
  progression at 1x/2x/0.5x, pause, seek, sound-system reload, managed-resource
  detach/restore, stop/replay and disposal.
  Local evidence: `build/audio-client-probe/logs/latest.log`.
- Fabric 26.2 dedicated server and separate client:
  `AUDIO_REMOTE_PROBE_PASS`, covering entity-follow playback, pause, seek,
  rate change, confirmed snapshots and stop. The server then closes the handle
  and shuts down; the client reports `AUDIO_PEER_PROBE_EXIT connected=true`.
  Local evidence: `build/audio-server-probe/logs/latest.log` and
  `build/audio-peer-probe/logs/latest.log`.

The remote server launcher exited successfully. After the client exited,
its concurrent Gradle launcher reported a Paperweight maintenance-cache lock
error. The gameplay probe passed; this launcher exit is not counted as a
successful Gradle build. The separate build and example verification commands
above exited successfully.

Not exercised in-game: NeoForge, MC 26.1.2, Paper/Folia entity-region playback,
physical output-device switching, or a complete end-user script-reload
transaction. Managed detach/restore and sound-system reload were exercised,
but do not replace those remaining integration checks.

## Alpha 0.5.0 pre-release review

A static review of the audio subsystem before the 0.5.0 release found and fixed:
the mono downmix wrote one left-channel sample per stereo frame (half duration and
no right channel) — and the previous downmix test passed anyway because the
fixture has identical left and right channels, so a per-sample assertion with
opposite-phase channels was added; the PCM cache charged removed entries against
its 1 GiB budget forever and could block a decoder thread on the cache monitor;
`PcmStream` never closed its `FileChannel`; OpenAL queue bookkeeping failed short
clips permanently; client channel state could be mutated off the sound executor;
respawn and dimension changes were treated as disconnects for remote handles, so
client playback could not be stopped; one timed-out remote command poisoned every
later command; closed and failed instances were never reaped from the
64-instance budget; and `seek`/`setLoop` after `ENDED` did nothing. NeoForge
registered the audio payload twice, which aborted network registry setup at
startup. Regression coverage was extended for the downmix and for the new
script-pack protocol generation, and `verifyReleaseArtifacts` now checks that the
audio libraries and license notices are packaged on each platform.

## Self-evaluation

Overall: 4.0/5. No known failing code test; runtime coverage is narrower than
the supported build matrix.

| Axis | Score | Evidence and improvement |
| --- | --- | --- |
| Accuracy | 4/5 | Both versions compile and tests pass; extend actual game checks beyond Fabric 26.2. |
| Completeness | 4/5 | APIs, codecs, pack snapshots, lifecycle and examples are implemented; run the remaining integration matrix above. |
| Clarity | 4/5 | Usage and platform limits are documented; a shorter Chinese-only quickstart would improve onboarding. |
| Actionability | 4/5 | Six deployable artifacts and compiled examples exist; automate disposable integration-probe setup. |
| Conciseness | 4/5 | Public usage is compact; implementation necessarily spans decoding, networking and loader integration. |

Highest-impact follow-ups: run the remaining game/platform matrix, then
automate the disposable probe launch workflow. Self-check: this assessment
should match the user's evidence-based review because it distinguishes tested
behavior from compile-only support. Verdict: deliver with these verification
limits stated.
