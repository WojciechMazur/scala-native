#!/usr/bin/env bash
# Usage: scala-native-clean-cache.sh [subdir-under-~/.cache/scala-native]
# Example: scala-native-clean-cache.sh   # removes entire scala-native cache
#          scala-native-clean-cache.sh 0.5.12-SNAPSHOT/ab12...  # removes one subtree
set -euo pipefail
root="${HOME}/.cache/scala-native"
if [[ "${1:-}" == "" ]]; then
  rm -rf "${root}"
  echo "Removed ${root}"
else
  rm -rf "${root}/$1"
  echo "Removed ${root}/$1"
fi
