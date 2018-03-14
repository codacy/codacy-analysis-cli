# Codacy Analysis CLI

## **ADD CODACY CODE AND COVERAGE BADGES**

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
sbt "runMain com.codacy.analysis.cli.Main $PWD"
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

//// CONTINUE HERE

## Configuration File

* Java Properties:
    * `config.file` - absolute path to the configuration file.
    * `config.resource` - path to the configuration file provided through the resources.
* Environment
    * `CONFIG_FILE` - absolute path to the configuration file.
    * `CONFIG_RESOURCE` - path to the configuration file provided through the resources.

> Default: `-Dconfig.file=/etc/codacy/enterprise/application.conf`

## Server Port

* Java Properties - `http.port`
* Environment - `HTTP_PORT`
* Configuration File Property - `http.port`

> Default: `8080`

## Configuration File Options

### Make Directories Configuration

* Configuration File Property
    * `make.directories` - config with the directory structure to create

    **Example:**
    
        make.directories = [
          {
            root = "/base/directory"
            children = [
              {
                filename = "path/to/my/directory"
                group = "nogroup"
                owner = "nobody"
              }
            ]
          },
          {
            root = "/other/base/directory"
            children = [
              {
                 filename = "path/to/my/other/directory"
              }
            ]
          }
        ]

### Copy Files Configuration

* Configuration File Property
    * `copy.files` - config with the files to copy

    **Example:**

        copy.files = [
          {
            source = "/base/directory/to/my/file.log"
            destination = {
                filename = "path/to/my/file.log"
                group = "nogroup"
                owner = "nobody"
            }
          },
          {
            source = "/other/source/directory/to/my/other/file.log"
            destination = {
                filename = "path/to/my/other/file.log"
            }
          }
        ]

### Log Directories to Cleanup

* Configuration File Property
    * `cleanup.log.dirs` - absolute path to the directory where we should clean the log files

### Docker Compose/Swarm File (v3) to remove old images

* Configuration File Property
    * `cleanup.compose.file` - absolute path to the file containing the services to remove old image versions
