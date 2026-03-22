#!/usr/bin/env bash

set -euo pipefail

modules=(
  app
  core-data
  core-parser
  core-segmentation
  core-ai
  core-tts
  core-playback
  feature-library
  feature-generate
  feature-player
)

missing=()
for module in "${modules[@]}"; do
  if [[ ! -d "$module" ]]; then
    missing+=("$module")
  fi
done

if (( ${#missing[@]} > 0 )); then
  printf 'Missing module directories:\n' >&2
  for module in "${missing[@]}"; do
    printf '  - %s\n' "$module" >&2
  done
  exit 1
fi

printf 'All required module directories are present.\n'
