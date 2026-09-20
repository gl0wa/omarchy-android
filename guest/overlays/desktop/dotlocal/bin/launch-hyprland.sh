#!/bin/sh
# Launched as the `desktop` user (via su from root). Starts Hyprland with a
# minimal environment. Log: ~/hyprland.log
set -eu
export XDG_RUNTIME_DIR="/run/user/$(id -u)"
export AQ_DRM_DEVICES="${AQ_DRM_DEVICES:-/dev/dri/card0}"
cd "$HOME"
exec Hyprland > "$HOME/hyprland.log" 2>&1
