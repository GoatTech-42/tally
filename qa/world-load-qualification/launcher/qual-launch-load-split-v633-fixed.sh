#!/bin/bash
# Single supervised JVM WORLD-FIXTURE qualification launch, offline disposable profile + disposable world.
# Same signature-gated adopt/kill discipline as qual-launch.sh; the only differences are the harness
# jar, a 1024MB heap (integrated-server worldgen), its own rundir/harvest prefixes, and the result files.
# Every adopt/kill decision is gated on the exact argv signature of THIS attempt:
# /tmp/jdk21/bin/java + KnotClient + --gameDir <unique per-attempt rundir> + --username QualDev.
# Age or session shape alone NEVER authorizes adoption or a kill. Direct lineage (the pid we
# spawned and its direct children) is the only signature-free kill authority, and it is ours
# by construction. A failed signature means: do not adopt, do not kill, report.
set -u
RD=/tmp/qual-runtime
ATTEMPT=$(date +%Y%m%d-%H%M%S)-$$
RUN=/evidence/run-$ATTEMPT
HR=/evidence
CANDIDATE=/opt/candidate.jar
CANDIDATE_SHA=0f43e6673e52be7fb9fb7a0c53ed0c288fa81cf427832e827613d3f04feb155e
HARNESS_JAR=/opt/harness-preswap-split-v633.jar
HARNESS_SHA=bfc165463f7ec69742f584593186cc2d1f9dbec5386f7e56f2accf09e3e70387
FAPI="$RD/fabric-api-0.141.6+1.21.11.jar"
FAPI_SHA1=c98467cbbaf4d197377266795ae015f4130d65b6

echo "$CANDIDATE_SHA  $CANDIDATE" | sha256sum -c - || exit 1
echo "$HARNESS_SHA  $HARNESS_JAR" | sha256sum -c - || exit 1
echo "$FAPI_SHA1  $FAPI" | sha1sum -c - || exit 1

[[ -z "$(ls -A "$HR")" ]] || { echo "EVIDENCE-DIR-NOT-EMPTY"; exit 1; }
mkdir -p "$RUN/mods" "$HR"
echo "23ebd4bd18823788d58476869d5ba36dea426834e07c0251c536d49646c36c07  /opt/owned-world-template.tgz" | sha256sum -c - || exit 1
mkdir -p "$RUN/saves"
tar --no-same-owner -xzf /opt/owned-world-template.tgz -C "$RUN/saves" || exit 1
# Pinned owned world COPY only; original save is not mounted in the private namespace.
cp "$CANDIDATE" "$RUN/mods/"
cp "$FAPI" "$RUN/mods/"
cp "$HARNESS_JAR" "$RUN/mods/qual-world-harness.jar"
sha256sum -c /opt/classpath.SHA256SUMS || exit 1
# Reuse ONLY the loader-produced game remap from the prior admitted runtime.
# No processedMods, config, profile, world or harness class is included.
echo "2dcd51890aaf8bb219d58dec1df48b56d955edefa167bf78c1e7142ac4ab4acd  /opt/game-cache-intermediary.jar" | sha256sum -c - || exit 1
mkdir -p "$RUN/.fabric/remappedJars/minecraft-1.21.11-0.20.1"
cp /opt/game-cache-intermediary.jar "$RUN/.fabric/remappedJars/minecraft-1.21.11-0.20.1/client-intermediary.jar"
CP="$(cat "$RD/classpath.txt")"

sig_ok() { # $1 = pid; exact argv signature of THIS attempt's launch (NUL-safe)
  local p=$1; [ -r "/proc/$p/cmdline" ] || return 1
  local -a a; mapfile -d '' -t a < "/proc/$p/cmdline"
  [ "${a[0]:-}" = "/tmp/jdk21/bin/java" ] || return 1
  local gd="" un="" mc=0 i
  for i in "${!a[@]}"; do
    case "${a[$i]}" in
      net.fabricmc.loader.impl.launch.knot.KnotClient) mc=1;;
      --gameDir)   gd="${a[$((i+1))]:-}";;
      --username)  un="${a[$((i+1))]:-}";;
    esac
  done
  [ "$mc" -eq 1 ] && [ "$gd" = "$RUN" ] && [ "$un" = "QualDev" ]
}

sig_kill() { # kill only a signature-verified pid of this attempt; refuse otherwise
  local p=$1
  if ! sig_ok "$p"; then echo "REFUSE-KILL pid $p: signature mismatch or gone"; return 1; fi
  kill "$p" 2>/dev/null; sleep "${2:-2}"
  if kill -0 "$p" 2>/dev/null && sig_ok "$p"; then kill -9 "$p" 2>/dev/null; fi
}

# preflight: any foreign java aborts the launch; nothing here ever kills by pattern or age
if pgrep -x java >/dev/null; then echo "PREFLIGHT-ABORT foreign java present"; exit 1; fi

export DISPLAY=:97
if ! pgrep -f 'Xvfb :97' >/dev/null; then
  Xvfb :97 -screen 0 1600x900x24 -nolisten tcp >/tmp/xvfb.log 2>&1 &
  XVPID=$!
  ready=0
  for n in $(seq 1 30); do
    kill -0 "$XVPID" || exit 1
    if [ -S /tmp/.X11-unix/X97 ]; then ready=1; break; fi
    sleep 0.1
  done
  [ "$ready" -eq 1 ] || { echo "XVFB-NOT-READY"; exit 1; }
