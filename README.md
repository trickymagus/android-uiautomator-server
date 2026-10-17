# Purpose
[![Android CI](https://github.com/openatx/android-uiautomator-server/actions/workflows/android.yml/badge.svg)](https://github.com/openatx/android-uiautomator-server/actions/workflows/android.yml)

[UIAutomator](http://developer.android.com/tools/testing/testing_ui.html) is a
great tool to perform Android UI testing, but to do it, you have to write java
code, compile it, install the jar, and run. It's a complex steps for all
testers...

This project is to build a light weight jsonrpc server in Android device, so
that we can just write PC side script to write UIAutomator tests.

Requires Android 8.0 (API level 26) or later.

# Build

- Run command:

```bash
$ ./gradlew build
$ ./gradlew packageDebugAndroidTest
```

- Run the jsonrpc server on Android device

```bash
$ ./gradlew cC
$ adb forward tcp:9008 tcp:9008 # tcp forward
```

If debug apk already installed, There is no need to use gradle.

simply run the following command

```
adb forward tcp:9008 tcp:9008
adb shell am instrument -w -r -e debug false -e class com.github.uiautomator.stub.Stub \
    com.github.uiautomator.test/androidx.test.runner.AndroidJUnitRunner
```

# Run
```bash
$ curl -X POST -d '{"jsonrpc": "2.0", "id": "1f0f2655716023254ed2b57ba4198815", "method": "deviceInfo", "params": {}}' 'http://127.0.0.1:9008/jsonrpc/0'
{'currentPackageName': 'com.smartisanos.launcher',
 'displayHeight': 1920,
 'displayRotation': 0,
 'displaySizeDpX': 360,
 'displaySizeDpY': 640,
 'displayWidth': 1080,
 'productName': 'surabaya',
 'screenOn': True,
 'sdkInt': 26,
 'naturalOrientation': True}
```

# Litho accessibility compatibility

For automation of apps using older Litho versions, the APK includes
`com.github.uiautomator/.LithoCompatAccessibilityService`. This service subscribes
to window-content accessibility events and can retrieve window content.
Its callbacks do nothing; it does not request touch exploration,
gestures, key filtering, or the accessibility button. The server keeps enabled
accessibility services running with `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`.
Only the external automation tool changes accessibility settings through ADB;
the server does not change `Settings.Secure`.

The helper uses nonzero `feedbackGeneric` so it passes service-list filtering
on API 26–27. `isAccessibilityTool` avoids the full-control warning notification
on API 31 and later; older Android versions ignore it.

Use this order:

1. Start the server with `am instrument` as shown above, forward port 9008, and
   wait for `GET http://127.0.0.1:9008/ping` to return `pong`.
2. Read `adb shell settings get secure enabled_accessibility_services`. Treat
   the literal `null` and an empty result as an empty list.
3. Remove only the helper from that list and write the remaining entries back
   with `settings put secure enabled_accessibility_services`, or use `settings
   delete secure enabled_accessibility_services` if none remain. Wait about one
   second, append the helper, write the list back, then run `settings put secure
   accessibility_enabled 1`. Preserve all other service entries.
4. Poll the `getAccessibilityStatus` RPC until `lithoCompatible &&
   helperServiceBound && !touchExplorationEnabled`. If binding times out,
   repeat step 3 and poll again.
5. After the check succeeds, force-stop the target app, launch it, wait for its
   UI, and call `dumpWindowHierarchy`.
6. When finished, read the current service list again and remove only the
   helper, using `settings delete` if the remaining list is empty.

The following host-side Python 3.8+ example implements this sequence after the
server has been started in another terminal. Run `adb forward tcp:9008 tcp:9008`
first. Replace `target_package` and adapt the launch/readiness wait to the app
being tested. When using multiple devices, add the same `-s SERIAL` to every ADB
command, including the forward and instrumentation commands.

```python
import json
import shlex
import subprocess
import time
from pathlib import Path
from urllib.request import Request, urlopen

target_package = "com.example.lithoapp"
helper = "com.github.uiautomator/.LithoCompatAccessibilityService"
helper_ids = {
    helper,
    "com.github.uiautomator/com.github.uiautomator.LithoCompatAccessibilityService",
}
base_url = "http://127.0.0.1:9008"


def adb(*args):
    return subprocess.check_output(
        ["adb", "shell", shlex.join(args)], text=True
    ).strip()


def other_services():
    value = adb("settings", "get", "secure", "enabled_accessibility_services")
    if value in ("", "null"):
        return []
    return [entry for entry in value.split(":") if entry and entry not in helper_ids]


def write_services(entries):
    key = "enabled_accessibility_services"
    if entries:
        adb("settings", "put", "secure", key, ":".join(entries))
    else:
        adb("settings", "delete", "secure", key)


def rpc(method, params=None):
    body = json.dumps({
        "jsonrpc": "2.0", "id": 1, "method": method,
        "params": [] if params is None else params,
    }).encode()
    request = Request(base_url + "/jsonrpc/0", body,
                      {"Content-Type": "application/json"})
    with urlopen(request, timeout=30) as response:
        reply = json.load(response)
    if "error" in reply:
        raise RuntimeError(reply["error"])
    return reply["result"]


# Check server readiness before touching accessibility settings.
deadline = time.monotonic() + 30
while True:
    try:
        with urlopen(base_url + "/ping", timeout=2) as response:
            if response.read().decode().strip() == "pong":
                break
    except OSError:
        pass
    if time.monotonic() >= deadline:
        raise TimeoutError("uiautomator server did not become ready")
    time.sleep(0.5)

try:
    ready = False
    for attempt in range(3):
        write_services(other_services())
        time.sleep(1)  # Let Android observe the disabled state.
        write_services(other_services() + [helper])
        adb("settings", "put", "secure", "accessibility_enabled", "1")
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            status = rpc("getAccessibilityStatus")
            ready = (status["lithoCompatible"] and status["helperServiceBound"]
                     and not status["touchExplorationEnabled"])
            if ready:
                break
            time.sleep(0.5)
        if ready:
            break
    if not ready:
        raise TimeoutError(f"Helper did not become ready: {status}")
    print(json.dumps(status, indent=2))

    adb("am", "force-stop", target_package)
    adb("monkey", "-p", target_package, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(2)  # Replace with the target app's UI readiness check.
    hierarchy = rpc("dumpWindowHierarchy", [False, 50])
    Path("window.xml").write_text(hierarchy, encoding="utf-8")
finally:
    write_services(other_services())  # Preserve other services at cleanup time.
```

`getAccessibilityStatus` returns `enabled`, `touchExplorationEnabled`,
`helperServiceBound`, `lithoCompatible`, and `services`. Each service entry has
`id`, `eventTypes`, `capabilities`, and `feedbackType`. The list comes from
`getEnabledAccessibilityServiceList(FEEDBACK_ALL_MASK)` and represents bound
services. `lithoCompatible` reproduces the older Litho check: accessibility must
be enabled, and either touch exploration is enabled or at least one listed
service has both `TYPE_WINDOW_CONTENT_CHANGED` and
`CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT`. The readiness condition above also
requires the helper to be bound and touch exploration to be off.

Keep these lifecycle details in mind:

- Do not enable the helper before starting the server. `am instrument` kills
  the `com.github.uiautomator` process, which can leave the service marked as
  crashed. Disabling and enabling the service again clears that state.
- Writing the same setting value does not count as a change. The disable step
  also clears the crashed state; the delay is needed because back-to-back
  writes can cause Android to observe only the final value.
- Older Litho caches this accessibility decision once per app process. Restart
  the target app only after the readiness check succeeds.
- `adb shell am force-stop com.github.uiautomator` makes Android automatically
  remove the helper from the enabled-services setting.
- Other enabled accessibility services, including TalkBack, remain active
  while the server runs. If another service enables touch exploration, the
  readiness condition will not pass; this procedure preserves that service's
  settings. Apps that detect accessibility services may show warnings or block
  execution.

# Resources
- [Google UiAutomator Tutorial](https://developer.android.com/training/testing/ui-testing/uiautomator-testing?hl=zh-cn)
- [Google UiAutomator API](https://developer.android.com/reference/kotlin/androidx/test/uiautomator/package-summary)
- [Maven repository of uiautomator](https://mvnrepository.com/artifact/androidx.test.uiautomator/uiautomator)
- [androidx.test.uiautomator release notes](https://developer.android.com/jetpack/androidx/releases/test-uiautomator)

Clipboard, Thanks to @fplust

- https://github.com/majido/clipper
- https://github.com/appium/io.appium.settings/blob/5d3bc99ff35f3b816b4342395aba1bdea82ad48f/app/src/main/java/io/appium/settings/receivers/ClipboardReceiver.java

# The buildin input method
**com.github.uiautomator/.AdbKeyboard**

Encode the text into UTF-8 and then Base64

For example:

    "Hello 你好" -> (UTF-8 && Base64) = SGVsbG8g5L2g5aW9

Send to AdbKeyboard with broadcast

> Broadcast completed: result=-1 means success
```bash
# show ime list
$ adb shell ime list -s -a
com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME
com.google.android.tts/com.google.android.apps.speech.tts.googletts.settings.asr.voiceime.VoiceInputMethodService
com.github.uiautomator/.AdbKeyboard

# enable and set ime
$ adb shell ime enable com.github.uiautomator/.AdbKeyboard
$ adb shell settings put secure default_input_method com.github.uiautomator/.AdbKeyboard

# Append text to input field
$ adb shell am broadcast -a ADB_KEYBOARD_INPUT_TEXT --es text SGVsbG8g5L2g5aW9
Broadcasting: Intent { act=ADB_KEYBOARD_INPUT_TEXT flg=0x400000 (has extras) }
Broadcast completed: result=-1

# Clear text
$ adb shell am broadcast -a ADB_KEYBOARD_CLEAR_TEXT
Broadcasting: Intent { act=ADB_KEYBOARD_CLEAR_TEXT flg=0x400000 }
Broadcast completed: result=-1

# Clear text before append text
$ adb shell am broadcast -a ADB_KEYBOARD_SET_TEXT --es text SGVsbG8g5L2g5aW9
Broadcasting: Intent { act=ADB_KEYBOARD_SET_TEXT flg=0x400000 (has extras) }
Broadcast completed: result=-1

# Send Keycode or Editor code according to the InputEditor requires
$ adb shell am broadcast -a ADB_KEYBOARD_SMART_ENTER
Broadcasting: Intent { act=ADB_KEYBOARD_SMART_ENTER flg=0x400000 }
Broadcast completed: result=-1

# Send keycode, eg: ENTER
$ adb shell am broadcast -a ADB_KEYBOARD_INPUT_KEYCODE --ei code 66
# Send Editor code, eg: 2
$ adb shell am broadcast -a ADB_KEYBOARD_EDITOR_CODE --ei code 2 # IME_ACTION_GO

# Get clipboard (without data)
$ adb shell am broadcast -a ADB_KEYBOARD_GET_CLIPBOARD
Broadcasting: Intent { act=ADB_GET_CLIPBOARD flg=0x400000 }
Broadcast completed: result=0

# Get clipboard (with data, base64 encoded)
$ adb shell am broadcast -a ADB_GET_KEYBOARD_CLIPBOARD
Broadcasting: Intent { act=ADB_GET_CLIPBOARD flg=0x400000 }
Broadcast completed: result=-1, data="5LqG6Kej5Lyg57uf5paH5YyW"

# show keyboard
$ adb shell am broadcast -a ADB_KEYBOARD_SHOW
# hide keyboard
$ adb shell am broadcast -a ADB_KEYBOARD_HIDE
```

- [Editor Code](https://developer.android.com/reference/android/view/inputmethod/EditorInfo)
- [Key Event](https://developer.android.com/reference/android/view/KeyEvent)

# Change GPS mock location
You can change mock location from terminal using adb in order to test GPS on real devices.

```
adb [-s <specific device>] shell am broadcast -a send.mock [-e lat <latitude>] [-e lon <longitude>]
        [-e alt <altitude>] [-e accurate <accurate>]
```

For example:

```
adb  shell am broadcast -a send.mock -e lat 15.3 -e lon 99
```

## Show toast

```
adb shell am start -n com.github.uiautomator/.ToastActivity -e message hello
```

## Float window

```
adb shell am start -n com.github.uiautomator/.ToastActivity -e showFloatWindow true # show
adb shell am start -n com.github.uiautomator/.ToastActivity -e showFloatWindow false # hide
```

# How to use with Python

```python
import uiautomator2 as u2

d = u2.connect()

d.screen.on()
d(text="Settings").click()
d(scrollable=True).scroll.vert.forward()
```

Refer to python wrapper library [uiautomator](https://github.com/xiaocong/uiautomator).

# How to generate changelog
[conventional-changelog](https://github.com/conventional-changelog/conventional-changelog/tree/master/packages/conventional-changelog-cli)

```bash
npm install -g conventional-changelog-cli
conventional-changelog -p grunt -i CHANGELOG.md -s -r 0
```

# Notes

If you have any idea, please email codeskyblue@gmail.com or [submit tickets](https://github.com/openatx/android-uiautomator-server/issues/new).

# Dependencies

- [nanohttpd](https://github.com/NanoHttpd/nanohttpd)
- [jsonrpc4j](https://github.com/briandilley/jsonrpc4j)
- [jackson](https://github.com/FasterXML/jackson)
- [androidx.test.uiautomator](https://mvnrepository.com/artifact/androidx.test.uiautomator/uiautomator-v18)

# Added features

- [x] support unicode input

# Thanks to
- [xiaocong](https://github.com/xiaocong)
- https://github.com/willerce/WhatsInput
- https://github.com/senzhk/ADBKeyBoard
- https://github.com/amotzte/android-mock-location-for-development
- https://github.com/gladed/gradle-android-git-version
