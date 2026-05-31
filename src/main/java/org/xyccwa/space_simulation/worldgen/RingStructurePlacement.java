package org.xyccwa.space_simulation.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

import java.util.Optional;

public class RingStructurePlacement extends StructurePlacement {

    private final double minRadius;
    private final double maxRadius;

    // 新增：控制结构密度
    private final int spacing;
    private final int separation;

    public double getMinRadius() { return minRadius; }
    public double getMaxRadius() { return maxRadius; }
    public int getSpacing() { return spacing; }
    public int getSeparation() { return separation; }

    public Vec3i getLocateOffset() { return locateOffset(); }
    public FrequencyReductionMethod getFrequencyReductionMethod() { return frequencyReductionMethod(); }
    public float getFrequency() { return frequency(); }
    public int getSalt() { return salt(); }
    public Optional<ExclusionZone> getExclusionZone() { return exclusionZone(); }

    public static final MapCodec<RingStructurePlacement> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Vec3i.offsetCodec(16).optionalFieldOf("locate_offset", Vec3i.ZERO).forGetter(RingStructurePlacement::getLocateOffset),
                    FrequencyReductionMethod.CODEC.optionalFieldOf("frequency_reduction_method", FrequencyReductionMethod.DEFAULT).forGetter(RingStructurePlacement::getFrequencyReductionMethod),
                    Codec.floatRange(0.0F, 1.0F).optionalFieldOf("frequency", 1.0F).forGetter(RingStructurePlacement::getFrequency),
                    Codec.intRange(0, Integer.MAX_VALUE).fieldOf("salt").forGetter(RingStructurePlacement::getSalt),
                    ExclusionZone.CODEC.optionalFieldOf("exclusion_zone").forGetter(RingStructurePlacement::getExclusionZone),
                    Codec.DOUBLE.fieldOf("min_radius").forGetter(RingStructurePlacement::getMinRadius),
                    Codec.DOUBLE.fieldOf("max_radius").forGetter(RingStructurePlacement::getMaxRadius),
                    Codec.intRange(1, 4096).fieldOf("spacing").forGetter(RingStructurePlacement::getSpacing),
                    Codec.intRange(0, 4095).fieldOf("separation").forGetter(RingStructurePlacement::getSeparation)
            ).apply(instance, RingStructurePlacement::new)
    );

    public RingStructurePlacement(
            Vec3i locateOffset,
            FrequencyReductionMethod frequencyReductionMethod,
            float frequency,
            int salt,
            Optional<ExclusionZone> exclusionZone,
            double minRadius,
            double maxRadius,
            int spacing,
            int separation) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone);
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.spacing = spacing;
        this.separation = separation;

        // 验证参数
        if (spacing <= separation) {
            throw new IllegalArgumentException("Spacing must be greater than separation");
        }
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState structureState, int chunkX, int chunkZ) {
        // 1. 首先计算该区域的结构放置网格单元格
        int spacing = this.spacing;
        int separation = this.separation;

        // 获取区域坐标（每个区域大小为 spacing x spacing 区块）
        int regionX = Math.floorDiv(chunkX, spacing);
        int regionZ = Math.floorDiv(chunkZ, spacing);

        // 2. 使用盐值生成该区域内的结构放置位置
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(structureState.getLevelSeed(), regionX, regionZ, this.salt());

        int structureChunkX = regionX * spacing + random.nextInt(spacing - separation);
        int structureChunkZ = regionZ * spacing + random.nextInt(spacing - separation);

        // 3. 检查当前区块是否正好是结构生成区块
        if (chunkX != structureChunkX || chunkZ != structureChunkZ) {
            return false;
        }

        // 4. 计算结构在世界中的位置（区块中心点）
        double worldX = structureChunkX * 16.0 + 8.0;
        double worldZ = structureChunkZ * 16.0 + 8.0;

        // 5. 检查是否在环状区域内
        double distSq = worldX * worldX + worldZ * worldZ;
        double minRadiusSq = minRadius * minRadius;
        double maxRadiusSq = maxRadius * maxRadius;

        return distSq >= minRadiusSq && distSq <= maxRadiusSq;
    }

    @Override
    public BlockPos getLocatePos(ChunkPos chunkPos) {
        return new BlockPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ()).offset(this.locateOffset());
    }

    @Override
    public StructurePlacementType<?> type() {
        return ModStructurePlacements.RING_PLACEMENT.get();
    }
}