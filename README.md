# VYRON Code

VYRON Code is an Android-first coding workbench inspired by the information architecture of VS Code, but redesigned for a phone/tablet: Activity Bar, Explorer hierarchy, code editor tabs, Outline/code hierarchy, Preview, Console, Problems and Integrated Terminal.

## What is included

- 8-second animated startup screen.
- Local account system: SQLite database, salted PBKDF2 password hash, no Firebase.
- Session persistence: after creating/logging in once, reopening the app restores the local session.
- Red error + shake interaction for wrong login credentials.
- One-time Android permission gate for Shizuku, notifications and battery/background settings.
- Shizuku detection/request flow. The terminal uses the normal Android shell by default and can use Shizuku for system commands when permission is granted.
- Project explorer with nested paths and an Outline/code-hierarchy panel.
- HTML/CSS/JavaScript local preview in a sandboxed iframe.
- Console and runtime problem reporting.
- Integrated native Android shell terminal.
- New-file support for arbitrary project paths and language templates/modes.
- ZIP export using Android Storage Access Framework; the ZIP name is the project name chosen by the user before export.
- ZIP import with safe path handling.
- Project persistence in SQLite plus a mirrored local workspace directory for terminal commands.
- Foreground background-runtime service with a persistent notification.
- GitHub Actions workflow that builds the debug APK and uploads it as an artifact.

## Language support model

The editor recognizes common programming-language extensions including HTML, CSS, JavaScript, TypeScript, JSON, Markdown, Python, Java, Kotlin, Rust, C/C++, Go, PHP, Shell, SQL and XML.

HTML/CSS/JavaScript have an embedded browser runtime. Other languages can be run through the integrated Android shell when a compatible runtime/toolchain is available on the device (for example through Termux or another shell environment). Shizuku does not magically install those runtimes.

## Why the UI is VS Code-like

The layout follows the same high-level workbench hierarchy used by VS Code: Activity Bar on the left, primary side bar / Explorer, editor with tabs, bottom/runtime panel, and a status bar. See the official VS Code UI documentation for those concepts.

## Build locally

Use Android Studio with AGP 9.5.0 or run the included workflow in GitHub Actions. The workflow uses JDK 17 and Gradle 9.8.0.

```text
gradle :app:assembleDebug
```

The generated APK is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Notes

- Shizuku must be installed and started separately by the user. The app requests access; it does not bundle Shizuku.
- Android background execution is device/OS dependent. The background mode uses a foreground service and system battery-optimization settings; OEM restrictions can still apply.
- The editor keeps the original VOCODE HTML prototype in `legacy/` for reference.
