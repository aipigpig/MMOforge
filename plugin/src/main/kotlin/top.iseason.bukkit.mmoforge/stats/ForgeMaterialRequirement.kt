package top.iseason.bukkit.mmoforge.stats

import top.iseason.bukkittemplate.debug.warn

data class ForgeMaterialRequirement(
    val type: String,
    val id: String,
    val amount: Int = 1,
) {
    fun matches(itemType: String, itemId: String?): Boolean {
        if (itemId == null) return false
        return type.equals(itemType, true) && id.equals(itemId, true)
    }

    companion object {
        fun parse(raw: String): ForgeMaterialRequirement? {
            val split = raw.split(':')
            if (split.size !in 2..3) return null
            val type = split[0].trim()
            val id = split[1].trim()
            val amount = if (split.size == 3) split[2].trim().toIntOrNull() ?: return null else 1
            if (type.isEmpty() || id.isEmpty() || amount <= 0) return null
            return ForgeMaterialRequirement(type, id, amount)
        }
    }
}

fun parseForgeMaterialRequirements(values: List<String>, source: String): List<ForgeMaterialRequirement> {
    val requirements = mutableListOf<ForgeMaterialRequirement>()
    values.forEachIndexed { index, raw ->
        val requirement = ForgeMaterialRequirement.parse(raw)
        if (requirement == null) {
            warn("Invalid breakthrough material config '$raw' at $source[$index]. Expected type:id or type:id:amount with amount > 0.")
            return@forEachIndexed
        }
        requirements += requirement
    }
    return requirements
}

typealias ForgeMaterialMap = LinkedHashMap<Int, List<ForgeMaterialRequirement>>
