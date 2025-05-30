<p align="center">

  <img alt="Files hero image" src="./fastlane/metadata/android/en-US/images/featureGraphic.png" />

</p>

# Sefirah - Android

**Sefirah** is an opinionated phone link alternative designed to enhance your workflow with seamless clipboard and notification sharing between your Windows PC and Android device. 

If you have any feature requests as a whole or a bug report, open the issue in the repo for the Windows app, unless you're sure that this only concerns the Android version.

**[Windows app](https://github.com/shrimqy/Sefirah)**

## Features

- **Clipboard Sharing**: Seamlessly share clipboard content between your Android device and Windows PC.
- **Media Control**: Control media playback and volume of your PC from android.
- **File Sharing**: Share files between your devices easily.
- **Storage Integration**: Integrate your Android storage into the Windows Explorer.
- **Notification**: Allows toasting the notifications from your android in desktop.
- **Screen mirroring**: Mirror and control the Android device via scrcpy.

## Limitations

### **Notification Sync**
- Due to Android's restrictions, sensitive notifications are no longer visible from Android 15 onwards.
- To work around this limitation, you can grant the necessary permission using ADB. Run the following command:

  ```sh
  adb shell appops set com.castle.sefirah RECEIVE_SENSITIVE_NOTIFICATIONS allow

## Installation

### Windows App
<p align="left">
  <!-- Store Badge -->
  <a style="text-decoration:none" href="https://apps.microsoft.com/detail/9PJV6D1JPG0H?launch=true&mode=full" target="_blank" rel="noopener noreferrer">
    <picture>
      <source media="(prefers-color-scheme: light)" srcset=".github/./readme-images/StoreBadge-dark.png" width="220" />
      <img src=".github/./readme-images/StoreBadge-light.png" width="200" />
    </picture>
  </a>
</p>

### Android App

[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" width="220">](https://apt.izzysoft.de/fdroid/index/apk/com.castle.sefirah)

## How to Use

1. **Download and Install the [Android app](https://github.com/shrimqy/Sefirah-Android)**

2. **Setting Up**:
   - On the Android device, allow the necessary permissions on the onboarding page. (**Note:** Allow restricted settings from App Info after attempting to grant notification access or accessibility permission, as Android blocks side-loaded apps from requesting sensitive permissions.)
   - Ensure both your Android device and Windows PC are connected to the same network.
   - Launch the app on your Windows PC and wait for the devices to show up on both.
   - Initiate the connection on your Android device using either manual connect or auto connect. Manual connect is faster, while auto connect takes a bit more time to determine which IP address works for you.
   - Once the connection is initiated, Windows will receive a pop-up to accept or decline the connection. Ensure that the keys match on both devices.
   - After the authentication is done, you should be navigated to the home screens on both devices, wait a bit for the notifications on Windows to load up for the first time.
3. **Clipboard Sharing**:
   - When you copy content on your desktop, it will automatically sync with your Android device (provided you have enabled this feature in the settings). If you have also enabled image syncing, images should be sent as well. **Note:** You must enable the 'add received images to clipboard' option for image syncing to work.
   - To automatically share the clipboard, enable the corresponding preference in the settings (accessibility permission is required). **Note:** This method may not work in every scenario.
   - To manually share the clipboard, there are two primary methods: using the persistent device status notification or the share sheet.
4. **File Transfer**:
   - Use the share sheet on your Android or Windows device and select the app to share files between the devices.
5. **Android Storage**:
   - You would need Android 11 or higher
   - Enable storage access permission in Android app's settings and the desktop app will create a link to the Android storage in the File Explorer.
   - **Note:** This feature is still a bit experimental and may not work on all Windows versions especially older versions of Windows 10 and other unofficial debloated Windows 11 versions.
   - **WARNING**: DO NOT set the remote storage location to a pre-existing folder as it will delete the contents of that folder.
6. **SMS Texting**:
   - Grant all the permissions required from the permissions page in Android. After that, reconnect and the messages should appear in the Messages tab in the Desktop app. You can now view and send texts as sms. You can also switch sims if your device has dual sims. Attachments have not been implemented and the messages are not cached to local storage  (yet).
7. **Screen Mirroring using [Scrcpy](https://github.com/Genymobile/scrcpy)**:
   - You would need to [download](https://github.com/Genymobile/scrcpy/releases), extract and set the scrcpy location in App settings.
   - You can specify your preferences for the scrcpy to launch with in the screen mirroring section.
   - For initiating screen mirroring, the easiest way would be to plug your device through usb and start it by clicking on Phone frame.
   - Sefirah will try to connect to your device if the default tcpip port is open for subsequent connections.
   - If you have any doubts or problems in connecting scrcpy, refer [scrcpy docs](https://github.com/Genymobile/scrcpy/blob/master/doc/connection.md) and [Scrcpy FAQ](https://github.com/Genymobile/scrcpy/blob/master/FAQ.md).
   - If you like their project, please consider supporting the author [rom1v](https://blog.rom1v.com/about/#support-my-open-source-work)
## Screenshots

![screenshots of app](./.github/readme-images/Screenshot.png)

## Contribute

Feel free to open an issue if you want to report a bug, give feedback, or ask a question. Pull requests are very welcome!

If you wish to know any specific details or questions hit me up on [the Discord server](https://discord.gg/MuvMqv4MES)
or email, I would be happy to help.

## Special Thanks
- **Clipboard Detection with Accessibility Service**: [XClipper](https://github.com/KaustubhPatange/XClipper)
- **Sftp Server**: [Kdeconnect-android](https://github.com/KDE/kdeconnect-android)
