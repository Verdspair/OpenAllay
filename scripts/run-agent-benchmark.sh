#!/usr/bin/env bash
set -euo pipefail

mode="${1:-deterministic}"

case "$mode" in
  deterministic)
    exec ./gradlew :common:test \
      --tests 'dev.openallay.benchmark.*' \
      --rerun-tasks \
      --max-workers=1
    ;;
  live)
    : "${OPENALLAY_MODEL_BASE_URL:?Set OPENALLAY_MODEL_BASE_URL, including the API version path}"
    : "${OPENALLAY_MODEL:?Set OPENALLAY_MODEL}"
    : "${OPENALLAY_API_KEY:?Set OPENALLAY_API_KEY in the environment}"
    export OPENALLAY_LIVE_JAVASCRIPT_AGENT=true
    export OPENALLAY_MODEL_PROTOCOL="${OPENALLAY_MODEL_PROTOCOL:-OPENAI_CHAT}"
    export OPENALLAY_BENCHMARK_REPEATS="${OPENALLAY_BENCHMARK_REPEATS:-3}"
    exec ./gradlew-curl :common:test \
      --tests 'dev.openallay.model.live.LiveJavascriptAgentAcceptanceTest' \
      --rerun-tasks \
      --max-workers=1
    ;;
  *)
    echo "Usage: $0 [deterministic|live]" >&2
    exit 2
    ;;
esac
