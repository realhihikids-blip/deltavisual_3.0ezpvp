package com.pvpclient.module.visual;

import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import com.pvpclient.setting.BooleanSetting;
import com.pvpclient.setting.ColorSetting;
import com.pvpclient.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/** Colored outlines around storage blocks, optionally visible through walls. Private worlds only. */
public final class ChestEspModule extends ClientModule {
    private final BooleanSetting chests = add(new BooleanSetting("Chests", true));
    private final BooleanSetting enderChests = add(new BooleanSetting("Ender Chests", true));
    private final BooleanSetting barrels = add(new BooleanSetting("Barrels", true));
    private final BooleanSetting shulkers = add(new BooleanSetting("Shulker Boxes", true));
    private final BooleanSetting throughWalls = add(new BooleanSetting("Through Walls", true));
    private final NumberSetting distance = add(new NumberSetting("Distance", 64, 16, 128, 1));
    private final ColorSetting chestColor = add(new ColorSetting("Chest Color", 0xFACC15));
    private final ColorSetting trappedColor = add(new ColorSetting("Trapped Chest Color", 0xEF4444));
    private final ColorSetting enderColor = add(new ColorSetting("Ender Chest Color", 0x8B5CF6));
    private final ColorSetting barrelColor = add(new ColorSetting("Barrel Color", 0xF97316));
    private final ColorSetting shulkerColor = add(new ColorSetting("Shulker Color", 0xEC4899));

    private record Target(Box box, float r, float g, float b) {}

    private final List<Target> targets = new ArrayList<>();
    private int ticks;

    public ChestEspModule() {
        super("Chest ESP", "Colored outlines for chests, barrels and shulkers (private worlds only)", Category.VISUALS);
        markRestricted();
    }

    @Override
    protected void onDisable() { targets.clear(); }

    // Scan loaded chunks a few times per second instead of every frame.
    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) { targets.clear(); return; }
        if (ticks++ % 10 != 0) return;

        List<Target> found = new ArrayList<>();
        double max = distance.get();
        int radius = (int) Math.ceil(max / 16.0);
        int pcx = mc.player.getBlockPos().getX() >> 4;
        int pcz = mc.player.getBlockPos().getZ() >> 4;
        Vec3d me = mc.player.getPos();

        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    ColorSetting color = colorFor(be);
                    if (color == null) continue;
                    BlockPos pos = be.getPos();
                    if (Vec3d.ofCenter(pos).distanceTo(me) > max) continue;

                    BlockState state = mc.world.getBlockState(pos);
                    VoxelShape shape = state.getOutlineShape(mc.world, pos);
                    Box box = shape.isEmpty() ? new Box(pos) : shape.getBoundingBox().offset(pos);
                    found.add(new Target(box, color.r(), color.g(), color.b()));
                }
            }
        }
        targets.clear();
        targets.addAll(found);
    }

    private ColorSetting colorFor(BlockEntity be) {
        if (be instanceof TrappedChestBlockEntity) return chests.get() ? trappedColor : null;
        if (be instanceof ChestBlockEntity) return chests.get() ? chestColor : null;
        if (be instanceof EnderChestBlockEntity) return enderChests.get() ? enderColor : null;
        if (be instanceof BarrelBlockEntity) return barrels.get() ? barrelColor : null;
        if (be instanceof ShulkerBoxBlockEntity) return shulkers.get() ? shulkerColor : null;
        return null;
    }

    @Override
    public void onWorldRender(WorldRenderContext ctx) {
        if (targets.isEmpty()) return;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;

        Vec3d cam = ctx.camera().getPos();
        ms.push();
        ms.translate(-cam.x, -cam.y, -cam.z);

        // Pass 1: normal outlines (visible parts).
        VertexConsumer front = vcp.getBuffer(RenderLayer.getLines());
        for (Target t : targets) VertexRendering.drawBox(ms, front, t.box(), t.r(), t.g(), t.b(), 1.0f);
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getLines());

        // Pass 2: vanilla's "secondary outline" layer is drawn where blocks are in the way.
        if (throughWalls.get()) {
            VertexConsumer behind = vcp.getBuffer(RenderLayer.getSecondaryBlockOutline());
            for (Target t : targets) VertexRendering.drawBox(ms, behind, t.box(), t.r(), t.g(), t.b(), 1.0f);
            if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getSecondaryBlockOutline());
        }
        ms.pop();
    }
}
