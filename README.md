# Noteify

Noteify is an offline Android deadline notifier. Create an activity, choose its deadline, and receive local reminders one day and/or one hour before it is due.

## Command-line setup on Windows

Android Studio is not required. Install these separately:

1. Install JDK 17 and make sure `java --version` works.
2. Download the Android **Command-line tools for Windows** from the Android developer website and extract them to:
	`C:\Android\cmdline-tools\latest\`
3. Set these environment variables:

	```bat
	setx ANDROID_HOME "C:\Android"
	setx PATH "%PATH%;C:\Android\platform-tools;C:\Android\cmdline-tools\latest\bin"
	```

4. Open a new terminal and install the required SDK packages:

	```bat
	sdkmanager.bat --licenses
	sdkmanager.bat "platform-tools" "platforms;android-35" "build-tools;35.0.0"
	```

5. Clone this repository and open a terminal in the cloned `Noteify` folder.

## Build the APK

From the project root, run `gradlew.bat assembleDebug` on Windows or `./gradlew assembleDebug` on macOS/Linux.

The generated APK is located at `app/build/outputs/apk/debug/app-debug.apk`.

## Build and install directly to a phone

On the phone, enable **Developer options** and **USB debugging**, then connect it by USB and accept the debugging prompt. Confirm that it is detected:

```bat
adb devices
```

Build and install the debug APK:

```bat
gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Launch the app from the phone. Android 13+ will ask for notification permission the first time it opens.

If the wrapper files are missing from a fresh clone, run `gradle wrapper` once from the project root, then use the wrapper commands above.

All activity data is stored on-device. No account, server, or network permission is used. Android 13+ will ask for notification permission on first launch.

## Browser PWA

The `web` folder contains a browser version that can be served from any HTTPS static host, including GitHub Pages. Open its URL in a modern browser and it works without a custom installer; the browser may optionally offer **Add to Home screen**. Activities are stored in browser-local storage and the app shell is cached for offline use after the first visit.

To test locally, serve the `web` folder over HTTP rather than opening `index.html` directly. For example, with Node.js installed:

```bat
npx serve web
```

Browser reminders are available while the page is open and notification permission is granted. Reliable reminders while the browser is completely closed require a push-notification service; the native Android app remains the stronger option for scheduled offline alarms.
