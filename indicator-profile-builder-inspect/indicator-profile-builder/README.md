# Indicator Profile Builder

Standalone technician tool for discovering, capturing, and validating a real weighbridge
indicator's communication protocol, and turning it into a saved, versioned **profile JSON
file** that the main TanroadsVehicleLoadControlSystem application imports as a new DRAFT
indicator profile.

It runs as a small local web application on the technician's laptop: no database, no Spring,
no internet connection needed. Profiles and evidence samples are plain JSON files in a
`data` folder next to the program.

## Workflow at a weighbridge

1. Run `run.cmd` (or `java -jar indicator-profile-builder.jar`). The browser opens at
   http://127.0.0.1:8765.
2. Type your name in **Technician name** (recorded on every save and sample).
3. **New Profile**: fill in manufacturer, model, build, firmware, software, protocol.
4. Choose **Communication Type** (TCP/IP or Serial / COM) and the connection parameters.
   For serial, **Rescan** lists the COM ports the laptop currently sees.
5. **Save & Connect**. The **Raw Data** panel shows every byte as it arrives, in ASCII
   (control bytes made visible as `\r` `\n` `<STX>` ...) and in HEX.
6. Look at the raw stream and set **Message Framing**, **Delimiter** / fixed length,
   **Extraction Rule** and pattern, **Unit**, **Stability Rule**.
7. Click **Apply Rules to Live Stream**: the live stream is re-parsed with the new rules
   without reconnecting. **Parser Preview** shows the weight, status and the reason a
   message was rejected. **Test rules against captured data** re-runs the rules over the
   bytes captured so far (or over text you paste) so you can compare variants offline.
8. When the parsed weight matches what the indicator display shows, **Save Sample as
   Evidence** (station, lane, label, notes), several times, including unstable/startup samples.
9. **Advance Status**: DRAFT -> TESTING -> VALIDATED -> APPROVED -> DEPLOYED. Reaching
   VALIDATED requires at least 20 messages in the current capture session, at least one
   valid reading, and the explicit "RAW vs. PARSED is CORRECT" confirmation.
10. **Export JSON (download)**: the file `indicator-profile-<name>-v<n>.json` is what gets
    imported into the main system (Indicator Profile Builder screen, "Import profile"). The
    main system always imports it as a new DRAFT and re-earns approval there.

An APPROVED or DEPLOYED profile is never edited in place: **Create New Version** makes a
new DRAFT (version + 1) that supersedes it, exactly as in the main system.

## Compatibility with the main system

* The exported JSON uses the exact property names of `tload.indicator.communication.IndicatorProfile`
  (`name`, `connectionType`, `messageDelimiter`, `extractionRule`, `stabilityRule`, ...), so the
  main system's `IndicatorProfileImportExportService.importAsDraft()` reads it unchanged.
  Extra properties (`captureSamples`, `exportFormat`, `createdByName`, ...) are ignored there.
* Delimiters and query commands are stored as the real control characters (a real CR LF),
  which is what the main system's packetizer searches for. The form shows them escaped.
* The packetizer, message parser and stability engine are ports of the main system's own
  classes, so a rule that parses correctly here behaves identically in production.
* Only the two connection types the main system knows (SERIAL, TCP client) are offered.

## Files

```
data/profiles/<id>.json          one profile per file (importable as-is)
data/samples/<profileId>/<id>.json  one evidence sample per file
```

## Command line

```
java -jar indicator-profile-builder.jar [--port 8765] [--host 127.0.0.1] [--data <dir>]
                                        [--no-browser] [--simulator <tcpPort>]
```

`--simulator 9001` also starts a **simulated** TCP indicator on 127.0.0.1:9001 that streams
`ST,GS,  12340 kg\r\n` style messages (status alternating ST/US). It exists for training and
for trying the tool without hardware; nothing it produces is evidence about a real indicator.

## Build

`build.cmd` (uses `mvn` from PATH, or the Maven 3.9.9 vendored in the main project) runs the
tests and produces `target/indicator-profile-builder.jar`. Requires JDK 21 or newer.

## Notes / limits

* The UI binds to 127.0.0.1 only and has no login: it is a single-technician laptop tool.
  Use `--host 0.0.0.0` only on a trusted network.
* Serial access uses jSerialComm 2.11.0 (same as the main system). If its native library
  cannot load on a given laptop, the error is shown in the UI when connecting; TCP is unaffected.
* Serial and TCP code paths were exercised against a loopback TCP simulator only; real
  indicator hardware validation is still required at a station.
