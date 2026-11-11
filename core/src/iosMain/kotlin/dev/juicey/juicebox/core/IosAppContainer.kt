package dev.juicey.juicebox.core

import dev.juicey.juicebox.core.model.normalizeServerUrl
import dev.juicey.juicebox.core.net.JuiceboxApi
import dev.juicey.juicebox.core.net.PersistentCookieStorage
import dev.juicey.juicebox.core.net.createJuiceboxHttpClient
import dev.juicey.juicebox.core.storage.LibraryStore
import dev.juicey.juicebox.core.storage.SettingsStore
import dev.juicey.juicebox.core.storage.UserDefaultsKeyValueStore
import platform.Foundation.NSUserDefaults

/**
 * The iOS analogue of the Android AppContainer: one object graph built over
 * the default user defaults suite, shared by every SwiftUI screen.
 */
class IosAppContainer(version: String) {

    private val keyValue = UserDefaultsKeyValueStore(NSUserDefaults.standardUserDefaults)

    val settingsStore = SettingsStore(keyValue)
    val libraryStore = LibraryStore(keyValue)
    val cookies = PersistentCookieStorage(keyValue)

    val clientInfo = ClientInfo(
        name = "ios",
        userAgent = "Juicebox-iOS/$version",
    )

    private val httpClient = createJuiceboxHttpClient(
        engine = platformHttpClientEngine(),
        cookies = cookies,
        client = clientInfo,
    )

    val api = JuiceboxApi(httpClient) {
        normalizeServerUrl(settingsStore.current().serverUrl)
    }

    val core = JuiceboxCore(api, cookies, clientInfo, settingsStore, libraryStore)
}
