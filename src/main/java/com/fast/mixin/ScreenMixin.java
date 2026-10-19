package com.fast.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Unique
    private int lastSlotId = -1;

    // In 26.3, Screen#render was replaced with extractRenderState using GuiGraphicsExtractor
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderStateTail(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if ((Object)this instanceof AbstractContainerScreen<?> gui) {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.gameMode == null) return;

            // 1. Shift key check via 26.3 SDL3 InputConstants
            boolean isShiftDown = InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || 
                                 InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);

            if (!isShiftDown) {
                lastSlotId = -1;
                return;
            }

            // 2. Find hovered slot
            Slot focusedSlot = ((HandledScreenAccessor) gui).getHoveredSlot();

            if (focusedSlot != null && focusedSlot.hasItem()) {
                // 3. Totem check
                if (focusedSlot.getItem().is(Items.TOTEM_OF_UNDYING)) {
                    
                    // --- PROTECTED ZONE (leaves Slot 9 & Offhand alone) ---
                    if (focusedSlot.container instanceof Inventory) {
                        int index = focusedSlot.getContainerSlot();
                        // index 8 = Hotbar slot 9; index 40 = Offhand
                        if (index == 8 || index == 40) return;
                    }

                    // 4. Movement Logic (Strict: No overflow)
                    if (focusedSlot.index != lastSlotId) {
                        lastSlotId = focusedSlot.index;
                        int containerId = gui.getMenu().containerId;

                        // PRIORITY 1: Fill Offhand
                        if (!client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                            client.gameMode.handleContainerInput(containerId, focusedSlot.index, 40, ContainerInput.SWAP, client.player);
                        } 
                        // PRIORITY 2: Fill Hotbar Slot 9 (Forced Swap)
                        else if (!client.player.getInventory().getItem(8).is(Items.TOTEM_OF_UNDYING)) {
                            client.gameMode.handleContainerInput(containerId, focusedSlot.index, 8, ContainerInput.SWAP, client.player);
                        }
                    }
                }
            } else {
                lastSlotId = -1;
            }
        }
    }
}