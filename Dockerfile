FROM alpine:3.21.3

RUN apk add --no-cache --update bash docker openjdk8

WORKDIR /workdir

COPY --chown=root:root cli/target/universal/stage /workdir

USER root

ENTRYPOINT ["/workdir/bin/codacy-analysis-cli"]

CMD []