# Bootstrap: Jenkins + Docker + Kubernetes

This document describes how to bring up the CI/CD foundation for the Circle Guard project (Story 1.1). All subsequent pipeline work (Stories 1.2 – 4.4) depends on this infrastructure being live.

---

## Prerequisites

Install the following tools on your **host machine** before proceeding.

| Tool | Minimum Version | Install Link |
|------|----------------|-------------|
| Docker Engine | 27.x | https://docs.docker.com/engine/install/ |
| Docker Compose (plugin) | v2.x | included with Docker Desktop / Engine |
| `kind` | 0.23+ | https://kind.sigs.k8s.io/docs/user/quick-start/#installation |
| `kubectl` | 1.28+ | https://kubernetes.io/docs/tasks/tools/ |
| `helm` | 3.x | https://helm.sh/docs/intro/install/ |

> **Note:** `kind` runs Kubernetes nodes as Docker containers. No VM is required. This is the recommended setup for local development and university lab machines.

Verify prerequisites before continuing:

```bash
docker --version      # Docker version 27.x.x
docker compose version # Docker Compose version v2.x.x
kind version          # kind v0.23.x
kubectl version --client --short  # Client Version: v1.28+
helm version --short  # v3.x.x+
```

---

## Step-by-Step Bootstrap Procedure

### Step 1 — Create the Kubernetes Cluster (kind)

```bash
# Create a single-node cluster named "circleguard"
kind create cluster --name circleguard

# Confirm the cluster is responding
kubectl cluster-info --context kind-circleguard
kubectl get nodes
# Expected: one node in "Ready" state
```

### Step 2 — Create Kubernetes Namespaces

All three pipeline namespaces must exist before any Helm deploy runs.

```bash
kubectl create namespace circleguard-dev
kubectl create namespace circleguard-stage
kubectl create namespace circleguard-master

# Verify
kubectl get namespaces | grep circleguard
# Expected: circleguard-dev, circleguard-stage, circleguard-master  Active
```

### Step 3 — Start Jenkins and the Local Registry

```bash
# From the repo root (circle-guard-public/)
docker compose -f docker-compose.jenkins.yml up -d

# Confirm both containers are running
docker compose -f docker-compose.jenkins.yml ps
# Expected: jenkins (running), docker-registry (running)
```

Wait ~30 seconds for Jenkins to fully initialize, then retrieve the initial admin password:

```bash
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

Open http://localhost:8080 and complete the initial setup wizard:
1. Enter the admin password retrieved above.
2. Select **Install suggested plugins**.
3. Create the first admin user (or skip and use admin).
4. Set the Jenkins URL to `http://localhost:8080/`.

### Step 4 — Configure Jenkins Agent with kubectl and helm

The Jenkins container runs as `root` and shares the Docker socket with the host (Docker-out-of-Docker). To give Jenkins access to the `kind` cluster, copy the kubeconfig into the Jenkins home volume.

```bash
# Retrieve the kubeconfig for the kind cluster
kind get kubeconfig --name circleguard > /tmp/circleguard-kubeconfig.yaml

# Get the kind control-plane's Docker network IP
# (kind uses 127.0.0.1 for the host, which Jenkins can't reach from inside Docker)
KIND_IP=$(docker inspect circleguard-control-plane \
  --format='{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')

# Replace the loopback address with the container-reachable IP
# -i.bak works on both GNU sed (Linux) and BSD sed (macOS)
sed -i.bak "s/127.0.0.1/${KIND_IP}/g" /tmp/circleguard-kubeconfig.yaml
rm -f /tmp/circleguard-kubeconfig.yaml.bak

# Copy kubeconfig into the Jenkins home volume
docker cp /tmp/circleguard-kubeconfig.yaml \
  jenkins:/var/jenkins_home/.kube/config

docker exec jenkins chmod 600 /var/jenkins_home/.kube/config
```

Install `docker` CLI, `kubectl`, and `helm` inside the Jenkins container:

```bash
docker exec -u root jenkins bash -c "
  # docker CLI (needed for DooD smoke test and image builds)
  apt-get update -qq && apt-get install -y --no-install-recommends docker.io

  # kubectl
  KUBECTL_VERSION=\$(curl -L -s https://dl.k8s.io/release/stable.txt)
  curl -LO \"https://dl.k8s.io/release/\${KUBECTL_VERSION}/bin/linux/amd64/kubectl\"
  install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
  rm kubectl

  # helm
  curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
"
```

### Step 5 — Configure the Local Registry as Insecure (kind)

**Recommended: create the cluster with registry trust baked in.** This avoids having to patch the containerd config post-creation (appending to an existing config file can create duplicate sections if keys already exist).

First, get the registry container's IP on the Docker bridge so kind nodes can reach it:

```bash
REGISTRY_IP=$(docker inspect docker-registry \
  --format='{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
echo "Registry IP: $REGISTRY_IP"
```

Create a `kind-config.yaml` using that IP:

```yaml
# kind-config.yaml  — create this file, then use it in Step 1 instead of bare `kind create cluster`
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
containerdConfigPatches:
  - |-
    [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:5000"]
      endpoint = ["http://${REGISTRY_IP}:5000"]
```

> **Tip:** If you started the registry **before** creating the cluster, substitute `${REGISTRY_IP}` with the actual IP from the command above, then run:
> ```bash
> kind create cluster --name circleguard --config kind-config.yaml
> ```
> Skip the rest of this step.

