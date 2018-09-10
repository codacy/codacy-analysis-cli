#!/usr/bin/env bash

command -v docker > /dev/null 2>&1 || {
  echo "Unable to find 'docker'. Docker might not be correctly installed." >&2
  echo "Make sure it is present in your \$PATH." >&2
  exit 1
}

docker version --format '{{.Server.Version}}' || {
  echo "Unable to run 'docker version'. Docker daemon needs to be running." >&2
  exit 1
}