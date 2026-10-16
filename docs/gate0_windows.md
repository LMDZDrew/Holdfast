# Gate 0 on Andrew's Windows PC

This checks a clean Forge installation and the two animation libraries. It does **not** load the exported Holdfast character clips; those need a minimal registered test mod and a visual client check after the library gate passes.

## 1. Prepare

- Use a Java 17 runtime on your Windows PATH. In PowerShell, `java -version` should print version 17. If not, stop and tell Codex what it printed.
- Get `tools/gate0-windows.ps1` from the `codex/gate0-windows-kit` branch. In PowerShell, run it from a clean folder with `powershell -ExecutionPolicy Bypass -File .\gate0-windows.ps1`. It downloads the exact Forge installer, GeckoLib and Player Animator jars, verifies the recorded hashes and ZIP structure, installs the server into `holdfast-gate0/server`, and stops before accepting the Minecraft EULA. This script has not been executed on Windows by Codex.
- If it fails, save the full PowerShell output and `holdfast-gate0/server/forge-install.log` (if present), then stop. Do not substitute a different mod version silently.

## 2. Clean client in Prism Launcher

1. Add an instance for **Minecraft 1.20.1, Forge 47.4.8**, with no other mods or shaders.
2. In the instance's **Edit → Mods** tab, add the two `.jar` files from `holdfast-gate0/server/mods`.
3. Launch to the title screen. Check that the mods are listed, open a temporary test world, and observe whether it runs for two minutes without errors. This verifies library loading, **not** our exported assets.
4. Save the full Prism launcher log and the instance's `logs/latest.log`. If it crashes, preserve the crash report too.

## 3. Dedicated server

1. The script installs the server but does not accept the EULA on your behalf. Open PowerShell in `holdfast-gate0/server` and run `.\run.bat nogui` once. It should generate `eula.txt` and stop. Read that file and change it only if you agree to the Minecraft EULA.
2. Run `.\run.bat nogui` again and wait for the `Done` message. Keep it running two minutes, then type `stop` in the server console.
3. Save `logs/latest.log` and any crash report. If the server stops before the EULA prompt or `Done`, save its full console output.

## 4. Send back

- `java -version` output, whether the script completed, and exact client/server status.
- Full client Prism log, client `latest.log`, server `latest.log`, and crash reports if any. A zipped log folder is fine. Do not paste account access tokens or session data if a log contains them.
- If both client and server load, we will build the small registration/test mod needed to load the Holdfast exports and check poses, first-person camera, held items, crawl, and timing. This is still stage 1; world generation remains on hold.
