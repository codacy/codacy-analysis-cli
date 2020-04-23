#!/usr/bin/env bash

set -e

export JVM_OPTS=../.jvmopts
sbt "scalafmtCheckAll;scalafmtSbtCheck;clean;scapegoat;scalafixEnable;scalafix --test"
