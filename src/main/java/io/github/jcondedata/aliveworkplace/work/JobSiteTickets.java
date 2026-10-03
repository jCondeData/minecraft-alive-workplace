package io.github.jcondedata.aliveworkplace.work;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Whose ticket a job site's record holds (bug B15). A villager's memory of their job site outlives the block: when the
 * block is broken while they're away and the same kind is put back, the new record may be taken by another villager,
 * and that one may be in an unloaded chunk, where nothing can see them. So each level counts how often a workstation's
 * record was removed at each spot, and each villager remembers that count from when they got their job site. If the
 * count has moved on since, the record there isn't theirs: giving them another job mustn't release it.
 */
public final class JobSiteTickets extends SavedData {
	private static final String NAME = "aliveworkplace_job_site_breaks";
	/** How close to their job site a villager must be for us to note it as theirs on our own (vanilla takes one from 2 blocks). */
	private static final double NEAR = 8;

	/** spot → how often a workstation's record there was removed */
	private final Long2IntOpenHashMap breaks = new Long2IntOpenHashMap();

	/** A villager's job site and the spot's break count when they got it (saved on the villager). */
	public record Held(GlobalPos site, int breaks) {
		public static final Codec<Held> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.fieldOf("site").forGetter(Held::site),
			Codec.INT.fieldOf("breaks").forGetter(Held::breaks)
		).apply(i, Held::new));
	}

	private static JobSiteTickets get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(JobSiteTickets::new, JobSiteTickets::load, null), NAME);
	}

	/** How often a workstation's record at {@code pos} was removed. */
	static int breaks(ServerLevel level, BlockPos pos) {
		return get(level).breaks.get(pos.asLong());
	}

	/** A block changed (from ServerLevel.onBlockStateChange, before vanilla updates the records): count a workstation going. */
	public static void changed(ServerLevel level, BlockPos pos, BlockState old, BlockState now) {
		Optional<Holder<PoiType>> was = PoiTypes.forState(old);
		if (was.isEmpty() || was.equals(PoiTypes.forState(now)) || !workstation(was.get())) {
			return;
		}
		JobSiteTickets tickets = get(level);
		tickets.breaks.addTo(pos.asLong(), 1);
		tickets.setDirty();
	}

	private static boolean workstation(Holder<PoiType> type) {
		return type.is(PoiTypeTags.ACQUIRABLE_JOB_SITE) || Stations.ALL.stream().anyMatch(s -> s.poi().test(type));
	}

	/** Notes {@code villager}'s job site as theirs as it is now (they've just been given it). */
	public static void hold(ServerLevel level, Villager villager) {
		Optional<GlobalPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
		if (site.isEmpty()) {
			ModAttachments.JOB_SITE_HELD.remove(villager);
			return;
		}
		ServerLevel there = level.getServer().getLevel(site.get().dimension());
		if (there != null) {
			ModAttachments.JOB_SITE_HELD.set(villager, new Held(site.get(), breaks(there, site.get().pos())));
		}
	}

	/**
	 * Each tick (mixin/VillagerMixin): notes a job site vanilla gave {@code villager}. Only near it: a villager loaded from
	 * a save made before this was kept may remember a site far off that was broken and put back since, which isn't theirs.
	 */
	public static void tick(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		Optional<GlobalPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
		@Nullable Held held = ModAttachments.JOB_SITE_HELD.get(villager);
		if (site.isEmpty()) {
			if (held != null) {
				ModAttachments.JOB_SITE_HELD.remove(villager);
			}
		} else if ((held == null || !held.site().equals(site.get())) && site.get().dimension().equals(level.dimension())
			&& site.get().pos().distToCenterSqr(villager.position()) <= NEAR * NEAR) {
			hold(level, villager);
		}
	}

	/**
	 * Whether the record at {@code site} holds {@code villager}'s ticket: yes or no when we know, empty when we never saw
	 * them get it (then only the loaded villagers can tell).
	 */
	static Optional<Boolean> theirs(ServerLevel level, Villager villager, GlobalPos site) {
		@Nullable Held held = ModAttachments.JOB_SITE_HELD.get(villager);
		if (held == null || !held.site().equals(site) || !site.dimension().equals(level.dimension())) {
			return Optional.empty();
		}
		return Optional.of(held.breaks() == breaks(level, site.pos()));
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		long[] spots = new long[breaks.size()];
		int[] counts = new int[breaks.size()];
		int i = 0;
		for (Long2IntMap.Entry e : breaks.long2IntEntrySet()) {
			spots[i] = e.getLongKey();
			counts[i++] = e.getIntValue();
		}
		tag.put("spots", new LongArrayTag(spots));
		tag.putIntArray("counts", counts);
		return tag;
	}

	private static JobSiteTickets load(CompoundTag tag, HolderLookup.Provider registries) {
		JobSiteTickets out = new JobSiteTickets();
		long[] spots = Nbt.getLongArray(tag, "spots");
		int[] counts = Nbt.getIntArray(tag, "counts");
		for (int i = 0; i < Math.min(spots.length, counts.length); i++) {
			out.breaks.put(spots[i], counts[i]);
		}
		return out;
	}
}
