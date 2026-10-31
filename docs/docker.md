# 镜像构建

`docker/Dockerfile` 有两条取发行物的路径，由 `SOURCE_STAGE` 选择。两条路径产出同一个 runtime 镜像。

| | `builder`（默认） | `prebuilt` |
| --- | --- | --- |
| 发行物来源 | 容器内从源码编译 | 宿主机 `assemblies/nexus-base-template/target` 下的 zip |
| 宿主机要求 | 只要 Docker | JDK 17 + 能跑 `./mvnw` |
| 耗时 | 冷缓存十几分钟，`.m2` 有缓存后约 5 分钟 | 取决于镜像层，zip 已存在 |
| 用途 | 一条命令起环境、CI、干净环境 | 本地已经在跑 `./mvnw` 时更快 |

默认是 `builder`，所以这一条命令就够，宿主机不需要 JDK 或 Maven：

```bash
cd docker
BUILD_REVISION="$(git rev-parse HEAD)" \
BUILD_BRANCH="$(git branch --show-current)" \
  docker compose up -d --build
```

`BUILD_REVISION` 不传也能构建，只是镜像里的 `build.revision` 会记成 `unknown` —— `.git` 不进构建上下文，buildnumber-maven-plugin 读不到。

## 镜像命名

tag 的构成是 `<DIST_NAME>-<VERSION>-<JAVA_VERSION>-<FIXID>`，默认产出：

```
seanly/appset:nexus3-coelacanth-3.76.1-02-java17-1
```

`DIST_NAME` 是 `nexus3-coelacanth` 而不是 `nexus3`，因为这个镜像带了本仓库的增补（外部 PostgreSQL、LDAP realm、user token、默认拒绝遥测），名字里点明是这个分支的构建，不会被当成上游镜像。

`VERSION` 保持上游的 Nexus 版本号（`3.76.1-02`）。它要和数据目录格式、随镜像打进去的插件版本对得上，所以这里不放分支自己的版本号 —— 后者在分支名和镜像的 `codename` label 上。

**改名字要动两个文件。** `docker/docker-bake.hcl` 里用的是 `DIST_NAME` 变量（4 个 target 共 8 个 tag 都引用它），但 `docker/docker-compose.yml` 的 `image:` 只能写死字面量，compose 读不到 bake 的变量。两边不一致的话，compose 构建完会给镜像打上它自己那个 tag，跟 bake 产出的不是同一个。

单次覆盖不用改文件：

```bash
DIST_NAME=nexus3-test docker buildx bake nexus3
```

## 用宿主机已构建的 zip

```bash
./mvnw clean install -Dpublic -DskipTests

# 两个参数上的坑：
#   用 -DskipTests，不要用 -Dmaven.test.skip=true。后者连测试代码一起不编，
#   nexus-repository-content 就不会产出 test-jar，而 nexus-repository-apt 依赖它
#   （classifier 为 tests），reactor 会挂在那个模块上。
#   不要加 -T 并行。并行构建会在 nexus-repository-testsupport 上撞到 JDK zipfs 的
#   内部编译器错误（this.inodes is null），是多线程共用一个 zip 的竞争。

SOURCE_STAGE=prebuilt docker compose up -d --build
# 或走 bake：
docker buildx bake nexus3-prebuilt
```

`prebuilt` 是需要显式指定的，不是默认值。原因是 zip 不存在时它只会抛一句 BuildKit 的 `failed to compute cache key ... not found`，完全看不出要先跑 Maven。这个没法在 Dockerfile 里包装成友好提示 —— BuildKit 在任何 `RUN` 执行之前就解析完 `COPY` 的源路径，父目录不存在时通配符也救不了（两种写法我都试过，分别报 `lstat /assemblies/nexus-base-template` 和 `lstat /assemblies`）。所以让它需要主动选择，默认那条路径永远能跑通。

bake 的两个 target tag 不同（`nexus3-prebuilt` 多一个 `-prebuilt` 后缀），两条路径不会互相覆盖镜像。镜像上的 `cloud.opsbox.image.source-stage` label 记录走的是哪条路径 —— 但只有 bake 会打这个 label，compose 不套 bake 的 labels。

## 几个必要的细节

