#!/usr/bin/env bash
set -euo pipefail

exec bash .github/scripts/run-sonar-analysis.sh full
