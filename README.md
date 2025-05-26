> ⚠️ **Legacy Component Warning**  
> This component is part of our **classic/legacy** implementation is no longer actively maintained for local analysis; please use the [new Codacy CLI v2 instead](https://github.com/codacy/codacy-cli-v2/).
> For uploading results to Codacy, use this CLI.


# Codacy Analysis CLI

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/2aa6927599ba4d9db6c9cfcb38e397e4)](https://www.codacy.com/gh/codacy/codacy-analysis-cli?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=codacy/codacy-analysis-cli&amp;utm_campaign=Badge_Grade)
[![Codacy Badge](https://api.codacy.com/project/badge/Coverage/2aa6927599ba4d9db6c9cfcb38e397e4)](https://www.codacy.com/gh/codacy/codacy-analysis-cli?utm_source=github.com&utm_medium=referral&utm_content=codacy/codacy-analysis-cli&utm_campaign=Badge_Coverage)
[![CircleCI](https://circleci.com/gh/codacy/codacy-analysis-cli.svg?style=svg)](https://circleci.com/gh/codacy/codacy-analysis-cli)
[![Docker Version](https://images.microbadger.com/badges/version/codacy/codacy-analysis-cli.svg)](https://microbadger.com/images/codacy/codacy-analysis-cli "Get your own version badge on microbadger.com")
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.codacy/codacy-analysis-core_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.codacy/codacy-analysis-core_2.12)

Command line interface to execute Codacy code analysis locally.

With a single command you can:
  - Get static code analysis issues, complexity, duplication and other code metrics
  - Run a tool or the whole suite of supported tools by Codacy
  - Use the tools' default patterns, your configuration files or your settings saved on Codacy

## Prerequisites

### Usage

* Java 8+
* Docker 17.09+

### Development

* Java 8+
* SBT 1.1.x
* Scala 2.12.x
* Docker 17.09+

## Install

```bash
curl -L https://github.com/codacy/codacy-analysis-cli/archive/master.tar.gz | tar xvz
cd codacy-analysis-cli-* && sudo make install
```

### Windows

#### Pre-Requisites
- Have Docker installed on Windows (https://hub.docker.com/editions/community/docker-ce-desktop-windows)
- Have WSL enabled with Ubuntu bash installed (https://docs.microsoft.com/en-us/windows/wsl/install-win10)

#### Docker Configuration
Once the pre-requisites are met, it’s time to enable the connectivity between bash and docker.

It’s mandatory that the daemon is exposed without TLS. In order to do that go to Docker Settings -> General. Just click on the checkbox with the label 'Expose daemon on tcp://localhost:2375 without TLS' and docker will reload.

#### Preparing docker client on bash
Now it’s time to go to the bash and install and configure the docker client.

If you are using Windows 10 (build above 1803) the following command will make the docker client available from the bash
```sudo ln -s "/mnt/c/Program Files/Docker/Docker/resources/bin/docker.exe" /usr/local/bin/docker```

If you are using a previous version of Windows 10, [here](https://medium.com/@sebagomez/installing-the-docker-client-on-ubuntus-windows-subsystem-for-linux-612b392a44c4) you can find a very easy tutorial to follow.

Since you’ll be using the WSL, the variable DOCKER_HOST needs to be configured to `tcp://0.0.0.0:2375`, in order to do that just type the following command on the bash

```export DOCKER_HOST=tcp://0.0.0.0:2375```

It’s also possible to add this variable to your .bashrc or .bash_profile files so that the variable is always ready when you start the bash

```echo "export DOCKER_HOST=tcp://0.0.0.0:2375" >> ~/.bash_profile```

```echo "export DOCKER_HOST=tcp://0.0.0.0:2375" >> ~/.bashrc```

### Mac

If you are using Apple silicon, please make sure to:
- Update Docker to the latest version
- Open Docker Settings
- Turn on `Use Rosetta for x86/amd64 emulation on Apple Silicon` (under _Features in development_)

#### Installing codacy-analysis-cli
At this point, codacy-analysis-cli is ready to be installed.

In bash, go to the folder you want to download the tool into and type the following commands:

```sudo apt-get install make```

```curl -L https://github.com/codacy/codacy-analysis-cli/archive/master.tar.gz | tar xvz```

```cd codacy-analysis-cli-*```

Once again, due to the use of the WSL, it’s mandatory to add the two highlighted lines to the Makefile in this directory.

Before the **test** section:

```export DOCKER_HOST=tcp://0.0.0.0:2375```

and, in the **install**, section:

```docker login```

Finally, just type the following command and the installation will start

```sudo make install```

When Docker’s username is required, be sure to write the username and not the e-mail because depending on how you’ve created your docker account, they might be different.

## Usage

### Script

```sh
codacy-analysis-cli analyze \
  --tool <TOOL-SHORT-NAME> \
  --directory <SOURCE-CODE-PATH>
```

### Java

```
java -jar codacy-analysis-cli-assembly-{VERSION}.jar analyze \
  --tool <TOOL-SHORT-NAME> \
  --directory <SOURCE-CODE-PATH> \
  # other options
```

### Local

```sh
sbt "codacyAnalysisCli/runMain com.codacy.analysis.cli.Main analyze --tool <TOOL-SHORT-NAME> --directory <SOURCE-CODE-PATH>"
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
    analyze --tool <TOOL-SHORT-NAME>
```


### Output
The default format for the CLI output is text and is divided into 3 categories:

#### Issues

Issues reported by the tools that vary between 3 different levels (`Error`, `Warning` and `Info`). Example:

```
Found [Error] `Expected "#E1675A" to be "#e1675a" (color-hex-case)` in styles/variables.less:4 (Stylelint_color-hex-case)
Found [Warning] `'object short notation' is available in ES6 (use esnext option) or Mozilla JS extensions (use moz).` in scripts/main.js:28 (iterator)
Found [Info] `Missing semicolon.` in views/components/Progress.jsx:18 (asi)
```

#### Metrics

The metrics data is printed for each file and contains 5 types of data:
* `LOC` - Lines of Code
* `CLOC` - Commented Lines of Code
* `CC` - Cyclomatic Complexity
* `#methods` - Number of methods
* `#classes` - Number of classes

Example:
```
Found [Metrics] in generic/Test.java:
  CC - 33
  LOC - 778
  CLOC - 864
  #methods - 3
  #classes - 1
```

#### Clones
Each clone found is printed with information about the total number of lines, number of tokens and
all the occurrences (the lines where it starts and where it ends). Example:

```
Found [Clone] 7 duplicated lines with 10 tokens:
  generic/test.rb
    l. 681 - 687
    l. 693 - 699
  generic/another_test.rb
    l. 601 - 607
    l. 193 - 199
```

## Exit Status Codes

* :tada: 0: Success
* :dizzy_face: 1: Generic Error
* :sleeping: 2: Timeout
* :weary: 10: Failed Upload
* :confused: 11: Uncommited changes in project (with upload option selected)
* :open_mouth: 12: The commit uuid passed by parameter does not match the current commit of the project's repository
* :angry: 13: Non-existent tool provided as input
* :cold_sweat: 100: Failed Analysis
* :frowning: 101: Partially Failed Analysis
* :cop: 102: Max Allowed Issues Exceeded

## Configuration

### Commands and Configuration

* `analyze` - Run a Codacy analysis over a directory/files
    * `--help` - Displays all the configuration options, their meaning and possible values.
    * `--verbose` - Run the tool with verbose output
    * `--tool` - [Choose the tool](https://docs.codacy.com/repositories-configure/codacy-configuration-file/#which-tools-can-be-configured-and-which-name-should-i-use) to analyze the code (e.g. brakeman), or "metrics", "duplication", "issues" to run only a specific tool category
    * `--directory` - Choose the directory to be analysed
    * `--codacy-api-base-url` or env.`CODACY_API_BASE_URL` - Change the Codacy installation API URL to retrieve the configuration (e.g. Enterprise installation)
    * `--output` - Send the output results to a file
    * `--format` [default: text] - Change the output format (e.g. json)
    * `--commit-uuid` [default: latest commit of current git branch] - Set the commit UUID that will receive the results on Codacy
    * `--skip-commit-uuid-validation` [default: false] - Force using a commit UUID even if it doesn't belong to the current git branch.
    * `--skip-uncommitted-files-check` [default: false] - Skip check for uncommitted files in the analysis directory
    * `--upload` [default: false] - Request to push results to Codacy
    * `--upload-batch-size` [default: 50000] - Maximum number of results in each batch to upload to Codacy
    * `--skip-ssl-verification` [default: false] - Skip the SSL certificate verification when communicating with the Codacy API
    * `--parallel` [default: 2] - Number of tools to run in parallel
    * `--max-allowed-issues` [default: 0] - Maximum number of issues allowed for the analysis to succeed
    * `--registry-address` [default: empty] - Alternative registry address (e.g. artprod.mycompany/)
    * `--fail-if-incomplete` [default: false] - Fail the analysis if any tool fails to run
    * `--allow-network` [default: false] - Allow network access, so tools that need it can execute (e.g. findbugs)
    * `--force-file-permissions` [default: false] - Force files to be readable by changing the permissions before running the analysis
    * `--tool-timeout` [default: 15minutes] - Maximum time each tool has to execute (e.g. 15minutes, 1hour)
    * `--max-tool-memory` [default: 3g] - Maximum of allowed memory for each tool execution (in bytes or using the notation of [Docker's memory limit flags](https://docs.docker.com/config/containers/resource_constraints/#limit-a-containers-access-to-memory))
    * `--tmp-directory` [optional] - Temporary directory for analysis purposes
    * `--gh-code-scanning-compat` [default: false] - Reduce issue severity by one level, __for non-security issues__, for compatibility with GitHub's code scanning feature.
    This option will only have an effect when used in conjunction with `--format sarif`. Note that in this case, the same issues on Codacy side will have higher priority.
* `validate-configuration` - Validate the Codacy configuration file
    * `--directory` - Choose the directory where to look for the Codacy configuration file

### Environment Variables

* `CODACY_ANALYSIS_CLI_VERSION` [default: stable] - Set an alternative version of the CLI to run. (e.g. latest, 0.1.0-alpha3.1350, ...)
* `SKIP_CONTAINER_ENGINE_CHECK` [default: false] - Skip the initial test for presence of docker socket (useful when running in systems that dont have the docker socket available)

### Local configuration

To perform certain advanced configurations, Codacy allows to create a configuration file.
Check our [documentation](https://support.codacy.com/hc/en-us/articles/115002130625-Codacy-Configuration-File) for
more details.

### Remote configuration

To run locally the same analysis that Codacy does in your code you can request remotely the configuration.

#### Project Token

See [how to generate a project token](https://docs.codacy.com/codacy-api/api-tokens/#project-api-tokens).

> ⚠️ **Warning:** For security reasons we recommend that you store your project API token in the environment variable CODACY_PROJECT_TOKEN instead of setting `--project-token`.

```sh
codacy-analysis-cli analyze \
  --project-token <PROJECT-TOKEN> \
  --tool <TOOL-SHORT-NAME> \
  --directory <SOURCE-CODE-PATH>
```

#### API Token

See [how to generate an account API token](https://docs.codacy.com/codacy-api/api-tokens/#account-api-tokens).

> ⚠️ **Warning:** For security reasons we recommend that you store your account API token in the environment variable CODACY_API_TOKEN instead of setting `--api-token`.

The provider, username, and project name can be retrieved from the URL in Codacy.

```sh
codacy-analysis-cli analyze \
  --api-token <API-TOKEN> \
  --provider <PROVIDER> \
  --username <USERNAME> \
  --project <PROJECT-NAME> \
  --tool <TOOL-SHORT-NAME> \
  --directory <SOURCE-CODE-PATH>
```

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
sbt scalafmtAll scalafmtSbt
```

### Dependency Updates

```sh
sbt dependencyUpdates
```

### Static Analysis

```sh
sbt scapegoat
sbt scalafix
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

        sbt 'codacyAnalysisCli/stage'
        docker build -t codacy-analysis-cli .


### Library

* **Local**

        sbt 'set version in codacyAnalysisCore := "<VERSION>"' codacyAnalysisCore/publishLocal

* **Release**

        sbt 'set version in codacyAnalysisCore := "<VERSION>"' 'set pgpPassphrase := Some("<SONATYPE_GPG_PASSPHRASE>".toCharArray)' codacyAnalysisCore/publishSigned
        sbt 'set version in codacyAnalysisCore := "<VERSION>"' sonatypeRelease

## Breaking Changes

- `7.0.0`: Fix `--parallel` that wasn't making the tools run actually in parallel. To restore the previous behaviour use `--parallel 1`

- `4.0.0`: Rename `analyse` command to `analyze`. This is a breaking change if you are running the CLI by using the
  [jar](#java) or [`sbt`](#local), but not if you are using the [provided script](#script).


## What is Codacy

[Codacy](https://www.codacy.com/) is a platform that monitors your technical debt, helps you improve your code quality and security, teaches best practices to your developers, and helps you save time in code reviews. We help developers ship billions of lines of code per day by automating and standardizing code reviews.  
Integrating seamlessly into workflows, Codacy helps engineering teams save time in code reviews and find, fix, and prevent coding defects that would otherwise stack up as technical debt. Our platform uses Artificial Intelligence to suggest code quality fixes. Codacy supports 40+ languages & frameworks and is available in free open-source.

[Start a free trial](https://www.codacy.com/signup-codacy) to learn more.

### Among Codacy’s features

- Identify new Static Analysis issues
- Track issues in Code Style, Security, Error Proneness, Performance, Unused Code, and other categories
- Leverage AI to automatically explain issues and suggest fixes
- Integrate seamlessly with your favorite dev tools (Slack, Jira, Semgrep)
- Commit and Pull Request Analysis with GitHub, Bitbucket, GitLab (and also direct git repositories)

Codacy also helps keep track of Code coverage, Code duplication, and Code complexity.

Codacy supports PHP, Python, Ruby, Java, JavaScript, and Scala, among others.

### Free for Open Source

Codacy is free for Open Source projects.
