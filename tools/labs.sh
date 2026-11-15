#!/bin/sh
# Runs the Lab (gradlew runLab) in a checkout of any branch, once per run asked for, one after another, each run's log in
# the log folder given (never the mods folder). A watchdog closes the client at once if its log shows a loading error
# or a crash (an error screen would otherwise wait on the desktop of the person at the computer), and closes a run that
# has gone on for 6 minutes (a run takes about 2).
#
#   sh tools/labs.sh <checkout> <log folder> <log prefix> <run>...
#   runs: plain | iris | irisstable (the 1.21.1 branch's released Iris and Sodium pair) | iris:<pack> | dh
DIR="$1"; LOGS="$2"; PREFIX="$3"; shift 3
export JAVA_HOME=/c/Users/lawso/curseforge/minecraft/Install/runtime/java-runtime-epsilon/windows-x64/java-runtime-epsilon
cd "$DIR" || exit 1
NAME=$(basename "$DIR")
close_client() {
  powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"name='java.exe' or name='javaw.exe'\" | Where-Object { \$_.CommandLine -match [regex]::Escape('$NAME') -and \$_.CommandLine -notmatch 'GradleDaemon' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }" > /dev/null 2>&1
}
for run in "$@"; do
  case $run in
    plain) args="" ;;
    iris) args="-PwithIris" ;;
    irisstable) args="-PwithIris -PirisStable" ;;
    iris:*) args="-PwithIris -PirisPack=${run#iris:}" ;;
    dh) args="-PwithDH" ;;
    *) echo "unknown run $run"; continue ;;
  esac
  log="$LOGS/${PREFIX}_$(echo "$run" | tr ':' '_').log"
  ./gradlew runLab $args --console=plain > "$log" 2>&1 &
  gradle=$!
  waited=0
  while kill -0 $gradle 2>/dev/null; do
    waited=$((waited + 3))
    if [ $waited -gt 360 ]; then
      echo "lab $run: still running after 6 minutes (a run takes about 2), closing the client"
      close_client
      break
    fi
    if grep -q "is not a valid mod file\|Mod loading has failed\|Error loading mods\|ModLoadingException\|Critical injection failure\|MixinTransformerError\|---- Minecraft Crash Report ----" "$log" 2>/dev/null; then
      echo "lab $run: loading error or crash, closing the client"
      close_client
      break
    fi
    sleep 3
  done
  wait $gradle
  echo "lab $run exit $?"
  grep -a -n "Lab: frame scan\|never timed\|Critical injection\|is not a valid mod file\|Crash Report" "$log" | cut -c1-400 | head -8
done
