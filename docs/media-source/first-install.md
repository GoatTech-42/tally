# First-install walkthrough source

Website media refresh on October 21, 2026 for the published PR219 client (merge a202d6d2ed51e410b0a0f1341c241bff40d03ce4). No new client binary and no live bot updates. Current GUI captures came from the isolated tutorial preview; it never joins a server or presses Start. START HERE was captured by the same verified beginner GUI preview used for PR219. Source screenshot hashes are recorded per scene.

The 3 minute 14 second walkthrough has ten numbered steps plus an introduction: prerequisites, download, correct mods folder, server/B, START HERE, spending limits, protections, Review & Start, finding Trade, and Show menus + hotbar. Screenshots remain real GUI captures with disabled controls and offline blockers intact. The Fabric/folder panels are explicitly labelled instructional diagrams, not application screenshots. Screenshot amounts are examples, not recommendations.

MP4: 1920x1080 H.264, 24fps, fast-start metadata, no audio stream. Captions are rendered onto the video so they remain visible without subtitle configuration. An optional English WebVTT track and a chapter WebVTT are also supplied. The HTML chapter buttons use the same scene start times. Both site players, cache-version queries, poster and guide text are updated together.

Render with Pillow, ffmpeg and Windows Segoe UI: `python docs/media-source/render-first-install.py --captures <capture-directory> --output <output-directory>`. The renderer reads first-install.json; it uses no network, voice service, credentials or live client. Capture files are local work products, not user data. This documentation/media PR does not replace the published client release or create a new binary changelog entry.
