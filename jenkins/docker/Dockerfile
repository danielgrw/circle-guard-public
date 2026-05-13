# Custom Jenkins LTS with plugins, CasC, and Docker CLI (DooD) for CircleGuard pipelines.
# Build context: repository root (circle-guard-public/).
#
# Debian trixie's docker.io may not expose /usr/bin/docker as a usable client (apt succeeds but
# `test -x /usr/bin/docker` fails). We install Docker's official static client only — enough to
# talk to the host daemon via the mounted socket (DooD).

FROM jenkins/jenkins:lts-jdk21

ARG DOCKER_CLI_VERSION=27.4.1

USER root
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl ca-certificates \
  && rm -rf /var/lib/apt/lists/* \
  && ARCH="$(dpkg --print-architecture)" \
  && case "$ARCH" in \
       amd64)  DOCKER_ARCH=x86_64 ;; \
       arm64)  DOCKER_ARCH=aarch64 ;; \
       *) echo "unsupported architecture for Docker static CLI: $ARCH" && exit 1 ;; \
     esac \
  && curl -fsSL "https://download.docker.com/linux/static/stable/${DOCKER_ARCH}/docker-${DOCKER_CLI_VERSION}.tgz" \
       | tar -xz -C /tmp \
  && install -m 755 /tmp/docker/docker /usr/bin/docker \
  && rm -rf /tmp/docker \
  && /usr/bin/docker --version

USER jenkins

RUN /usr/bin/docker --version

COPY jenkins/docker/plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

COPY jenkins/docker/casc /usr/share/jenkins/ref/casc

ENV JAVA_OPTS=-Djenkins.install.runSetupWizard=false
ENV CASC_JENKINS_CONFIG=/var/jenkins_home/casc
