# orbit-lantern

A small server add-on. After installing, open `/lantern` on the server.

## Install

1. Dashboard → Plugins → Repositories → add
   `https://raw.githubusercontent.com/neelious/orbit-lantern/main/manifest.json`
2. Catalog → Orbit Lantern → Install, then restart the server.
3. Open `http://<server>:8096/lantern`.

## Release

Push a tag like `v1.0.1`. The workflow builds the plugin, attaches it to a GitHub release and updates `manifest.json`.
