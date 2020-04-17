#!/usr/bin/env bash

set -e

sbt "scalafmtCheckAll;scalafmtSbtCheck;clean;scapegoat;scalafixEnable;scalafix --test"
