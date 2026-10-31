# syntax=docker/dockerfile:1.7
#
# Two ways in, one runtime.
#
#   SOURCE_STAGE=builder (default) compiles from source inside the container, so the host
#   needs no JDK or Maven and `docker compose up -d --build` is self-contained. Slower on a
#   cold cache: Nexus pulls a large dependency set.
#
#   SOURCE_STAGE=prebuilt takes the zip the host already built under
#   assemblies/nexus-base-template/target. Faster, but opt-in — see that stage's comment.
#
#   docker compose up -d --build                      # from source
#   SOURCE_STAGE=prebuilt docker compose up -d --build # from the host's zip
#   docker buildx bake nexus3 | nexus3-prebuilt        # same two paths via bake
#
ARG SOURCE_STAGE=builder

# ---------------------------------------------------------------------------
# prebuilt: the zip the host built. .dockerignore admits exactly this file.
#
# If the zip is not there, this fails with BuildKit's opaque "failed to compute cache key ...
# not found". It cannot be turned into a friendlier message from inside the Dockerfile:
# BuildKit resolves COPY sources before any RUN executes, and a wildcard does not help once
# the parent directory is absent. Hence SOURCE_STAGE defaults to 'builder' in
# docker-compose.yml and docker-bake.hcl, so the opaque failure needs an explicit opt-in.
# ---------------------------------------------------------------------------
FROM docker.syscube.dev/seanly/toolset:ubuntu-22.04-1 AS prebuilt

ARG NEXUS_VERSION=3.76.1-02

COPY assemblies/nexus-base-template/target/nexus-base-template-${NEXUS_VERSION}.zip /tmp/nexus.zip

# ---------------------------------------------------------------------------
# builder: compile from source.
#
# frontend-maven-plugin downloads its own node/yarn into each module's target directory, so
# the Maven build needs neither here. Node is installed anyway for one reason, below.
#
# The frontend is a Yarn 3 workspace: yarn.lock is '__metadata: version 6' and two packages
# depend on "@sonatype/nexus-ui-plugin": "workspace:*". But the root pom pins
# yarn.version=v1.22.19, and Yarn 1 cannot resolve the workspace: protocol — it looks
# "@sonatype/nexus-ui-plugin@workspace:*" up on the npm registry and fails. Both the pin and
# the lockfile come from upstream; a host build only gets away with it because a previous
# Yarn 3 run already left a root node_modules with the workspace symlinked in, and the
# plugin's Yarn 1 never has to resolve it.
#
# A clean tree has no such node_modules, so the install is done here with the Yarn 3 that
# package.json pins (packageManager: yarn@3.2.3), via corepack. Yarn 1 can still *run* the
# build scripts against what Yarn 3 installed — that part is verified — so the plugin's own
# 'yarn run build' executions are left alone and only its install step is neutralised below.
#
# C/C++ toolchain and python3: some frontend dependencies build native modules on install.
#
# The build runs with -Dpublic. The default 'sonatype' profile wants
# groovy-eclipse-batch:3.0.13-02, which is internal to Sonatype and absent from Central.
# ---------------------------------------------------------------------------
FROM docker.syscube.dev/seanly/toolset:ubuntu-22.04-1 AS builder

ARG NEXUS_VERSION=3.76.1-02
ARG JAVA_VERSION=java17

# Provenance. buildnumber-maven-plugin reads git, and .git is deliberately kept out of the
# build context (246M for a revision string). Without it build.revision lands as null, so the
# SHA is passed in instead. docker-bake.hcl fills these from the host.
ARG BUILD_REVISION=unknown
ARG BUILD_BRANCH=unknown

