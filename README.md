<div align="center">

<img src="docs/assets/banner.svg" alt="ComfyUI Mobile — 把工作站，带到手边" width="960" />

**连接自己的 ComfyUI，用手机和平板继续创作。**

A native Android companion for your own ComfyUI — workflows, parameters, and original results.

[![Release](https://img.shields.io/github/v/release/NekoMirra/ComfyUIMobile?label=Release&color=5263CB)](https://github.com/NekoMirra/ComfyUIMobile/releases/latest) [![Android](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white)](#下载安装) [![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)](#界面预览) [![License](https://img.shields.io/github/license/NekoMirra/ComfyUIMobile?color=688078)](LICENSE)

[**下载 APK**](https://github.com/NekoMirra/ComfyUIMobile/releases/latest) · [连接教程](docs/connection-guide.md) · [使用指南](docs/user-guide.md) · [English](README.en.md)

</div>

> **本仓库为 fork**：本项目是 [7116-byte/ComfyUIMobile](https://github.com/7116-byte/ComfyUIMobile) 的 fork（`NekoMirra/ComfyUIMobile`），使用**独立签名密钥**，与上游官方包签名不同。本 fork 不会向上游推送任何内容；两者包名相同但签名不同，**不能互相覆盖安装**，从上游官方包升级需先卸载上游包。

## 在手机上，专注创作

当前 fork 版：[v0.2.1-fork.1](https://github.com/NekoMirra/ComfyUIMobile/releases/tag/v0.2.1-fork.1) · 支持完整实例的 HTTPS / WSS 连接，设置内可直达本仓库。

工作流和模型留在你的工作站，手机负责选择工作流、调整参数、提交任务和查看作品。日常操作使用原生触控界面；需要遮罩、画板等复杂控件时，再进入服务器的高级编辑。

| 从哪里开始 | 可以做什么 |
| --- | --- |
| **工作流** | 搜索与文件夹浏览，导入文件或带工作流的图片，点选后打开参数 |
| **参数** | 提示词、种子、模型、单图与有序多图；悬浮生成操作，错误定位到部件 |
| **结果** | 本地 / 云端分开查看，任务相册、图片缩放、原图保存、生成参数查看 |
| **后台** | 队列、任务状态、工作站与 GPU 信息，跟踪本 App 提交或明确接管的任务 |
| **服务器** | 服务器图片选图、服务器作品与历史生成、服务器能力探测与连接诊断 |

手机使用底部导航，平板使用侧栏和分栏；深浅主题跟随系统。首次打开有可跳过的引导，之后可从连接页或设置重新查看。

## 本轮新增能力

v0.2.0-fork.1 在既有功能之上合并了三条功能分支并完成整轮界面换肤；v0.2.1-fork.1 在此基础上修复了参数页的偶发滚动错位：

| 新增 | 说明 |
| --- | --- |
| **服务器选图** | 参数页的图片字段旁新增“服务器选图”，可直接浏览服务器上的图片库，按输入 / 输出标签筛选、按名称搜索，选中后回填字段并显示缩略预览 |
| **作品管理** | 结果页新增“服务器”分段，内含“服务器图片”与“历史生成”两个子分段，支持按名称过滤、分页加载与任务预览 |
| **工作流节点图** | 参数页可展开当前工作流的节点图，显示节点、连线与执行进度；点节点即可定位到对应参数 |
| **节点绕过 / 静音** | 每个节点可切换“始终执行 / 静音 / 绕过”：静音的节点不会进入执行链，绕过的节点不执行且下游改接上游输出（语义与 ComfyUI 前端一致） |
| **提示词库** | 常用提示词可收藏进本地提示词库，在参数页随时搜索、复用与删除 |
| **服务器能力与诊断** | 连接页与设置页可读取并查看服务器版本、队列与显卡状态；设置页新增服务器档案管理，可重命名、置顶或删除已保存的工作站 |
| **Glasmio 液态玻璃界面** | 界面换用 Glasmio 液态玻璃设计系统，工具栏、分段选择、胶囊标签、卡片、按钮与弹窗统一走同一套语义配色，深浅色跟随系统 |
| **参数页滚动修复**（v0.2.1-fork.1） | 被程序自动判定为“有问题”的节点展开时不再抢滚动位置，页面内容不会被顶出视口、顶部固定提示也不会压到内容上 |

> **最低系统要求变为 Android 13（API 33）**：本轮引入的 Glasmio 0.1.1 依赖声明 `minSdkVersion=33`、`minCompileSdk=37`，App 必须与之对齐，Android 12 及更早的设备无法安装。
>
> 服务器选图与作品管理依赖服务器端的资源接口；服务器未启用该功能时会明确提示“服务器未启用资源功能”，其余功能不受影响。

## 界面预览

<div align="center">
<img src="docs/screenshots/guide-v0.1.73-phone.png" alt="首次连接指南" width="30%" />
<img src="docs/screenshots/workflows-v0.1.73-phone.png" alt="工作流与悬浮打开参数" width="30%" />
<img src="docs/screenshots/parameters-v0.1.73-phone.png" alt="原生参数与悬浮生成" width="30%" />
<br />
<sub>连接指南 · 工作流 · 参数</sub>
<br /><br />
<img src="docs/screenshots/parameters-v0.1.73-tablet.png" alt="平板深色分栏参数界面" width="94%" />
</div>

截图为真实 Compose 界面的测试数据渲染，不含用户工作流或作品。更多预览与来源见 [截图说明](docs/screenshots/README.md)。

## 下载安装

1. 打开 [最新 fork 版 Release](https://github.com/NekoMirra/ComfyUIMobile/releases/latest)，下载 APK。
2. 在 Android 13（API 33）或以上设备安装，按系统提示允许此次安装。
3. 已装本 fork 的旧版可直接覆盖更新；也可从 App 的 **设置 → 软件更新** 检查、下载和安装。

已装 **0.2.0-fork.1** 的设备可直接覆盖升级到 **0.2.1-fork.1**，签名一致、数据保留、无需卸载；App 内「检查更新」会按版本号数字部分（`0.2.1` 高于 `0.2.0`）识别为新版本。

Release 同时提供 APK 的 SHA-256 校验文件。**本 fork 使用独立签名密钥，与上游官方包签名不同**：两者包名相同（`com.local.comfyuimobile`）但签名不同，无法互相覆盖安装，从上游官方版升级需先卸载上游包再安装本版本；卸载会清除本地数据，请先备份作品与草稿。本 fork 与上游正式版的数据互不相通。旧版检查不到更新时，请从 Release 下载覆盖，详见 [更新检查说明](docs/update-checks.md)。

## 三步开始

**① 准备工作站**

先让 ComfyUI 工作流在电脑网页正常运行，再开启局域网监听。已有启动命令追加 `--listen 0.0.0.0`；启动器用户在设置里开启局域网访问或添加监听参数，然后重启 ComfyUI。

**② 连接手机**

手机与电脑接入同一局域网，填写 `http://电脑的局域网IP:实际端口`，例如 `http://192.168.1.20:8188`。先用手机浏览器打开这个地址确认可达，再回 App 连接。同网段也可尝试扫描。

**③ 打开工作流**

点选工作流 → **打开参数** → 调整参数 → **生成**。完成后在“结果”查看；要自动保存到手机，需设置输出白名单并开启自动保存。

**第一次配置？** [完整连接教程](docs/connection-guide.md) 包含启动器、便携版 `.bat`、手动 Python 启动、IP 查找、防火墙和排错步骤。

## 我能连接什么？

| 连接方式 | 当前支持情况 |
| --- | --- |
| 自有电脑，同一局域网 | 支持，需开放监听及相应端口 |
| 已互通的 VPN / 虚拟局域网 | 支持使用可达的 HTTP 根地址；App 不负责建立 VPN |
| 云 GPU 上的完整 ComfyUI 实例 | v0.1.74 起支持 HTTP / HTTPS 根地址与安全 WebSocket；须提供原生接口和完整前端，具体平台需实测 |
| 仅应用分享页、平台登录 / Cookie / API Key、带路径或令牌的链接 | 目前不支持 |
| 纯 API、Serverless、部署后的单工作流接口、Comfy Cloud API | 目前不支持直接接入 |

“浏览器能打开一个网页”不等于完整兼容。App 需要服务器自己的前端、节点定义、工作流读写、上传和任务接口；它不提供算力、模型或平台账号。ComfyUI 通常没有默认访问认证，请在可信网络或 VPN 内使用，不要直接公开无认证端口。更多判断方法见 [云平台兼容说明](docs/connection-guide.md#云平台给的纯链接能用吗)。

HTTPS 实例链接请按原样粘贴，例如 `https://实例域名`；省略端口时使用 443，自定义端口原样保留。证书无效会拒绝连接。设置中的“GitHub 项目”可直接打开本仓库。

## 保存规则，先说清楚

- **服务器工作流**：点“保存 / 另存”才写回服务器，日常修改先留在手机工作副本。
- **本地草稿**：在设置中开启后，可恢复已保存服务器工作流的未保存修改；临时导入需手动保存才能长期保留。
- **本地作品**：云端可见不代表已下载。自动保存需命中输出白名单，只处理本 App 提交或明确接管的任务；也可手动保存原图。

复杂自定义节点可能需要高级编辑，缺少节点或模型需在电脑端处理。音频等输出暂以任务元数据展示；其他客户端任务的实时节点进度受 ComfyUI 服务端能力限制。详见 [使用指南](docs/user-guide.md)。

## 参与项目

遇到问题请 [提交 Issue](https://github.com/NekoMirra/ComfyUIMobile/issues/new/choose)，注明 App / ComfyUI 版本、连接类型与复现步骤；请遮盖地址、令牌、提示词和个人作品。欢迎改进兼容性、无障碍、小屏体验及文档，也欢迎分享实际使用反馈。

| 文档 | 内容 |
| --- | --- |
| [使用指南](docs/user-guide.md) / [连接教程](docs/connection-guide.md) | 从第一次连接到保存作品 |
| [贡献指南](CONTRIBUTING.md) | 构建 Debug、反馈问题和提交修改 |
| [更新记录](RELEASE_NOTES.md) | 各版本变化 |
| [开发文档](docs/development.md) / [文档索引](docs/README.md) | 工具链、测试、设计与技术记录 |

如果这个项目帮你把 ComfyUI 用得更顺手，欢迎点一颗 Star，让更多创作者找到它。

## 致谢与许可

基于 [ComfyUI](https://github.com/Comfy-Org/ComfyUI) 的服务端与前端能力，使用 Kotlin、Jetpack Compose、[Glasmio](https://github.com/NEORUAA/Glasmio) 液态玻璃组件、OkHttp、Coil 和 AndroidX Media3 构建。此项目为独立客户端，与 Comfy 组织无隶属关系。

[MIT License](LICENSE) · Copyright © 2026 7116-byte · Fork 改动（`NekoMirra/ComfyUIMobile`）同样以 MIT 发布
