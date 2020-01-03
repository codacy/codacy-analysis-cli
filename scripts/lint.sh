#!/usr/bin/env bash

set -e

# sbt scalafmtCheck scapegoat scalafixEnable "scalafixCli --test"
sbt scalafmtCheckAll scapegoat
