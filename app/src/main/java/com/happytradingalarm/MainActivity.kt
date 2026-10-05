private fun startBackgroundMonitoring() {

    val intent =
        android.content.Intent(
            this,
            PriceMonitorService::class.java
        ).apply {
            action =
                PriceMonitorService.ACTION_START
        }

    if (
        android.os.Build.VERSION.SDK_INT >=
        android.os.Build.VERSION_CODES.O
    ) {

        startForegroundService(intent)

    } else {

        startService(intent)
    }
}

private fun stopBackgroundMonitoring() {

    val intent =
        android.content.Intent(
            this,
            PriceMonitorService::class.java
        ).apply {
            action =
                PriceMonitorService.ACTION_STOP
        }

    startService(intent)
}
