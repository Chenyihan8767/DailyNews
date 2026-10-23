#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$task_root"
if [[ -x "$task_root/.tooling/jdk/bin/java" ]]; then
  export JAVA_HOME="$task_root/.tooling/jdk"
fi
if [[ -d "$task_root/.tooling/sdk" ]]; then
  export ANDROID_HOME="$task_root/.tooling/sdk"
fi
if [[ -x "$task_root/.tooling/gradle-8.13/bin/gradle" ]]; then
  export GRADLE_USER_HOME="$task_root/.tooling/gradle-home"
  task_gradle="$task_root/.tooling/gradle-8.13/bin/gradle"
else
  task_gradle="$task_root/gradlew"
fi
if [[ "$#" -eq 0 ]]; then
  set -- :app:assembleDebug
fi
exec "$task_gradle" "$@" --console=plain
