package io.github.jcondedata.aliveworkplace.cup;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * The Festival Cup's champions (ROADMAP 28.21). The champion's village holds the host's Cup until a new champion is
 * crowned there: it's named on the roll of champions (the Cup page), in every circuit village's chronicle (the Cup's
 * entry, 28.19), on its hall's name tooltip, in other halls' trade-route lists and on its Village Map's legend
 * ("Holders of the Thornholm Cup"). The Cup banner, a banner of our pattern {@link #PATTERN} in the theme's colours,
 * flies over the holder: on its Arena III's champion's pole, else on top of its Village Hall where there's room, and
 * comes down when the title passes. A village that isn't loaded gets (or loses) its banner at its hall's next round.
 * Every villager of a holder has the mood reason "our village holds the Cup" ({@link #MOOD}). The winning Leader (or
 * host trainer) gets {@link #WIN_BONUSES} extra win bonuses of trainer XP; the defending champion is seeded first at
 * the next Cup ({@link Cups#seed(List, UUID)}). With {@code festivalCup} off nobody holds a Cup: banners come down at the
 * next round and the mood goes.
 */
public final class CupChampions {
	/** The Cup banner's pattern (data/aliveworkplace/banner_pattern/cup.json). */
	public static final ResourceKey<BannerPattern> PATTERN = ResourceKey.create(Registries.BANNER_PATTERN, AliveWorkplace.id("cup"));
	/** The holders' mood. */
	public static final int MOOD = 5;
	/** Extra win bonuses of trainer XP for the champion. */
	public static final int WIN_BONUSES = 2;
	/** How far above the hall a roof may be for the banner to go on top of it. */
	static final int ROOF_REACH = 24;

	private CupChampions() {
	}

	// ---------------------------------------------------------------- who holds what

	/** The newest champion of a host's Cup, or null. */
	@Nullable
	public static CupData.Champion holder(CupData.Cup cup) {
		return cup.champions.isEmpty() ? null : cup.champions.get(cup.champions.size() - 1);
	}

	/** A Cup a village holds: the host and the win. */
	public record Held(BlockPos host, CupData.Champion champion) {
	}

	/** The Cups the village round {@code hall} holds, newest first; none with {@code festivalCup} off. */
	public static List<Held> held(ServerLevel level, BlockPos hall) {
		List<Held> out = new ArrayList<>();
		if (!Cups.ENABLED) {
			return out;
		}
		for (Map.Entry<BlockPos, CupData.Cup> e : CupData.get(level).all().entrySet()) {
			CupData.Champion c = holder(e.getValue());
			if (c != null && c.village().equals(hall)) {
				out.add(new Held(e.getKey(), c));
			}
		}
		out.sort(Comparator.comparingLong((Held h) -> -h.champion().day()));
		return out;
	}

	public static boolean holds(ServerLevel level, BlockPos hall) {
		return !held(level, hall).isEmpty();
	}

	/** "the Thornholm Cup": the host's Cup, by the host's name. */
	public static Component title(ServerLevel level, BlockPos host) {
		return Component.translatable("cup.aliveworkplace.title", CupBouts.villageName(level, host));
	}

	/** "Holders of the Thornholm Cup", one line a Cup: for the trade-route lists and the Village Map's legend. */
	public static List<Component> holderLines(ServerLevel level, BlockPos hall) {
		return held(level, hall).stream().map(h -> (Component) Component.translatable("screen.aliveworkplace.cup.holders", title(level, h.host()))).toList();
	}

	/** "Holders of the Thornholm Cup, won on day 12", one line a Cup: for the hall's name tooltip. */
	public static List<Component> hallLines(ServerLevel level, BlockPos hall) {
		return held(level, hall).stream().map(h -> (Component) Component.translatable("screen.aliveworkplace.cup.holders_since", title(level, h.host()),
			h.champion().day())).toList();
	}

	// ---------------------------------------------------------------- the pride

	/** "our village holds the Cup" (+{@link #MOOD}) for a villager of a holder, else null. Delegates are guests. */
	@Nullable
	public static LegendPowers.MoodReason mood(ServerLevel level, Villager villager) {
		if (!Cups.ENABLED || CupDays.isDelegate(villager) || !anyHolder(level)) {
			return null;
		}
		Optional<BlockPos> hall = VillageHalls.nearest(level, villager.blockPosition());
		return hall.isPresent() && holds(level, hall.get()) ? new LegendPowers.MoodReason(Component.translatable("mood.aliveworkplace.reason.cup"), MOOD) : null;
	}

	private static boolean anyHolder(ServerLevel level) {
		return CupData.get(level).all().values().stream().anyMatch(c -> !c.champions.isEmpty());
	}

	// ---------------------------------------------------------------- crowned

	/**
	 * A champion is crowned at {@code host} (its win already on the roll; {@code before} held it until now, or null): the
	 * champion's win bonuses, the banner down at the old holder and up at the new where they're loaded (the others at
	 * their next round), and the moods worked out again.
	 */
	static void crowned(ServerLevel level, BlockPos host, CupData.Entrant champ, @Nullable BlockPos before) {
		bonus(level, champ);
		List<BlockPos> villages = new ArrayList<>();
		if (before != null && !before.equals(champ.village())) {
			villages.add(before);
		}
		villages.add(champ.village());
		for (BlockPos hall : villages) {
			if (level.isLoaded(hall) && level.getBlockEntity(hall) instanceof VillageHallBlockEntity) {
				fly(level, hall);
				level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Villager::isAlive).forEach(Moods::forget);
			}
		}
	}

	/** {@link #WIN_BONUSES} win bonuses of trainer XP for a villager champion: given if they're here, else banked on their village. */
	static void bonus(ServerLevel level, CupData.Entrant champ) {
		if (champ.kind() == CupData.Kind.PLAYER) {
			return; // a player gets the purse only
		}
		int xp = WIN_BONUSES * Trainers.XP_FOR_WIN;
		if (level.getEntity(champ.id()) instanceof Villager v && v.isAlive()) {
			BuilderLevels.addXp(level, v, Guilds.trainerXp(v, xp), null);
		} else {
			Caravans.Data.get(level).bankXp(champ.village(), champ.id(), xp);
		}
	}

	// ---------------------------------------------------------------- the banner

	/** The village's round: its Cup banner put up if it holds a Cup and none flies, taken down if it no longer does. */
	public static void round(ServerLevel level, BlockPos hall) {
		fly(level, hall);
	}

	/** Puts the Cup banner up over the village round {@code hall} if it holds a Cup, or takes it down if it doesn't. */
	public static void fly(ServerLevel level, BlockPos hall) {
		CupData data = CupData.get(level);
		List<Held> held = held(level, hall);
		List<BlockPos> flying = data.banners(hall).stream().filter(p -> isCupBanner(level, p)).toList();
		if (held.isEmpty()) {
			flying.forEach(p -> level.removeBlock(p, false));
			data.setBanners(hall, List.of());
			return;
		}
		if (!flying.isEmpty()) {
			if (flying.size() != data.banners(hall).size()) {
				data.setBanners(hall, flying);
			}
			return;
		}
		Optional<Holder.Reference<BannerPattern>> pattern = pattern(level);
		if (pattern.isEmpty()) {
			AliveWorkplace.LOG.warn("The Cup banner pattern {} isn't loaded: no Cup banner over {}", PATTERN.location(), hall);
			return;
		}
		Colours colours = colours(CupThemes.get(held.get(0).champion().theme()));
		VillageBanners.Colours design = new VillageBanners.Colours(colours.field(),
			new BannerPatternLayers.Builder().add(pattern.get(), colours.cup()).build());
		data.setBanners(hall, put(level, hall, design));
	}

	/** The banner's colours: the field and the cup. */
	public record Colours(DyeColor field, DyeColor cup) {
	}

	/**
	 * The theme's colours as dyes: the cup in the dye nearest its first firework colour, the field in the one nearest its
	 * second (white, or black under a white cup, with only one); a gold cup on red without a theme.
	 */
	public static Colours colours(@Nullable CupThemes.Theme theme) {
		if (theme == null || theme.fireworks().isEmpty()) {
			return new Colours(DyeColor.RED, DyeColor.YELLOW);
		}
		DyeColor cup = nearest(theme.fireworks().get(0));
		DyeColor field = theme.fireworks().size() > 1 ? nearest(theme.fireworks().get(1)) : null;
		if (field == null || field == cup) {
			field = cup == DyeColor.WHITE ? DyeColor.BLACK : DyeColor.WHITE;
		}
		return new Colours(field, cup);
	}

	/** The dye whose firework colour is nearest {@code rgb}. */
	public static DyeColor nearest(int rgb) {
		DyeColor best = DyeColor.WHITE;
		long bestD = Long.MAX_VALUE;
		for (DyeColor dye : DyeColor.values()) {
			int c = dye.getFireworkColor();
			long dr = ((c >> 16) & 0xFF) - ((rgb >> 16) & 0xFF);
			long dg = ((c >> 8) & 0xFF) - ((rgb >> 8) & 0xFF);
			long db = (c & 0xFF) - (rgb & 0xFF);
			long d = dr * dr + dg * dg + db * db;
			if (d < bestD) {
				bestD = d;
				best = dye;
			}
		}
		return best;
	}

	static Optional<Holder.Reference<BannerPattern>> pattern(ServerLevel level) {
		Registry<BannerPattern> registry = Lookup.registry(level.registryAccess(), Registries.BANNER_PATTERN);
		return Lookup.holder(registry, PATTERN);
	}

	/** True if a banner with the Cup pattern stands or hangs at {@code pos}. */
	public static boolean isCupBanner(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BannerBlockEntity banner
			&& banner.getPatterns().layers().stream().anyMatch(l -> l.pattern().is(PATTERN));
	}

	/**
	 * Puts up the banner: on both faces of the champion's pole of the village's Arena III (where they're free), else on
	 * the hall block if there's room above it, else on the roof over the hall. Returns where it went (empty: no room).
	 */
	static List<BlockPos> put(ServerLevel level, BlockPos hall, VillageBanners.Colours design) {
		List<BlockPos> out = new ArrayList<>();
		Optional<Arenas.Arena> arena = Arenas.find(level, hall);
		// the village's own Arena: one whose nearest hall is this one (a neighbour's within reach isn't ours)
		if (arena.isPresent() && arena.get().championPole().isPresent() && VillageHalls.nearest(level, arena.get().ring()).map(hall::equals).orElse(false)) {
			BlockPos pole = arena.get().championPole().get();
			if (!level.getBlockState(pole).isAir()) {
				for (BlockPos side : List.of(Arenas.world(arena.get().placement(), Arenas.CHAMPION_POLE.north()),
					Arenas.world(arena.get().placement(), Arenas.CHAMPION_POLE.south()))) {
					Direction facing = Direction.getNearest(side.getX() - pole.getX(), 0, side.getZ() - pole.getZ());
					if (level.getBlockState(side).canBeReplaced() && level.getFluidState(side).isEmpty()) {
						Block wall = VillageBanners.wallBanner(design.base());
						BlockState state = wall.defaultBlockState().setValue(WallBannerBlock.FACING, facing);
						set(level, side, state, design);
						out.add(side.immutable());
					}
				}
			}
			if (!out.isEmpty()) {
				return out;
			}
		}
		BlockPos spot = standing(level, hall);
		if (spot != null) {
			BlockState state = BannerBlock.byColor(design.base()).defaultBlockState().setValue(BannerBlock.ROTATION, 0);
			set(level, spot, state, design);
			out.add(spot.immutable());
		}
		return out;
	}

	/** Where a standing banner goes on top of the hall: right on the hall block if two blocks are free above it, else on the roof over it. */
	@Nullable
	static BlockPos standing(ServerLevel level, BlockPos hall) {
		BlockPos above = hall.above();
		if (free(level, above) && free(level, above.above())) {
			return above;
		}
		BlockPos roof = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hall);
		if (roof.getY() > hall.getY() && roof.getY() - hall.getY() <= ROOF_REACH && free(level, roof)
			&& level.getBlockState(roof.below()).isFaceSturdy(level, roof.below(), Direction.UP)) {
			return roof;
		}
		return null;
	}

	private static boolean free(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).isAir();
	}

	private static void set(ServerLevel level, BlockPos pos, BlockState state, VillageBanners.Colours design) {
		level.setBlock(pos, state, Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof BannerBlockEntity banner) {
			banner.fromItem(design.banner(), design.base());
			banner.setChanged();
			level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
		}
	}

	// ---------------------------------------------------------------- the defending champion

	/** Who defends the host's Cup: the last champion if they're entered, else the holder village's villager entrant; null if neither. */
	@Nullable
	public static UUID defending(CupData.Cup cup, List<CupData.Entrant> entrants) {
		CupData.Champion c = holder(cup);
		if (c == null || !Cups.ENABLED) {
			return null;
		}
		if (c.id() != null && entrants.stream().anyMatch(e -> e.id().equals(c.id()))) {
			return c.id();
		}
		return entrants.stream().filter(e -> e.kind() != CupData.Kind.PLAYER && e.village().equals(c.village())).map(CupData.Entrant::id).findFirst().orElse(null);
	}
}
