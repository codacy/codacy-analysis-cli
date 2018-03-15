.PHONY: install uninstall compile build lint test clean

DESTDIR ?= /usr/local/bin

test:
	scripts/test.sh

lint:
	scripts/lint.sh

compile:
	scripts/compile.sh

install:
	scripts/check_requirements.sh
	docker pull codacy/codacy-analysis-cli:latest
	mkdir -p $(DESTDIR)
	install -m 0755 bin/codacy-analysis-cli.sh $(DESTDIR)/codacy-analysis-cli

uninstall:
	$(RM) $(DESTDIR)/codacy-analysis-cli
	docker rmi codacy/codacy-analysis-cli:latest

build: compile lint test
	scripts/deploy.sh

clean:
	scripts/clean.sh
