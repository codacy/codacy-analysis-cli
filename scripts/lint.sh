#!/usr/bin/env bash

set -e

sbt "scalafmtCheckAll;scapegoat;scalafixEnable;scalafix --test"
