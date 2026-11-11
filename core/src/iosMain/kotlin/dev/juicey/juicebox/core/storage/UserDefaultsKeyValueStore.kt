package dev.juicey.juicebox.core.storage

import platform.Foundation.NSUserDefaults

/** [KeyValueStore] on the app's default suite; keys are the same across launches. */
class UserDefaultsKeyValueStore(
    private val defaults: NSUserDefaults,
) : KeyValueStore {

    override fun read(key: String): String? = defaults.stringForKey(key)

    override fun write(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    override fun delete(key: String) {
        defaults.removeObjectForKey(key)
    }
}