# Gateway blocks apt's default user agent and returns a stub page.
RUN printf 'Acquire::http::User-Agent "curl/8.5.0";\n' > /etc/apt/apt.conf.d/99user-agent \
    && sed -i \
        -e 's|http://archive.ubuntu.com/ubuntu/|http://mirrors.tuna.tsinghua.edu.cn/ubuntu/|g' \
        -e 's|http://security.ubuntu.com/ubuntu/|http://mirrors.tuna.tsinghua.edu.cn/ubuntu/|g' \
        -e 's|http://ports.ubuntu.com/ubuntu-ports/|http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/|g' \
        /etc/apt/sources.list \
    && apt update -y \
    ; JAVA_VERSION_NUMBER="$(echo $JAVA_VERSION | sed -e 's/java//')" \
    ; apt install --no-install-recommends -y \
        openjdk-${JAVA_VERSION_NUMBER}-jdk-headless \
        unzip zip ca-certificates curl \
        build-essential python3 \
    && rm -rf /var/lib/apt/lists/*

# Node, only to get corepack for the Yarn 3 install described above. Matches node.version in
# the root pom so the frontend builds against the same runtime Maven would have used.
ARG NODE_VERSION=18.17.1
RUN set -eux \
    && ARCH="$(dpkg --print-architecture)" \
    && case "${ARCH}" in \
         amd64) NODE_ARCH=x64 ;; \
         arm64) NODE_ARCH=arm64 ;; \
         *) echo "unsupported architecture: ${ARCH}" >&2; exit 1 ;; \
       esac \
    && curl -fsSL -o /tmp/node.tar.xz \
        "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${NODE_ARCH}.tar.xz" \
    && tar -xJf /tmp/node.tar.xz -C /usr/local --strip-components=1 \
        --exclude CHANGELOG.md --exclude LICENSE --exclude README.md \
    && rm -f /tmp/node.tar.xz \
    && corepack enable \
    && node --version

WORKDIR /src

COPY . /src

# Cache mount for the Maven repository, so a rebuild skips the downloads. It is not an image
# layer and does not reach the runtime stage. sharing=locked because concurrent Maven writes
# to one repository race.
#
# The Yarn 3 cache is mounted too, for the workspace install below. The node and yarn that
# frontend-maven-plugin downloads are not cached: they land in each module's own target
# directory, which 'clean' removes anyway.
#
# -DskipTests skips running the suite, which needs a database and far longer than an image
# build should take.
#
# Not -Dmaven.test.skip=true: that also skips test *compilation*, so nexus-repository-content
# never produces its test-jar, and nexus-repository-apt depends on it (classifier 'tests').
# The reactor fails on that module.
#
# No -T either. A parallel build dies in nexus-repository-testsupport with an internal
# compiler error out of the JDK's zip filesystem ("Cannot invoke LinkedHashMap.get because
# this.inodes is null") — threads racing on one shared zip. The README's documented command
# is sequential; this matches it.
RUN --mount=type=cache,target=/root/.yarn-cache,sharing=locked \
    --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    set -eux \
    && export MAVEN_OPTS="-Xmx3g -XX:MaxMetaspaceSize=768m" \
    \
    # Yarn 3 defaults enableGlobalCache=false, which puts the cache in the project's
    # .yarn/cache — inside /src, so it would not survive as a cache mount. Redirected to the
    # mount instead.
    && export YARN_ENABLE_GLOBAL_CACHE=true \
    && export YARN_GLOBAL_FOLDER=/root/.yarn-cache \
    \
    # Workspace install with the pinned Yarn 3, which understands the lockfile and the
    # workspace: protocol. This is what leaves node_modules in place for the plugin's Yarn 1.
    && yarn install --immutable \
    \
    # npm.install is overridden to '--version', a no-op that keeps the plugin's 'yarn install'
    # execution from re-running under Yarn 1 and failing on workspace:*. Its 'yarn run
    # build-all' executions are untouched and still produce the frontend bundles.
    && ./mvnw clean install \
        -Dpublic \
        -DskipTests \
        -Dnpm.install=--version \
        -Dmaven.buildNumber.revisionOnScmFailure="${BUILD_REVISION}" \
        -Dbuild.revision="${BUILD_REVISION}" \
        -Dbuild.branch="${BUILD_BRANCH}" \
    && cp "assemblies/nexus-base-template/target/nexus-base-template-${NEXUS_VERSION}.zip" /tmp/nexus.zip

# ---------------------------------------------------------------------------
# dist: alias for whichever stage above produced the zip. A global ARG expands in FROM but
# not in COPY --from, so the choice is resolved here and the runtime stage copies from a
# fixed name. Adds no layer.
# ---------------------------------------------------------------------------
FROM ${SOURCE_STAGE} AS dist

# ---------------------------------------------------------------------------
# runtime
# ---------------------------------------------------------------------------
FROM docker.syscube.dev/seanly/toolset:ubuntu-22.04-1 AS runtime

LABEL name="Nexus Repository Manager" \
      maintainer="YS Liu <seanly@opsbox.cloud>" \
      vendor="coelacanth" \
      version="3.76.1-02" \
      release="3.76.1-02" \
      codename="coelacanth" \
      url="https://github.com/sonatype/nexus-public" \
      summary="Nexus Repository built from the 3.76.1-02 EPL sources." \
      description="Nexus Repository built from the 3.76.1-02 EPL sources, with external PostgreSQL support." \
      run="docker run -d --name NAME \
          -p 8081:8081 \
          IMAGE" \
      stop="docker stop NAME" \
      com.sonatype.license="Eclipse Public License, Version 1.0" \
      com.sonatype.name="Nexus Repository Manager"

ARG NEXUS_VERSION=3.76.1-02
ARG JAVA_VERSION=java17

ENV SONATYPE_DIR=/opt/sonatype
ENV NEXUS_HOME=${SONATYPE_DIR}/nexus \
    NEXUS_DATA=/nexus-data \
    NEXUS_CONTEXT='' \
    SONATYPE_WORK=${SONATYPE_DIR}/sonatype-work

# The gateway blocks apt's default user agent and returns a stub page.
RUN printf 'Acquire::http::User-Agent "curl/8.5.0";\n' > /etc/apt/apt.conf.d/99user-agent \
    && sed -i \
        -e 's|http://archive.ubuntu.com/ubuntu/|http://mirrors.tuna.tsinghua.edu.cn/ubuntu/|g' \
        -e 's|http://security.ubuntu.com/ubuntu/|http://mirrors.tuna.tsinghua.edu.cn/ubuntu/|g' \
        -e 's|http://ports.ubuntu.com/ubuntu-ports/|http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/|g' \
        /etc/apt/sources.list \
    && apt update -y \
    ; JAVA_VERSION_NUMBER="$(echo $JAVA_VERSION | sed -e 's/java//')" \
    ; apt install --no-install-recommends -y openjdk-${JAVA_VERSION_NUMBER}-jdk-headless unzip \
    && groupadd --gid 1000 -r nexus \
    && useradd --uid 1000 -r nexus -g nexus -s /bin/false -d /opt/sonatype/nexus -c 'Nexus Repository Manager user'

WORKDIR ${SONATYPE_DIR}

# 'dist' is the prebuilt or builder stage, per SOURCE_STAGE. Both leave the zip at the same
# path, so this line does not care which ran.
COPY --from=dist /tmp/nexus.zip /tmp/nexus.zip

RUN unzip /tmp/nexus.zip -d ${SONATYPE_DIR} \
    && rm -f /tmp/nexus.zip \
    && mv nexus-base-template-${NEXUS_VERSION} ${NEXUS_HOME} \
    && mkdir -p ${NEXUS_DATA} ${SONATYPE_WORK} \
    && if [ -d ${SONATYPE_WORK}/nexus3 ] && [ ! -L ${SONATYPE_WORK}/nexus3 ]; then \
         cp -a ${SONATYPE_WORK}/nexus3/. ${NEXUS_DATA}/; \
         rm -rf ${SONATYPE_WORK}/nexus3; \
       fi \
    && ln -sfn ${NEXUS_DATA} ${SONATYPE_WORK}/nexus3 \
    && chown -R nexus:nexus ${NEXUS_HOME} ${SONATYPE_WORK} ${NEXUS_DATA}

RUN if [ -f "$NEXUS_HOME/bin/nexus.vmoptions" ]; then \
      sed -i '/^-Xms/d;/^-Xmx/d;/^-XX:MaxDirectMemorySize/d' "$NEXUS_HOME/bin/nexus.vmoptions"; \
    fi \
    && echo "#!/bin/bash" >> ${SONATYPE_DIR}/start-nexus-repository-manager.sh \
    && echo "cd /opt/sonatype/nexus" >> ${SONATYPE_DIR}/start-nexus-repository-manager.sh \
    && echo "exec ./bin/nexus server" >> ${SONATYPE_DIR}/start-nexus-repository-manager.sh \
    && chmod a+x ${SONATYPE_DIR}/start-nexus-repository-manager.sh \
    && sed -e '/^nexus-context/ s:$:${NEXUS_CONTEXT}:' -i ${NEXUS_HOME}/etc/nexus-default.properties

VOLUME ${NEXUS_DATA}

EXPOSE 8081
USER nexus

ENV JAVA_MIN_MEM=512m \
    JAVA_MAX_MEM=1024m \
    DIRECT_MAX_MEM=512m \
    INSTALL4J_ADD_VM_PARAMS="-Xms512m -Xmx1024m -XX:MaxDirectMemorySize=512m -Djava.util.prefs.userRoot=${NEXUS_DATA}/javaprefs"

# 'server' keeps the local Karaf console off. 'run' starts it, and with no tty attached
# the console reads EOF on stdin and shuts the OSGi framework down before it finishes booting.
CMD ["/opt/sonatype/nexus/bin/nexus", "server"]
