package top.iseason.bukkit.mmoforge.ui

import io.lumine.mythic.lib.api.item.NBTItem
import net.Indyuce.mmoitems.ItemStats
import net.Indyuce.mmoitems.MMOItems
import net.Indyuce.mmoitems.api.Type
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem
import net.Indyuce.mmoitems.stat.data.DoubleData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import top.iseason.bukkit.mmoforge.config.BreakUIConfig
import top.iseason.bukkit.mmoforge.config.Lang
import top.iseason.bukkit.mmoforge.config.MainConfig
import top.iseason.bukkit.mmoforge.hook.PAPIHook
import top.iseason.bukkit.mmoforge.hook.VaultHook.takeMoney
import top.iseason.bukkit.mmoforge.stats.BreakChance
import top.iseason.bukkit.mmoforge.stats.ForgeMaterialRequirement
import top.iseason.bukkit.mmoforge.stats.MMOForgeData
import top.iseason.bukkit.mmoforge.stats.MMOForgeRuleResolver
import top.iseason.bukkit.mmoforge.stats.MMOForgeRuleSet
import top.iseason.bukkit.mmoforge.stats.MMOForgeStat
import top.iseason.bukkit.mmoforge.uitls.breakthrough
import top.iseason.bukkit.mmoforge.uitls.getForgeData
import top.iseason.bukkittemplate.ui.container.ChestUI
import top.iseason.bukkittemplate.ui.slot.*
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils.applyMeta
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils.getDisplayName
import top.iseason.bukkittemplate.utils.bukkit.MessageUtils.formatBy
import top.iseason.bukkittemplate.utils.bukkit.MessageUtils.sendColorMessage
import top.iseason.bukkittemplate.utils.other.EasyCoolDown
import top.iseason.bukkittemplate.utils.other.RandomUtils
import top.iseason.bukkittemplate.utils.other.submit
import kotlin.math.max
import kotlin.math.min