fi

cd "$RUN"
setsid /tmp/jdk21/bin/java \
  -Xmx1024m -Xms128m \
  -XX:TieredStopAtLevel=1 \
  -Djava.library.path="$RD/libraries" \
  -Dqual.group="${QUAL_GROUP:-all}" \
  -cp "$CP" net.fabricmc.loader.impl.launch.knot.KnotClient \
  --gameDir "$RUN" --assetsDir "$RD/assets" --assetIndex 29 \
  --version 1.21.11 --username QualDev \
  --uuid 01234567-89ab-cdef-0123-456789abcdef \
  --accessToken 0 --userType legacy \
  > "$HR/client.log" 2>&1 &
SPAWN=$!

# bounded 10s adoption: direct lineage first (spawn itself, then its children), the
# session-leader scan last; every candidate must pass the exact signature. age is never
# used as authority for anything.
JPID=""
for i in $(seq 1 20); do
  if [ "$(ps -o comm= -p "$SPAWN" 2>/dev/null)" = "java" ] && sig_ok "$SPAWN"; then JPID=$SPAWN; break; fi
  for CHILD in $(pgrep -P "$SPAWN" 2>/dev/null); do
    if sig_ok "$CHILD"; then JPID=$CHILD; break 2; fi
  done
  for LEAD in $(ps -eo pid,sid,comm --no-headers | awk '$3=="java" && $1==$2 {print $1}'); do
    if sig_ok "$LEAD"; then JPID=$LEAD; break 2; fi
  done
  sleep 0.5
done
if [ -z "$JPID" ]; then
  echo "SPAWN-FAIL: no signature-verified java pid; cleaning up own lineage only"
  for CHILD in $(pgrep -P "$SPAWN" 2>/dev/null); do sig_kill "$CHILD" 5; done
  kill "$SPAWN" 2>/dev/null; sleep 2; kill -9 "$SPAWN" 2>/dev/null
  pgrep -x java >/dev/null && echo "WARNING unsigned java remains - NOT killed, needs manual review"
  exit 1
fi
SID=$(ps -o sid= -p "$JPID" | tr -d ' '); PGID=$(ps -o pgid= -p "$JPID" | tr -d ' ')
if [ "$SID" != "$JPID" ] || [ "$PGID" != "$JPID" ]; then
  echo "SPAWN-FAIL: verified java pid $JPID not its own session/group leader (sid=$SID pgid=$PGID)"
  sig_kill "$JPID" 5
  exit 1
fi
{
  echo "attempt=$ATTEMPT"
  echo "pid=$JPID sid=$SID pgid=$PGID"
  echo "gameDir=$RUN"
  echo "start=$(date -Is)"
  tr '\0' ' ' < "/proc/$JPID/cmdline"; echo
} > "$HR/launch-receipt.txt"
echo "launch pid $JPID (signature-verified, own session) start $(date -Is)"

ABORT="inner wall clock 105s or clean exit"
STALL=0
for i in $(seq 1 105); do
  sleep 1
  if ! kill -0 "$JPID" 2>/dev/null; then ABORT="process exited on its own after ${i}s"; break; fi
  RSS=$(awk '/^VmRSS:/{sum+=$2}END{print sum+0}' /proc/[0-9]*/status 2>/dev/null)
  THR=$(awk '/^Threads:/{sum+=$2}END{print sum+0}' /proc/[0-9]*/status 2>/dev/null)
  AVAILABLE=$(awk '/^MemAvailable:/{print $2}' /proc/meminfo)
  echo "resource seconds=$i aggregate_rss_kib=$RSS aggregate_threads=$THR available_kib=$AVAILABLE" >> "$HR/resource-watch.log"
  if [ "${RSS:-0}" -gt 1433600 ]; then ABORT="aggregate rss ${RSS}KiB >1400MiB soft cap"; break; fi
  if [ "${THR:-0}" -gt 400 ]; then ABORT="aggregate threads $THR >400 soft cap"; break; fi
  if [ "${AVAILABLE:-0}" -lt 262144 ]; then ABORT="available memory ${AVAILABLE}KiB <256MiB floor"; break; fi
  if [ -f "$RUN/qual-world-success.txt" ] || [ -f "$RUN/qual-world-error.txt" ]; then
    STALL=$((STALL+1))
    if [ "$STALL" -ge 60 ]; then ABORT="result file present 60s but jvm did not stop"; break; fi
  fi
done
if kill -0 "$JPID" 2>/dev/null; then
  sig_kill "$JPID" 5
  sleep 2
  # private-group sweep, re-gated: only while the leader still exists with our signature
  if kill -0 "$JPID" 2>/dev/null && sig_ok "$JPID"; then kill -- -"$JPID" 2>/dev/null; fi
fi
wait "$JPID" 2>/dev/null
cp "$RUN"/qual-* "$HR"/ 2>/dev/null
if kill -0 "$JPID" 2>/dev/null; then echo "CLEANUP-FAIL pid $JPID still alive"; else echo "cleanup verified: own pid $JPID exited ($ABORT)"; fi
pgrep -x java >/dev/null && echo "WARNING unsigned java present - NOT killed, needs manual review" || echo "zero java verified"
cp /tmp/xvfb.log "$HR/xvfb.log" 2>/dev/null || true
echo "harvested:"; ls -la "$HR"
