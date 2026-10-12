package ai.morphos.launcher.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class DesignRequest(val prompt: String, val current: ThemeSpec, val revision: Int, val route: String = "fast")
@Serializable data class ImageDesignRequest(val prompt: String, val current: ThemeSpec, val revision: Int, val imageBase64: String, val route: String = "complex")
@Serializable
data class DesignProposal(val explanation: String, val patch: JsonObject, val revision: Int, val source: String, val model: String, val route: String = "fast")

object DesignEngine {
    private val allowed = setOf("light", "dark", "columns", "drawer", "cornerRadius", "iconRadius", "monochromeIcons", "typography", "spacing", "dockOpacity", "gradient", "reducedMotion", "clockStyle", "homeStyle", "wallpaperStyle")
    fun apply(current: ThemeSpec, patch: JsonObject): ThemeSpec {
        require(patch.isNotEmpty() && patch.size <= allowed.size && patch.keys.all { it in allowed }) { "Unsupported design action" }
        require(patch.values.none { it == JsonNull })
        val values = Json.encodeToJsonElement(current).jsonObject.toMutableMap()
        values.putAll(patch)
        values["schemaVersion"] = JsonPrimitive(2)
        return ThemeValidator.decode(JsonObject(values).toString())
    }
    // Explicit offline commands, not pretend inference. Unknown instructions go to Nebius.
    fun local(prompt: String, revision: Int): DesignProposal? {
        val patch = when (prompt.trim().lowercase()) {
            "use monochrome icons" -> "{\"monochromeIcons\":true}"
            "use color icons" -> "{\"monochromeIcons\":false}"
            "use a list drawer" -> "{\"drawer\":\"LIST\"}"
            "use a grid drawer" -> "{\"drawer\":\"GRID\"}"
            "use a dot clock" -> "{\"clockStyle\":\"DOT\"}"
            "use a minimal black theme" -> "{\"gradient\":false,\"wallpaperStyle\":\"SOLID\",\"monochromeIcons\":true,\"dark\":{\"background\":\"#080808\",\"surface\":\"#181818\",\"foreground\":\"#FFFFFF\",\"muted\":\"#B4B4B4\",\"accent\":\"#E94141\"}}"
            else -> return null
        }
        return DesignProposal("Only the requested fields change. Everything else is preserved.", Json.parseToJsonElement(patch).jsonObject, revision, "local", "Offline command")
    }
}
