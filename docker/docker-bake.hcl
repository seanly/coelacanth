variable "VERSION" {
  default = "3.76.1-02"
}

variable "JAVA_VERSION" {
  default = "java17"
}

variable "FIXID" {
  default = "1"
}

# Which Dockerfile stage supplies the distribution zip.
#   builder  - compiled from source inside the container (default: self-contained)
#   prebuilt - the zip the host already built under assemblies/nexus-base-template/target.
#              Faster, but fails with an opaque BuildKit cache-key error if that zip is absent,
#              so it is opt-in rather than the default.
variable "SOURCE_STAGE" {
  default = "builder"
}

# .git is kept out of the build context, so buildnumber-maven-plugin cannot read the revision
# and would record null. Passed in from the host instead:
#   BUILD_REVISION="$(git rev-parse HEAD)" BUILD_BRANCH="$(git branch --show-current)" \
#     docker buildx bake nexus3
variable "BUILD_REVISION" {
  default = "unknown"
}

variable "BUILD_BRANCH" {
  default = "unknown"
}

group "default" {
  targets = ["nexus3"]
}

target "nexus3" {
  labels = {
    "cloud.opsbox.author"       = "seanly"
    "cloud.opsbox.image.name"   = "nexus3"
    "cloud.opsbox.image.version" = "${VERSION}"
    "cloud.opsbox.image.java-version" = "${JAVA_VERSION}"
    "cloud.opsbox.image.fixid"  = "${FIXID}"
    "cloud.opsbox.image.source-stage" = "${SOURCE_STAGE}"
  }
  dockerfile = "docker/Dockerfile"
  context    = ".."
  args = {
    NEXUS_VERSION  = "${VERSION}"
    JAVA_VERSION   = "${JAVA_VERSION}"
    SOURCE_STAGE   = "${SOURCE_STAGE}"
    BUILD_REVISION = "${BUILD_REVISION}"
    BUILD_BRANCH   = "${BUILD_BRANCH}"
  }
  tags = ["seanly/appset:nexus3-${VERSION}-${JAVA_VERSION}-${FIXID}"]
  output = ["type=docker"]
}

# Same image, but from a zip the host already built. Faster when you are running ./mvnw
# anyway; requires assemblies/nexus-base-template/target to hold the zip.
#
# The label and tag are restated rather than inherited: an inherited label interpolates
# SOURCE_STAGE, which is still "builder" here, and sharing nexus3's tag would let the two
# paths silently overwrite each other's image.
target "nexus3-prebuilt" {
  inherits = ["nexus3"]
  args = {
    SOURCE_STAGE = "prebuilt"
  }
  labels = {
    "cloud.opsbox.image.source-stage" = "prebuilt"
  }
  tags = ["seanly/appset:nexus3-${VERSION}-${JAVA_VERSION}-${FIXID}-prebuilt"]
}

variable "ACR_REGISTRY" {
  default = "registry.cn-chengdu.aliyuncs.com"
}

group "acr" {
  targets = ["nexus3-amd64", "nexus3-arm64"]
}

target "nexus3-amd64" {
  labels = {
    "cloud.opsbox.author"       = "seanly"
    "cloud.opsbox.image.name"   = "nexus3"
    "cloud.opsbox.image.version" = "${VERSION}"
    "cloud.opsbox.image.java-version" = "${JAVA_VERSION}"
    "cloud.opsbox.image.fixid"  = "${FIXID}"
    "cloud.opsbox.image.source-stage" = "${SOURCE_STAGE}"
  }
  dockerfile = "docker/Dockerfile"
  context    = ".."
  args = {
    NEXUS_VERSION  = "${VERSION}"
    JAVA_VERSION   = "${JAVA_VERSION}"
    SOURCE_STAGE   = "${SOURCE_STAGE}"
    BUILD_REVISION = "${BUILD_REVISION}"
    BUILD_BRANCH   = "${BUILD_BRANCH}"
  }
  platforms = ["linux/amd64"]
  tags = [
    "${ACR_REGISTRY}/seanly/appset:nexus3-${VERSION}-${JAVA_VERSION}-${FIXID}",
    "${ACR_REGISTRY}/seanly/appset:nexus3-${VERSION}-${JAVA_VERSION}",
    "${ACR_REGISTRY}/seanly/appset:nexus3-${VERSION}-${JAVA_VERSION}-${FIXID}-amd64",
    "${ACR_REGISTRY}/seanly/appset:nexus3-${VERSION}-${JAVA_VERSION}-amd64"
  ]
  output = ["type=docker"]
}

target "nexus3-arm64" {
  labels = {
    "cloud.opsbox.author"       = "seanly"
    "cloud.opsbox.image.name"   = "nexus3"
    "cloud.opsbox.image.version" = "${VERSION}"
    "cloud.opsbox.image.java-version" = "${JAVA_VERSION}"
    "cloud.opsbox.image.fixid"  = "${FIXID}"
    "cloud.opsbox.image.source-stage" = "${SOURCE_STAGE}"
  }
  dockerfile = "docker/Dockerfile"
  context    = ".."
  args = {
    NEXUS_VERSION  = "${VERSION}"
    JAVA_VERSION   = "${JAVA_VERSION}"
    SOURCE_STAGE   = "${SOURCE_STAGE}"
    BUILD_REVISION = "${BUILD_REVISION}"
    BUILD_BRANCH   = "${BUILD_BRANCH}"
  }
  platforms = ["linux/arm64"]
  tags = [
    "${ACR_REGISTRY}/seanly/appset:nexus3-${VERSION}-${JAVA_VERSION}-${FIXID}-arm64",
    "${ACR_REGISTRY}/seanly/appset:nexus3-${VERSION}-${JAVA_VERSION}-arm64"
  ]
  output = ["type=docker"]
}
