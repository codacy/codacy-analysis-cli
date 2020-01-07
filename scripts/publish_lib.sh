#!/usr/bin/env bash

set -e

CURRENT_BRANCH="$(git symbolic-ref --short HEAD)"
PUBLISH_BRANCH="master"

VERSION=$(cat ../.version)

echo "Publishing version ${VERSION}"
if [[ -n "$CI" ]] && [[ "$CURRENT_BRANCH" == "$PUBLISH_BRANCH" || "$CIRCLE_BRANCH" == "$PUBLISH_BRANCH" ]]; then
  sbt 'set codacyAnalysisCore / version := "'"${VERSION}"'"' 'set pgpPassphrase := Some("'"$SONATYPE_GPG_PASSPHRASE"'".toCharArray)' codacyAnalysisCore/publishSigned sonatypeBundleRelease
else
  sbt 'set codacyAnalysisCore / version := "'"${VERSION}"'"' codacyAnalysisCore/publishLocal
fi