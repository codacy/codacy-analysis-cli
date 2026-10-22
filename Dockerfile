FROM alpine:3.24.3

RUN apk add --no-cache --update bash docker openjdk8

# Fail the build if Java still trusts the root behind the compromised eMudhra intermediates (Oct 2026)
RUN keytool -list -v -keystore /etc/ssl/certs/java/cacerts -storepass changeit > /tmp/cacerts.txt \
    && ! grep -qi "emSign ECC Root CA - G3" /tmp/cacerts.txt \
    && rm /tmp/cacerts.txt

WORKDIR /workdir

COPY --chown=root:root cli/target/universal/stage /workdir

USER root

ENTRYPOINT ["/workdir/bin/codacy-analysis-cli"]

CMD []