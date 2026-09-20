#!/bin/sh
# /usr/local/bin/m1-avf-probe.sh — runs INSIDE the AVF guest at boot.
# Dumps systemd/net/DRM/Mesa state to BOTH serial consoles (ttyS0 + hvc0),
# which AVF mirrors to the vm --console log file on the host.
# Installed + enabled via systemd one-shot (m1-avf-probe.service).
set -u
OUT=/dev/ttyS0
OUT2=/dev/hvc0
{
  echo "===== M1-AVF-PROBE $(date -u +%FT%TZ) ====="
  echo "--- uname"; uname -a
  echo "--- systemd"; systemctl is-system-running; systemctl --failed --no-legend | head -5
  echo "--- net"; ip -4 -brief addr; ip route show default 2>/dev/null | head -2
  echo "--- dri"; ls -l /dev/dri 2>&1
  echo "--- dmesg drm/gpu"; dmesg | grep -iE "drm|virtio.*gpu|virgl|venus|gfxstream" | head -20
  echo "--- mesa"; pacman -Q mesa vulkan-virtio 2>/dev/null
  echo "--- icd"; ls /usr/share/vulkan/icd.d/ 2>/dev/null
  echo "--- eglinfo"; eglinfo -B 2>&1 | grep -vE "^MESA-EGL: warning" | head -50
  echo "--- vulkaninfo"; vulkaninfo --summary 2>&1 | grep -iE "GPU|driver|ERROR" | head -15
  echo "===== M1-AVF-PROBE DONE ====="
} | tee "$OUT" "$OUT2" 2>/dev/null || true
