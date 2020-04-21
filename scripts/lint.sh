#!/usr/bin/env bash

set -e

sbt -mem 2048 "scalafmtCheckAll;scalafmtSbtCheck;clean;scapegoat;scalafixEnable;scalafix --test"
