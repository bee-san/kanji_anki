#!/usr/bin/env bash
set -euo pipefail

mode="${1:-fast}"
if [[ "${mode}" != "fast" && "${mode}" != "full" ]]; then
  echo "usage: $0 [fast|full]" >&2
  exit 2
fi

: "${SONAR_PROJECT_VERSION:?SONAR_PROJECT_VERSION is required}"

sonar_args=()
github_event_name="${GITHUB_EVENT_NAME:-}"
if [[ "${github_event_name}" == "pull_request" ]]; then
  pr_number="${PR_NUMBER:-}"
  pr_head_ref="${PR_HEAD_REF:-}"
  pr_base_ref="${PR_BASE_REF:-}"
  if [[ -z "${pr_number}" || -z "${pr_head_ref}" || -z "${pr_base_ref}" ]]; then
    echo "PR_NUMBER, PR_HEAD_REF, and PR_BASE_REF are required for pull-request analysis." >&2
    exit 2
  fi
  sonar_args+=(
    "-Dsonar.pullrequest.key=${pr_number}"
    "-Dsonar.pullrequest.branch=${pr_head_ref}"
    "-Dsonar.pullrequest.base=${pr_base_ref}"
  )
elif [[ "${github_event_name}" == "workflow_dispatch" ]]; then
  github_ref_name="${GITHUB_REF_NAME:-}"
  pr_info="$(gh pr view "${github_ref_name}" --json number,baseRefName,headRefName --jq '[.number,.baseRefName,.headRefName] | @tsv' 2>/dev/null || true)"
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
