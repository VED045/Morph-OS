package ai.morphos.launcher.domain

import kotlinx.serialization.json.*
import java.util.UUID

object LocalProposal {
    private fun objectFrom(raw: String): JsonObject {
        require(raw.length <= 8192)
        val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return Json.parseToJsonElement(clean).jsonObject
    }
    fun design(raw: String, current: ThemeSpec, revision: Int, model: String): DesignProposal {
        val response = objectFrom(raw)
        require(response.keys == setOf("explanation", "patch"))
        val explanationValue = response.getValue("explanation").jsonPrimitive
        require(explanationValue.isString)
        val explanation = explanationValue.content
        require(explanation.length in 1..500)
        val patch = response.getValue("patch").jsonObject
        patch.forEach { (key, value) ->
            if (key in setOf("columns", "cornerRadius", "iconRadius", "spacing", "dockOpacity", "monochromeIcons", "gradient", "reducedMotion")) require(value is JsonPrimitive && !value.isString)
            else if (key in setOf("light", "dark")) require(value is JsonObject && value.values.all { it is JsonPrimitive && it.isString })
            else require(value is JsonPrimitive && value.isString)
        }
        DesignEngine.apply(current, patch)
        return DesignProposal(explanation, patch, revision, "device", model)
    }
    fun widget(raw: String, revision: Int, model: String): WidgetProposal {
        val response = objectFrom(raw)
        require(response.keys == setOf("explanation", "widget"))
        val explanationValue = response.getValue("explanation").jsonPrimitive
        require(explanationValue.isString)
        val explanation = explanationValue.content
        require(explanation.length in 1..500)
        val widget = response.getValue("widget").jsonObject
        require(widget.keys.all { it in setOf("kind", "title", "shape", "durationSeconds") })
        val values = widget.toMutableMap().apply { put("id", JsonPrimitive("w_" + UUID.randomUUID().toString().take(8))) }
        return WidgetProposal(explanation, WidgetRegistry.decode(JsonObject(values).toString()), revision, "device", model)
    }
}