class BreakThroughUI(val player: Player) : ChestUI(
    PAPIHook.setPlaceHolderAndColor(BreakUIConfig.title, player),
    BreakUIConfig.row,
    BreakUIConfig.clickDelay
) {
    private var inputData: MMOForgeData? = null
    private var inputRuleSet: MMOForgeRuleSet? = null

    private var canBreak = false
    private lateinit var inputSlot: IOSlot
    private lateinit var outputSlot: IOSlot
    private val breakThroughButtons = mutableListOf<Button>()
    private val materialSlots = mutableListOf<MaterialSlot>()
    private val requirementSlots = mutableListOf<Icon>()

    private var gold = 0.0
    private var breakLevel = 0
    private var newBreakLevel = 0
    private var chance = 0.0

    init {
        lockOnTop = false
        BreakUIConfig.slots["background"]?.forEach { (item, slots) ->
            val background = PAPIHook.setPlaceHolderAndColor(item.clone(), player)
            for (slot in slots) {
                Icon(background, slot).setup()
            }
        }
        BreakUIConfig.slots["default-materials"]?.forEach { (item, slots) ->
            for (slot in slots) {
                materialSlots.add(MaterialSlot(slot, PAPIHook.setPlaceHolderAndColor(item.clone(), player)).setup())
            }
        }
        BreakUIConfig.slots["required-materials"]?.forEach { (item, slots) ->
            for (slot in slots) {
                requirementSlots.add(Icon(PAPIHook.setPlaceHolderAndColor(item.clone(), player), slot).setup())
            }
        }
        BreakUIConfig.slots["input"]?.forEach { (item, slots) ->
            val index = slots.firstOrNull() ?: return@forEach
            inputSlot = IOSlot(index, PAPIHook.setPlaceHolderAndColor(item.clone(), player))
                .inputFilter {
                    val nbtItem = NBTItem.get(it) ?: return@inputFilter false
                    val inputData = nbtItem.getForgeData() ?: return@inputFilter false
                    val inputRuleSet = MMOForgeRuleResolver.resolve(nbtItem) ?: return@inputFilter false
                    if (inputData.forge != inputRuleSet.getCurrentMaxForge(inputData.limit)) return@inputFilter false
                    this@BreakThroughUI.inputData = inputData
                    this@BreakThroughUI.inputRuleSet = inputRuleSet
                    true
                }.onInput(async = true) {
                    updateInput(inputData)
                }.onOutput(async = true) {
                    inputData = null
                    inputRuleSet = null
                    updateInput(null)
                }.setup()
        }
        BreakUIConfig.slots["output"]?.forEach { (item, slots) ->
            val index = slots.firstOrNull() ?: return@forEach
            outputSlot = IOSlot(index, PAPIHook.setPlaceHolderAndColor(item.clone(), player)).lockable(true).setup()
        }
        BreakUIConfig.slots["default-break"]?.forEach { (item, slots) ->
            val index = slots.firstOrNull() ?: return@forEach
            breakThroughButtons.add(
                Button(PAPIHook.setPlaceHolderAndColor(item.clone(), player), index)
                    .onClicked {
                        if (!canBreak) return@onClicked
                        val player = it.whoClicked as Player
                        if (!player.takeMoney(gold)) {
                            if (EasyCoolDown.check("${player.uniqueId}-ui_break_no_gold", Lang.cooldown)) {
                                player.sendColorMessage(Lang.ui_break_no_gold)
                            }
                            return@onClicked
                        }
                        consumeRequiredMaterials(player, getCurrentRequirements())
                        reset()
                        if (chance < 100.0 && RandomUtils.checkPercentage(chance)) {
                            if (MainConfig.breakFailureRemoveItem) {
                                inputSlot.reset()
                            } else {
                                submit {
                                    val tItem = inputSlot.itemStack ?: return@submit
                                    inputData = NBTItem.get(tItem).getForgeData()
                                    inputSlot.onInput.invoke(inputSlot, tItem)
                                }
                            }
                            outputSlot.reset()
                            outputSlot.outputAble(false)
                            player.sendColorMessage(
                                Lang.ui_break_failure.formatBy(
                                    breakLevel,
                                    newBreakLevel,
                                    outputSlot.itemStack?.getDisplayName()
                                )
                            )
                        } else {
                            inputSlot.reset()
                            outputSlot.outputAble(true)
                            player.sendColorMessage(
                                Lang.ui_break_success.formatBy(
                                    breakLevel,
                                    newBreakLevel,
                                    outputSlot.itemStack?.getDisplayName()
                                )
                            )
                        }
                        canBreak = false
                        gold = 0.0
                        chance = 0.0
                        inputData = null
                        breakLevel = 0
                        newBreakLevel = 0
                    }.setup()
            )
        }
    }

    inner class MaterialSlot(slotIndex: Int, placeholder: ItemStack?) :
        IOSlot(slotIndex, placeholder) {

        private val basePlaceholder = placeholder?.clone()
        var chance = 0.0

        init {
            inputFilter {
                if (!isMaterialInputActive()) return@inputFilter false
                val nbtItem = NBTItem.get(it) ?: return@inputFilter false
                if (!nbtItem.hasType()) return@inputFilter false
                matchesAnyRequirement(nbtItem)
            }
            onInput(async = true) {
                chance = getBreakChance(it)
                updateResult()
            }
            onOutput(async = true) {
                chance = 0.0
                updateResult()
            }
        }

        override fun reset() {
            chance = 0.0
            placeholder = getConfiguredItem(if (isMaterialInputActive()) "allow-materials" else "default-materials", index)
                ?: basePlaceholder
            itemStack = null
        }

        fun refreshPlaceholder() {
            val current = itemStack
            placeholder = getConfiguredItem(if (isMaterialInputActive()) "allow-materials" else "default-materials", index)
                ?: basePlaceholder
            if (current == null) {
                itemStack = null
            }
        }
    }

    private fun materialKey(requirement: ForgeMaterialRequirement): String =
        "${requirement.type.lowercase()}:${requirement.id.lowercase()}"

    private fun materialKey(item: ItemStack?): String? {
        val nbtItem = NBTItem.get(item) ?: return null
        if (!nbtItem.hasType()) return null
        val id = nbtItem.getString("MMOITEMS_ITEM_ID") ?: return null
        return "${nbtItem.type.lowercase()}:${id.lowercase()}"
    }

    private fun getCurrentRequirements(): List<ForgeMaterialRequirement> {
        val inputData = inputData ?: return emptyList()
        val ruleSet = inputRuleSet ?: return emptyList()
        if (inputData.limit >= ruleSet.maxLimit) return emptyList()
        return ruleSet.limitType[inputData.limit + 1].orEmpty()
    }

    private fun isMaterialInputActive(): Boolean = getCurrentRequirements().isNotEmpty()

    private fun getConfiguredItem(type: String, index: Int): ItemStack? {
        return BreakUIConfig.slots[type]?.entries?.firstNotNullOfOrNull { (item, slots) ->
            item.clone().takeIf { slots.contains(index) }
        }?.let { PAPIHook.setPlaceHolderAndColor(it, player) }
            ?.let { if (type == "allow-materials") sanitizeAllowMaterialPlaceholder(it) else it }
    }

    private fun sanitizeAllowMaterialPlaceholder(item: ItemStack): ItemStack = item.applyMeta {
        if (hasDisplayName()) {
            setDisplayName(
                displayName
                    .replace("{0}", "")
                    .replace("{1}", "")
                    .replace("{2}", "")
                    .replace("  ", " ")
                    .trim()
            )
        }
        if (hasLore()) {
            lore = lore!!.map {
                it.replace("{0}", "")
                    .replace("{1}", "")
                    .replace("{2}", "")
                    .replace("  ", " ")
                    .trim()
            }
        }
    }

    private fun matchesAnyRequirement(nbtItem: NBTItem): Boolean {
        val id = nbtItem.getString("MMOITEMS_ITEM_ID") ?: return false
        return getCurrentRequirements().any { it.matches(nbtItem.type, id) }
    }

    private fun getRequirementTotals(requirements: List<ForgeMaterialRequirement> = getCurrentRequirements()): LinkedHashMap<String, Int> {
        val totals = LinkedHashMap<String, Int>()
        requirements.forEach { requirement ->
            val key = materialKey(requirement)
            totals[key] = (totals[key] ?: 0) + requirement.amount
        }
        return totals
    }

    private fun getPlacedMaterialTotals(): LinkedHashMap<String, Int> {
        val totals = LinkedHashMap<String, Int>()
        materialSlots.forEach { slot ->
            val item = slot.itemStack ?: return@forEach
            val key = materialKey(item) ?: return@forEach
            totals[key] = (totals[key] ?: 0) + item.amount
        }
        return totals
    }

    private fun hasEnoughMaterials(requirements: List<ForgeMaterialRequirement>): Boolean {
        if (requirements.isEmpty()) return false
        val requiredTotals = getRequirementTotals(requirements)
        val placedTotals = getPlacedMaterialTotals()
        return requiredTotals.all { (key, amount) -> (placedTotals[key] ?: 0) >= amount }
    }

    private fun getBreakChance(itemStack: ItemStack?): Double {
        val nbtItem = NBTItem.get(itemStack) ?: return 0.0
        return if (nbtItem.hasTag(BreakChance.stat.nbtPath)) {
            nbtItem.getDouble(BreakChance.stat.nbtPath)
        } else {
            0.0
        }
    }

    private fun calculateMaterialChance(requirements: List<ForgeMaterialRequirement>): Double {
        val requiredKeys = getRequirementTotals(requirements).keys
        val chanceByKey = mutableMapOf<String, Double>()
        materialSlots.forEach { slot ->
            val item = slot.itemStack ?: return@forEach
            val key = materialKey(item) ?: return@forEach
            if (key !in requiredKeys) return@forEach
            chanceByKey[key] = max(chanceByKey[key] ?: 0.0, slot.chance)
        }
        return chanceByKey.values.sum()
    }

    private fun buildRequirementDisplayItem(requirement: ForgeMaterialRequirement): ItemStack? {
        val type = Type.get(requirement.type) ?: return null
        val template = MMOItems.plugin.templates.getTemplate(type, requirement.id) ?: return null
        val built = template.newBuilder().build().newBuilder().build() ?: return null
        val displayAmount = min(requirement.amount, max(1, built.maxStackSize))
        return built.apply {
            amount = displayAmount
        }
    }

    private fun updateRequirementDisplay() {
        val requirements = getCurrentRequirements()
        requirementSlots.forEachIndexed { index, icon ->
            val requirement = requirements.getOrNull(index)
            if (requirement == null) {
                icon.reset()
            } else {
                icon.itemStack = buildRequirementDisplayItem(requirement) ?: icon.rawItemStack
            }
        }
    }

    private fun clearMaterialSlots(player: Player) {
        materialSlots.forEach { slot ->
            if (slot.itemStack != null) {
                slot.ejectSilently(player)
            }
            slot.reset()
        }
    }

    private fun consumeRequiredMaterials(player: Player, requirements: List<ForgeMaterialRequirement>) {
        val requiredTotals = getRequirementTotals(requirements)
        requiredTotals.forEach { (key, amount) ->
            var remain = amount
            for (slot in materialSlots) {
                if (remain <= 0) break
                val item = slot.itemStack ?: continue
                if (materialKey(item) != key) continue
                val currentAmount = item.amount
                val consume = min(currentAmount, remain)
                if (consume <= 0) continue
                if (consume >= currentAmount) {
                    slot.itemStack = null
                } else {
                    item.amount = currentAmount - consume
                    slot.itemStack = item
                }
                remain -= consume
            }
        }
        clearMaterialSlots(player)
    }

    private fun resetResult() {
        canBreak = false
        gold = 0.0
        chance = 0.0
        breakLevel = 0
        newBreakLevel = 0
        breakThroughButtons.forEach { it.reset() }
        clearMaterialSlots(player)
        requirementSlots.forEach { it.reset() }
        outputSlot.ejectSilently(player)
        outputSlot.reset()
        outputSlot.outputAble(false)
    }

    private fun updateInput(inputData: MMOForgeData?) {
        if (inputData == null) {
            resetResult()
            return
        }
        materialSlots.forEach { it.refreshPlaceholder() }
        updateRequirementDisplay()
        updateResult()
    }

    private fun updateResult() {
        updateRequirementDisplay()
        if (inputData == null) {
            resetResult()
            return
        }
        val ruleSet = inputRuleSet ?: return resetResult()
        if (inputData!!.limit >= ruleSet.maxLimit) {
            resetResult()
            return
        }
        val requirements = getCurrentRequirements()
        if (requirements.isEmpty()) {
            resetResult()
            return
        }
        if (!hasEnoughMaterials(requirements)) {
            canBreak = false
            gold = 0.0
            chance = 0.0
            breakLevel = 0
            newBreakLevel = 0
            breakThroughButtons.forEach { it.reset() }
            outputSlot.reset()
            outputSlot.outputAble(false)
            return
        }
        val itemStack = inputSlot.itemStack ?: return
        val inputData = inputData!!.clone()
        val expression = MainConfig.goldBreakExpression.getString(inputData.star.toString()) ?: return
        gold = MainConfig.getValueByFormula(
            expression,
            inputData.star,
            limit = 1,
            nowForge = inputData.forge,
            nowLimit = inputData.limit,
            nowRefine = inputData.refine,
        )
        val liveMMOItem = LiveMMOItem(itemStack)
        liveMMOItem.breakthrough(inputData, ruleSet, 1)
        breakLevel = inputData.limit
        inputData.limit += 1
        newBreakLevel = inputData.limit
        liveMMOItem.setData(MMOForgeStat, inputData)
        chance = if (liveMMOItem.hasData(BreakChance.stat)) {
            (liveMMOItem.getData(BreakChance.stat) as DoubleData).value
        } else {
            100.0
        } + calculateMaterialChance(requirements)
        outputSlot.ejectSilently(player)
        outputSlot.outputAble(false)
        val oldName = itemStack.getDisplayName()
        outputSlot.itemStack = liveMMOItem.newBuilder().build()?.applyMeta {
            setDisplayName(oldName)
        }
        val modelDataHis = liveMMOItem.getStatHistory(ItemStats.CUSTOM_MODEL_DATA)
        if (modelDataHis != null) {
            val modelData = modelDataHis.recalculate(liveMMOItem.upgradeLevel) as DoubleData
            outputSlot.itemStack!!.applyMeta { this.setCustomModelData(modelData.value.toInt()) }
        }
        BreakUIConfig.slots["allow-break"]?.forEach { (item, indexes) ->
            val stack = PAPIHook.setPlaceHolderAndColor(item.clone(), player).applyMeta {
                if (hasDisplayName()) {
                    setDisplayName(
                        displayName.replace("{gold}", gold.toString())
                            .replace("{chance}", chance.toString())
                    )
                }
                if (hasLore()) {
                    lore = lore!!.map {
                        it
                            .replace("{gold}", gold.toString())
                            .replace("{chance}", chance.toString())
                    }
                }
            }
            for (index in indexes) {
                getSlot(index)?.itemStack = stack
            }
        }
        canBreak = true
    }
}
