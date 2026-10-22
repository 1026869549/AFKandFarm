package com.qiaosylveon.afkfarm;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side runtime logic.
 *
 * <p>Two independent features:</p>
 * <ol>
 *     <li>AFK look rotation: F7 toggles it; while enabled the player yaw is advanced by
 *     +5 degrees every 10 seconds. In Minecraft +yaw is a turn to the right.</li>
 *     <li>Harvest/replant: pressing the vanilla "use" key (the pack binds it to left mouse)
 *     while aiming at a mature potato/carrot above farmland breaks the crop with the item
 *     currently in the main hand, then plants the matching seed on a later client tick.
 *     The seed can come from the main hand, off hand, or inventory; inventory seeds are
 *     moved into the selected slot with normal container SWAP clicks and swapped back.</li>
 * </ol>
 *
 * <p>Harvest is a three-phase state machine (IDLE -> AWAIT_PLANT -> AWAIT_VERIFY).
 * Breaking and planting intentionally happen on different client ticks, and the planted
 * crop is verified two ticks later. Verification only logs; it never retries.</p>
 */
public final class ClientHarvest {

    private static final float ROTATION_STEP_DEGREES = 5.0F;
    private static final long ROTATION_INTERVAL_NANOS = 10_000_000_000L;
    private static final int HARVEST_COOLDOWN_TICKS = 4;
    private static final long HINT_COOLDOWN_MS = 2_000L;

    /** Break -> plant delay. Keep PLANT + VERIFY <= HARVEST_COOLDOWN_TICKS. */
    private static final int PLANT_DELAY_TICKS = 1;
    /** Plant -> verify delay. */
    private static final int VERIFY_DELAY_TICKS = 2;

    private static final boolean VERBOSE = Boolean.getBoolean("afkfarm.verbose");

    /**
     * Registered in {@code RegisterKeyMappingsEvent}. F7 is free in the target instance.
     * The "use/place" key is intentionally NOT a new keybind: we reuse
     * {@code Minecraft.options.keyUse}, so the mod follows the pack's left-mouse binding.
     */
    public static final KeyMapping AFK_TOGGLE = new KeyMapping(
            "key.afkfarm.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            KeyMapping.Category.MISC
    );

    private enum Phase {
        IDLE,
        AWAIT_PLANT,
        AWAIT_VERIFY
    }

    private static boolean afkEnabled;
    private static long nextRotationAt;
    private static int harvestCooldown;
    private static long lastHintAt;

    private static VersionAdapter VERSION;

    private static Phase phase = Phase.IDLE;
    private static int phaseTicks;

    // Context for the current harvest. Filled at break time, cleared by resetPhase().
    private static long pendingStartGameTime;
    private static ClientLevel pendingLevel;
    private static BlockPos pendingCropPos;
    private static BlockPos pendingFarmlandPos;
    private static Direction pendingFace;
    private static Block pendingCropBlock;
    private static Item pendingSeedItem;
    private static boolean pendingSwapped;
    private static int pendingMenuSlot = -1;
    private static int pendingHotbarIndex = -1;
    private static InteractionHand pendingPlantHand;

    private ClientHarvest() {
    }

    public static void init(VersionAdapter adapter) {
        VERSION = adapter;
    }

    public static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (VERSION == null) {
            return;
        }

        if (player == null || minecraft.level == null || minecraft.gameMode == null) {
            afkEnabled = false;
            nextRotationAt = 0L;
            harvestCooldown = 0;
            abortPhase("env_null");
            return;
        }

        if (VERBOSE && (minecraft.options.keyUse.isDown() || player.tickCount % 20 == 0)) {
            AfkFarmCommon.LOGGER.info("AFK_FARM_TICK keyDown={} screenOpen={} paused={} cooldown={} phase={} phaseTicks={} yaw={} pitch={}",
                    minecraft.options.keyUse.isDown(),
                    VERSION.isScreenOpen(minecraft),
                    minecraft.isPaused(),
                    harvestCooldown,
                    phase,
                    phaseTicks,
                    player.getYRot(),
                    player.getXRot());
        }

