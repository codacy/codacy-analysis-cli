#!/usr/bin/env bash

set -e

sbt +compile +test:compile
