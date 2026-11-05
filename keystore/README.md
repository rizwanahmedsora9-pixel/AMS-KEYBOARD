# Debug signing key

`ams-debug.jks` is the **public debug signing key** for AMS Keyboard. It exists so that every build
(including every GitHub Actions run) is signed with the same certificate:

- Android only installs an APK over an existing app when the package name **and** the signing
  certificate match. Gradle's default debug keystore is created fresh per machine/runner, so an APK
  built on one machine could not update an APK built on another — that is the "App not installed"
  error users were hitting.
- With this file checked in, every debug APK has the fingerprint
  `SHA-256 B4:5B:3D:9D:7A:19:4F:32:07:A9:A4:C2:0D:C4:18:25:CF:3D:0F:D6:18:7A:63:41:01:D7:13:77:FE:3A:88:AD`
  and later builds install in place.

Credentials (public, intentionally weak):

| Field | Value |
|---|---|
| Store file | `keystore/ams-debug.jks` |
| Store type | JKS |
| Store password | `amskeyboard` |
| Key alias | `amsdebug` |
| Key password | `amskeyboard` |
| Validity | until 2056 |

**Never use this key for release builds.** Anyone can read it here, so it provides no security; it
only makes debug installs reproducible. For Play Store or public releases create a private keystore
and configure a `release` signing config that is not committed.

Commands:

```bash
# Built automatically by :app:assembleDebug. Manual signing of any APK:
apksigner sign --ks keystore/ams-debug.jks --ks-pass pass:amskeyboard \
  --key-pass pass:amskeyboard --ks-key-alias amsdebug app-debug.apk

# Check which key a build was signed with:
apksigner verify --print-certs app-debug.apk
```