**`-Dpublic` 是必须的。** 这棵树默认激活 `sonatype` profile（根 pom，激活条件是 `!public`），它给 maven-compiler-plugin 加了 `groovy-eclipse-batch:3.0.13-02` 依赖。那是 Sonatype 内部制品，Central 上没有，不加 `-Dpublic` 构建会在第一个编译模块上失败。

**`.git` 不进构建上下文。** 246M 只为换一个 revision 字符串，不值得。代价是 buildnumber-maven-plugin 读不到 git，`build.revision` 会变成 null（实测如此，该插件在没有 git 时不会让构建失败）。所以 commit SHA 由 `BUILD_REVISION` build arg 从宿主机传进去，上面的命令就是这么做的。不传则记为 `unknown`。

**只缓存 `~/.m2`。** builder stage 用 BuildKit cache mount 挂 Maven 仓库，重建时省掉绝大部分下载。node 和 yarn 没有缓存，因为 frontend-maven-plugin 把它们装进各模块自己的 `target/node`（会被 `clean` 删掉），而且每次 `yarn install` 之前还有一个 `yarn cache clean` execution，缓存它们没有意义。

**builder stage 装了 `build-essential` 和 `python3`。** 前端依赖里有需要现场编译的原生模块（`tree-sitter` 这类），缺 C/C++ 工具链会在 `yarn install` 的 link 步骤失败。

**前端那段要用 yarn 3 装，这是上游自带的一个矛盾。** 根 pom 把 `yarn.version` 钉在 `v1.22.19`，但 `yarn.lock` 是 yarn 3 格式（`__metadata: version 6`），而且 `components/nexus-rapture` 和 `plugins/nexus-coreui-plugin` 都依赖 `"@sonatype/nexus-ui-plugin": "workspace:*"`。`workspace:` 协议是 yarn 2+ 才有的，yarn 1 看不懂，会把 `@sonatype/nexus-ui-plugin@workspace:*` 当成 npm 包去 registry 上找，然后报 `Couldn't find package`。

宿主机上通常看不到这个问题：只要以前用 corepack 的 yarn 3 装过一次，根目录就留下了 `node_modules` 和指向 `components/nexus-ui-plugin` 的软链，插件那个 yarn 1 根本不需要再解析 `workspace:*`。干净的树没有这个目录，所以容器内第一次构建会直接撞上。

builder stage 的处理办法是：先用 `package.json` 里 `packageManager` 钉的 yarn 3.2.3（经 corepack）跑一次 `yarn install`，再把插件自己的 install 步骤用 `-Dnpm.install=--version` 置空。yarn 1 **执行**脚本是没问题的（实测 `yarn run build-all` 在 yarn 3 装出来的 `node_modules` 上正常跑完），所以插件的 `yarn run build-all` 保持原样，前端产物照旧生成。

这也是 builder stage 里单独装了 node 的唯一原因 —— 版本跟根 pom 的 `node.version` 对齐。Maven 构建本身不需要它，frontend-maven-plugin 会自己往各模块 `target/node` 下载。

**`.dockerignore` 的最后一行不能动位置。** 它用 `!` 重新放行 prebuilt 需要的那个 zip，而上面的 `**/target` 规则排除了它。Docker 以后出现的规则为准，所以那行必须留在最后。换版本号时要跟着根 pom 的 `nexus.version` 一起改。

## 跨平台

```bash
docker buildx bake acr          # linux/amd64 + linux/arm64，推到 ACR
```

`acr` 组里的两个 target 跟 `nexus3` 一样默认走 `builder`，即在容器内从源码编译。推之前记得把 revision 传进去：

```bash
BUILD_REVISION="$(git rev-parse HEAD)" \
BUILD_BRANCH="$(git branch --show-current)" \
  docker buildx bake acr
```

想用宿主机已经构建好的 zip，显式切过去：

```bash
SOURCE_STAGE=prebuilt docker buildx bake acr
```

两个 target 的 tag 不重叠：`nexus3-arm64` 只打带 `-arm64` 后缀的两个，而不带架构后缀的 `...-java17-1` 和 `...-java17` 只挂在 `nexus3-amd64` 上，所以 amd64 实际上就是这两个 tag 指向的默认架构。

注意这个组是分别构建两个单平台镜像，不产出 manifest list —— 它们都是 `output = ["type=docker"]`，各自装进本地 docker。要真正的多架构 manifest 需要改成 `type=registry` 并把两个平台放进同一个 target 的 `platforms`。
