#!/usr/bin/env bash

#
# Codacy Analysis CLI Wrapper
#

log_error() {
  local message=$1

  cat >&2 <<-EOF
		We encountered a problem with your Docker setup:
		  > ${message}
		
		Please check https://github.com/codacy/codacy-analysis-cli for alternative instructions.
		
	EOF
  exit 1
}

test_docker_socket() {
  if [ ! -S /var/run/docker.sock ]; then
    log_error "/var/run/docker.sock must exist as a Unix domain socket"
  elif [ -n "${DOCKER_HOST}" ] && [ "${DOCKER_HOST}" != "unix:///var/run/docker.sock" ]; then
    log_error "invalid DOCKER_HOST=${DOCKER_HOST}, must be unset or unix:///var/run/docker.sock"
  fi
}

run() {
  extra_args=()

  # Test stdin and stdout
  if [ -t 0 ] && [ -t 1 ]; then
    extra_args+=("--tty")
  fi

  docker run \
    --interactive --rm \
    --volume /var/run/docker.sock:/var/run/docker.sock \
    --name codacy-analysis-cli \
    ${extra_args[@]} \
    codacy/codacy-analysis-cli \
    "$@"
}

test_docker_socket

run "$@"
