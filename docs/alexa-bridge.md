# Alexa bridge

Lets a cloud function (an Alexa skill's Lambda) control the TV through [ntfy.sh](https://ntfy.sh), with no
open port on your network. The launcher's `AlexaBridgeService` keeps one outgoing HTTPS stream open to a
command topic, runs the signed commands it receives, and publishes the TV's state and app list to a second
topic. It does nothing, and shows no notification, unless the config file below exists.

## Configure

Create `alexa.properties` on your computer (never commit it; it is in `.gitignore`):

```
NTFY_BASE=https://ntfy.sh
CMD_TOPIC=<long random command topic>
STATE_TOPIC=<long random state topic>
HMAC_SECRET=<long random secret>
```

The format is a shell env file: `KEY=VALUE` lines, `#` comments, optional quotes. Backslashes are literal.
The topics are only as private as their names, and every message is also signed with `HMAC_SECRET`, so make all
three long and random.

Push it, then restart the launcher (or the TV):

```
adb shell mkdir -p /sdcard/Android/data/com.gathin.tvhome/files
adb push alexa.properties /sdcard/Android/data/com.gathin.tvhome/files/alexa.properties
```

Locations tried, in order:

1. `/sdcard/Android/data/com.gathin.tvhome/files/alexa.properties` (the app's own folder; use this one)
2. `/data/local/tmp/alexa.properties` (`adb push` can write it, but on most firmware apps cannot read that folder)

If the launcher was installed with another `launcherApplicationId`, use that package name in the first path.

## Start-up

The service starts when the launcher opens, at boot, and when either accessibility service
(`HomeKeyService`, `InputKeyService`) connects. It runs as a foreground service with a minimum-importance
notification, reconnects after any failure with a 1 s to 60 s backoff, and resumes from the last message id it
saw (`?since=`; `30s` on the very first start).

## Wire protocol

Topics are on `NTFY_BASE`. The TV reads `GET <NTFY_BASE>/<CMD_TOPIC>/json` and writes
`POST <NTFY_BASE>/<STATE_TOPIC>`. Every message body is an envelope:

```
{"p":"<payload JSON as a string>","s":"<lowercase hex HMAC-SHA256 of p, keyed with HMAC_SECRET>"}
```

Both are UTF-8. The TV checks `s` in constant time before parsing `p`, then rejects a command whose `ts` (unix
seconds) is more than 120 s from its clock, or whose `id` it has seen among the last 200.

Command payload: `{"v":1,"id":"<random hex>","ts":<unix seconds>,"cmd":"<cmd>","args":{...}}`

| cmd | args | Action |
|---|---|---|
| `power_off` | `{}` | Accessibility `GLOBAL_ACTION_LOCK_SCREEN` (API 28+); else `KEYCODE_SLEEP` |
| `power_on` | `{}` | 3 s `SCREEN_BRIGHT_WAKE_LOCK` with `ACQUIRE_CAUSES_WAKEUP` |
| `volume_set` | `{"level":0-100}` | `STREAM_MUSIC` to `round(level * max / 100)` |
| `volume_adjust` | `{"delta":-100..100}` | delta scaled to `max` steps, at least one |
| `mute` | `{"mute":true}` | `ADJUST_MUTE` / `ADJUST_UNMUTE` |
| `media` | `{"key":"play\|pause\|stop\|next\|previous\|rewind\|fastforward\|play_pause"}` | media key down and up |
| `launch` | `{"package":"com.netflix.ninja"}` | TV launch intent, else phone launch intent |
| `channel_skip` | `{"delta":n}` | `KEYCODE_CHANNEL_UP` / `DOWN`, at most 20 presses |
| `key` | `{"keycode":int}` | media key dispatch, best effort |
| `publish_apps` | `{}` | republish the app list |

State payloads the TV publishes, in the same envelope:

- `{"v":1,"type":"apps","ts":..,"apps":[{"l":"Netflix","p":"com.netflix.ninja"},...]}` at service start, after an
  app is installed, removed or updated, and on `publish_apps`. Apps with a TV or a phone launcher entry,
  one per package, sorted by label.
- `{"v":1,"type":"state","ts":..,"power":"ON","volume":0-100,"muted":false}` after every command (power
  commands wait 2 s so the screen has changed) and every 15 minutes.

## Limits you should know about

- **Power off is best effort.** `GLOBAL_ACTION_LOCK_SCREEN` needs an enabled accessibility service
  (`HomeKeyService` or `InputKeyService`, see their opt-in flags) and Android 9 or later; whether it blanks the
  screen of a TV with no lock screen depends on the firmware. The `KEYCODE_SLEEP` fallback goes through
  `AudioManager.dispatchMediaKeyEvent`, which only reaches media sessions; real key injection needs the
  `INJECT_EVENTS` permission that ordinary apps cannot hold, so expect it to do nothing.
- **Power on only works while the TV's CPU is awake.** A TV in deep standby has dropped its Wi-Fi stream, and no
  app can wake it; use HDMI-CEC or Wake-on-LAN for that.
- **Volume** does nothing on boxes with fixed output (HDMI to a receiver that owns the volume).
- **Launching from the background** can be blocked on Android 10+. The service starts apps through a running
  accessibility service when there is one, which is exempt.
- **ntfy messages over 4096 bytes become attachments**, which the cloud side cannot read as an envelope. When
  the app list would not fit, the TV drops phone-only apps first and then the end of the list.
- ntfy.sh topics are public by name. The HMAC stops forged commands, but anyone who learns `CMD_TOPIC` or
  `STATE_TOPIC` can read what the TV publishes (app names, volume, power) and see when commands arrive.
