#!/usr/bin/env bash
set -euo pipefail

sonar_args=()
if [ "${GITHUB_EVENT_NAME}" = "pull_request" ]; then
  sonar_args+=(
    "-Dsonar.pullrequest.key=${PR_NUMBER}"
    "-Dsonar.pullrequest.branch=${PR_HEAD_REF}"
    "-Dsonar.pullrequest.base=${PR_BASE_REF}"
  )
elif [ "${GITHUB_EVENT_NAME}" = "workflow_dispatch" ]; then
  pr_info="$(gh pr view "${GITHUB_REF_NAME}" --json number,baseRefName,headRefName --jq '[.number,.baseRefName,.headRefName] | @tsv' 2>/dev/null || true)"
  if [ -n "${pr_info}" ]; then
    IFS=$'\t' read -r pr_number pr_base pr_head <<< "${pr_info}"
    sonar_args+=(
      "-Dsonar.pullrequest.key=${pr_number}"
      "-Dsonar.pullrequest.branch=${pr_head}"
      "-Dsonar.pullrequest.base=${pr_base}"
    )
  fi
fi

./gradlew \
  :core:test \
  :core:jacocoTestReport \
  :fsrs-java:test \
  :fsrs-java:jacocoTestReport \
  :domain:test \
  :domain:jacocoTestReport \
  :sync-domain:test \
  :sync-domain:jacocoTestReport \
  :writing-core:test \
  :writing-core:jacocoTestReport \
  :dictionary-core:test \
  :dictionary-core:jacocoTestReport \
  :update-core:test \
  :update-core:jacocoTestReport \
  :app:testDebugUnitTest \
  :app:jacocoDebugUnitTestReport \
  :app:lintDebug \
  :app:compileDebugAndroidTestJavaWithJavac \
  :app:createDebugCoverageReport \
  --parallel \
  -Dorg.gradle.parallel=true \
  -PsonarFullCoverage=true \
  -PsonarProjectVersion="${SONAR_PROJECT_VERSION}"

test -f app/build/reports/coverage/androidTest/debug/connected/report.xml

./gradlew sonar \
  --parallel \
  -Dorg.gradle.parallel=true \
  -PsonarFullCoverage=true \
  -PsonarProjectVersion="${SONAR_PROJECT_VERSION}" \
  "${sonar_args[@]}"
