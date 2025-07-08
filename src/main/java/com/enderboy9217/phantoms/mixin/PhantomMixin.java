package com.enderboy9217.phantoms.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.spawner.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Mixin(PhantomSpawner.class)
public class PhantomMixin {
    @Unique
    private final Map<Long, Long> chunkLastSpawn = new ConcurrentHashMap<>();

    @Unique
    private final int COOLDOWN_TICKS = 4000;

    // Helper method to get the chunk key for a BlockPos
    @Unique
    private long getChunkKey(BlockPos pos) {
        return new ChunkPos(pos).toLong();
    }

    @Inject(method = "spawn", at = @At("HEAD"), cancellable = true)
    private void modifySpawn(ServerWorld world, boolean spawnMonsters, boolean spawnAnimals, CallbackInfoReturnable<Integer> cir) {
        if (!spawnMonsters || !world.getGameRules().getBoolean(GameRules.DO_INSOMNIA)) {
            cir.setReturnValue(0);
            cir.cancel();
            return;
        }

        if (world.getAmbientDarkness() < 5 && world.getDimension().hasSkyLight()) {
            cir.setReturnValue(0);
            cir.cancel();
            return;
        }

        int spawnedPhantoms = 0;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) continue;

            BlockPos playerPos = player.getBlockPos();
            if (!world.getDimension().hasSkyLight() || (playerPos.getY() >= world.getSeaLevel() && world.isSkyVisible(playerPos))) {
                LocalDifficulty localDifficulty = world.getLocalDifficulty(playerPos);
                if (!localDifficulty.isHarderThan(world.random.nextFloat() * 3.0F)) continue;

                if (playerPos.getY() <= (world.getTopY() - (world.getHeight() / 10))) continue;

                BlockPos spawnPos = playerPos.up(20 + world.random.nextInt(15))
                        .east(-10 + world.random.nextInt(21))
                        .south(-10 + world.random.nextInt(21));

                long chunkKey = getChunkKey(spawnPos);
                long currentTime = world.getTime();
                long lastSpawnTime = chunkLastSpawn.getOrDefault(chunkKey, 0L);

                if (currentTime - lastSpawnTime < COOLDOWN_TICKS) {
                    continue; // Cooldown active for this chunk
                }

                BlockState blockState = world.getBlockState(spawnPos);
                FluidState fluidState = world.getFluidState(spawnPos);
                if (!SpawnHelper.isClearForSpawn(world, spawnPos, blockState, fluidState, EntityType.PHANTOM)) continue;

                EntityData entityData = null;
                int toSpawn = 1 + world.random.nextInt(localDifficulty.getGlobalDifficulty().getId() + 1);

                for (int i = 0; i < toSpawn; i++) {
                    PhantomEntity phantom = EntityType.PHANTOM.create(world);
                    if (phantom == null) continue;

                    phantom.refreshPositionAndAngles(spawnPos, 0.0F, 0.0F);
                    entityData = phantom.initialize(world, localDifficulty, SpawnReason.NATURAL, entityData, null);
                    world.spawnEntityAndPassengers(phantom);
                    spawnedPhantoms++;
                }

                if (spawnedPhantoms > 0) {
                    // Update cooldown for the spawn chunk and its neighbors
                    ChunkPos spawnChunk = new ChunkPos(spawnPos);
                    for (Object neighbor : ChunkPos.stream(spawnChunk, 2).toArray() ) {
                        ChunkPos newChunk = (ChunkPos)neighbor;
                        chunkLastSpawn.put(newChunk.toLong(), currentTime);
                    }
                }
            }
        }

        cir.setReturnValue(spawnedPhantoms);
        cir.cancel();
    }
}