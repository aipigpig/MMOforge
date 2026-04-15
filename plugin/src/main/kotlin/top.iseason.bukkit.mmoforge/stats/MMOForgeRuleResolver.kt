package top.iseason.bukkit.mmoforge.stats

import io.lumine.mythic.lib.api.item.NBTItem
import net.Indyuce.mmoitems.MMOItems
import net.Indyuce.mmoitems.api.Type
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem
import net.Indyuce.mmoitems.api.item.template.MMOItemTemplate
import org.bukkit.inventory.ItemStack
import top.iseason.bukkit.mmoforge.config.MainConfig

object MMOForgeRuleResolver {

    fun resolve(itemStack: ItemStack?): MMOForgeRuleSet? {
        if (itemStack == null || itemStack.type.isAir) return null
        val nbtItem = NBTItem.get(itemStack) ?: return null
        return resolve(nbtItem)
    }

    fun resolve(nbtItem: NBTItem?): MMOForgeRuleSet? {
        if (nbtItem == null || !nbtItem.hasType()) return null
        val type = Type.get(nbtItem.type) ?: return null
        val id = nbtItem.getString("MMOITEMS_ITEM_ID") ?: return null
        val template = MMOItems.plugin.templates.getTemplate(type, id)
        return resolve(template)
    }

    fun resolve(mmoItem: MMOItem?): MMOForgeRuleSet? {
        if (mmoItem == null) return null
        val template = MMOItems.plugin.templates.getTemplate(mmoItem.type, mmoItem.id)
        return resolve(template)
    }

    fun resolve(template: MMOItemTemplate?): MMOForgeRuleSet {
        val templateData = template?.baseItemData?.get(MMOForgeStat) as? MMOForgeData
        return MMOForgeRuleSet(
            maxRefine = templateData?.maxRefine ?: MainConfig.MAX_REFINE,
            maxLimit = templateData?.maxLimit ?: MainConfig.MAX_LIMIT,
            maxForge = templateData?.maxForge ?: MainConfig.MAX_LIMIT * MainConfig.LimitRate,
            refineGain = templateData?.refineGain ?: MainConfig.refineGain,
            limitGain = templateData?.limitGain ?: MainConfig.limitGain,
            forgeGain = templateData?.forgeGain ?: MainConfig.forgeGain,
            forgeType = templateData?.forgeType?.map { it.trim() }?.filter { it.isNotEmpty() } ?: MainConfig.forgeType,
            limitType = templateData?.limitType ?: MainConfig.limitType,
        )
    }
}