**If the cluster already exists** (and you cannot recreate it), patch each node individually. Note: this uses `>` (overwrite with merge) rather than `>>` (append) to avoid duplicate config sections:

```bash
for node in $(kind get nodes --name circleguard); do
  REGISTRY_IP=$(docker inspect docker-registry \
    --format='{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
  docker exec "${node}" bash -c "
    mkdir -p /etc/containerd/certs.d/localhost:5000
    cat > /etc/containerd/certs.d/localhost:5000/hosts.toml <<EOF
[host.\"http://${REGISTRY_IP}:5000\"]
  capabilities = [\"pull\", \"resolve\", \"push\"]
  skip_verify = true
EOF
  "
done
# Restart containerd on each node to pick up the new config
for node in $(kind get nodes --name circleguard); do
  docker exec "${node}" systemctl restart containerd
done
```

---

## Verification Checklist

Run all checks **from inside the Jenkins container** to confirm the agent environment is properly wired:

```bash
docker exec jenkins bash -c "
  echo '=== Docker ===' && docker info | head -5
  echo '=== kubectl ===' && kubectl get nodes
  echo '=== helm ===' && helm version --short
  echo '=== namespaces ===' && kubectl get namespaces | grep circleguard
  echo '=== registry ===' && curl -s http://host.docker.internal:5000/v2/_catalog
"
```

| Check | Command | Expected Output |
|-------|---------|----------------|
| Jenkins UI | `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/login` | `200` |
| Docker daemon | `docker exec jenkins docker info` | Displays server version, no errors |
| K8s cluster | `docker exec jenkins kubectl get nodes` | Node in `Ready` state |
| kubectl version | `docker exec jenkins kubectl version --client --short` | `Client Version: v1.28+` |
| helm version | `docker exec jenkins helm version --short` | `v3.*.*+` |
| Namespaces | `docker exec jenkins kubectl get namespaces \| grep circleguard` | 3 namespaces listed |
| DooD smoke test | `docker exec jenkins docker run --rm hello-world` | `Hello from Docker!` |
| Registry | `curl http://localhost:5000/v2/_catalog` | `{"repositories":[]}` |

All 8 checks must pass before this story is considered complete (AC-1.1.1).

---

## Conventional Commits Reference

All commits in this repo must follow Conventional Commits from day 1 (required by git-cliff for automated Release Notes in Epic 4).

```
ci: bootstrap kind cluster and Jenkins LTS with Docker socket mount
ci: create circleguard-dev/stage/master Kubernetes namespaces
ci: add local Docker registry to CI compose stack
docs: add bootstrap README with verification checklist
```

**Forbidden patterns:**
```
initial commit          ← no type prefix
setup done              ← no type prefix
WIP                     ← no type prefix
```

---

## Troubleshooting

### Jenkins cannot reach K8s API (`connection refused`)

The kubeconfig `server:` URL likely still uses `127.0.0.1` (the host's loopback, not reachable from inside Docker). Re-run Step 4's `sed` substitution with the correct kind control-plane container IP:

```bash
KIND_IP=$(docker inspect circleguard-control-plane \
  --format='{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
echo "Use this IP in kubeconfig: $KIND_IP"
```

### Docker socket permission denied inside Jenkins

The Jenkins container runs as `root`, so socket access should work. If you see permission errors, confirm the socket ownership on the host:

```bash
ls -la /var/run/docker.sock
# Should show: srw-rw---- ... root docker
```

Add the host `docker` GID to the Jenkins container if needed:

```yaml
# In docker-compose.jenkins.yml, under jenkins service:
group_add:
  - "${DOCKER_GID}"   # export DOCKER_GID=$(stat -c '%g' /var/run/docker.sock)
```

### `helm` not found after install

The `PATH` inside a `docker exec bash` shell may differ. Verify:

```bash
docker exec jenkins which helm
# Should print: /usr/local/bin/helm
```

If missing, re-run the helm install step from Step 4.

### Registry push fails (`http: server gave HTTP response to HTTPS client`)

The local registry runs over HTTP (no TLS). Docker on the **host** must trust `localhost:5000` as an insecure registry:

```json
// /etc/docker/daemon.json (Linux host)
{
  "insecure-registries": ["localhost:5000"]
}
```

Restart Docker after changing: `sudo systemctl restart docker`

For pushes **from inside the Jenkins container**, the registry is reachable as `docker-registry:5000` (same `ci-net` network). Add `docker-registry:5000` to the insecure registries inside the container if needed, or configure it in the Jenkins Docker daemon settings via `DOCKER_OPTS`.

### `host-gateway` does not resolve inside kind nodes

`host-gateway` is a Docker Desktop alias and does not work on Linux Docker Engine. Use the registry container's actual IP (from `docker inspect docker-registry`) when configuring the containerd mirror — see Step 5.

### kind cluster loses kubeconfig after host restart

```bash
# Re-export kubeconfig after host restart
kind get kubeconfig --name circleguard > ~/.kube/config
# Then re-run the KIND_IP substitution from Step 4
```

---

## Port Reference

| Service | Port | Purpose |
|---------|------|---------|
| Jenkins UI | 8080 | Web interface and API |
| Jenkins agent | 50000 | JNLP inbound agent connections |
| Docker registry | 5000 | Image push/pull |

No conflicts with Circle Guard microservices (ports 8083–8088).
