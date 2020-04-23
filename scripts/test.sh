#!/usr/bin/env bash

set -e

if [ -n "$1" ]; then
    export CODACY_PROJECT_TOKEN="$1"
fi

sbt coverage +test coverageReport
bash <(curl -Ls https://coverage.codacy.com/get.sh) report --skip
