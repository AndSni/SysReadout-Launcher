# SysReadout Launcher — user manual

SysReadout Launcher (SRL) is a home screen: a short list of your apps in front of a live, terminal-style readout of what the phone is doing. This manual covers everything in the app, from setting it as your home screen to the system monitor.

- [1. Make it your home screen](#1-make-it-your-home-screen)
- [2. The home screen](#2-the-home-screen)
- [3. The app drawer](#3-the-app-drawer)
- [4. Settings](#4-settings)
- [5. The log](#5-the-log)
- [6. The system monitor](#6-the-system-monitor)
- [7. Appearance](#7-appearance)
- [8. Lock screen](#8-lock-screen)
- [9. Battery use](#9-battery-use)
- [10. Troubleshooting](#10-troubleshooting)
- [11. Privacy](#11-privacy)

## 1. Make it your home screen

After installing, open **Settings › Apps › Default apps › Home app** and choose **SysReadout**. (The exact path differs a little between phone makers; searching settings for "home app" finds it.) Until then you can also open SysReadout from your current launcher to try it.

To go back, pick your previous launcher in the same place.

## 2. The home screen

The home screen has three layers: the **log** in the background, an optional **clock and date**, and your **menu entries** (pinned apps) on top.

| Gesture | Does |
|---|---|
| Swipe up | Open the app drawer |
| Swipe down | Open the notification shade |
| Swipe left / right | Open the app you assigned (settings › gestures) |
| Double-tap empty space | Lock the screen (needs the lock service, see below) |
| Long-press empty space | Open settings |
| Tap the clock / date | Open your alarms / calendar |

**Menu entries.** Pin apps from the drawer (long-press an app › *pin to home*). Entries are separate text blocks; you can add as many as fit the free height of the screen, and the drawer tells you when there's no room left. Long-press an entry to rename it, move it up or down, remove it, or open its app info.

**Double-tap to lock** uses a small accessibility service whose only job is to lock the screen; it reads no screen content. Switching the option on takes you to Android's accessibility settings to enable *SysReadout: double-tap to lock*.

## 3. The app drawer

Swipe up. The keyboard opens straight into search (you can turn that off).

- Search ignores accents and matches, in this order: names that **start** with what you typed, names whose **initials** match (`gm` finds *Google Maps*), then names that **contain** it.
- When only one app matches, it opens by itself (*launch single match*, can be turned off). The keyboard's Go key opens the first result.
- Long-press an app to pin it, rename it, hide it, open its app info or uninstall it.
- Apps from a work profile are marked `[w]`.

Hidden apps are listed under settings › hidden apps, where you can bring them back.

## 4. Settings

Long-press an empty part of the home screen. The header (`sysreadout --config › …`) shows where you are; tap it or press Back to go up a level.

- **screens › appearance**: presets, colours, fonts, CRT effects ([section 7](#7-appearance)).
- **screens › log**: banner, rows, system monitor, stream ([sections 5](#5-the-log) and [6](#6-the-system-monitor)).
- **home**: clock, date, log background on/off, entry style, horizontal alignment, vertical position.
- **lock screen**: [section 8](#8-lock-screen).
- **drawer**, **gestures**, **hidden apps**, **about & licenses**.

**Entry styles** keep menu text readable over the log: *frosted* (blurred glass), *highlight* (a solid box), *inverted* (reverse video) or *bare*.

## 5. The log

The log has up to four parts, top to bottom: the **banner**, the **pinned rows**, the **monitor tables** and the **stream**.

### Banner

Free text above everything else, like a terminal's boot header. Edit it under log › banner › *edit text*. These placeholders are filled in live:

| Placeholder | Becomes |
|---|---|
| `{year}` | the current year |
| `{date}` / `{time}` / `{weekday}` | today's date, the time (HH:mm), the day's name |
| `{device}` | the name you gave the phone in Android settings (usually its model name) |
| `{model}` / `{maker}` | the model and manufacturer codes |
| `{android}` / `{api}` / `{kernel}` / `{build}` | Android version, API level, kernel version, build id |
| `{uptime}` / `{battery}` | time since boot, battery level |

For example:

```
ASNIDEV INDUSTRIES UNIFIED OPERATING SYSTEM
COPYRIGHT 2026-{year} ASNIDEV INC.
- {device} -
```

The banner has its own font and colour (appearance › text › banner) and can be aligned left, centre or right.

### Pinned rows

Rows that stay in place and update every few seconds. Under log › *pinned rows* you can switch each one on or off (tap it) and reorder the ones you show (↑ ↓). A row that needs a permission asks for it the moment you switch it on; until it's granted it shows `needs …` instead of a value. Signal strength is written out in words (*very strong* … *very weak*, *no signal*) rather than drawn as bars.

| Row | Shows | Needs | On by default |
|---|---|---|---|
| `time` | time with seconds, UTC offset, unix epoch | — |  |
| `up` | uptime and how much of it was awake | — | yes |
| `boot` | boot count and when this boot started | — |  |
| `os` | Android version, API level, security patch | — | yes |
| `kern` | kernel release | — | yes |
| `props` | A/B slot, verified boot, bootloader lock, treble, first API, build type | — |  |
| `dev` | manufacturer, model, codename | — |  |
| `self` | SysReadout's own process: pid, memory, threads | — |  |
| `cpu` | online cores, current clock range, governor | — | yes |
| `cores` | load of every CPU core in percent | Shizuku |  |
| `load` | load average and process count | Shizuku |  |
| `soc` | chipset and CPU architecture | — | yes |
| `gpu` | GPU, OpenGL ES and Vulkan versions | — |  |
| `mem` | available / total RAM, low-memory flag | — | yes |
| `vm` | memory detail: cached, active, dirty, slab | — |  |
| `swap` | swap (usually zram) in use | — | yes |
| `therm` | thermal status and throttling headroom | — | yes |
| `temps` | every hardware temperature sensor: cpu, gpu, skin… | Shizuku |  |
| `bat` | level, charge state, temperature, health | — | yes |
| `pwr` | voltage, current, watts, power source | — | yes |
| `chg` | charge counter, estimated capacity, cycles, time to full | — |  |
| `mode` | battery saver, doze, do-not-disturb, ringer | — |  |
| `net` | connection type and live throughput | — | yes |
| `wifi` | Wi-Fi signal, link speed, band | — | yes |
| `cell` | mobile operator, network type, signal | — |  |
| `ip` | IP address and gateway | — | yes |
| `radio` | airplane mode, bluetooth, NFC, location on/off | — |  |
| `rf` | mobile signal quality: RSRP, RSRQ, SINR (LTE/5G) or RSSI | — |  |
| `link` | network type incl. 5G NSA/SA and LTE-CA, carrier bandwidths | phone |  |
| `tower` | serving cell: technology, band, channel, PCI, area code | location |  |
| `ssid` | Wi-Fi name, BSSID, channel, Wi-Fi standard | location |  |
| `aps` | Wi-Fi networks nearby and the strongest | location |  |
| `data` | traffic since boot, total and mobile | — |  |
| `fs` | internal storage free / total | — | yes |
| `sd` | removable storage free / total | — |  |
| `disp` | resolution, refresh rate, density, brightness | — |  |
| `audio` | media and ring volume, audio output | — |  |
| `media` | what's playing and in which app | Shizuku |  |
| `alarm` | next alarm | — |  |
| `env` | light, pressure, altitude, temperature, humidity sensors | — |  |
| `compass` | heading, pitch and roll | — |  |
| `moon` | moon phase and days to the next full moon | — |  |
| `bt` | connected Bluetooth devices and their battery | Bluetooth |  |
| `debug` | adb, wireless debugging, developer options, USB | — |  |
| `apps` | launchable, hidden and work-profile app counts | — |  |
| `gps` | GPS fix: coordinates, altitude, accuracy, speed (GPS on while visible) | location |  |
| `gnss` | satellites used / in view per system: GPS, GLONASS, Galileo, BeiDou… | location |  |
| `sun` | sunrise, sunset and day length where you are | location |  |
| `today` | screen-on time and unlocks today | usage access |  |
| `month` | data used this month, Wi-Fi and mobile | usage access |  |
| `steps` | steps today and since boot | physical activity |  |
| `ntf` | notifications showing now and received today | notification access |  |

*Refresh every* sets how often rows update (1–10 s).

### Stream

The scrolling part at the bottom: events, newest at the bottom. Timestamps are off by default (log › stream › *timestamps*). What appears there is chosen under log › system monitor › *stream events*:

| Key | Event |
|---|---|
| `net` `power` `bat` `therm` `mem` `pkg` | network changes, charger plugged/unplugged, battery every 5%, thermal status, low memory, apps installed/removed |
| `fg` `svc` `scrn` `lock` | app switches, foreground services starting/stopping, screen on/off and unlocks (usage access) |
| `proc` `conn` `logE` `logW` | app processes starting/exiting, new connections, system errors and optionally warnings from logcat (Shizuku) |
| `dns` | which app looked up which server name (DNS monitor) |
| `ntf` | which app posted a notification; its title only if you turn *show titles* on (notification access) |

Events that happen while another app is open are collected the next time you return home, with their original times.

## 6. The system monitor

Android lets a normal app see only its own processes, so the deeper parts of the monitor use access you grant separately. Everything is optional; the status of each is shown under log › *system monitor*.

### Usage access

Settings › log › system monitor › *usage access*. Unlocks: screen-time and traffic-per-app tables, the `today` and `month` rows, and the app-switch/service/screen events.

### Shizuku

[Shizuku](https://shizuku.rikka.app/) runs a helper with the same rights as `adb shell`, started by you. With it, SysReadout shows:

- **processes** sorted by CPU or memory (`top`), with real app names
- **connections**: every open TCP/UDP connection per app, with the server's name
- **wakelocks**: what is keeping the phone awake
- **battery drain per app** since the last charge, with what used it (screen, CPU, sensors…)
- rows: **load** average, **cores** (per-core load), **temps** (every hardware temperature sensor), **media** (what's playing)
- stream: process start/exit, new connections, system errors

Setting up Shizuku:

1. Install Shizuku (from GitHub, IzzyOnDroid or Google Play).
2. Start it: on Android 11+ via *wireless debugging* following the steps in the Shizuku app (no computer needed), or once over USB with `adb`, or automatically on a rooted phone.
3. In SysReadout, settings › log › system monitor › shizuku › *grant*.

Without root, Shizuku has to be started again after every reboot.

Server names come from reverse DNS by default (*hostnames (reverse dns)*), which often gives provider names such as `…1e100.net` (Google). The DNS monitor gives the names apps actually asked for.

### DNS monitor

Settings › log › *dns monitor*. It shows which app looks up which server (`Gmail → imap.gmail.com`), and gives the connections table real host names.

How it works: SysReadout starts a VPN that carries **only DNS**. Android sends apps' lookups to a resolver address inside it; each lookup is noted (app, name, returned addresses) and passed on unchanged to your network's normal DNS server. No other traffic goes through it, and nothing is sent anywhere else. This is the only feature that uses the internet permission.

Limits:

- Android allows one VPN at a time, so it can't run together with another VPN app.
- If Private DNS is set to a specific provider (Settings › Network › Private DNS), Android sends lookups there directly and the monitor sees nothing. *Automatic* or *off* works.
- Apps with their own encrypted DNS (some browsers) bypass it.

### Notification access

Needed for the `ntf` row, the notification stream and the *notifications today* table. Notification titles are kept in memory only and shown only if you turn on *show titles*.

## 7. Appearance

Settings › appearance.

- **Presets**: *sysreadout* (default), *wasteland* (green phosphor terminal), *amber p3* (DOS amber), *paper p4* (white phosphor), *minimal*, *arcade*, *lime*. Tap to apply. *Save current as preset…* keeps your own; long-press one of yours to delete it.
- **Colours**: background, accent (prompt, cursor), backing (the tint behind menu entries).
- **Text**: clock, date, menu entries, drawer, log and banner each have their own font, size, bold, letter spacing, case (as-is / lower / UPPER) and colour, with a live preview.
- **Fonts**: 11 bundled (system mono/sans, JetBrains Mono, IBM Plex Mono, Space Mono, Share Tech Mono, VT323, Px437 IBM VGA, Major Mono Display, Press Start 2P, Silkscreen) plus *import .ttf / .otf* for your own. Long-press an imported font to delete it.
- **Menu entries › prefix**: text in front of every entry, such as `> `, `$ `, `C:\> ` or a number (`[1]`).

### CRT effects

Appearance › *crt effects*: glow, scanlines, vignette, curvature and colour fringe (the last two need Android 13+), plus animated flicker and grain. *Menu, clock and date too* applies them to the whole home screen instead of just the log. Flicker and grain redraw the screen about 20 times a second while it's visible, so they cost battery.

## 8. Lock screen

Settings › *lock screen*:

- **leave alone** (default): SysReadout doesn't touch your lock-screen wallpaper.
- **your image**: pick a picture for the lock screen only.
- **log snapshot**: each time the screen turns off (at most once a minute), SysReadout draws the log, with your theme and CRT effects, as the lock-screen wallpaper. *Update now* redraws it immediately.

Android's own clock and notifications are drawn on top. Only the lock screen is changed; the home screen is always SysReadout's own drawing.

## 9. Battery use

- The log runs **only while the home screen is visible**. In another app or with the screen off it uses nothing.
- Typical cost while visible is a few percent of one CPU core; more with every Shizuku table on at a 2-second refresh.
- Rows that turn hardware on say so: `gps` and `gnss` keep GPS active while visible; `env` and `compass` read sensors once a second.
- Flicker and grain are the only effects that redraw continuously.

The *battery drain per app* table (Shizuku) shows SysReadout's own share too.

## 10. Troubleshooting

| Symptom | Fix |
|---|---|
| A row shows `needs … permission` | Tap the row in settings › log to switch it on again and allow the permission, or grant it in Android's app settings. |
| Monitor tables are missing | They appear only when their source is available: check *usage access* and *shizuku* under settings › log › system monitor. |
| `shizuku installed, not running` after a reboot | Start Shizuku again in its app. |
| The DNS monitor switches itself off | Another VPN took over, or Private DNS is set to a provider. |
| `wifi` shows no network name | The `ssid` row needs the location permission *and* location switched on. |
| Lock-screen snapshot doesn't show | Some phones override lock-screen wallpapers in their own theme settings; set the lock screen there to use the wallpaper. |
| Text over the log is hard to read | Use the *frosted* or *highlight* entry style, or turn down CRT glow. |

## 11. Privacy

SysReadout collects nothing and sends nothing: no ads, analytics or tracking. Everything it shows is read on the phone and kept in memory while the home screen is visible. The only network use is the optional DNS monitor, which passes your apps' own lookups to your normal DNS server, and optional reverse-DNS lookups of connection addresses (made by Shizuku's helper process).
