# Custom Jenkins LTS with plugins, CasC, and Docker CLI (DooD) for CircleGuard pipelines.
# Build context: repository root (circle-guard-public/).
#
# Debian trixie's docker.io may not expose /usr/bin/docker as a usable client (apt succeeds but
# `test -x /usr/bin/docker` fails). We install Docker's official static client only — enough to
# talk to the host daemon via the mounted socket (DooD).

FROM jenkins/jenkins:lts-jdk21

ARG DOCKER_CLI_VERSION=27.4.1
ARG KUBECTL_VERSION=v1.36.1
ARG HELM_VERSION=v3.17.3

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
  && /usr/bin/docker --version \
  # --- kubectl ---
  && curl -fsSL "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${DOCKER_ARCH/x86_64/amd64}/kubectl" \
       -o /usr/local/bin/kubectl \
  && chmod 755 /usr/local/bin/kubectl \
  && /usr/local/bin/kubectl version --client \
  # --- helm ---
  && HELM_ARCH="$(dpkg --print-architecture)" \
  && curl -fsSL "https://get.helm.sh/helm-${HELM_VERSION}-linux-${HELM_ARCH}.tar.gz" \
       | tar -xz -C /tmp \
  && install -m 755 /tmp/linux-${HELM_ARCH}/helm /usr/local/bin/helm \
  && rm -rf /tmp/linux-${HELM_ARCH} \
  && /usr/local/bin/helm version

USER jenkins

RUN /usr/bin/docker --version

COPY jenkins/docker/plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

COPY jenkins/docker/casc /usr/share/jenkins/ref/casc

ENV JAVA_OPTS=-Djenkins.install.runSetupWizard=false
ENV CASC_JENKINS_CONFIG=/var/jenkins_home/casc
