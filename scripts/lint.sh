#!/usr/bin/env bash

set -e

# sbt scalafmtCheck scapegoat scalafixEnable "scalafixCli --test" dependencyCheckAggregate
sbt scalafmtCheck scapegoat dependencyCheckAggregate
