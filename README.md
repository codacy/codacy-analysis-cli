# Codacy Analysis CLI

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/37634a4929cb44999101ba29d7da96dc)](https://www.codacy.com/app/Codacy/codacy-analysis-cli?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=codacy/codacy-analysis-cli&amp;utm_campaign=Badge_Grade)
[![Codacy Badge](https://api.codacy.com/project/badge/Coverage/37634a4929cb44999101ba29d7da96dc)](https://www.codacy.com/app/Codacy/codacy-analysis-cli?utm_source=github.com&utm_medium=referral&utm_content=codacy/codacy-analysis-cli&utm_campaign=Badge_Coverage)
[![CircleCI](https://circleci.com/gh/codacy/codacy-analysis-cli.svg?style=svg)](https://circleci.com/gh/codacy/codacy-analysis-cli)

Small command line interface to execute Codacy code analysis locally.

## :warning: This project is under development and not production ready

## Features

- (P) Invoke a tool
  - (D) Local tool configuration file
  - (D) Remote Codacy patterns, ignored files and language extensions
  - ( ) Default settings
- (P) Invoke multiple tools
- ( ) Invoke tools in parallel
- (P) Post results to Codacy
- ( ) Exit with status from Codacy quality settings

> (D) - Done | (P) - Partially Done | ( ) - Not Started

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

## Install

### MacOS

```bash
brew tap codacy/tap
brew install codacy-analysis-cli
```

### Others

```bash
curl -L https://github.com/codacy/codacy-analysis-cli/archive/master.tar.gz | tar xvz
cd codacy-analysis-cli-* && sudo make install
```

## Usage

### Local

```sh
sbt "runMain com.codacy.analysis.cli.Main analyse --tool <TOOL-SHORT-NAME> --directory <SOURCE-CODE-PATH>"
```

### Docker

```sh
docker run \
  --rm=true \
  --env CODACY_CODE="$CODACY_CODE" \
  --volume /var/run/docker.sock:/var/run/docker.sock \
  --volume "$CODACY_CODE":"$CODACY_CODE" \
  --volume /tmp:/tmp \
  codacy/codacy-analysis-cli \
    --tool <TOOL-SHORT-NAME>
```

### Script

```sh
codacy-analysis-cli analyse \
  --tool <TOOL-SHORT-NAME> \
  --directory <SOURCE-CODE-PATH>
```

## Configuration

### CLI Parameters

* `--tool` - Choose the tool to analyse the code (e.g. brakeman)
* `--directory` - Choose the directory to be analysed
* `--codacy-api-base-url` or env.`CODACY_API_BASE_URL` - Change the Codacy installation API URL to retrieve the configuration (e.g. Enterprise installation)
* `--output` - Send the output results to a file
* `--format` - Change the output format (e.g. json)
* `--commit-uuid` - Set the commit UUID that will receive the results on Codacy
* ` --upload` - Request to push results to Codacy 

### Local configuration

To perform certain advanced configurations, Codacy allows to create a configuration file.
Check our [documentation](https://support.codacy.com/hc/en-us/articles/115002130625-Codacy-Configuration-File) for
more details.

### Remote configuration

To run locally the same analysis that Codacy does in your code you can request remotely the configuration.

#### Project Token

You can find the project token in:
* `Project -> Settings -> Integrations -> Add Integration -> Project API`

```sh
codacy-analysis-cli analyse \
  --project-token <PROJECT-TOKEN> \
  --tool <TOOL-SHORT-NAME> \
  --directory <SOURCE-CODE-PATH>
```

> In alternative to setting `--project-token` you can define CODACY_PROJECT_TOKEN in the environment.

#### API Token

You can find the project token in:
* `Account -> API Tokens`

The username and project name can be retrieved from the URL in Codacy.

```sh
codacy-analysis-cli analyse \
  --api-token <PROJECT-TOKEN> \
  --username <USERNAME> \
  --project <PROJECT-NAME> \
  --tool <TOOL-SHORT-NAME> \
  --directory <SOURCE-CODE-PATH>
```

> In alternative to setting `--api-token` you can define CODACY_API_TOKEN in the environment.

## What is Codacy

[Codacy](https://www.codacy.com/) is an Automated Code Review Tool that monitors your technical debt, helps you improve your code quality, teaches best practices to your developers, and helps you save time in Code Reviews.

### Among Codacy’s features

- Identify new Static Analysis issues
- Commit and Pull Request Analysis with GitHub, BitBucket/Stash, GitLab (and also direct git repositories)
- Auto-comments on Commits and Pull Requests
- Integrations with Slack, HipChat, Jira, YouTrack
- Track issues in Code Style, Security, Error Proneness, Performance, Unused Code and other categories

Codacy also helps keep track of Code Coverage, Code Duplication, and Code Complexity.

Codacy supports PHP, Python, Ruby, Java, JavaScript, and Scala, among others.

### Free for Open Source

Codacy is free for Open Source projects.

