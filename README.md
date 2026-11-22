# ShizuGuard (v1.1.0)

No-root Android App Freezer, Background/Wakelock Clamper, Quick Settings **Panic Freeze** Tile, and Live RAM/Wakelock Telemetry powered by **Shizuku**.

## Included Features
- **Deep Freeze / Thaw (`pm disable-user --user 0` / `pm enable`):** Completely disables background execution until thawed.
- **Background & Wakelock Clamp (`cmd appops`):** Blocks `RUN_IN_BACKGROUND`, `RUN_ANY_IN_BACKGROUND`, and `WAKE_LOCK` while keeping apps usable on demand.
- **Panic Freeze Quick Settings Tile (`PanicFreezeTileService`):** Tag apps with the `Panic` chip and freeze/thaw all of them with one tap from your notification shade.
- **Live RAM & Wakelock Telemetry (`dumpsys meminfo` / `dumpsys power`):** Inspect system memory and active wake locks directly from the top bar.

## Build in Termux (Unrooted Android)
```bash
pkg update && pkg install -y openjdk-17 gradle android-tools aapt2
unzip ShizuGuard-v1.1.0.zip
cd ShizuGuard
gradle assembleDebug --no-daemon -Pandroid.aapt2FromMavenOverride=$(which aapt2)
```
Output APK: `app/build/outputs/apk/debug/app-debug.apk`

## Support / Donate

If you find ShizuGuard useful, consider supporting its development:

| Method | Destination |
|--------|-------------|
| Bitcoin (BTC) | `1PHrmfUPMSeyaGS1bmcCmJJxev4QFGALNQ` |
| Solana (SOL) | `mQ1YahGp1WuicSRZxeP6CV9CrkZ1ifoU4VEorNGRQvw` |
| PayPal | `yuhanbae@gmail.com` |
| Dana / OVO / GoPay | `082186816881` |

Full details in [DONATE.md](DONATE.md). Thank you!
