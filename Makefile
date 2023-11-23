.PHONY: install uninstall compile build lint test clean

PREFIX ?= /usr/local

test:
	scripts/test.sh

lint:
	scripts/lint.sh

compile:
	scripts/compile.sh

install:
	scripts/check_requirements.sh
	docker pull codacy/codacy-analysis-cli:stable
	mkdir -p $(DESTDIR)$(PREFIX)/bin
	install -m 0755 bin/codacy-analysis-cli.sh $(DESTDIR)$(PREFIX)/bin/codacy-analysis-cli

uninstall:
	$(RM) $(DESTDIR)$(PREFIX)/bin/codacy-analysis-cli
	docker rmi codacy/codacy-analysis-cli:stable

build: compile lint test
	scripts/deploy.sh

clean:
	sbt clean
