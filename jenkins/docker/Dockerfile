# Custom Jenkins LTS with plugins, CasC, and Docker CLI (DooD) for CircleGuard pipelines.
# Build context: repository root (circle-guard-public/).

FROM jenkins/jenkins:lts-jdk21

USER root
RUN apt-get update \
  && apt-get install -y --no-install-recommends docker.io curl ca-certificates \
  && rm -rf /var/lib/apt/lists/*

USER jenkins

COPY jenkins/docker/plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

COPY jenkins/docker/casc /usr/share/jenkins/ref/casc

ENV JAVA_OPTS=-Djenkins.install.runSetupWizard=false
ENV CASC_JENKINS_CONFIG=/var/jenkins_home/casc
