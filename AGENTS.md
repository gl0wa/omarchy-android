# AGENTS.md — working agreement for agents and humans

## Goal
Milestone 1: aarch64 Arch Linux + systemd + networking + persistent storage +
accelerated virtual GPU (Mesa non-llvmpipe) + Hyprland + Foot + keyboard/pointer,
preferably under an emulated AVF-like environment. Omarchy is OUT OF SCOPE.

## Layer discipline
Treat as separate layers and attribute failures to one layer:
1. host / Android / AVF
2. VM boot infrastructure
3. Linux kernel and guest devices
4. Arch Linux ARM root filesystem
5. graphics stack: DRM → virtio-gpu → Mesa → EGL/Vulkan
6. Wayland compositor: Hyprland
7. applications

Do not bundle everything into one opaque image script.

## Evidence discipline
- Prefer primary sources: AOSP source/docs, crosvm source/docs, kernel
  source/docs, Mesa source/issues, Hyprland source/issues/docs, Arch/ArchARM.
- `docs/status.md` is project memory — update it when any experiment changes
  understanding. Never rely solely on conversational context.
- Graphics failures: OBSERVATION → HYPOTHESIS → ONE experiment → RESULT →
  update hypothesis. One variable at a time. Logs go to
  `artifacts/diagnostics/<date>-<topic>/`.
- A successful graphics result MUST NOT be llvmpipe. `glxinfo -B` must show
  `virgl`/`Virtio GPU`/`Venus` with `Accelerated: yes`.

## Reproducibility
- Everything important via repo commands: `./dev/build`, `./dev/boot`,
  `./dev/shell`, `./dev/diagnose`, `./dev/test-gpu`, `./dev/test-desktop`.
- No undocumented manual setup. Host deps are detected, never silently
  installed; document Homebrew/SDK requirements. Ask before destructive or
  security-sensitive host changes.
- Small commits for proven progress (`guest: …`, `graphics: …`, `desktop: …`).
  Never commit disk images or large artifacts. Never push without explicit
  permission. Never rewrite published history.

## Emulator-first
1. QEMU/UTM on Mac first (rootfs, kernel, systemd, Mesa, Hyprland packaging).
2. Linux box (nested or remote) for crosvm/Cuttlefish `vm`-tool flow.
3. Pixel only when the hypothesis explicitly needs real AVF/pKVM — and only
   after `docs/pixel-test-plan.md` exists. Minimize invasive device changes.

## Model usage
Muse Spark 1.3 Free does the bulk of the work (use Muse Spark 1.3-contrib
from the Meta integration only when Free cannot proceed). Parallel fast
research first; escalate only on genuine architectural blockers with
contradictory evidence gathered.
