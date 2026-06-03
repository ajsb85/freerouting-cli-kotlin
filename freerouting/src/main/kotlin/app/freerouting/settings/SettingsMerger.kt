package app.freerouting.settings

import app.freerouting.logger.FRLogger
import java.util.ArrayList
import java.util.Comparator

class SettingsMerger : Cloneable {

    private val sources: MutableList<SettingsSource> = ArrayList()

    constructor(vararg sources: SettingsSource) {
        this.addOrReplaceSources(*sources)
    }

    constructor(sources: List<SettingsSource>) {
        this.addOrReplaceSources(sources)
    }

    fun addOrReplaceSources(vararg sources: SettingsSource) {
        addOrReplaceSources(sources.toList())
    }

    fun addOrReplaceSources(newSources: List<SettingsSource>) {
        for (newSource in newSources) {
            var replaced = false
            for (i in sources.indices) {
                val existingSource = sources[i]
                if (existingSource.javaClass == newSource.javaClass
                    || existingSource.javaClass.isAssignableFrom(newSource.javaClass)) {
                    sources[i] = newSource
                    replaced = true
                    break
                }
            }
            if (!replaced) {
                sources.add(newSource)
            }
        }
    }

    fun merge(): RouterSettings {
        if (sources.isEmpty()) {
            FRLogger.warn("No settings sources provided, using defaults")
            return RouterSettings()
        }

        val sortedSources = ArrayList(sources)
        sortedSources.sortWith(Comparator.comparingInt { it.getPriority() })

        var mergedSettings: RouterSettings? = null

        FRLogger.debug("Merging settings from ${sortedSources.size} sources:")

        for (source in sortedSources) {
            val sourceSettings = source.getSettings()

            if (sourceSettings == null) {
                FRLogger.debug("  - ${source.getSourceName()} (priority ${source.getPriority()}): no settings")
                continue
            }

            if (mergedSettings == null) {
                mergedSettings = sourceSettings.clone()
                FRLogger.debug("  - ${source.getSourceName()} (priority ${source.getPriority()}): base settings")
            } else {
                val fieldsChanged = mergedSettings.applyNewValuesFrom(sourceSettings)
                FRLogger.debug("  - ${source.getSourceName()} (priority ${source.getPriority()}): $fieldsChanged fields changed")
            }
        }

        val finalSettings = mergedSettings ?: RouterSettings().also {
            FRLogger.warn("No valid settings found in any source, using defaults")
        }

        finalSettings.validate()

        FRLogger.debug("Settings merged successfully from ${sortedSources.size} source(s)")
        return finalSettings
    }

    public override fun clone(): SettingsMerger {
        return SettingsMerger(this.sources)
    }
}
