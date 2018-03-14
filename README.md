# Codacy Analysis CLI

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/e490e1a232a04bccb113ff55b8126947)](https://www.codacy.com?utm_source=git@bitbucket.org&amp;utm_medium=referral&amp;utm_content=qamine/codacy-analysis-cli&amp;utm_campaign=Badge_Grade)

Small command line interface to execute code analysis locally.

## Prerequisites

* Java 8+
* SBT 1.1.x
* Scala 2.12.x

## Build

### Compile

* **Code**

    **Note:** - Scapegoat runs during compile in Test, to disable it, set `NO_SCAPEGOAT`.

        sbt compile
        
* **Tests**

        sbt test:compile

### Test

```sh
sbt test
```

### Format Code

```sh
sbt scalafmtCheck
sbt scalafmt
```

### Dependency Updates

```sh
sbt dependencyUpdates
```

### Static Analysis

```sh
sbt scapegoat
sbt scalafix sbtfix
```

### Coverage

```sh
sbt coverage test
sbt coverageReport
sbt coverageAggregate
export CODACY_PROJECT_TOKEN="<TOKEN>"
sbt codacyCoverage
```

### Docker

* **Local**

        sbt 'set version := "<VERSION>"' docker:publishLocal

* **Release**

        sbt 'set version := "<VERSION>"' docker:publish

## Usage

### Local

```sh
sbt "runMain com.codacy.analysis.cli.Main"
```

### Docker

```sh
docker run \
  --interactive --tty --rm \
  --env CODACY_CODE="$PWD" \
  --volume "$PWD":/code \
  --volume /var/run/docker.sock:/var/run/docker.sock \
  --volume /tmp/codacy/cli:/tmp/codacy/cli \
  codacy/codacy-analysis-cli:<VERSION>
```

### Script

```sh
# TODO
```
