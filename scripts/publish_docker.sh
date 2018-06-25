#!/usr/bin/env bash

set -e

VERSION="1.0.0-$(git symbolic-ref --short HEAD)-SNAPSHOT"

if [ -n "$1" ]; then
    VERSION="$1"
fi

# Sanitize
VERSION="$(echo $VERSION | awk -F '[^(?!A-Za-z0-9\.\-\_)]+' -v OFS=- '{$0=tolower($0); $1=$1; gsub(/^-|-$/, "")} 1')"

echo "Deploying version ${VERSION}"
sbt 'set version in codacyAnalysisCli := "'"${VERSION}"'"' codacyAnalysisCli/docker:publishLocal
