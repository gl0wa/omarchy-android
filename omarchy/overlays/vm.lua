-- Additional VM shortcuts avoid macOS Command+Space/Command+W interception.
o.bind("CTRL + SHIFT + RETURN", "VM terminal", { omarchy = "terminal" })
o.bind("CTRL + SHIFT + SPACE", "VM Omarchy menu", "omarchy-menu toggle")
o.bind("CTRL + SHIFT + A", "VM apps", "omarchy-menu toggle apps")
o.bind("CTRL + SHIFT + W", "VM close window", hl.dsp.window.close())
