package io.github.jcondedata.aliveworkplace.story;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.guard.TrainingDummyBlock;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * The eight reforms as stories (ROADMAP 31.22). With {@code storyArcs} on, an edict whose reform names an arc
 * ({@code arc}, 30.5) is told as that arc, a {@code side} one: a villager of the fitting trade asks for it (a
 * {@code talk}), M30's three steps follow as its quests, and its ending leaves something lasting, then reforms the
 * edict ({@code reform_edict}, {@link Reforms#reform}). The endings are the arc effect {@code reform} with a
 * {@code step}: {@code shift_bell}, {@code harvest_supper}, {@code midwife}, {@code old_soldier},
 * {@code grand_festival}, {@code fair_ledger}, {@code street_lamps} and {@code drill_day}.
 *
 * <p>What an ending leaves for later (the bell, the supper, the drill, the first lamplit night, the grand festival) is
 * kept in the village's flags ({@code Stories.Entry#villageFlags}) under {@link #MARK}, so it outlives the arc and a
 * reload, and is acted on every {@link #TICK_EVERY} ticks ({@link #tick}).
 */
public final class ReformStories {
	public static final Set<String> STEPS = Set.of("shift_bell", "harvest_supper", "midwife", "old_soldier", "grand_festival", "fair_ledger",
		"street_lamps", "drill_day");
	/** The bell post set beside the hall (The Shift Bell). */
	public static final ResourceLocation BELL_TEMPLATE = AliveWorkplace.id("story/shift_bell");
	static final String MARK = "reform.";
	public static final String BELL = MARK + "bell";
	static final String BELL_RUNG = MARK + "bell_rung";
	public static final String SUPPER = MARK + "supper";
	static final String SUPPER_ON = MARK + "supper_on";
	public static final String GRAND = MARK + "grand";
	public static final String GRAND_DAY = MARK + "grand_day";
	public static final String LAMPLIT = MARK + "lamplit";
	public static final String DRILL = MARK + "drill";
	static final String DRILL_HITS = MARK + "drill_hits";
	static final String DRILL_MEN = MARK + "drill_men";
	public static final int TICK_EVERY = 20;
	/** The work day's start and end (the villagers' WORK activity), when the Shift Bell rings; a bell late by up to {@link #RING_LATE} still rings. */
	public static final long WORK_START = 2000;
	public static final long WORK_END = 9000;
	static final long RING_LATE = 1200;
	/** The harvest supper: from this time of day, for {@link #SUPPER_LENGTH}. */
	public static final long SUPPER_AT = 11000;
	static final long SUPPER_LENGTH = 1500;
	/** Drill day: noon, for a while. */
	public static final long DRILL_FROM = 6000;
	public static final long DRILL_UNTIL = 8400;
	/** Nightfall: the first lamplit night. */
	public static final long NIGHT = 13000;
	static final int DUMMY_RANGE = 24;
	/** The midwife comes as a Journeyman Nurse, the old soldier as an Expert Guard. */
	public static final int MIDWIFE_LEVEL = 3;
	public static final int SOLDIER_LEVEL = 4;
	/** Days of the treasury's takings in the Fair Ledger. */
	public static final int LEDGER_DAYS = 7;
	static final int TITLE_LENGTH = 32;
	/** Every reform-story villager carries this tag (midwife and old soldier). */
	public static final String NEWCOMER = "aliveworkplace_reform_newcomer";

	/** The dummies a drill is using, by hall (found at its start). */
	private static final Map<BlockPos, List<BlockPos>> DUMMIES = new HashMap<>();

	public static void init() {
		Platform.get().onLevelTick(level -> {
			if (level.getGameTime() % TICK_EVERY == 0) {
				tick(level);
			}
		});
	}

	// ---- the arc in place of the steps ---------------------------------------------------------------------------

	/**
	 * Whether the reform of {@code edict} in the village round {@code hall} is told as its story: storyArcs on, the arc
	 * its {@code arc} names loaded, and either already running, or the plain steps not begun ({@code step} 0, none up)
	 * and the arc starts now (it can't with nobody to ask: then the plain steps go up).
	 */
	public static boolean tells(ServerLevel level, BlockPos hall, String edict, Reforms.Reform reform, int step, boolean plainUp) {
		if (!Arcs.ENABLED || reform.arc().isEmpty()) {
			return false;
		}
		Arcs.Arc arc = Arcs.find(reform.arc().get()).orElse(null);
		if (arc == null || Arcs.disabled(arc.id())) {
			return false;
		}
		if (runs(level, hall, arc.id())) {
			return true;
		}
		if (step > 0 || plainUp) {
			return false;
		}
		return Arcs.start(level, hall, arc) != null;
	}

	/** Whether the arc {@code id} runs in the village round {@code hall} (as a side arc or the main one). */
	public static boolean runs(ServerLevel level, BlockPos hall, ResourceLocation id) {
		ArcState main = Arcs.running(level, hall);
		return main != null && main.id.equals(id.toString()) || Arcs.side(level, hall).stream().anyMatch(s -> s.id.equals(id.toString()));
	}

	// ---- reform_edict --------------------------------------------------------------------------------------------

	/** {@code reform_edict}: the edict {@code edict} is reformed in the village for good (its boost stays, its cost goes). */
	public record ReformEdict(String edict) implements Rewards.Reward {
		static ReformEdict read(JsonObject json) {
			return new ReformEdict(Rewards.id(json, "edict").toString());
		}

		@Override
		public String type() {
			return "reform_edict";
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("edict", edict);
			return o;
		}

		@Override
		public void give(ServerLevel level, BlockPos hall, @Nullable ServerPlayer finisher) {
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
				Reforms.reform(level, hall, entity, edict);
			}
		}
	}

	// ---- the endings ---------------------------------------------------------------------------------------------

	static void validate(String step) {
		if (!STEPS.contains(step)) {
			throw new IllegalArgumentException("reform: unknown step '" + step + "', not one of " + STEPS);
		}
	}

	/** Runs the ending {@code step} of a reform story in the village round {@code hall}. */
	static void effect(ServerLevel level, BlockPos hall, Stories.Entry e, ArcState s, String step) {
		long today = Chronicle.day(level);
		ServerPlayer finisher = s.last == null ? null : level.getServer().getPlayerList().getPlayer(s.last);
		switch (step) {
			case "shift_bell" -> {
				BlockPos bell = placeBell(level, hall);
				if (bell != null) {
					e.villageFlags.put(BELL, bell.asLong());
					level.playSound(null, bell, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5f, 1f);
				}
			}
			case "harvest_supper" -> e.villageFlags.put(SUPPER, today + 1);
			case "midwife" -> newcomer(level, hall, ModVillagers.NURSE, MIDWIFE_LEVEL, false);
			case "old_soldier" -> newcomer(level, hall, ModVillagers.GUARD, SOLDIER_LEVEL, true);
			case "grand_festival" -> e.villageFlags.put(GRAND, 1L);
			case "fair_ledger" -> ledger(level, hall);
			case "street_lamps" -> {
				for (int i = 0; i < 2; i++) {
					ItemStack lamp = BlueprintItem.create(StarterBlueprints.STREET_LAMP.id(), StarterBlueprints.STREET_LAMP.size());
					if (finisher != null) {
						if (!finisher.getInventory().add(lamp)) {
							finisher.drop(lamp, false);
						}
					} else {
						net.minecraft.world.level.block.Block.popResource(level, hall.above(), lamp);
					}
				}
				e.villageFlags.put(LAMPLIT, today);
			}
			case "drill_day" -> e.villageFlags.put(DRILL, today + 1);
			default -> {
			}
		}
		ResourceLocation arc = ResourceLocation.tryParse(s.id);
		if (arc != null && arc.getNamespace().equals(AliveWorkplace.MOD_ID)) {
			Component told = Component.translatable("message.aliveworkplace.reform_story." + arc.getPath(), VillageHalls.name(level, hall)).withStyle(ChatFormatting.GOLD);
			for (ServerPlayer player : ArcEffects.audience(level, hall)) {
				Chat.chat(player, told);
			}
		}
		Stories.Data.get(level).setDirty();
	}

	/** The bell post, set on natural ground beside the hall; the bell's position, or null where there's no room. */
	@Nullable
	public static BlockPos placeBell(ServerLevel level, BlockPos hall) {
		StructureTemplate template = level.getStructureManager().get(BELL_TEMPLATE).orElse(null);
		if (template == null) {
			AliveWorkplace.LOG.warn("Reform story: no template {}", BELL_TEMPLATE);
			return null;
		}
		for (int r = 3; r <= 10; r++) {
			for (int i = 0; i < 16; i++) {
				double a = Math.PI * 2 * i / 16;
				int x = hall.getX() + (int) Math.round(Math.cos(a) * r);
				int z = hall.getZ() + (int) Math.round(Math.sin(a) * r);
				BlockPos origin = site(level, x, z);
				if (origin != null) {
					template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
					for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(2, 5, 2))) {
						if (level.getBlockState(p).is(Blocks.BELL)) {
							return p.immutable();
						}
					}
					return null;
				}
			}
		}
		AliveWorkplace.LOG.info("Reform story: no room for the shift bell beside the hall at {}", hall);
		return null;
	}

	/** The template's origin for a 3 x 3 footing centred on {@code x, z}: flat natural ground with air above, or null. */
	@Nullable
	private static BlockPos site(ServerLevel level, int x, int z) {
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz) - 1;
				BlockPos under = new BlockPos(x + dx, g, z + dz);
				if (g != ground || !natural(level.getBlockState(under))) {
					return null;
				}
				for (int y = 1; y <= 5; y++) {
					if (!level.getBlockState(under.above(y)).canBeReplaced()) {
						return null;
					}
				}
			}
		}
		return new BlockPos(x - 1, ground, z - 1);
	}

	private static boolean natural(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(BlockTags.BASE_STONE_OVERWORLD)
			|| state.is(Blocks.SNOW_BLOCK);
	}

	/** A newcomer to the village: a villager of {@code job} at {@code rank}, kept; in iron if {@code iron}. */
	@Nullable
	public static Villager newcomer(ServerLevel level, BlockPos hall, net.minecraft.world.entity.npc.VillagerProfession job, int rank, boolean iron) {
		Villager v = EntityType.VILLAGER.create(level);
		if (v == null) {
			return null;
		}
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hall.offset(level.random.nextInt(5) - 2, 0, 3 + level.random.nextInt(3)));
		v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(rank));
		v.setVillagerXp(VillagerData.getMinXpPerLevel(rank));
		v.setPersistenceRequired();
		v.addTag(NEWCOMER);
		if (iron) {
			v.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			v.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			v.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
			v.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				v.setDropChance(slot, 0f);
			}
		}
		level.addFreshEntity(v);
		level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
		return v;
	}

	/** The Fair Ledger: the treasury's takings day by day for the last {@link #LEDGER_DAYS} days, for the hall's owner. */
	public static ItemStack ledger(ServerLevel level, BlockPos hall) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return ItemStack.EMPTY;
		}
		ItemStack book = ledgerBook(level, hall, entity);
		ServerPlayer owner = entity.owner() == null ? null : level.getServer().getPlayerList().getPlayer(entity.owner());
		if (owner != null) {
			if (!owner.getInventory().add(book.copy())) {
				owner.drop(book.copy(), false);
			}
			Chat.chat(owner, Component.translatable("message.aliveworkplace.reform_story.ledger", VillageHalls.name(level, hall)).withStyle(ChatFormatting.GOLD));
		} else {
			ItemStack rest = io.github.jcondedata.aliveworkplace.build.SupplyContainers.insert(level, VillageNeeds.store(level, hall), book.copy());
			if (!rest.isEmpty()) {
				net.minecraft.world.level.block.Block.popResource(level, hall.above(), rest);
			}
		}
		return book;
	}

	/** The book itself: its title, a page of the takings by day, and their sum. */
	public static ItemStack ledgerBook(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		long today = Chronicle.day(level);
		Component village = VillageHalls.name(level, hall);
		String title = Component.translatable("item.aliveworkplace.fair_ledger.title", village).getString();
		MutableComponent days = Component.translatable("item.aliveworkplace.fair_ledger.days");
		long sum = 0;
		for (long d = today - LEDGER_DAYS + 1; d <= today; d++) {
			long cents = entity.takings(d);
			sum += cents;
			days.append("\n").append(Component.translatable("item.aliveworkplace.fair_ledger.day", d, emeralds(cents)));
		}
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
			Filterable.passThrough(title.length() > TITLE_LENGTH ? title.substring(0, TITLE_LENGTH) : title),
			village.getString(), 0,
			List.of(Filterable.passThrough(Component.translatable("item.aliveworkplace.fair_ledger.page.1", village)),
				Filterable.passThrough(days),
				Filterable.passThrough(Component.translatable("item.aliveworkplace.fair_ledger.page.3", emeralds(sum), LEDGER_DAYS))),
			true));
		return book;
	}

	static String emeralds(long cents) {
		return String.format(java.util.Locale.ROOT, "%d.%02d", cents / 100, cents % 100);
	}

	// ---- what the endings leave for later ------------------------------------------------------------------------

	/** Every {@link #TICK_EVERY} ticks: the bells ring, suppers and drills are held, the first lamplit night is noted. */
	static void tick(ServerLevel level) {
		Stories.Data data = Stories.Data.get(level);
		for (Map.Entry<BlockPos, Stories.Entry> en : List.copyOf(data.halls.entrySet())) {
			Map<String, Long> flags = en.getValue().villageFlags;
			if (flags.keySet().stream().noneMatch(k -> k.startsWith(MARK))) {
				continue;
			}
			BlockPos hall = en.getKey();
			if (!level.isLoaded(hall) || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity)) {
				continue;
			}
			run(level, hall, en.getValue(), level.getDayTime() % VillageNeeds.DAY);
		}
	}

	/** What is due in the village round {@code hall} at {@code time} of day (tests call it with a time of their own). */
	public static void run(ServerLevel level, BlockPos hall, long time) {
		Stories.Entry e = Stories.Data.get(level).halls.get(hall);
		if (e != null) {
			run(level, hall, e, time);
		}
	}

	private static void run(ServerLevel level, BlockPos hall, Stories.Entry e, long time) {
		long today = Chronicle.day(level);
		Map<String, Long> flags = e.villageFlags;
		boolean changed = false;
		Long bell = flags.get(BELL);
		if (bell != null) {
			long rung = flags.getOrDefault(BELL_RUNG, -1L);
			long slot = time >= WORK_START && time < WORK_START + RING_LATE ? today * 2 : time >= WORK_END && time < WORK_END + RING_LATE ? today * 2 + 1 : -1;
			if (slot >= 0 && rung < slot) {
				flags.put(BELL_RUNG, slot);
				changed = true;
				ring(level, BlockPos.of(bell), slot % 2 == 0);
			}
		}
		Long supper = flags.get(SUPPER);
		if (supper != null && today >= supper && time >= SUPPER_AT) {
			flags.remove(SUPPER);
			flags.put(SUPPER_ON, today);
			changed = true;
			supper(level, hall);
		}
		Long on = flags.get(SUPPER_ON);
		if (on != null) {
			if (on == today && time >= SUPPER_AT && time < SUPPER_AT + SUPPER_LENGTH) {
				for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && !v.isSleeping())) {
					Festivals.gatherAt(level, v, hall);
				}
			} else {
				flags.remove(SUPPER_ON);
				changed = true;
			}
		}
		Long drill = flags.get(DRILL);
		if (drill != null && today >= drill && time >= DRILL_FROM) {
			if (today == drill && time < DRILL_UNTIL) {
				drill(level, hall, flags);
			} else {
				drillOver(level, hall, flags);
			}
			changed = true;
		}
		Long lamplit = flags.get(LAMPLIT);
		if (lamplit != null && (today > lamplit || time >= NIGHT)) {
			flags.remove(LAMPLIT);
			changed = true;
			Chronicle.atHall(level, hall, Chronicle.Kind.STORY, Component.translatable("chronicle.aliveworkplace.reform_story.lamplit", VillageHalls.name(level, hall)));
		}
		if (changed) {
			Stories.Data.get(level).setDirty();
		}
	}

	/** The Shift Bell rings (if it's still there): the start or the end of the work day. */
	static void ring(ServerLevel level, BlockPos bell, boolean start) {
		if (level.getBlockState(bell).getBlock() instanceof BellBlock block) {
			block.attemptToRing(level, bell, Direction.NORTH);
			RUNG.incrementAndGet();
		}
	}

	/** Bells rung by the Shift Bell since the server started (tests). */
	public static final java.util.concurrent.atomic.AtomicInteger RUNG = new java.util.concurrent.atomic.AtomicInteger();

	/** The harvest supper: a festival's feast at the hall, without the fireworks. */
	static void supper(ServerLevel level, BlockPos hall) {
		io.github.jcondedata.aliveworkplace.build.BuildReserve store = io.github.jcondedata.aliveworkplace.build.BuildReserve.of(level, VillageNeeds.store(level, hall));
		long today = Chronicle.day(level);
		int came = 0;
		int fed = 0;
		for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Villager::isAlive)) {
			came++;
			ModAttachments.FESTIVAL_DAY.set(v, today);
			if (!v.isBaby() && VillageNeeds.eat(level, v, store, Items.BREAD)) {
				fed++;
			}
		}
		Chronicle.atHall(level, hall, Chronicle.Kind.STORY, Component.translatable("chronicle.aliveworkplace.reform_story.supper", came, fed));
		for (ServerPlayer player : ArcEffects.audience(level, hall)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.reform_story.supper", VillageHalls.name(level, hall)).withStyle(ChatFormatting.GOLD));
		}
		level.playSound(null, hall, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.5f, 1f);
	}

	/** One beat of drill day: the grown villagers spar at the dummies (the guards on the square when there are none). */
	static void drill(ServerLevel level, BlockPos hall, Map<String, Long> flags) {
		List<BlockPos> dummies = DUMMIES.computeIfAbsent(hall.immutable(), h -> dummies(level, h));
		dummies.removeIf(d -> !level.getBlockState(d).is(ModBlocks.TRAINING_DUMMY));
		List<Villager> men = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && !v.isBaby() && !v.isSleeping()
			&& (!dummies.isEmpty() || v.getVillagerData().getProfession() == ModVillagers.GUARD));
		long hits = flags.getOrDefault(DRILL_HITS, 0L);
		Set<UUID> sparred = new java.util.HashSet<>();
		for (int i = 0; i < men.size(); i++) {
			Villager v = men.get(i);
			BlockPos at = dummies.isEmpty() ? hall : dummies.get(i % dummies.size());
			double reach = dummies.isEmpty() ? 4.0 : 2.5;
			if (!at.closerToCenterThan(v.position(), reach)) {
				v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(at, 0.6f, 1));
				continue;
			}
			v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(dummies.isEmpty() ? men.get((i + 1) % men.size()).blockPosition().above() : at.above()));
			v.swing(InteractionHand.MAIN_HAND);
			if (!dummies.isEmpty()) {
				TrainingDummyBlock.hit(level, at);
			}
			hits++;
			sparred.add(v.getUUID());
		}
		flags.put(DRILL_HITS, hits);
		flags.put(DRILL_MEN, Math.max(flags.getOrDefault(DRILL_MEN, 0L), (long) men.size()));
		flags.put(MARK + "drill_dummies", (long) dummies.size());
	}

	/** Drill day is over: the chronicle says how it went. */
	static void drillOver(ServerLevel level, BlockPos hall, Map<String, Long> flags) {
		long men = flags.getOrDefault(DRILL_MEN, 0L);
		long dummies = flags.getOrDefault(MARK + "drill_dummies", 0L);
		long hits = flags.getOrDefault(DRILL_HITS, 0L);
		flags.remove(DRILL);
		flags.remove(DRILL_MEN);
		flags.remove(DRILL_HITS);
		flags.remove(MARK + "drill_dummies");
		DUMMIES.remove(hall);
		Chronicle.atHall(level, hall, Chronicle.Kind.STORY, dummies > 0
			? Component.translatable("chronicle.aliveworkplace.reform_story.drill", men, dummies, hits)
			: Component.translatable("chronicle.aliveworkplace.reform_story.drill_guards", men, hits));
	}

	private static List<BlockPos> dummies(ServerLevel level, BlockPos hall) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(hall.offset(-DUMMY_RANGE, -6, -DUMMY_RANGE), hall.offset(DUMMY_RANGE, 6, DUMMY_RANGE))) {
			if (level.getBlockState(p).is(ModBlocks.TRAINING_DUMMY)) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	// ---- the grand festival --------------------------------------------------------------------------------------

	/** A festival's feast is held: a grand one waiting (The Festival Fund) is this one. */
	public static void festivalHeld(ServerLevel level, BlockPos hall) {
		Stories.Entry e = Stories.Data.get(level).halls.get(hall);
		if (e == null || !e.villageFlags.containsKey(GRAND)) {
			return;
		}
		e.villageFlags.remove(GRAND);
		e.villageFlags.put(GRAND_DAY, Chronicle.day(level));
		Stories.Data.get(level).setDirty();
		Chronicle.atHall(level, hall, Chronicle.Kind.STORY, Component.translatable("chronicle.aliveworkplace.reform_story.grand", VillageHalls.name(level, hall)));
		for (ServerPlayer player : ArcEffects.audience(level, hall)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.reform_story.grand", VillageHalls.name(level, hall)).withStyle(ChatFormatting.GOLD));
		}
	}

	/** Whether today's festival in the village round {@code hall} is a grand one (twice the fireworks). */
	public static boolean grand(ServerLevel level, BlockPos hall) {
		return grandDay(level, hall) == Chronicle.day(level);
	}

	/** The day of the village's grand festival, or -1. */
	public static long grandDay(ServerLevel level, BlockPos hall) {
		Stories.Entry e = Stories.Data.get(level).halls.get(hall);
		return e == null ? -1 : e.villageFlags.getOrDefault(GRAND_DAY, -1L);
	}

	/** A mark of the village round {@code hall} (tests), or empty. */
	public static Optional<Long> mark(ServerLevel level, BlockPos hall, String key) {
		Stories.Entry e = Stories.Data.get(level).halls.get(hall);
		return e == null ? Optional.empty() : Optional.ofNullable(e.villageFlags.get(key));
	}

	private ReformStories() {
	}
}
