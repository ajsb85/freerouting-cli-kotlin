package app.freerouting.management.analytics

import java.util.Locale
import java.util.UUID

object FRAnalytics {
    @JvmStatic
    fun setAccessKey(libraryVersion: String?, key: String?) {}

    @JvmStatic
    fun setUserId(userId: String?, userEmail: String?) {}

    @JvmStatic
    fun identify() {}

    @JvmStatic
    fun setAppLocation(windowClassName: String?, windowTitle: String?) {}

    @JvmStatic
    fun buttonClicked(buttonClassName: String?, buttonText: String?) {}

    @JvmStatic
    fun setEnabled(enabled: Boolean) {}

    @JvmStatic
    fun appStarted(
        freeroutingVersion: String?, freeroutingBuildDate: String?, commandLineArguments: String?,
        osName: String?, osArchitecture: String?, osVersion: String?, javaVersion: String?,
        javaVendor: String?, systemLanguage: Locale?, guiLanguage: Locale?, cpuCoreCount: Int,
        ramAmount: Long, host: String?, width: Int, height: Int, dpi: Int
    ) {}

    @JvmStatic
    fun appClosed() {}

    @JvmStatic
    fun autorouterStarted() {}

    @JvmStatic
    fun autorouterFinished() {}

    @JvmStatic
    fun routeOptimizerStarted() {}

    @JvmStatic
    fun routeOptimizerFinished() {}

    @JvmStatic
    fun fileLoaded(fileFormat: String?, fileDetails: String?) {}

    @JvmStatic
    fun boardLoaded(hostName: String?, hostVersion: String?, layerCount: Int, componentCount: Int, netCount: Int) {}

    @JvmStatic
    fun fileSaved(fileFormat: String?, fileDetails: String?) {}

    @JvmStatic
    fun exceptionThrown(localizedMessage: String?, e: Throwable?) {}

    @JvmStatic
    fun apiEndpointCalled(apiMethod: String?, requestBody: String?, responseBody: String?) {}

    @JvmStatic
    fun apiEndpointCalled(apiMethod: String?, requestBody: String?, responseBody: String?, userId: UUID?) {}
}
