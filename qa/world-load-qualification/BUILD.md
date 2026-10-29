# World-load qualification harness (test-only)

Offline qualification harness for the Tally fabric mod on Minecraft 1.21.11:
world load, sidebar objective mask pair, orbit camera detach, and staff-HUD
capture surfaces. It runs against a copied owned disposable singleplayer world
in an isolated namespace. It never opens LAN, never contacts an external
server, and never uses a real account (offline-mode `QualDev` username).

No production code is modified: the harness is a separate test-only mod jar
loaded alongside the candidate. The capture mixin hooks only the harness's own
`QualWorldSmoke.captureBeforeSwap()` at the head of `Window.updateDisplay`
(require=1, so a mapping mismatch fails closed at apply time).

## Layout

- `QualWorldSmoke-split-v6_3_3.java` - harness entrypoint (ClientModInitializer)
- `CaptureGate.java` - request/wait capture gate used by the harness
- `TestCaptureGate.java` - standalone gate checks (15 checks, plain `main`)
- `mixin/WindowCaptureMixin.java` - pre-swap capture hook (harness classes only)
- `launcher/qual-launch-load-split-v633-fixed.sh` - supervised launch script with
  sha-pinned inputs, per-attempt disposable rundir, and signature-gated
  adopt/kill discipline (age or session shape alone never authorizes a kill)

## Build requirements

- JDK 21 (tested with Temurin 21.0.13); `javac -proc:none -parameters`
- Compile classpath: mojmap client jar 1.21.11, fabric-loader 0.20.1,
  authlib 7.0.61, lwjgl 3.3.3 (core/opengl/glfw), sponge-mixin
  0.17.5+mixin.0.8.7, plus stub sources matching the candidate jar's binary
  signatures (verified via `javap` against the candidate)
- Remap: two-step tiny-remapper over the compiled jar -
  `named -> official` then `official -> intermediary` - with tiny v2 mappings.
  Classpath jars must be passed as separate arguments, never colon-joined.
- Assembly: remapped `dev/**` classes plus `fabric.mod.json`,
  `qual-world-harness.mixins.json`, and `META-INF/MANIFEST.MF`.
- Gate test: run `TestCaptureGate` standalone before packaging; all 15 checks
  must pass.

## Runtime contract (launcher)

The launcher verifies every input by pin before staging (candidate jar,
harness jar, Fabric API, owned world template, loader-produced game remap
cache, classpath manifest), unpacks a copy of the owned world template into a
unique per-attempt rundir, and refuses to adopt or kill any process whose
exact argv signature does not match the attempt it launched. The world
template's original save is never opened.

See `SHA256SUMS.txt` for the reviewed pins.
