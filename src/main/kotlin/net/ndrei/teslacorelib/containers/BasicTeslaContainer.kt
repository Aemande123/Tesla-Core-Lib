package net.ndrei.teslacorelib.containers

import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.inventory.Container
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemArmor
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import net.ndrei.teslacorelib.tileentities.SidedTileEntity

/**
 * Created by CF on 2017-06-28.
 */
open class BasicTeslaContainer<T : SidedTileEntity>(private val entity: T, private val player: EntityPlayer?) : Container() {
    private val entitySlots: Int
    private var playerSlots = 0
    private var playerExtraSlots = 0
    private var playerQuickSlots = 0

    init {
        val slots = this.entity.getSlots(this)
        this.entitySlots = slots.size
        slots.forEach { this.addSlotToContainer(it) }

        if (player != null) {
            this.playerExtraSlots = this.addPlayerExtraSlots(player)
            this.playerQuickSlots = this.addPlayerQuickBar(player)
            this.playerSlots = this.addPlayerInventory(player)

            if (player is EntityPlayerMP) {
                // server side, register TE
                @Suppress("LeakingThis")
                this.entity.containerOpened(this, player)
            }
        }
    }

    override fun onContainerClosed(playerIn: EntityPlayer?) {
        super.onContainerClosed(playerIn)

        if (playerIn is EntityPlayerMP) {
            this.entity.containerClosed(this, playerIn)
        }
    }

    //#region player inventory

    override fun canInteractWith(playerIn: EntityPlayer): Boolean {
        return !this.entity.isInvalid && playerIn.getDistanceSq(this.entity.pos.add(0.5, 0.5, 0.5)) <= 64.0
    }

    protected open val inventoryOffsetX get() = 8
    protected open val inventoryOffsetY get() = 102
    protected open val inventoryQuickBarOffsetY get() = 160
    protected open val inventoryExtraSlotsOffsetX get() = 174
    protected open val inventoryExtraSlotsOffsetY get() = 84

    @Suppress("MemberVisibilityCanPrivate")
    protected fun addPlayerQuickBar(player: EntityPlayer?): Int {
        if ((player == null) || !this.showPlayerQuickBar) {
            return 0
        }
        val playerInventory = player.inventory

        for (x in 0..8) {
            this.addSlotToContainer(Slot(playerInventory, x, this.inventoryOffsetX + x * 18, this.inventoryQuickBarOffsetY))
        }
        return 9
    }

    protected open val showPlayerQuickBar get() = true

    @Suppress("MemberVisibilityCanPrivate")
    protected fun addPlayerInventory(player: EntityPlayer?): Int {
        if ((player == null) || !this.showPlayerInventory) {
            return 0
        }
        val playerInventory = player.inventory

        for (y in 0..2) {
            for (x in 0..8) {
                this.addSlotToContainer(Slot(playerInventory, x + (y + 1) * 9, this.inventoryOffsetX + x * 18, this.inventoryOffsetY + y * 18))
            }
        }
        return 9 * 3
    }

    protected open val showPlayerInventory get() = true

    @Suppress("MemberVisibilityCanPrivate")
    protected fun addPlayerExtraSlots(player: EntityPlayer?): Int {
        if ((player == null) || !this.showPlayerExtraSlots) {
            return 0
        }
        val playerInventory = player.inventory
        for (k in 0..3) {
            val entityequipmentslot = VALID_EQUIPMENT_SLOTS[k]
            this.addSlotToContainer(object : Slot(playerInventory, 36 + (3 - k), this.inventoryExtraSlotsOffsetX, this.inventoryExtraSlotsOffsetY + k * 18) {
                /**
                 * Returns the maximum stack size for a given slot (usually the same as getInventoryStackLimit(), but 1
                 * in the case of armor slots)
                 */
                override fun getSlotStackLimit(): Int {
                    return 1
                }

                /**
                 * Check if the stack is allowed to be placed in this slot, used for armor slots as well as furnace
                 * fuel.
                 */
                override fun isItemValid(stack: ItemStack?): Boolean {
                    return stack!!.item.isValidArmor(stack, entityequipmentslot, player)
                }

                /**
                 * Return whether this slot's stack can be taken from this slot.
                 */
                override fun canTakeStack(playerIn: EntityPlayer?): Boolean {
                    val itemStack = this.stack
                    return !(!itemStack.isEmpty && !playerIn!!.isCreative
                            && EnchantmentHelper.hasBindingCurse(itemStack)) && super.canTakeStack(playerIn)
                }

                @SideOnly(Side.CLIENT)
                override fun getSlotTexture(): String? {
                    return ItemArmor.EMPTY_SLOT_NAMES[entityequipmentslot.index]
                }
            })
        }

        this.addSlotToContainer(object : Slot(playerInventory, 40, 174, 160) {
            @SideOnly(Side.CLIENT)
            override fun getSlotTexture(): String? {
                return "minecraft:items/empty_armor_slot_shield"
            }
        })

        return 5
    }

    protected open val showPlayerExtraSlots get() = true

    fun hasPlayerInventory(): Boolean {
        return this.playerSlots > 0
    }

    fun hidePlayerInventory() {
        while (this.playerSlots > 0) {
            this.inventorySlots.removeAt(this.inventorySlots.size - 1)
            this.playerSlots--
        }
    }

    fun showPlayerInventory() {
        if (this.playerSlots == 0 && this.player != null) {
            this.playerSlots = this.addPlayerInventory(this.player)
        }
    }

    //#endregion

    override fun transferStackInSlot(playerIn: EntityPlayer?, index: Int): ItemStack {
        var copyStack = ItemStack.EMPTY

        val slot = this.inventorySlots[index]
        if (slot != null && slot.hasStack) {
            val origStack = slot.stack
            copyStack = origStack.copy()

            var merged = false
            for (range in this.getSlotsRange(index)) {
                if (super.mergeItemStack(origStack, range.start, range.end, range.reverse)) {
                    merged = true
                    break
                }
            }
            if (!merged) {
                return ItemStack.EMPTY
            }
        }

        return copyStack
    }

    private fun getSlotsRange(sourceIndex: Int): List<SlotRange> {
        val slots = this.inventorySlots.size
        val playerSlots = this.playerSlots + this.playerQuickSlots + this.playerExtraSlots
        val containerSlots = slots - playerSlots

        val list = mutableListOf<SlotRange>()

        if (sourceIndex < containerSlots) {
            // transfer from container to player
            // slot order is [container] -> [armor + shield] -> [hot bar] -> [inventory]
            if (this.playerSlots > 0) {
                // container -> player inventory
                list.add(SlotRange(slots - this.playerSlots, slots, false))
            }

            // container -> player armor slots
            list.add(SlotRange(containerSlots, containerSlots + this.playerExtraSlots - 1, false))

            // container -> player hot bar
            list.add(SlotRange(
                    containerSlots + this.playerExtraSlots,
                    containerSlots + this.playerExtraSlots + this.playerQuickSlots, true))

            // container -> player shield slot
            list.add(SlotRange(containerSlots + this.playerExtraSlots - 1, containerSlots + this.playerExtraSlots, false))
        } else {
            // transfer from player to container
            // player -> container
            list.add(SlotRange(0, containerSlots, false))
        }

        return list
    }

    private inner class SlotRange(val start: Int, val end: Int, val reverse: Boolean)

    companion object {
        //region armor and off hand
        private val VALID_EQUIPMENT_SLOTS = arrayOf(EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET)
    }
}
