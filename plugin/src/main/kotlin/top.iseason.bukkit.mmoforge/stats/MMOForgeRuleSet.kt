package top.iseason.bukkit.mmoforge.stats

import kotlin.math.min

data class MMOForgeRuleSet(
    val maxRefine: Int,
    val maxLimit: Int,
    val maxForge: Int,
    val refineGain: ForgeParserMap,
    val limitGain: ForgeParserMap,
    val forgeGain: ForgeParserMap,
    val forgeType: List<String>,
    val limitType: ForgeMaterialMap,
) {
    fun getCurrentMaxForge(limit: Int): Int = min((limit + 1) * top.iseason.bukkit.mmoforge.config.MainConfig.LimitRate, maxForge)
}
