#!/bin/bash
set -e
export LANG=C.UTF-8
export XDG_SESSION_TYPE=wayland
export XDG_CURRENT_DESKTOP=Hyprland
export AQ_DRM_DEVICES=/dev/dri/card0
source /etc/profile.d/omarchy.sh
exec uwsm start -F -D Hyprland -- /usr/bin/Hyprland --config "$HOME/.config/hypr/hyprland.lua"
