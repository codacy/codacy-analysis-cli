FROM alpine:3.21.3

RUN apk add --no-cache --update bash docker openjdk8

RUN adduser -D analysisCli \
  && addgroup analysisCli docker

WORKDIR /workdir
COPY --chown=analysisCli:analysisCli cli/target/universal/stage /workdir

USER analysisCli
ENTRYPOINT ["/workdir/bin/codacy-analysis-cli"]
