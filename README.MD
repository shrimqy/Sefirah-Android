[//]: # (<p align="center">)

[//]: # (  <img alt="Files hero image" src="./.github/readme-images/ReadmeHero.png" />)

[//]: # (</p>)

# Sefirah - Android

**Sefirah** (Yes I am renaming it) is an opinionated phone link alternative designed to enhance your workflow with seamless clipboard and notification sharing between your Windows PC and Android device. It's tailored for users who want a straightforward and efficient way to keep their devices in sync.

## Features

- **Clipboard Sync**: Seamlessly share clipboard content between your Android device and Windows PC.
- **Media Control**: Control media playback and volume of your PC from android. 
- **Notification Sync**: Allows toasting the notifications from your android in desktop.
- **Storage Integration**: Integrate your android storage with your pc to access files from your phone.

## Limitations

- **Notification Sync**:
    - Due to Android's restrictions, sensitive notifications are not shown from android 15 onwards.

## How to Use

1. **Download and Install the [Windows app](https://github.com/shrimqy/Sefirah)

2. **Setting Up**:
    - Permissions: Allow the app to post notifications, location access, and notification access (Allow restricted access from App Info only after trying to grant notification access).
    - Ensure both your Android device and Windows PC are connected to the same network.
    - Launch the app on your Windows PC.
    - Try to add a new device from the device card or device tab (Pull to refresh if it doesn't show any devices).

3. **Clipboard Sharing**:
    - Copy content on the desktop and it will automatically sync with your android (That is if you have enabled it from the settings).
    - To share clipboard from android you will have to manually sent it through the share sheet that shows after you perform a copy action).
4. **File Transfer**:
    - Use the share sheet from android/windows and select the app to share any files between the devices. 

## Screenshots

![screenshots of app](./.github/readme-images/Screenshot.png)

## Roadmap

- Notification Actions 
- Filter Notifications by app 
- Customizable startup settings 
- Add "Copy to Clipboard" action in the notification 
- Automatic Clipboard Sync 
- Storage Integration 
- Configure clipboard to work automatically till android 10 
- Allow using the device as a webcam 
- Windows Widget Support 
- Calls and SMS Feature 


## Acknowledgements
- **Clipboard Detection with Accessibility Service**: [XClipper](https://github.com/KaustubhPatange/XClipper)
- **Sftp Server**: [Kdeconnect-android](https://github.com/KDE/kdeconnect-android)
