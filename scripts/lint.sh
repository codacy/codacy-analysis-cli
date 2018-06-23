#!/usr/bin/env bash

set -e

sbt scalafmtCheck
sbt scapegoat
sbt scalafixEnable "scalafixCli --test"
sbt dependencyCheckAggregate