        if (harvestCooldown > 0) {
            harvestCooldown--;
        }

        if (AFK_TOGGLE.consumeClick()) {
            afkEnabled = !afkEnabled;
            nextRotationAt = System.nanoTime() + ROTATION_INTERVAL_NANOS;
            Component message = Component.translatable(afkEnabled ? "msg.afkfarm.afk_on" : "msg.afkfarm.afk_off");
            VERSION.sendChat(player, message);
            VERSION.sendOverlay(player, message);
            AfkFarmCommon.LOGGER.info("AFK look rotation {}", afkEnabled ? "enabled" : "disabled");
        }

        if (afkEnabled) {
            tickAfkRotation(minecraft, player);
        }

        // The phase machine must run every tick, regardless of the use key.
        tickHarvestPhase(minecraft, player);
        if (phase != Phase.IDLE) {
            return;
        }

        // Hold-to-repeat: the vanilla use key only reports a click on press, so this loop
        // makes sweeping across a crop row pleasant. beginHarvest() has its own cooldown.
        if (harvestCooldown == 0
                && !VERSION.isScreenOpen(minecraft)
                && !minecraft.isPaused()
                && minecraft.options.keyUse.isDown()) {
            HarvestTarget target = findHarvestTarget(minecraft, player);
            if (target != null) {
                if (VERBOSE) {
                    AfkFarmCommon.LOGGER.info("AFK_FARM_TARGET crop={} pos={} farmland={} seed={}",
                            target.cropBlock(), target.cropPos(), target.farmlandPos(), target.seedItem());
                }
                beginHarvest(minecraft, player, target);
            } else if (VERBOSE) {
                AfkFarmCommon.LOGGER.info("AFK_FARM_NO_TARGET hit={}",
                        player.pick(player.blockInteractionRange(), 0.0F, false));
            }
        }
    }

    private static void tickAfkRotation(Minecraft minecraft, LocalPlayer player) {
        boolean paused = VERSION.isScreenOpen(minecraft)
                || minecraft.isPaused()
                || !player.isAlive()
                || player.isSpectator()
                || player.isSleeping();

        if (paused) {
            // Do not accumulate missed rotations while a screen is open or the player is dead.
            nextRotationAt = System.nanoTime() + ROTATION_INTERVAL_NANOS;
            return;
        }

        long now = System.nanoTime();
        if (now < nextRotationAt) {
            return;
        }

        float newYaw = Mth.wrapDegrees(player.getYRot() + ROTATION_STEP_DEGREES);
        player.setYRot(newYaw);
        player.setYHeadRot(newYaw);
        nextRotationAt = now + ROTATION_INTERVAL_NANOS;

        AfkFarmCommon.LOGGER.debug("AFK rotation: yaw -> {}", newYaw);
    }

    /**
     * Shared click path used by the Forge/NeoForge use-key event and the Fabric
     * {@code startUseItem} Mixin. Returns true when the click targeted a harvestable
     * crop and should be canceled (vanilla use must not also run).
     */
    public static boolean onUseKeyClicked(Minecraft minecraft) {
        if (VERSION == null) {
            return false;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gameMode == null) {
            return false;
        }
        if (VERSION.isScreenOpen(minecraft) || minecraft.isPaused() || player.isUsingItem()) {
            // Eat / bow / shield etc. must not be swallowed by the harvest logic.
            return false;
        }

        HarvestTarget target = findHarvestTarget(minecraft, player);
        if (target == null) {
            return false;
        }

        // Aiming at a mature potato/carrot above farmland: this click belongs to us.
        if (phase != Phase.IDLE) {
            return true;
        }
        beginHarvest(minecraft, player, target);
        return true;
    }

    private static HarvestTarget findHarvestTarget(Minecraft minecraft, LocalPlayer player) {
        if (minecraft.level == null) {
            return null;
        }

        HitResult hit = player.pick(player.blockInteractionRange(), 0.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos cropPos = ((BlockHitResult) hit).getBlockPos();
        Direction face = ((BlockHitResult) hit).getDirection();
        BlockState cropState = minecraft.level.getBlockState(cropPos);
        Block cropBlock = cropState.getBlock();

        Item seedItem;
        if (cropBlock == Blocks.CARROTS) {
            seedItem = Items.CARROT;
        } else if (cropBlock == Blocks.POTATOES) {
            seedItem = Items.POTATO;
        } else {
            return null;
        }

        if (!(cropBlock instanceof CropBlock crop) || !crop.isMaxAge(cropState)) {
            return null;
        }

        BlockPos farmlandPos = cropPos.below();
        if (minecraft.level.getBlockState(farmlandPos).getBlock() != Blocks.FARMLAND) {
            return null;
        }

        return new HarvestTarget(cropPos, farmlandPos, face, cropBlock, seedItem);
    }

    /**
     * Break-only entry point. Both the hold loop and the use-key click event converge here.
     * The actual planting is done by {@link #tickHarvestPhase(Minecraft, LocalPlayer)} on a
     * later client tick.
     */
    private static void beginHarvest(Minecraft minecraft, LocalPlayer player, HarvestTarget target) {
        if (harvestCooldown > 0) {
            return;
        }
        if (player.isSpectator() || !player.isAlive() || player.isUsingItem()) {
            return;
        }
        if (minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return;
        }
        // The container SWAP path only makes sense for the standard player inventory menu.
        if (player.containerMenu != player.inventoryMenu) {
            return;
        }
        if (player.getMainHandItem().isEmpty()) {
            hint(player, "msg.afkfarm.no_tool");
            return;
        }

        SeedPlan plan = findSeed(player, target.seedItem());
        if (plan == null) {
            hint(player, "msg.afkfarm.no_seed");
            return;
        }

        if (VERBOSE) {
            AfkFarmCommon.LOGGER.info("AFK_FARM_TRY crop={} pos={} hand={} plan={}",
                    target.cropBlock(), target.cropPos(), player.getMainHandItem(), plan);
        }

        harvestCooldown = HARVEST_COOLDOWN_TICKS;

        // 26.2 moved the network action into startDestroyBlock(); destroyBlock() is now
        // only the client-side prediction helper and sends no packet. Use the former.
        boolean started = minecraft.gameMode.startDestroyBlock(target.cropPos(), target.face());
        if (!started) {
            if (VERBOSE) {
                AfkFarmCommon.LOGGER.info("AFK_FARM_START_BREAK_FAILED pos={} face={}",
                        target.cropPos(), target.face());
            }
            return;
        }
        player.swing(InteractionHand.MAIN_HAND);

        pendingCropPos = target.cropPos();
        pendingFarmlandPos = target.farmlandPos();
        pendingFace = target.face();
        pendingCropBlock = target.cropBlock();
        pendingSeedItem = target.seedItem();
        pendingStartGameTime = minecraft.level.getGameTime();
        pendingLevel = minecraft.level;
        phase = Phase.AWAIT_PLANT;
        phaseTicks = PLANT_DELAY_TICKS;

        if (VERBOSE) {
            AfkFarmCommon.LOGGER.info("AFK_PHASE break crop={} pos={} farmland={} face={} seed={} gameTime={}",
                    target.cropBlock(), target.cropPos(), target.farmlandPos(),
                    target.face(), target.seedItem(), pendingStartGameTime);
        }
    }

    private static void tickHarvestPhase(Minecraft minecraft, LocalPlayer player) {
        if (phase == Phase.IDLE) {
            return;
        }

        // Do not send any more packets from a half-finished state after a hard invalidation.
        String invalid = invalidReason(minecraft, player);
        if (invalid != null) {
            abortPhase(invalid);
            return;
        }

        // Goal: break and plant must not happen on the same client tick. This guard also
        // covers the use-key event path, whose event runs before ClientTickEvent.Post in
        // the same Minecraft.tick().
        if (phase == Phase.AWAIT_PLANT && minecraft.level.getGameTime() <= pendingStartGameTime) {
            return;
        }

        if (--phaseTicks > 0) {
            return;
        }

        if (phase == Phase.AWAIT_PLANT) {
            doPlant(minecraft, player);
        } else {
            doVerify(minecraft, player);
        }
    }

    private static String invalidReason(Minecraft minecraft, LocalPlayer player) {
        if (player == null || minecraft.level == null || minecraft.gameMode == null) {
            return "env_null";
        }
        if (pendingLevel != null && minecraft.level != pendingLevel) {
            return "dimension_changed";
        }
        if (!player.isAlive()) {
            return "dead";
        }
        if (player.isSpectator()) {
            return "spectator";
        }
        if (minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return "spectator_mode";
        }
        if (player.isUsingItem()) {
            return "using_item";
        }
        if (minecraft.isPaused()) {
            return "paused";
        }
        if (player.containerMenu != player.inventoryMenu) {
            return "container_open";
        }
        return null;
    }

    private static void doPlant(Minecraft minecraft, LocalPlayer player) {
        BlockState state = minecraft.level.getBlockState(pendingCropPos);

        // If the crop is still there locally, the break did not take. Do not send a plant
        // packet that the server will reject and then roll back on the client.
        if (!state.canBeReplaced()) {
            if (VERBOSE) {
                AfkFarmCommon.LOGGER.info("AFK_PHASE plant_skip_occupied pos={} state={} gameTime={}",
                        pendingCropPos, state, minecraft.level.getGameTime());
            }
            resetPhase();
            return;
        }

        SeedPlan plan = findSeed(player, pendingSeedItem);
        if (plan == null) {
            hint(player, "msg.afkfarm.no_seed");
            if (VERBOSE) {
                AfkFarmCommon.LOGGER.info("AFK_PHASE plant_skip_no_seed pos={} gameTime={}",
                        pendingCropPos, minecraft.level.getGameTime());
            }
            resetPhase();
            return;
        }

        pendingPlantHand = plan.hand();
        if (plan.swap()) {
            clickSwap(minecraft, player, plan.menuSlot(), plan.hotbarIndex());
            pendingSwapped = true;
            pendingMenuSlot = plan.menuSlot();
            pendingHotbarIndex = plan.hotbarIndex();
        }

        BlockHitResult plantHit = new BlockHitResult(
                Vec3.atCenterOf(pendingFarmlandPos).add(0.0D, 0.5D, 0.0D),
                Direction.UP,
                pendingFarmlandPos,
                false
        );
        InteractionResult result = minecraft.gameMode.useItemOn(player, plan.hand(), plantHit);
        player.swing(plan.hand());

        // Swap the tool back into the selected slot. The use packet was already queued
        // before this click, and the server processes packets in order.
        if (plan.swap()) {
            clickSwap(minecraft, player, plan.menuSlot(), plan.hotbarIndex());
            pendingSwapped = false;
        }

        if (VERBOSE) {
            AfkFarmCommon.LOGGER.info("AFK_PHASE plant pos={} hand={} swapped={} result={} gameTime={}",
                    pendingCropPos, plan.hand(), plan.swap(), result, minecraft.level.getGameTime());
        }

        phase = Phase.AWAIT_VERIFY;
        phaseTicks = VERIFY_DELAY_TICKS;
    }

    private static void doVerify(Minecraft minecraft, LocalPlayer player) {
        // LevelReader.hasChunkAt(BlockPos) is deprecated in 26.2; hasChunk(chunkX, chunkZ)
        // is the non-deprecated equivalent.
        if (!minecraft.level.hasChunk(pendingCropPos.getX() >> 4, pendingCropPos.getZ() >> 4)) {
            if (VERBOSE) {
                AfkFarmCommon.LOGGER.info("AFK_PHASE verify_skip_no_chunk pos={} gameTime={}",
                        pendingCropPos, minecraft.level.getGameTime());
            }
            resetPhase();
            return;
        }

        BlockState actual = minecraft.level.getBlockState(pendingCropPos);
        boolean ok = actual.getBlock() == pendingCropBlock;

        if (VERBOSE) {
            AfkFarmCommon.LOGGER.info("AFK_PHASE verify pos={} expected={} actual={} ok={} gameTime={}",
                    pendingCropPos, pendingCropBlock, actual, ok, minecraft.level.getGameTime());
        }
        if (!ok) {
            // By design: no retry, no backoff. This line is the audit signal.
            AfkFarmCommon.LOGGER.info("AFK_PLANT_LOST pos={} expected={} actual={} gameTime={}",
                    pendingCropPos, pendingCropBlock, actual, minecraft.level.getGameTime());
        }
        resetPhase();
    }

    private static void abortPhase(String reason) {
        if (phase == Phase.IDLE) {
            resetPhase();
            return;
        }

        if (pendingSwapped) {
            // Defensive only: the normal flow swaps back within doPlant().
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.gameMode != null
                    && minecraft.player.containerMenu == minecraft.player.inventoryMenu) {
                clickSwap(minecraft, minecraft.player, pendingMenuSlot, pendingHotbarIndex);
            }
        }

        if (VERBOSE) {
            Minecraft minecraft = Minecraft.getInstance();
            AfkFarmCommon.LOGGER.info("AFK_PHASE abort reason={} phase={} pos={} gameTime={}",
                    reason, phase, pendingCropPos,
                    minecraft.level == null ? -1L : minecraft.level.getGameTime());
        }
        resetPhase();
    }

    private static void resetPhase() {
        phase = Phase.IDLE;
        phaseTicks = 0;
        pendingStartGameTime = 0L;
        pendingLevel = null;
        pendingCropPos = null;
        pendingFarmlandPos = null;
        pendingFace = null;
        pendingCropBlock = null;
        pendingSeedItem = null;
        pendingSwapped = false;
        pendingMenuSlot = -1;
        pendingHotbarIndex = -1;
        pendingPlantHand = null;
    }

    private static SeedPlan findSeed(LocalPlayer player, Item seedItem) {
        if (player.getMainHandItem().getItem() == seedItem) {
            return new SeedPlan(InteractionHand.MAIN_HAND, false, -1, -1, "main hand");
        }
        if (player.getOffhandItem().getItem() == seedItem) {
            return new SeedPlan(InteractionHand.OFF_HAND, false, -1, -1, "off hand");
        }

        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() != seedItem) {
                continue;
            }
            if (i == selected) {
                return new SeedPlan(InteractionHand.MAIN_HAND, false, -1, -1, "selected slot");
            }
            // Inventory menu layout: hotbar 0..8 -> menu slots 36..44, main 9..35 -> 9..35.
            int menuSlot = i < 9 ? i + 36 : i;
            return new SeedPlan(InteractionHand.MAIN_HAND, true, menuSlot, selected, "inventory slot " + i);
        }

        return null;
    }

    private static void clickSwap(Minecraft minecraft, LocalPlayer player, int menuSlot, int hotbarIndex) {
        if (VERSION == null || minecraft.gameMode == null || menuSlot < 0 || hotbarIndex < 0) {
            return;
        }
        VERSION.swapSlot(minecraft, player, menuSlot, hotbarIndex);
    }

    private static void hint(LocalPlayer player, String translationKey) {
        long now = System.currentTimeMillis();
        if (now - lastHintAt < HINT_COOLDOWN_MS) {
            return;
        }
        lastHintAt = now;
        VERSION.sendOverlay(player, Component.translatable(translationKey));
    }

    private record HarvestTarget(BlockPos cropPos, BlockPos farmlandPos, Direction face, Block cropBlock, Item seedItem) {
    }

    private record SeedPlan(InteractionHand hand, boolean swap, int menuSlot, int hotbarIndex, String source) {
    }
}
