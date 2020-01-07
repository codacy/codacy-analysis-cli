#!/usr/bin/env bash

set -e

sbt "scalafmtCheckAll;scalafmtSbtCheck;scapegoat;scalafixEnable;scalafix --test"
