#!/usr/bin/env bash

set -e

CURRENT_BRANCH="$(git symbolic-ref --short HEAD)"
PUBLISH_BRANCH="master"

BASE_VERSION="0.1.0-pre"
DEFAULT_VERSION="${BASE_VERSION}.${CURRENT_BRANCH}-SNAPSHOT"

if [ -n "$1" ]; then
  VERSION="$1"
else
  VERSION="$DEFAULT_VERSION"
fi

echo "Publishing version ${VERSION}"
if [[ -n "$CI" ]] && [[ "$CURRENT_BRANCH" == "$PUBLISH_BRANCH" || "$CIRCLE_BRANCH" == "$PUBLISH_BRANCH" ]]; then
  sbt 'set version in codacyAnalysisCore := "'"${VERSION}"'"' 'set pgpPassphrase := Some("'"$SONATYPE_GPG_PASSPHRASE"'".toCharArray)' codacyAnalysisCore/publishSigned sonatypeRelease
else
  sbt 'set version in codacyAnalysisCore := "'"${VERSION}"'"' codacyAnalysisCore/publishLocal
fi