package io.github.jcondedata.aliveworkplace.wonder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

/**
 * One Wonder going up or finished in a village (M35), saved in {@link WonderManager}: its id, which Wonder, the
 * village's hall, the player who started it, the current stage (1 up; past the last stage once finished) and the days
 * it was begun and finished ({@code Chronicle.day}). 35.2 keeps what the caps and {@code /workplace wonders} read; 35.4
 * adds the placement, the Foundation Stone, the build sites of the current stage's parts and the builder it was handed to
 * (who takes on each next stage: a finished part's site is gone, and with it who built it): the crew's lead. 35.5 adds
 * whether every builder of the village was called to its crew ({@code called}). 35.7 adds whether the current stage's
 * goods were dedicated ({@code dedicated}) and the goods players handed in at the stone for it ({@code delivered}; the
 * goods in the stockpile's chests are counted where they lie). 35.8 adds how a finished one is kept up ({@link Kept}).
 * Every field but the first three has a default.
 */
public record WonderSite(UUID id, ResourceLocation wonder, BlockPos hall, Optional<UUID> owner, String ownerName, int stage,
						 long begun, Optional<Long> finished, Optional<BlueprintData.Placement> placement, Optional<BlockPos> stone,
						 List<UUID> parts, Optional<UUID> builder, boolean called, boolean dedicated, List<Handed> delivered, Kept kept) {
	/** A site as 35.2 recorded it: no placement, no stone, no parts, no builder. */
	public WonderSite(UUID id, ResourceLocation wonder, BlockPos hall, Optional<UUID> owner, String ownerName, int stage, long begun,
					  Optional<Long> finished) {
		this(id, wonder, hall, owner, ownerName, stage, begun, finished, Optional.empty(), Optional.empty(), List.of(), Optional.empty(), false);
	}

	/** A site as 35.5 recorded it: no goods dedicated or handed in yet. */
	public WonderSite(UUID id, ResourceLocation wonder, BlockPos hall, Optional<UUID> owner, String ownerName, int stage, long begun,
					  Optional<Long> finished, Optional<BlueprintData.Placement> placement, Optional<BlockPos> stone, List<UUID> parts,
					  Optional<UUID> builder, boolean called) {
		this(id, wonder, hall, owner, ownerName, stage, begun, finished, placement, stone, parts, builder, called, false, List.of());
	}

	/** A site as 35.7 recorded it: never counted, standing whole (35.8). */
	public WonderSite(UUID id, ResourceLocation wonder, BlockPos hall, Optional<UUID> owner, String ownerName, int stage, long begun,
					  Optional<Long> finished, Optional<BlueprintData.Placement> placement, Optional<BlockPos> stone, List<UUID> parts,
					  Optional<UUID> builder, boolean called, boolean dedicated, List<Handed> delivered) {
		this(id, wonder, hall, owner, ownerName, stage, begun, finished, placement, stone, parts, builder, called, dedicated, delivered, Kept.WHOLE);
	}

	/**
	 * How a finished Wonder is kept up (35.8): the share of it standing at the last count (0 to 100), the day of that
	 * count ({@code Chronicle.day}; 0: never counted), whether it's damaged (its powers off), and the day of its finishing
	 * festival (0: none), whose fireworks are the bigger show. Saved flat with the site's other fields, each optional.
	 */
	public record Kept(int standing, long counted, boolean damaged, long festival) {
		public static final Kept WHOLE = new Kept(100, 0L, false, 0L);
		public static final com.mojang.serialization.MapCodec<Kept> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.INT.optionalFieldOf("standing", 100).forGetter(Kept::standing),
			Codec.LONG.optionalFieldOf("counted", 0L).forGetter(Kept::counted),
			Codec.BOOL.optionalFieldOf("damaged", false).forGetter(Kept::damaged),
			Codec.LONG.optionalFieldOf("festival", 0L).forGetter(Kept::festival)
		).apply(i, Kept::new));
	}

	/**
	 * Goods handed in at the stone for the current stage's goods line {@code line} (35.7): the item, the potion for a
	 * potion line, and how many. Kept apart from the stockpile's chests so that a cancel can hand them back.
	 */
	public record Handed(int line, ResourceLocation item, Optional<ResourceLocation> potion, int count) {
		public static final Codec<Handed> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("line").forGetter(Handed::line),
			ResourceLocation.CODEC.fieldOf("item").forGetter(Handed::item),
			ResourceLocation.CODEC.optionalFieldOf("potion").forGetter(Handed::potion),
			Codec.INT.fieldOf("count").forGetter(Handed::count)
		).apply(i, Handed::new));
	}

	/**
	 * Whether the current stage has started: its goods were dedicated, or its parts are already down (a site saved before
	 * 35.7, when a stage started without goods).
	 */
	public boolean started() {
		return dedicated || !parts.isEmpty();
	}

	public static final Codec<WonderSite> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("id").forGetter(WonderSite::id),
		ResourceLocation.CODEC.fieldOf("wonder").forGetter(WonderSite::wonder),
		BlockPos.CODEC.fieldOf("hall").forGetter(WonderSite::hall),
		UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(WonderSite::owner),
		Codec.STRING.optionalFieldOf("ownerName", "").forGetter(WonderSite::ownerName),
		Codec.INT.optionalFieldOf("stage", 1).forGetter(WonderSite::stage),
		Codec.LONG.optionalFieldOf("begun", 0L).forGetter(WonderSite::begun),
		Codec.LONG.optionalFieldOf("finished").forGetter(WonderSite::finished),
		BlueprintData.Placement.CODEC.optionalFieldOf("placement").forGetter(WonderSite::placement),
		BlockPos.CODEC.optionalFieldOf("stone").forGetter(WonderSite::stone),
		UUIDUtil.CODEC.listOf().optionalFieldOf("parts", List.of()).forGetter(WonderSite::parts),
		UUIDUtil.CODEC.optionalFieldOf("builder").forGetter(WonderSite::builder),
		Codec.BOOL.optionalFieldOf("called", false).forGetter(WonderSite::called),
		Codec.BOOL.optionalFieldOf("dedicated", false).forGetter(WonderSite::dedicated),
		Handed.CODEC.listOf().optionalFieldOf("delivered", List.of()).forGetter(WonderSite::delivered),
		Kept.MAP_CODEC.forGetter(WonderSite::kept)
	).apply(i, WonderSite::new));

	/** Whether it's finished and its powers are on: not damaged (35.8). */
	public boolean empowered() {
		return done() && !kept.damaged();
	}

	/** Whether it's finished. */
	public boolean done() {
		return finished.isPresent();
	}

	/** Whether it's still going up. */
	public boolean goingUp() {
		return finished.isEmpty();
	}

	WonderSite atStage(int next) {
		return new WonderSite(id, wonder, hall, owner, ownerName, next, begun, finished, placement, stone, parts, builder, called, dedicated, delivered, kept);
	}

	WonderSite finishedOn(long day) {
		return new WonderSite(id, wonder, hall, owner, ownerName, stage, begun, Optional.of(day), placement, stone, parts, builder, called, dedicated, delivered, kept);
	}

	WonderSite placedAt(BlueprintData.Placement where, BlockPos stonePos) {
		return new WonderSite(id, wonder, hall, owner, ownerName, stage, begun, finished, Optional.of(where), Optional.of(stonePos.immutable()), parts, builder, called, dedicated, delivered, kept);
	}

	WonderSite withBuilder(UUID who) {
		return new WonderSite(id, wonder, hall, owner, ownerName, stage, begun, finished, placement, stone, parts, Optional.of(who), called, dedicated, delivered, kept);
	}

	WonderSite withoutBuilder() {
		return new WonderSite(id, wonder, hall, owner, ownerName, stage, begun, finished, placement, stone, parts, Optional.empty(), called, dedicated, delivered, kept);
	}

	WonderSite withCalled(boolean call) {
		return new WonderSite(id, wonder, hall, owner, ownerName, stage, begun, finished, placement, stone, parts, builder, call, dedicated, delivered, kept);
	}

	WonderSite withParts(List<UUID> sites) {
		return new WonderSite(id, wonder, hall, owner, ownerName, stage, begun, finished, placement, stone, List.copyOf(sites), builder, called, dedicated, delivered, kept);
	}

	WonderSite withDedicated(boolean done) {
		return new WonderSite(id, wonder, hall, owner, ownerName, stage, begun, finished, placement, stone, parts, builder, called, done, delivered, kept);
	}

	WonderSite withKept(Kept now) {
		return new WonderSite(id, wonder, hall, owner, ownerName, stage, begun, finished, placement, stone, parts, builder, called, dedicated, delivered, now);
	}

	WonderSite withDelivered(List<Handed> goods) {
		return new WonderSite(id, wonder, hall, owner, ownerName, stage, begun, finished, placement, stone, parts, builder, called, dedicated, List.copyOf(goods), kept);
	}
}
