``
adb shell dpm remove-active-admin com.example.anti_procrastinator2000/.MyDeviceAdminReceiver && adb uninstall com.example.anti_procrastinator2000 && adb install -t -r app/build/outputs/apk/debug/app-debug.apk && adb shell dpm set-device-owner com.example.anti_procrastinator2000/.MyDeviceAdminReceiver && adb logcat | grep AntiProcrastinator2000
``
