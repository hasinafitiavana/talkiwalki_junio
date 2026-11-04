# Architecture

## Intent

TalkiWalki Junior uses a pragmatic, single-module Clean Architecture style. The
application starts as a Compose app and keeps Android, transport, and audio
details out of business rules. It now provides a small TCP transport for a
manual local Wi-Fi Host/Client session; audio remains out of scope.

## Package structure

```text
com.talkiwalki.junior
├── core/          Shared utilities and app configuration
│   └── coroutines/ DispatcherProvider for testable asynchronous work
├── domain/        Platform-independent business models and repository contracts
│   └── model/      SessionId, Participant, ParticipantRole
├── data/          Repository implementations and data-source coordination (future)
├── network/       TCP local session transport and protocol
├── audio/         Recording, playback, and PTT audio pipeline (future)
├── service/       Android foreground/background service boundary (future)
└── ui/            Compose screens, components, themes, and ViewModels
    └── theme/      Existing Material 3 theme
```

`network/LocalSessionRepository` owns exactly one session. `NsdSessionDiscovery`
is an Android-only adapter for DNS-SD/mDNS registration, discovery and service
resolution; it publishes immutable snapshots and does not own sockets. The Host listens on
TCP port 49321, accepts simultaneous clients, validates JOIN requests and
broadcasts participants. The Client connects to a manually entered IPv4
address, sends heartbeats and closes with LEAVE. `network/protocol` uses
versioned, line-delimited UTF-8 frames with an 8 KiB maximum; individual text
fields use URL-safe Base64 so delimiters cannot corrupt frames.

Audio is foreground-only PCM: mono 16-bit 16 kHz frames from `AudioRecord` are
sent by UDP to the Host and relayed to registered clients. TCP remains the
authority for PTT leases and UDP endpoint registration. The Host validates the
source address, participant id and active PTT lease before relaying a packet.

NSD discovery holds a Wi-Fi multicast lock only while actively discovering and
always releases it on stop/failure. Service resolutions are serialized because
some Android/OEM NSD implementations reject or destabilize concurrent resolve
requests. NSD diagnostics use the stable Logcat tag `TalkiWalkiNSD`.

## Dependency direction

`ui` depends on `domain`; `data` implements domain contracts and may use
`network`, `audio`, and Android services. `domain` has no Android dependency.
`core` is shared only when it does not introduce a higher-layer dependency.
ViewModels will expose immutable `StateFlow` UI state and accept a
`DispatcherProvider`, allowing coroutine work to be controlled in tests.

## Current technical baseline

The app uses Kotlin, Jetpack Compose, Material 3, Kotlin Coroutines and the
Android `INTERNET` normal permission, which is required for local TCP sockets
but does not require an Internet connection. The minimum supported API is 26.
There is no discovery, audio capture/playback or foreground service yet.
