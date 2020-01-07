#!/usr/bin/env bash

set -e

if [[ -z "$1" || -z "$2" ]]
then
  echo "usage: $0 <GIT-REPO-URL> <TARGET-DIRECTORY> [BRANCH]"
fi

REPO_URL="$1"
TARGET_DIRECTORY="$2"
BRANCH="${3:-master}"

# Workaround old docker images with incorrect $HOME
# check https://github.com/docker/docker/issues/2968 for details
if [ "$HOME" = "/" ]
then
  HOME=$(getent passwd "$(id -un)" | cut -d: -f6)
  export HOME
fi

if [ -e "$TARGET_DIRECTORY"/.git ]
then
  cd "$TARGET_DIRECTORY"
  git remote set-url origin "$REPO_URL" || true
else
  mkdir -p "$TARGET_DIRECTORY"
  cd "$TARGET_DIRECTORY"
  git clone -b "$BRANCH" "$REPO_URL" .
fi

git fetch --force origin "$BRANCH:remotes/origin/$BRANCH"
git reset --hard "origin/$BRANCH"
