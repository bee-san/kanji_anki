#!/usr/bin/env bash
set -euo pipefail

mode="${1:-fast}"
if [[ "${mode}" != "fast" && "${mode}" != "full" ]]; then
  echo "usage: $0 [fast|full]" >&2
  exit 2
fi

: "${SONAR_PROJECT_VERSION:?SONAR_PROJECT_VERSION is required}"

sonar_args=()
if [[ "${GITHUB_EVENT_NAME}" == "pull_request" ]]; then
  sonar_args+=(
    "-Dsonar.pullrequest.key=${PR_NUMBER}"
    "-Dsonar.pullrequest.branch=${PR_HEAD_REF}"
    "-Dsonar.pullrequest.base=${PR_BASE_REF}"
  )
elif [[ "${GITHUB_EVENT_NAME}" == "workflow_dispatch" ]]; then
  pr_info="$(gh pr view "${GITHUB_REF_NAME}" --json number,baseRefName,headRefName --jq '[.number,.baseRefName,.headRefName] | @tsv' 2>/dev/null || true)"
  if [[ -n "${pr_info}" ]]; then
    IFS=$'\t' read -r pr_number pr_base pr_head <<< "${pr_info}"
    sonar_args+=(
      "-Dsonar.pullrequest.key=${pr_number}"
      "-Dsonar.pullrequest.branch=${pr_head}"
      "-Dsonar.pullrequest.base=${pr_base}"
    )
  fi
fi

full_coverage=false
build_tasks=(ciQuality)
if [[ "${mode}" == "full" ]]; then
  full_coverage=true
  build_tasks+=( :app:createDebugCoverageReport )
fi

./gradlew \
  "${build_tasks[@]}" \
  --parallel \
  -Dorg.gradle.parallel=true \
  -PsonarFullCoverage="${full_coverage}" \
  -PsonarProjectVersion="${SONAR_PROJECT_VERSION}"

if [[ "${mode}" == "full" ]]; then
  test -s app/build/reports/coverage/androidTest/debug/connected/report.xml
fi

./gradlew sonar \
  --parallel \
  -Dorg.gradle.parallel=true \
  -PsonarFullCoverage="${full_coverage}" \
  -PsonarProjectVersion="${SONAR_PROJECT_VERSION}" \
  "${sonar_args[@]}"
