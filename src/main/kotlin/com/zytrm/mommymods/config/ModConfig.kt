package com.zytrm.mommymods.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.zytrm.mommymods.MommyMods
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

const val DEFAULT_FINISHER_MESSAGE = "[MM] Jawbus {health}% - L5 finisher: {players}"

internal fun isInstallationId(value: String): Boolean = runCatching {
    val uuid = UUID.fromString(value)
    uuid.variant() == 2 && uuid.version() in 1..5 && uuid.toString().equals(value, ignoreCase = true)
}.getOrDefault(false)

internal fun applyMissingSettingDefaults(values: MommySettings, existingKeys: Set<String>) {
    if ("bobberVisibility" !in existingKeys) values.bobberVisibility = "Line Only"
    if ("partyReadinessHud" !in existingKeys) values.partyReadinessHud = true
    if ("jawbusFinisherEnabled" !in existingKeys) values.jawbusFinisherEnabled = true
    if ("jawbusFinisherHealth" !in existingKeys) values.jawbusFinisherHealth = 20
    if ("jawbusFinisherPartyMessage" !in existingKeys) values.jawbusFinisherPartyMessage = true
    if ("jawbusFinisherMessage" !in existingKeys) values.jawbusFinisherMessage = DEFAULT_FINISHER_MESSAGE
    if ("lastJawbusHookedAt" !in existingKeys) values.lastJawbusHookedAt = 0L
    if ("rareDropScreenshots" !in existingKeys) values.rareDropScreenshots = true
    if ("screenshotRngDrops" !in existingKeys) values.screenshotRngDrops = true
    if ("screenshotDyesAndVials" !in existingKeys) values.screenshotDyesAndVials = true
    if ("screenshotRareRewards" !in existingKeys) values.screenshotRareRewards = true
}

data class PartyCommandSetting(
    var enabled: Boolean = true,
    var alias: String = "",
)

data class MommySettings(
    var installationId: String = "",
    var hideFishingLine: Boolean = true,
    var bobberVisibility: String = "Line Only",
    var louderCatch: Boolean = true,
    var catchSound: String = "Experience",
    var catchVolume: Float = 4.0f,
    var catchPitch: Float = 1.0f,
    var fishingPartyHelper: Boolean = true,
    var autoKick: Boolean = false,
    var kickNoLootingV: Boolean = true,
    var kickCantJawbus: Boolean = true,
    var partyReadinessHud: Boolean = true,
    var jawbusFinder: Boolean = true,
    var deathMessageDetection: Boolean = true,
    var jawbusFinisherEnabled: Boolean = true,
    var jawbusFinisherHealth: Int = 20,
    var jawbusFinisherPartyMessage: Boolean = true,
    var jawbusFinisherMessage: String = DEFAULT_FINISHER_MESSAGE,
    var lastJawbusHookedAt: Long = 0L,
    var rareDropScreenshots: Boolean = true,
    var screenshotRngDrops: Boolean = true,
    var screenshotDyesAndVials: Boolean = true,
    var screenshotRareRewards: Boolean = true,
    var mediaPlayer: Boolean = true,
    var mediaVolume: Float = 0.8f,
    var mediaHud: Boolean = true,
    var mediaAutoplay: Boolean = true,
    var partyCommandsEnabled: Boolean = true,
    var partyCommandSettings: MutableMap<String, PartyCommandSetting> = mutableMapOf(),
    var clickGuiSound: Boolean = true,
    var clickGuiAccent: Int = 0xFFFF4F91.toInt(),
    var clickGuiSorting: String = "No Sorting",
    var jawbusHudCenterX: Float = -1f,
    var jawbusHudCenterY: Float = -1f,
    var mediaHudCenterX: Float = -1f,
    var mediaHudCenterY: Float = -1f,
)

object ModConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path = FabricLoader.getInstance().configDir.resolve("mommymods.json")

    @Volatile
    var values = MommySettings()
        private set

    fun load() {
        var existingKeys: Set<String>? = null
        values = runCatching {
            if (!Files.exists(path)) MommySettings()
            else Files.newBufferedReader(path).use { reader ->
                val root = JsonParser.parseReader(reader).asJsonObject
                existingKeys = root.keySet()
                gson.fromJson(root, MommySettings::class.java) ?: MommySettings()
            }
        }.onFailure { MommyMods.logger.warn("Could not load configuration", it) }
            .getOrElse { MommySettings() }
        existingKeys?.let { applyMissingSettingDefaults(values, it) }
        if (!isInstallationId(values.installationId)) values.installationId = UUID.randomUUID().toString()
        if (values.bobberVisibility !in setOf("Line Only", "Hide Others", "Hide All")) {
            values.bobberVisibility = "Line Only"
        }
        if (values.jawbusFinisherMessage.isNullOrBlank()) values.jawbusFinisherMessage = DEFAULT_FINISHER_MESSAGE
        values.jawbusFinisherHealth = values.jawbusFinisherHealth.coerceIn(5, 50)
        values.lastJawbusHookedAt = values.lastJawbusHookedAt.coerceAtLeast(0L)
        values.mediaVolume = values.mediaVolume.coerceIn(0f, 1f)
        if (values.clickGuiSorting !in setOf("A-Z Sorting", "Width Sorting", "No Sorting")) {
            values.clickGuiSorting = "No Sorting"
        }
        save()
    }

    fun partyCommandSetting(id: String, defaultAlias: String): PartyCommandSetting =
        values.partyCommandSettings.getOrPut(id) { PartyCommandSetting(alias = defaultAlias) }

    @Synchronized
    fun save() {
        runCatching {
            Files.createDirectories(path.parent)
            val temporary = path.resolveSibling("${path.fileName}.tmp")
            Files.newBufferedWriter(temporary).use { gson.toJson(values, it) }
            runCatching {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.getOrElse {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        }.onFailure { MommyMods.logger.warn("Could not save configuration", it) }
    }
}
