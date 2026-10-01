# Nobius

A native Android starter for the Nobius 360° relaxation experience.

## Project status

This repository started empty. The app now contains a Compose experience shell with an animated alien landscape fallback, touch look controls, hotspot card, 30-second Focus Mode, intensity control, optional 360° video playback, spatialization hints, and timestamped haptics.

## Run

Open this repository in Android Studio (Android Gradle Plugin 8.7.3, JDK 17, Android SDK 35) and run the `app` configuration. The project uses Gradle 8.9.

## Optional media

Place licensed media here:

- `app/src/main/res/raw/nobius_360.mp4`: 360° equirectangular video, ideally 4K HEVC with device-supported HDR metadata.
- `app/src/main/res/raw/nobius_ambisonic.m4a`: optional spatial ambience mix. A stereo binaural master is the most compatible fallback.

The player uses Media3's spherical video surface when the 360° video resource is present. Without it, the generated landscape remains interactive. Playback and HDR formats depend on the Android decoder, display, and source encoding. Atmos passthrough depends on the output route and Android audio stack; otherwise the track is spatialized when supported or played as a compatible stereo mix. The app does not force Bluetooth codecs.

## Haptics

Timestamped events are in `app/src/main/assets/haptics.json`. The app scales event amplitude with the intensity slider and respects Android vibrator availability and API capabilities.

## Asset production

The repository does not include production 4K video or a 10-minute Atmos master. Use original or properly licensed assets, and validate the final encodes on the target OnePlus device.