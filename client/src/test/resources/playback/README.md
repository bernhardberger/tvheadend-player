# Growing recording fixture

`growing-recording.ts` is a synthetic 12-second, mono MP2 tone in MPEG-TS,
generated without external inputs:

```sh
/usr/bin/ffmpeg -nostdin -hide_banner -loglevel error -f lavfi -i 'sine=frequency=440:sample_rate=32000:duration=12' -map 0:a -c:a mp2 -b:a 64k -f mpegts -y growing-recording.ts
```

`RecordingRecoveryPositionTest` splits it at a 188-byte packet boundary. Its
real-source cases pass these bytes through the SDK growing recording source and
Media3's TS extractor, not the controlled WAV source used by the other cases.
