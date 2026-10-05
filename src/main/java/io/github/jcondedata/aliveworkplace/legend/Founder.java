package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * The Founder (ROADMAP 29.23), a Mythic Legend {@code legends/founder.json} who keeps their own trade. They come by the
 * Founder's mood ({@link #mood}), always, at the village's first rise to City: its most experienced Master (the most
 * XP) is seized by a strange mood (29.10) asking for a block of gold, a block of emeralds and a diamond. The Masterwork
 * is the Charter of the village ({@link #charter}), a written book of its story drawn from its chronicle, signed by
 * the Founder. A failed mood passes, after the week of sulking, to the next most experienced Master who hasn't had it.
 * Their powers, worked on the hall's round:
 * <ul>
 *   <li>{@code statue} ({@link StatuePower}): the village's builders raise the Founder's statue
 *   ({@code legend/founder_statue}) on open ground near the hall, with materials from their chests; while it stands
 *   it's worth {@link StatuePower#beauty} beauty and every villager is {@link StatuePower#mood} happier;</li>
 *   <li>{@code found_villages} ({@link FoundVillagesPower}): every {@link FoundVillagesPower#days} days the hall's owner
 *   is given a Founder's Wagon: a Settler's Wagon whose camp also brings a Village Hall named "New &lt;village&gt;" and
 *   blueprints in the mother village's styles ({@link #stockCamp}). M33's colonies will let the Founder lead them.</li>
 * </ul>
 * Saved on the hall: the day the mood first came, the Masters who had it, whether the Founder was made, the last
 * wagon's day (each with a default).
 */
public final class Founder {
	public static final ResourceLocation ID = AliveWorkplace.id("founder");
	public static final ResourceLocation STATUE = AliveWorkplace.id("legend/founder_statue");
	/** How far from the hall the statue may stand (the ring searched for open ground). */
	public static final int STATUE_FROM = 5;
	public static final int STATUE_TO = 24;

	/** {@code statue}: the statue is worth {@code beauty} beauty and makes every villager {@code mood} happier. */
	public record StatuePower(int beauty, int mood) implements Power {
		public static final StatuePower DEFAULT = new StatuePower(5, 5);

		static StatuePower read(JsonObject json) {
			int beauty = json.has("beauty") ? json.get("beauty").getAsInt() : 5;
			int mood = json.has("mood") ? json.get("mood").getAsInt() : 5;
			if (beauty < 0 || mood < 0) {
				throw new IllegalArgumentException("'beauty' or 'mood' below 0");
			}
			return new StatuePower(beauty, mood);
		}

		@Override
		public String type() {
			return "statue";
		}

		@Override
		public Component describe() {
			return Component.translatable("legend.aliveworkplace.power.statue", beauty, mood);
		}
	}

	/** {@code found_villages}: a Founder's Wagon for the hall's owner every {@code days} days. */
	public record FoundVillagesPower(int days) implements Power {
		public static final FoundVillagesPower DEFAULT = new FoundVillagesPower(7);

		static FoundVillagesPower read(JsonObject json) {
			int days = json.has("days") ? json.get("days").getAsInt() : 7;
			if (days < 1) {
				throw new IllegalArgumentException("'days' below 1");
			}
			return new FoundVillagesPower(days);
		}

		@Override
		public String type() {
			return "found_villages";
		}

		@Override
		public Component describe() {
			return Component.translatable("legend.aliveworkplace.power.found_villages", days);
		}
	}

	static void register() {
		Powers.register("statue", StatuePower::read);
		Powers.register("found_villages", FoundVillagesPower::read);
	}

	/** Whether {@code legend} comes by the Founder's mood (an {@code inspired} way with {@code "founder": true}). */
	public static boolean isFounder(Legend legend) {
		return legend.ways("inspired").stream().anyMatch(w -> w.has("founder") && w.get("founder").getAsBoolean());
	}

	/** Whether {@code legend} keeps the trade they had (an {@code inspired} way with {@code "own_trade": true}). */
	public static boolean ownTrade(Legend legend) {
		return legend.ways("inspired").stream().anyMatch(w -> w.has("own_trade") && w.get("own_trade").getAsBoolean());
	}

	/** The Legend the Founder's mood leads to (the first loaded file with that way), if any. */
	public static Optional<Legend> legend() {
		return Legends.all().stream().filter(Founder::isFounder).min(Comparator.comparing(l -> l.id().toString()));
	}

	/** The hall's round: the Founder's mood, then the statue and the wagon of a settled Founder. */
	public static void round(ServerLevel level, BlockPos hall) {
		if (!Legends.ENABLED || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity)) {
			return;
		}
		long today = Chronicle.day(level);
		mood(level, hall, today);
		statue(level, hall);
		wagon(level, hall, today);
		synchronized (STANDS) {
			STANDS.remove(level); // counted again when next asked
		}
	}

	// --- The Founder's mood ------------------------------------------------------------------------------------------

	/**
	 * The Founder's mood in the village round {@code hall} on {@code today}: once the village first meets the Founder's
	 * conditions (the first rise to City), its most experienced Master is seized; while that mood or its sulk lasts,
	 * nothing; once it failed and the sulk is over (or that Master is gone), the next most experienced Master who hasn't
	 * had it. Never once a Founder was made, nor while another strange mood is on or the Mythic slot is taken. Returns
	 * the Master seized now, if any.
	 */
	public static Optional<Villager> mood(ServerLevel level, BlockPos hall, long today) {
		if (blocked(level, hall) != null || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return Optional.empty();
		}
		Legend legend = legend().orElseThrow();
		Villager next = nextMaster(level, hall, entity.founderTried()).orElseThrow();
		if (entity.founderMoodDay() == 0) {
			entity.setFounderMoodDay(Math.max(1, today));
		}
		entity.addFounderTried(next.getUUID());
		StrangeMoods.start(level, hall, next, legend, today, level.random);
		Component village = VillageHalls.name(level, hall);
		for (ServerPlayer player : LegendSlots.audience(level, hall, next.blockPosition(), legend.rarity())) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.founder.mood", next.getDisplayName(), village).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		return Optional.of(next);
	}

	/** Why the Founder's mood can't come to the village round {@code hall} now, or null when it comes. */
	@Nullable
	public static String blocked(ServerLevel level, BlockPos hall) {
		if (!Legends.ENABLED || !StrangeMoods.ENABLED) {
			return "off";
		}
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return "no hall";
		}
		if (entity.founderMade()) {
			return "the Founder was made";
		}
		Legend legend = legend().orElse(null);
		if (legend == null || StrangeMoods.masterworkItem(legend) == Items.AIR || StrangeMoods.pool(legend).size() < StrangeMoods.MATERIALS) {
			return "no Founder file";
		}
		if (entity.founderMoodDay() == 0) {
			for (Condition c : legend.conditions()) {
				if (!c.met(level, hall)) {
					return "condition " + c.type();
				}
			}
		} else if (founderMoodOn(level, hall, legend)) {
			return "a Founder's mood or sulk on"; // a mood or its sulk still on
		}
		Villager other = StrangeMoods.moodIn(level, hall);
		if (other != null) {
			return "a strange mood on: " + other.getUUID();
		}
		if (LegendSlots.whyNot(level, hall, legend, null).isPresent()) {
			return "no Mythic slot: " + LegendSlots.whyNot(level, hall, legend, null).get().getString();
		}
		if (nextMaster(level, hall, entity.founderTried()).isEmpty()) {
			return "no Master left";
		}
		return null;
	}

	/** Whether a villager of the village round {@code hall} is in the Founder's mood or sulking after it. */
	static boolean founderMoodOn(ServerLevel level, BlockPos hall, Legend legend) {
		return !level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> {
			StrangeMood m = ModAttachments.STRANGE_MOOD.get(v);
			return v.isAlive() && m != null && m.legend().equals(legend.id()) && (m.hall().isEmpty() || m.hall().get().equals(hall));
		}).isEmpty();
	}

	/**
	 * The village's most experienced Master who hasn't had the Founder's mood: grown, a Master (level 5), not a Legend,
	 * not in a strange mood, living in this village; the most XP first (ties: by UUID, the same every time).
	 */
	public static Optional<Villager> nextMaster(ServerLevel level, BlockPos hall, List<UUID> tried) {
		return level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && !v.isBaby()
				&& v.getVillagerData().getLevel() >= VillagerData.MAX_VILLAGER_LEVEL && !tried.contains(v.getUUID())
				&& !ModAttachments.LEGEND.has(v) && !ModAttachments.STRANGE_MOOD.has(v)
				&& VillageHalls.nearest(level, v.blockPosition()).equals(Optional.of(hall)))
			.stream()
			.min(Comparator.comparingInt((Villager v) -> -v.getVillagerXp()).thenComparing(Villager::getUUID));
	}

	/** The Founder's mood succeeded and {@code villager} became the Founder: it never comes to that village again. */
	static void made(ServerLevel level, @Nullable BlockPos hall) {
		if (hall != null && level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			entity.setFounderMade(true);
		}
	}

	// --- The Charter -------------------------------------------------------------------------------------------------

	/** Lines per page of the Charter. */
	static final int PER_PAGE = 3;

	/**
	 * The Charter of the village: a written book titled "The Charter of &lt;village&gt;", by the Founder, a cover page,
	 * then the village's story from its chronicle ({@link #story}), three days to a page, and the Founder's signature;
	 * marked a Masterwork and with a glint.
	 */
	public static ItemStack charter(ServerLevel level, @Nullable BlockPos hall, Villager founder, Legend legend, long day) {
		String village = hall != null ? VillageHalls.name(level, hall).getString()
			: Component.translatable("message.aliveworkplace.legend.the_wilds").getString();
		String name = founder.getName().getString();
		List<Filterable<Component>> pages = new ArrayList<>();
		pages.add(Filterable.passThrough(Component.translatable("book.aliveworkplace.charter.cover", village, name, day)));
		List<Chronicle.Entry> story = hall != null ? story(level, hall) : List.of();
		for (int i = 0; i < story.size(); i += PER_PAGE) {
			MutableComponent page = Component.empty();
			for (int j = i; j < Math.min(story.size(), i + PER_PAGE); j++) {
				if (j > i) {
					page.append("\n\n");
				}
				page.append(Component.translatable("book.aliveworkplace.charter.entry", story.get(j).day(), story.get(j).text()));
			}
			pages.add(Filterable.passThrough(page));
		}
		pages.add(Filterable.passThrough(Component.translatable("book.aliveworkplace.charter.signed", name, village, day)));
		String title = Component.translatable("book.aliveworkplace.charter.title", village).getString();
		if (title.length() > WrittenBookContent.TITLE_MAX_LENGTH) {
			title = title.substring(0, WrittenBookContent.TITLE_MAX_LENGTH);
		}
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), name, 0, pages, true));
		book.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(StrangeMoods.MARK, true);
		tag.putString("legend", legend.id().toString());
		book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return book;
	}

	/**
	 * The village's story, oldest first, from the chronicle of the hall at {@code hall}: its founding, each rank, the
	 * Legends who settled, its first wedding, and the raids it beat.
	 */
	public static List<Chronicle.Entry> story(ServerLevel level, BlockPos hall) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return List.of();
		}
		List<Chronicle.Entry> all = new ArrayList<>(entity.chronicle());
		all.sort(Comparator.comparingLong(Chronicle.Entry::day)); // stable: the same day keeps its order
		List<Chronicle.Entry> out = new ArrayList<>();
		boolean founded = false;
		boolean wedding = false;
		for (Chronicle.Entry e : all) {
			String key = e.text().getContents() instanceof TranslatableContents t ? t.getKey() : "";
			boolean keep = switch (e.kind()) {
				case FOUNDED -> !founded;
				case RANK -> true;
				case LEGEND -> key.equals("chronicle.aliveworkplace.legend.settled");
				case WEDDING -> !wedding;
				case RAID -> key.equals("chronicle.aliveworkplace.raid_won");
				default -> false;
			};
			if (keep) {
				founded |= e.kind() == Chronicle.Kind.FOUNDED;
				wedding |= e.kind() == Chronicle.Kind.WEDDING;
				out.add(e);
			}
		}
		return out;
	}

	// --- The statue --------------------------------------------------------------------------------------------------

	/** The settled Founder of the village round {@code hall} (powers working), if any. */
	static Optional<LegendPowers.Active> settled(ServerLevel level, BlockPos hall) {
		return LegendPowers.settled(level).stream()
			.filter(a -> a.villager().isAlive() && isFounder(a.legend()))
			.filter(a -> a.data().hall().map(hall::equals).orElseGet(() -> VillageHalls.nearest(level, a.villager().blockPosition()).map(hall::equals).orElse(false)))
			.findFirst();
	}

	/** The statue's site under way in the village round {@code hall}, or null. */
	@Nullable
	public static BuildSite statueSite(ServerLevel level, BlockPos hall) {
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			if (site.structure().equals(STATUE) && site.placement().dimension().equals(Ids.of(level.dimension()))
				&& VillageHalls.area(hall).contains(site.placement().origin().getCenter())) {
				return site;
			}
		}
		return null;
	}

	/** The statues the village's builders finished round {@code hall}. */
	public static List<BuildSiteManager.Finished> statues(ServerLevel level, BlockPos hall) {
		return BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS).stream().filter(f -> f.structure().equals(STATUE)).toList();
	}

	/**
	 * A settled Founder with the {@code statue} power and no statue finished or under way: the least busy builder of the
	 * village is handed the statue on open ground near the hall. Returns the site, if one was started.
	 */
	@Nullable
	public static BuildSite statue(ServerLevel level, BlockPos hall) {
		Optional<LegendPowers.Active> founder = settled(level, hall);
		if (founder.isEmpty() || founder.get().legend().powers(StatuePower.class).isEmpty() || founder.get().data().onStrike()
			|| statueSite(level, hall) != null || !statues(level, hall).isEmpty()) {
			return null;
		}
		Blueprint blueprint = BlueprintLibrary.get(level, STATUE).orElse(null);
		if (blueprint == null) {
			return null;
		}
		BlueprintData.Placement placement = spot(level, hall, blueprint).orElse(null);
		if (placement == null) {
			return null;
		}
		BlockPos centre = BlueprintOutline.bounds(placement, blueprint.size()).getCenter();
		Villager builder = level.getEntities(EntityType.VILLAGER, VillageHalls.area(hall), v -> v.isAlive() && Builders.isBuilder(v) && Builders.benchPos(v).isPresent())
			.stream()
			.filter(v -> Math.sqrt(centre.distSqr(Builders.benchPos(v).orElseThrow())) <= Builders.MAX_SITE_DISTANCE)
			.filter(v -> busy(level, v) <= Builders.MAX_QUEUE)
			.min(Comparator.comparingInt((Villager v) -> busy(level, v)).thenComparingDouble(v -> v.blockPosition().distSqr(centre)))
			.orElse(null);
		if (builder == null) {
			return null;
		}
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		UUID owner = entity != null && entity.owner() != null ? entity.owner() : founder.get().villager().getUUID();
		String ownerName = entity != null && entity.owner() != null ? entity.ownerName() : "";
		BuildSite site = Builders.activeSite(level, builder) != null
			? Builders.enqueue(level, builder, owner, ownerName, STATUE, placement)
			: Builders.start(level, builder, owner, ownerName, STATUE, placement);
		site.setStewardHall(hall);
		BuildSiteManager.get(level).setDirty();
		Chronicle.record(level, hall, Chronicle.Kind.LEGEND, Component.translatable("chronicle.aliveworkplace.founder.statue",
			founder.get().villager().getDisplayName(), builder.getDisplayName()));
		return site;
	}

	private static int busy(ServerLevel level, Villager builder) {
		return Builders.activeSite(level, builder) == null ? 0 : 1 + Builders.queue(level, builder).size();
	}

	/**
	 * Open ground for the statue near the hall: ring by ring from {@link #STATUE_FROM} to {@link #STATUE_TO} blocks out,
	 * the first spot whose footprint (a block of room round it) is level within a block, dry, clear above, and touches no
	 * other site or finished building and not the hall; its front faces the hall.
	 */
	public static Optional<BlueprintData.Placement> spot(ServerLevel level, BlockPos hall, Blueprint blueprint) {
		List<BoundingBox> taken = new ArrayList<>();
		for (BuildSite s : BuildSiteManager.get(level).all()) {
			BlueprintLibrary.get(level, s.structure()).ifPresent(b -> taken.add(BlueprintOutline.bounds(s.placement(), b.size())));
		}
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS + STATUE_TO)) {
			BlueprintLibrary.get(level, f.structure()).ifPresent(b -> taken.add(BlueprintOutline.bounds(f.placement(), b.size())));
		}
		ResourceLocation dimension = Ids.of(level.dimension());
		for (int r = STATUE_FROM; r <= STATUE_TO; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int x = hall.getX() + dx;
					int z = hall.getZ() + dz;
					int y = ground(level, x, hall.getY(), z);
					Direction toHall = Direction.getNearest(-dx, 0, -dz);
					BlueprintData.Placement placement = BlueprintItem.placementAt(dimension, blueprint.size(), new BlockPos(x, y, z), BlueprintItem.rotationFacing(toHall));
					BoundingBox box = BlueprintOutline.bounds(placement, blueprint.size());
					if (y != Integer.MIN_VALUE && fits(level, hall, box, taken)) {
						return Optional.of(placement);
					}
				}
			}
		}
		return Optional.empty();
	}

	/**
	 * The first open spot above solid ground in the column at {@code x, z}, looking from 8 blocks above {@code near}
	 * down to 8 below (leaves and plants aren't ground); {@code Integer.MIN_VALUE} when there's none.
	 */
	static int ground(ServerLevel level, int x, int near, int z) {
		for (int y = near + 8; y >= near - 8; y--) {
			BlockPos p = new BlockPos(x, y, z);
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(p);
			if (state.blocksMotion() && !state.is(net.minecraft.tags.BlockTags.LEAVES) && !level.getBlockState(p.above()).blocksMotion()) {
				return y + 1;
			}
		}
		return Integer.MIN_VALUE;
	}

	private static boolean fits(ServerLevel level, BlockPos hall, BoundingBox box, List<BoundingBox> taken) {
		BoundingBox room = box.inflatedBy(1);
		if (room.isInside(hall)) {
			return false;
		}
		for (BoundingBox t : taken) {
			if (t.intersects(room)) {
				return false;
			}
		}
		for (int x = room.minX(); x <= room.maxX(); x++) {
			for (int z = room.minZ(); z <= room.maxZ(); z++) {
				int top = ground(level, x, box.minY(), z);
				if (Math.abs(top - box.minY()) > 1 || !level.getFluidState(new BlockPos(x, top - 1, z)).isEmpty()
					|| level.getBlockState(new BlockPos(x, top - 1, z)).is(ModBlocks.VILLAGE_HALL)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Per level: per hall, the game time the statue was last looked at and whether it stood then. */
	private static final Map<ServerLevel, Map<BlockPos, long[]>> STANDS = new WeakHashMap<>();

	/**
	 * Whether a statue the builders finished still stands in the village round {@code hall}: the figure's head (the top
	 * of the footprint's middle) is still there. Looked at again every 100 ticks at most (moods ask often).
	 */
	public static boolean stands(ServerLevel level, BlockPos hall) {
		long now = level.getGameTime();
		synchronized (STANDS) {
			long[] seen = STANDS.computeIfAbsent(level, l -> new HashMap<>()).get(hall);
			if (seen != null && now - seen[0] < 100 && now >= seen[0]) {
				return seen[1] == 1;
			}
		}
		boolean standing = false;
		for (BuildSiteManager.Finished f : statues(level, hall)) {
			Optional<Blueprint> b = BlueprintLibrary.get(level, STATUE);
			if (b.isEmpty()) {
				break;
			}
			BoundingBox box = BlueprintOutline.bounds(f.placement(), b.get().size());
			BlockPos middle = box.getCenter();
			for (int y = box.maxY(); y >= box.maxY() - 2 && !standing; y--) {
				standing = !level.getBlockState(new BlockPos(middle.getX(), y, middle.getZ())).isAir();
			}
			if (standing) {
				break;
			}
		}
		synchronized (STANDS) {
			STANDS.computeIfAbsent(level, l -> new HashMap<>()).put(hall.immutable(), new long[]{now, standing ? 1 : 0});
		}
		return standing;
	}

	/** Forgets whether the statues stood (counted again when next asked). */
	public static void forgetStands() {
		synchronized (STANDS) {
			STANDS.clear();
		}
	}

	private static StatuePower statuePower() {
		return legend().flatMap(l -> l.powers(StatuePower.class).stream().findFirst()).orElse(null);
	}

	/** The beauty the Founder's statue adds to the village round {@code hall} while it stands. */
	public static int beauty(ServerLevel level, BlockPos hall) {
		StatuePower power = Legends.ENABLED ? statuePower() : null;
		return power != null && stands(level, hall) ? power.beauty() : 0;
	}

	/** The mood the Founder's statue gives every villager of its village while it stands (null: none). */
	@Nullable
	public static LegendPowers.MoodReason statueMood(ServerLevel level, Villager villager) {
		StatuePower power = Legends.ENABLED ? statuePower() : null;
		if (power == null || power.mood() == 0) {
			return null;
		}
		Optional<BlockPos> hall = VillageHalls.nearest(level, villager.blockPosition());
		if (hall.isEmpty() || !stands(level, hall.get())) {
			return null;
		}
		return new LegendPowers.MoodReason(Component.translatable("mood.aliveworkplace.reason.founder_statue"), power.mood());
	}

	// --- The Founder's Wagon -----------------------------------------------------------------------------------------

	/**
	 * A settled Founder with {@code found_villages}: when the last wagon was {@code days} days ago or more (or never),
	 * the hall's owner, if online, is given a Founder's Wagon. Returns the wagon given, if any.
	 */
	public static ItemStack wagon(ServerLevel level, BlockPos hall, long today) {
		Optional<LegendPowers.Active> founder = settled(level, hall);
		if (founder.isEmpty() || founder.get().data().onStrike() || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return ItemStack.EMPTY;
		}
		FoundVillagesPower power = founder.get().legend().powers(FoundVillagesPower.class).stream().findFirst().orElse(null);
		if (power == null || entity.founderWagonDay() > 0 && today - entity.founderWagonDay() < power.days() || entity.owner() == null) {
			return ItemStack.EMPTY;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(entity.owner());
		if (owner == null) {
			return ItemStack.EMPTY;
		}
		ItemStack wagon = wagonFor(level, hall);
		ItemStack record = wagon.copy();
		if (!owner.getInventory().add(wagon)) {
			level.addFreshEntity(new ItemEntity(level, owner.getX(), owner.getY(), owner.getZ(), wagon));
		}
		entity.setFounderWagonDay(today);
		Component village = VillageHalls.name(level, hall);
		Chat.chat(owner, Component.translatable("message.aliveworkplace.founder.wagon", founder.get().villager().getDisplayName(), village)
			.withStyle(ChatFormatting.GOLD));
		Chronicle.record(level, hall, Chronicle.Kind.LEGEND, Component.translatable("chronicle.aliveworkplace.founder.wagon",
			founder.get().villager().getDisplayName(), owner.getDisplayName()));
		return record;
	}

	/** A Founder's Wagon from the village round {@code hall}: its name and its building styles, most used first. */
	public static ItemStack wagonFor(ServerLevel level, BlockPos hall) {
		ItemStack wagon = new ItemStack(ModItems.FOUNDERS_WAGON);
		CompoundTag tag = new CompoundTag();
		tag.putString("village", VillageHalls.name(level, hall).getString());
		ListTag styles = new ListTag();
		for (String s : styles(level, hall)) {
			styles.add(StringTag.valueOf(s));
		}
		tag.put("styles", styles);
		wagon.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return wagon;
	}

	/** The building styles of what the village's builders finished round {@code hall}, most used first. */
	public static List<String> styles(ServerLevel level, BlockPos hall) {
		Map<String, Integer> count = new LinkedHashMap<>();
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS)) {
			BlueprintStyles.styleOf(f.structure()).ifPresent(s -> count.merge(s, 1, Integer::sum));
		}
		List<String> out = new ArrayList<>(count.keySet());
		out.sort(Comparator.comparingInt((String s) -> -count.get(s)));
		return out;
	}

	/**
	 * A Founder's Wagon made camp in {@code box}: the camp's chest is filled now, its Village Hall named "New
	 * &lt;village&gt;" (one is added if the chest has none), and each blueprint redrawn in the first of the mother
	 * village's styles it comes in.
	 */
	public static void stockCamp(ServerLevel level, @Nullable Player player, BoundingBox box, ItemStack wagon) {
		CompoundTag tag = wagon.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		String village = tag.getString("village");
		List<String> styles = new ArrayList<>();
		ListTag list = tag.getList("styles", 8);
		for (int i = 0; i < list.size(); i++) {
			styles.add(list.getString(i));
		}
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!(level.getBlockEntity(p) instanceof ChestBlockEntity chest)) {
				continue;
			}
			chest.unpackLootTable(player);
			boolean hall = false;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack stack = chest.getItem(i);
				if (stack.is(ModBlocks.VILLAGE_HALL.asItem())) {
					stack.set(DataComponents.CUSTOM_NAME, newName(village));
					hall = true;
				} else {
					Optional<BlueprintData> data = BlueprintItem.data(stack);
					if (data.isPresent()) {
						chest.setItem(i, styled(level, data.get(), styles, stack));
					}
				}
			}
			if (!hall) {
				ItemStack named = new ItemStack(ModBlocks.VILLAGE_HALL);
				named.set(DataComponents.CUSTOM_NAME, newName(village));
				for (int i = 0; i < chest.getContainerSize() && !named.isEmpty(); i++) {
					if (chest.getItem(i).isEmpty()) {
						chest.setItem(i, named);
						named = ItemStack.EMPTY;
					}
				}
			}
			chest.setChanged();
			return; // the camp has the one chest
		}
	}

	/** "New Oakbrook" (no italics: it's the hall's name once placed). */
	public static Component newName(String village) {
		String name = Component.translatable("item.aliveworkplace.founders_wagon.new_village", village.isEmpty()
			? Component.translatable("message.aliveworkplace.legend.the_wilds") : Component.literal(village)).getString();
		return Component.literal(name).withStyle(s -> s.withItalic(false));
	}

	private static ItemStack styled(ServerLevel level, BlueprintData data, List<String> styles, ItemStack stack) {
		ResourceLocation base = BlueprintStyles.base(data.structure());
		for (String style : styles) {
			ResourceLocation id = BlueprintStyles.styled(base, style);
			Optional<Blueprint> b = BlueprintLibrary.get(level, id);
			if (b.isPresent()) {
				ItemStack out = BlueprintItem.create(id, b.get().size());
				out.setCount(stack.getCount());
				return out;
			}
		}
		return stack;
	}

	private Founder() {
	}
}
