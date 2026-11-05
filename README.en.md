<div align="center">

<img src="docs/assets/banner.svg" alt="ComfyUI Mobile" width="960" />

**A native Android companion for your own ComfyUI.**

[Download APK](https://github.com/NekoMirra/ComfyUIMobile/releases/latest) · [简体中文](README.md) · [Report an issue](https://github.com/NekoMirra/ComfyUIMobile/issues/new/choose)

</div>

> **This repository is a fork.** It is a fork of [7116-byte/ComfyUIMobile](https://github.com/7116-byte/ComfyUIMobile) (`NekoMirra/ComfyUIMobile`) and is signed with an **independent signing key**, so its signature differs from the official upstream build. Nothing is ever pushed back to upstream. Both builds share the package name but not the signature, so they **cannot be installed over each other** — uninstall the official upstream build first.


Your models and workflows stay on your workstation. Use your phone or tablet to browse workflows, edit parameters, submit jobs, and save original results. The app uses Kotlin and Jetpack Compose with the Glasmio liquid-glass component set, phone navigation, tablet split layouts, and system light / dark themes. The app interface currently prioritizes Simplified Chinese.

## Preview

<div align="center">
<img src="docs/screenshots/guide-v0.1.73-phone.png" width="30%" alt="First-use connection guide" />
<img src="docs/screenshots/workflows-v0.1.73-phone.png" width="30%" alt="Workflow browser" />
<img src="docs/screenshots/parameters-v0.1.73-phone.png" width="30%" alt="Native parameter editing" />
</div>

These are native Compose renders with synthetic test data, not private user workflows or artwork.

## What's new in v0.2.1-fork.1

v0.2.0-fork.1 merged three feature branches and replaced the whole interface with the Glasmio liquid-glass design system; v0.2.1-fork.1 keeps that feature set and fixes an intermittent scroll jump on the parameter page.

| New | What it does |
| --- | --- |
| **Server image picker** | Image fields in the parameter page get a **服务器选图** action that browses the server-side image library (filter by input / output tag, search by name) and writes the chosen image back into the field with a thumbnail preview |
| **Server gallery** | The results page gains a **服务器** segment with **服务器图片** and **历史生成** sub-segments, name filtering, paged loading and job previews |
| **Workflow node graph** | The parameter page can expand the current workflow into a node graph with links and execution progress; tapping a node jumps to its parameters |
| **Node bypass / mute** | Every node can be switched between **始终执行 / 静音 / 绕过** (mode 0 / 2 / 4): muted nodes do not enter the execution chain, and a bypassed node is skipped while its downstream input is rewired to the upstream output — the same semantics the ComfyUI frontend uses |
| **Prompt library** | Frequently used prompts can be saved into a local prompt library, then searched, reused and deleted from the parameter page |
| **Server capabilities & diagnostics** | The connection screen and settings can read and show the server version, queue and GPU state; settings also gains server profile management (rename, pin, delete) |
| **Glasmio liquid-glass UI** | Toolbars, segmented pickers, capsule tags, cards, buttons and dialogs now share one Glasmio token set, in both light and dark themes |
| **Parameter page scroll fix** (v0.2.1-fork.1) | Nodes that the app auto-expands because they have a problem no longer steal the scroll position, so the page content is not pushed out of view and the pinned top bar no longer overlaps it |

> **Minimum system requirement is now Android 13 (API 33).** The Glasmio 0.1.1 artifacts declare `minSdkVersion=33` and `minCompileSdk=37`, so the app must be aligned with them; Android 12 and older devices cannot install this build.
>
> Server image picking and the server gallery rely on the server-side asset endpoints. When the server does not expose them the app says so explicitly (“服务器未启用资源功能”) and everything else keeps working.

## Get started

1. Download the signed release APK from [GitHub Releases](https://github.com/NekoMirra/ComfyUIMobile/releases/latest). **Android 13 (API 33) or newer is required.** This fork is signed with its own key, so it **cannot be installed over the official upstream build** — uninstall the upstream build first (that clears local data, so back up your work). Over an earlier build *of this fork* the update works in place: **0.2.0-fork.1 upgrades directly to 0.2.1-fork.1** with the same signature, keeping your data and needing no uninstall.
2. Run your own ComfyUI with LAN access enabled. Append `--listen 0.0.0.0` to your existing launch command, preserving other arguments. Allow the actual port through your firewall on a trusted network.
3. On your phone, first open `http://YOUR_COMPUTER_IP:PORT` in a browser. If reachable, use the same address in the app; for example, `http://192.168.1.20:8188`.
4. Select a workflow, tap **打开参数** (open parameters), edit it, and tap **生成** (generate).

The first-use guide can be skipped and reopened from the connection screen or settings. See the [connection guide](docs/connection-guide.md) for launcher, Windows portable, and Python setup instructions in Chinese.

## What it does

- Workflow folders, search, import / export, and explicit server saving.
- Native prompt, number, model, image, ordered multi-image, and video controls.
- Original server frontend for advanced custom widgets, sharing the current working copy.
- Local / server results, job albums, zoom, original downloads, and generation metadata.
- Output allowlists for automatic local saving, with explicit ownership / takeover of jobs.
- Queue, workstation information, task monitoring, and signed release updates.

## Connection compatibility

Starting with v0.1.74, the app accepts **HTTP / HTTPS root addresses**, with secure WebSocket for HTTPS instances. HTTPS uses port 443 when omitted; nonstandard ports are preserved. Existing HTTP addresses retain the default ComfyUI port 8188. It needs a complete ComfyUI instance: the real frontend, node definitions, userdata workflows, upload, task / result APIs, and WebSocket.

Path-prefix proxies, platform logins, cookies, API keys, token-bearing URLs, app sharing pages, serverless endpoints, and Comfy Cloud / deployed workflow APIs are **not currently supported**. Cloud GPU instances require a directly reachable full HTTP / HTTPS service; no cloud vendor acceptance is claimed. Invalid TLS certificates are rejected. Do not replace an HTTPS URL with HTTP or expose an unauthenticated instance publicly. Settings include a direct link to this GitHub project.

## Data and limits

Parameter changes affect a working copy; only explicit **保存 / 另存** saves to the server. Optional local drafts apply to saved server workflows; temporary imports require saving for long-term retention. Server thumbnails are not automatically local copies: enable automatic saving and configure output allowlists, or save manually. Export important work before clearing data or uninstalling.

Custom nodes may require advanced editing. Missing models / nodes must be resolved on the workstation. Audio has no dedicated player yet. Real-time node progress for other clients' jobs is limited by the server protocol. The app does not provide GPU compute or models.

## Contribute

See [CONTRIBUTING](CONTRIBUTING.md) for Debug builds and PR guidance, [documentation](docs/README.md) for technical notes, and [release notes](RELEASE_NOTES.md) for changes. Please remove private addresses, credentials, prompts, and artwork from issue reports.

Built on [ComfyUI](https://github.com/Comfy-Org/ComfyUI). This is an independent client, not an official Comfy product. This fork (`NekoMirra/ComfyUIMobile`) keeps the upstream MIT [license](LICENSE) and adds its own changes under the same terms.
