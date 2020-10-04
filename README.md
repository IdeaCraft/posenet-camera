# posenet-camera

Flutter plugin to show camera preview, run PoseNet on camera frames and send output as a stream.

*Note*: This plugin is still under development, and some APIs might not be available yet.

## Features:

* Display live camera preview in a widget.
* Get Pose co-ordinates in the form of Dart Stream.
* Camera and Posenet runs on different threads.

## Installation

First, add `posenet-camera` as a [git dependency in your pubspec.yaml file](https://flutter.io/using-packages/).

```
dependencies:
  posenet-camera:
    git:
      url: git://github.com/IdeaCraft/posenet-camera.git
```

### iOS

Add two rows to the `ios/Runner/Info.plist`:

* one with the key `Privacy - Camera Usage Description` and a usage description.
* and one with the key `Privacy - Microphone Usage Description` and a usage description.

Or in text format add the key:

```xml
<key>NSCameraUsageDescription</key>
<string>Can I use the camera please?</string>
<key>NSMicrophoneUsageDescription</key>
<string>Can I use the mic please?</string>
```

### Android

Change the minimum Android sdk version to 21 (or higher) in your `android/app/build.gradle` file.

```
minSdkVersion 21
```


## Learn more about Flutter plugins & packages

[plug-in package](https://flutter.dev/developing-packages/) is
a specialized package that includes platform-specific implementation code for
Android and/or iOS.

For help getting started with Flutter, view the
[online documentation](https://flutter.dev/docs), which offers tutorials, 
samples, guidance on mobile development, and a full API reference.

## Contribution

*Note*: This plugin is still under development, and some APIs might not be available yet.

[Feedback, Issues](https://github.com/IdeaCraft/posenet-camera/issues) and
[Pull Requests](https://github.com/IdeaCraft/posenet-camera/pulls) are most welcome!
