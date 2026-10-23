# Current UI walkthrough sources

October 23, 2026 UI rollout. All three user-facing videos are rebuilt from the current integrated client UI: first-install.json, hive-setup.json and discord-setup.json. The first-install chapter links are generated from the same timeline. Captures are from a disposable offline title-screen client; no server or world was joined and trading was never armed. Navigation through Help & setup and Review & continue used actual native clicks. Saved settings were verified unchanged across the capture sequence, including compact-window navigation.

MP4 output is silent 1920×1080 H.264 at 24fps, fast-start, with burned-in captions plus optional WebVTT. Each real screenshot has a SHA256 in its timeline. Fabric/folder cards are explicitly labelled instructional diagrams. External Discord screens are not simulated. Screenshot values are examples, not recommendations.

Render each source using `python docs/media-source/render-first-install.py --captures <directory> --output <directory> --timeline docs/media-source/<name>.json --name <name> --heading <heading>`. Source screenshots are local QA work products. Published build identity and actual artifact time belong in the release changelog after publication; this source file makes no claim that staging alone is a release.
