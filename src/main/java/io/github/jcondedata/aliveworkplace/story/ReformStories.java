package io.github.jcondedata.aliveworkplace.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.item.ItemEntity;
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
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The eight reforms as stories (ROADMAP 31.22). An edict file's {@code reform.arc} names a {@code side} arc; with
 * {@code storyArcs} on, {@link Reforms#round} starts it instead of putting M30's step up on the quest page. The arc's
 * opening chapter is a villager of the fitting trade asking for the reform ({@code talk}); its next three chapters run
 * {@code reform_step} (M30's step {@code n} as the arc's quest, M30's numbers, M30's pay); its ending pays the
 * {@code reform_edict} reward (the edict reformed for that village: boost kept, cost gone) and runs
 * {@code reform_ending}, the lasting thing each story leaves:
 * <ul>
 *   <li>{@code shift_bell}: the Shift Bell template on natural ground beside the hall, rung at the start and end of the
 *   work day ({@link #RING_START}, {@link #RING_END});</li>
 *   <li>{@code harvest_supper}: a festival's feast at the hall the next evening, without the fireworks;</li>
 *   <li>{@code midwife}: a new villager comes and becomes a Nurse at Journeyman;</li>
 *   <li>{@code old_soldier}: a new villager comes and becomes a Guard at Expert, in iron;</li>
 *   <li>{@code grand_festival}: the next festival is grand: twice the fireworks, its good mood a day longer;</li>
 *   <li>{@code fair_ledger}: "The Fair Ledger of &lt;village&gt;", the treasury's takings day by day for the last 7 days,
 *   for the hall's owner;</li>
 *   <li>{@code lamplighters}: two Street Lamp blueprints for whoever finished it, and a chronicle line on the first
 *   lamplit night;</li>
 *   <li>{@code drill_day}: at noon the next day every grown villager spars a while at the village's Training Dummies
 *   (the guards alone with none), then a chronicle line.</li>
 * </ul>
 * What the endings leave (the bell, the days set, the takings) is saved in {@code aliveworkplace_reform_stories}.
 */
public final class ReformStories {
	/** Time of day the work day starts and ends (the villagers' schedule): the Shift Bell rings then. */
	public static final long RING_START = 2000;
	public static final long RING_END = 9000;
	/** Noon, and when the drill is over. */
	public static final long DRILL_START = 6000;
	public static final long DRILL_END = 7400;
	/** When a night counts as lamplit. */
	public static final long NIGHT = 13000;
	/** Days of takings the Fair Ledger lists. */
	public static final int LEDGER_DAYS = 7;
	/** How far round the hall the Training Dummies are looked for. */
	public static final int DUMMY_RANGE = 32;
	static final int EVERY = 20;
	public static final ResourceLocation BELL = AliveWorkplace.id("story/shift_bell");
	public static final ResourceLocation STREET_LAMP = AliveWorkplace.id("street_lamp");
	static final Set<String> ENDINGS = Set.of("shift_bell", "harvest_supper", "midwife", "old_soldier", "grand_festival", "fair_ledger",
		"lamplighters", "drill_day");
	/** Times a Shift Bell rang since the server started (tests). */
	public static final AtomicInteger RINGS = new AtomicInteger();
	/** Villagers who sparred at a drill since the server started (tests). */
	public static final AtomicInteger DRILLED = new AtomicInteger();

	public static void init() {
		Platform.get().onServerTick(server -> {
			if (server.getTickCount() % EVERY == 0) {
				for (ServerLevel level : server.getAllLevels()) {
					tick(level);
				}
			}
		});
	}

	// ---- the arc: started from the reform, its steps -----------------------------------------------------------------

	/**
	 * Whether the reform of the edict {@code edict} is told as its story in the village round {@code hall} now: its arc
	 * runs, or starts now (the ask). False with {@code storyArcs} off, with no arc named or loaded, when nobody can ask,
	 * or while arcs don't start by themselves ({@link Arcs#AUTO}, off in GameTests).
	 */
	public static boolean told(ServerLevel level, BlockPos hall, String edict, Reforms.Reform reform) {
		if (!Arcs.ENABLED || reform.arc().isEmpty()) {
			return false;
		}
		Arcs.Arc arc = Arcs.find(reform.arc().get()).orElse(null);
		if (arc == null || !arc.side() || Arcs.disabled(arc.id())) {
			return false;
		}
		if (Arcs.side(level, hall).stream().anyMatch(s -> s.id.equals(arc.id().toString()))) {
			return true;
		}
		return Arcs.AUTO && Arcs.start(level, hall, arc) != null; // (GameTests start arcs themselves, as with every arc)
	}

	/** The edict whose reform is told by {@code arc}, or null. */
	@Nullable
	public static String edictOf(Arcs.Arc arc) {
		for (Edicts.Edict edict : Edicts.all()) {
			Optional<String> named = edict.reform().flatMap(Reforms.Reform::arc);
			if (named.isPresent() && Arcs.find(named.get()).map(a -> a.id().equals(arc.id())).orElse(false)) {
				return edict.id().toString();
			}
		}
		return null;
	}

	static void validate(JsonObject json) {
		String type = GsonHelper.getAsString(json, "type");
		if (type.equals("reform_step")) {
			if (GsonHelper.getAsInt(json, "step", -1) < 0) {
				throw new IllegalArgumentException("reform_step: 'step' (0 for the first) missing or below 0");
			}
		} else if (!ENDINGS.contains(GsonHelper.getAsString(json, "ending", ""))) {
			throw new IllegalArgumentException("reform_ending: unknown 'ending' (one of " + ENDINGS + ")");
		}
	}

	/** {@code reform_step}: the reform's step {@code n} (M30's kind, count and pay) goes up as the chapter's quest. */
	static void step(ServerLevel level, BlockPos hall, ArcState s, Arcs.Arc arc, int n) {
		String edict = edictOf(arc);
		Reforms.Reform reform = edict == null ? null : Reforms.of(edict).orElse(null);
		if (reform == null || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			AliveWorkplace.LOG.warn("Story arc {}: no edict's reform names it", arc.id());
			return;
		}
		Reforms.stepsDone(level, entity, edict, n); // the steps before this one are done
		if (n >= reform.steps().size() || Reforms.progress(entity, edict).step() > n) {
			return; // done before the story began: the chapter has nothing to ask
		}
		Reforms.Step step = reform.steps().get(n).resolve(level, hall);
		JsonObject objective = new JsonObject();
		switch (step.kind()) {
			case BRING -> {
				objective.addProperty("type", "bring");
				objective.addProperty("item", step.item());
				objective.addProperty("count", step.count());
			}
			case SLAY -> {
				objective.addProperty("type", "kill");
				objective.addProperty("entity", "monster");
				objective.addProperty("count", step.count());
			}
			default -> {
				objective.addProperty("type", "battle");
				objective.addProperty("count", 1);
			}
		}
		JsonObject q = new JsonObject();
		q.addProperty("giver", "arc");
		q.add("name", Rewards.json(Reforms.title(new VillageQuests.ReformStep(edict, n))));
		JsonArray objectives = new JsonArray();
		objectives.add(objective);
		q.add("objectives", objectives);
		JsonArray rewards = new JsonArray();
		if (step.reward() > 0) {
			JsonObject money = new JsonObject();
			money.addProperty("type", "money");
			money.addProperty("emeralds", step.reward());
			rewards.add(money);
		}
		q.add("rewards", rewards);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(arc.id().getNamespace(), "arc/" + arc.id().getPath() + "/step_" + (n + 1));
		Quest quest = Arcs.quest(level, hall, s, new Arcs.QuestRef(id, QuestFiles.read(id, q)));
		if (quest != null) {
			s.quests.add(new ArcState.ChapterQuest(quest.id, id.toString()));
			Stories.Data.get(level).setDirty();
		}
	}

	/** {@code reform_edict}: the edict reformed for the village (boost kept, cost gone), as its last step would. */
	public record ReformEdict(String edict) implements Rewards.Reward {
		static ReformEdict read(JsonObject json) {
			ResourceLocation id = Rewards.id(json, "edict");
			return new ReformEdict(id.toString());
		}

		@Override
		public String type() {
			return "reform_edict";
		}

		@Override
		public void give(ServerLevel level, BlockPos hall, @Nullable ServerPlayer finisher) {
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
				Reforms.stepsDone(level, entity, edict, Reforms.of(edict).map(r -> r.steps().size()).orElse(1) - 1);
				Reforms.reform(level, hall, entity, edict);
			}
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("edict", edict);
			return o;
		}
	}

	// ---- the endings ------------------------------------------------------------------------------------------------

	/** {@code reform_ending}: what the story leaves. */
	static void ending(ServerLevel level, BlockPos hall, ArcState s, Arcs.Arc arc, String ending) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return;
		}
		Data data = Data.get(level);
		Entry e = data.entry(hall);
		long today = Chronicle.day(level);
		Component village = VillageHalls.name(level, hall);
		ServerPlayer finisher = s.last == null ? null : level.getServer().getPlayerList().getPlayer(s.last);
		switch (ending) {
			case "shift_bell" -> {
				BlockPos bell = placeBell(level, hall);
				if (bell != null) {
					e.bell = bell.asLong();
					line(level, hall, Chronicle.Kind.BUILT, Component.translatable("chronicle.aliveworkplace.reform_story.shift_bell", village));
				}
			}
			case "harvest_supper" -> {
				e.supperDay = today + 1;
				if (Festivals.ENABLED) {
					Festivals.schedule(level, hall, entity, today + 1);
				}
				line(level, hall, Chronicle.Kind.FESTIVAL, Component.translatable("chronicle.aliveworkplace.reform_story.harvest_supper", village));
			}
			case "midwife" -> {
				Villager v = newcomer(level, hall, ModVillagers.NURSE, 3, "villager.aliveworkplace.reform_story.midwife");
				if (v != null) {
					line(level, hall, Chronicle.Kind.ARRIVED, Component.translatable("chronicle.aliveworkplace.reform_story.midwife", v.getDisplayName(), village));
				}
			}
			case "old_soldier" -> {
				Villager v = newcomer(level, hall, ModVillagers.GUARD, 4, "villager.aliveworkplace.reform_story.old_soldier");
				if (v != null) {
					v.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
					v.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
					v.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
					v.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
					v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
					line(level, hall, Chronicle.Kind.ARRIVED, Component.translatable("chronicle.aliveworkplace.reform_story.old_soldier", v.getDisplayName(), village));
				}
			}
			case "grand_festival" -> {
				e.grandPending = true;
				line(level, hall, Chronicle.Kind.FESTIVAL, Component.translatable("chronicle.aliveworkplace.reform_story.grand_festival", village));
			}
			case "fair_ledger" -> {
				ItemStack book = ledger(level, hall, e);
				UUID owner = entity.owner();
				ServerPlayer to = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
				give(level, hall, to != null ? to : finisher, book);
				line(level, hall, Chronicle.Kind.QUEST, Component.translatable("chronicle.aliveworkplace.reform_story.fair_ledger", village));
			}
			case "lamplighters" -> {
				for (int i = 0; i < 2; i++) {
					give(level, hall, finisher, BlueprintItem.create(STREET_LAMP, BlueprintLibrary.get(level, STREET_LAMP).map(b -> b.size()).orElse(null)));
				}
				e.lamplit = true;
			}
			case "drill_day" -> {
				e.drillDay = today + 1;
				e.drillDone = false;
				line(level, hall, Chronicle.Kind.STORY, Component.translatable("chronicle.aliveworkplace.reform_story.drill_day", village));
			}
			default -> {
			}
		}
		data.setDirty();
	}

	private static void line(ServerLevel level, BlockPos hall, Chronicle.Kind kind, Component text) {
		Chronicle.atHall(level, hall, kind, text);
		for (ServerPlayer player : ArcEffects.audience(level, hall)) {
			Chat.chat(player, text.copy().withStyle(ChatFormatting.GOLD));
		}
	}

	private static void give(ServerLevel level, BlockPos hall, @Nullable ServerPlayer player, ItemStack stack) {
		if (player != null && player.getInventory().add(stack)) {
			return;
		}
		ItemEntity item = new ItemEntity(level, hall.getX() + 0.5, hall.getY() + 1.2, hall.getZ() + 0.5, stack);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}

	/** A new villager by the hall, of {@code job} at {@code tier} (3: Journeyman, 4: Expert), staying for good. */
	@Nullable
	static Villager newcomer(ServerLevel level, BlockPos hall, net.minecraft.world.entity.npc.VillagerProfession job, int tier, String title) {
		Villager v = EntityType.VILLAGER.create(level);
		if (v == null) {
			return null;
		}
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hall.offset(level.random.nextInt(5) - 2, 0, 2 + level.random.nextInt(3)));
		v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(tier));
		v.setVillagerXp(VillagerData.getMinXpPerLevel(tier));
		v.refreshBrain(level); // the new trade's brain (a guard's fights in its CORE activity, B117)
		v.setCustomName(Component.translatable(title));
		v.setPersistenceRequired();
		level.addFreshEntity(v);
		return v;
	}

	// ---- the Shift Bell ---------------------------------------------------------------------------------------------

	/** Whether {@code state} is natural ground: dirt, grass, sand, gravel or stone. */
	public static boolean natural(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.GRAVEL);
	}

	/**
	 * The Shift Bell's template set beside the hall on natural ground (the nearest 3 by 3 of it, level, with room above,
	 * 3 to 12 blocks out). Returns the bell, or null when no spot or no template.
	 */
	@Nullable
	public static BlockPos placeBell(ServerLevel level, BlockPos hall) {
		StructureTemplate template = level.getStructureManager().get(BELL).orElse(null);
		if (template == null) {
			AliveWorkplace.LOG.warn("The Shift Bell's template {} is missing", BELL);
			return null;
		}
		List<BlockPos> spots = new ArrayList<>();
		for (int r = 3; r <= 12; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
						spots.add(hall.offset(dx, 0, dz));
					}
				}
			}
		}
		for (BlockPos column : spots) {
			BlockPos origin = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (fits(level, origin)) {
				template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
				BlockPos bell = origin.offset(1, 3, 1);
				AliveWorkplace.LOG.info("The Shift Bell stands at {} by the hall at {}", bell, hall);
				return bell;
			}
		}
		return null;
	}

	/** A 3 by 3 of natural ground with its top at {@code origin}'s y - 1, and 6 of air above. */
	private static boolean fits(ServerLevel level, BlockPos origin) {
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				if (!natural(level.getBlockState(origin.offset(x, -1, z)))) {
					return false;
				}
				for (int y = 0; y < 6; y++) {
					if (!level.getBlockState(origin.offset(x, y, z)).isAir()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** The village's Shift Bell, or null with none. */
	@Nullable
	public static BlockPos bell(ServerLevel level, BlockPos hall) {
		Entry e = Data.get(level).halls.get(hall);
		return e == null || e.bell == Entry.NONE ? null : BlockPos.of(e.bell);
	}

	/** Rings the bell at {@code pos}; false when it's gone. */
	public static boolean ring(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof BellBlock bell) || !bell.attemptToRing(level, pos, null)) {
			return false;
		}
		RINGS.incrementAndGet();
		return true;
	}

	// ---- the festivals ----------------------------------------------------------------------------------------------

	/** Whether the festival today in the village round {@code hall} is the harvest supper (no fireworks). */
	public static boolean supper(ServerLevel level, BlockPos hall) {
		Entry e = Data.get(level).halls.get(hall);
		return e != null && e.supperDay == Chronicle.day(level);
	}

	/** Whether the festival today in the village round {@code hall} is the grand one (twice the fireworks). */
	public static boolean grand(ServerLevel level, BlockPos hall) {
		Entry e = Data.get(level).halls.get(hall);
		return e != null && e.grandDay == Chronicle.day(level);
	}

	/** The feast begins in the village round {@code hall}: a grand festival waiting is today's. */
	public static void onFeast(ServerLevel level, BlockPos hall) {
		Data data = Data.get(level);
		Entry e = data.halls.get(hall);
		if (e != null && e.grandPending) {
			e.grandPending = false;
			e.grandDay = Chronicle.day(level);
			data.setDirty();
			line(level, hall, Chronicle.Kind.FESTIVAL, Component.translatable("chronicle.aliveworkplace.reform_story.grand_held", VillageHalls.name(level, hall)));
		}
	}

	/** Whether {@code villager}'s last festival, on {@code day}, was its village's grand one (its mood lasts a day longer). */
	public static boolean grandFestival(ServerLevel level, Villager villager, long day) {
		Data data = Data.get(level);
		if (data.halls.isEmpty()) {
			return false;
		}
		return VillageHalls.nearest(level, villager.blockPosition()).map(data.halls::get).map(e -> e.grandDay == day).orElse(false);
	}

	// ---- the Fair Ledger --------------------------------------------------------------------------------------------

	/** The treasury took {@code cents} in the village round {@code hall} on {@code day}: kept for the Fair Ledger. */
	public static void takings(ServerLevel level, BlockPos hall, long day, long cents) {
		Data data = Data.get(level);
		Entry e = data.entry(hall);
		e.takings.merge(day, Math.max(0, cents), Long::sum);
		e.takings.keySet().removeIf(d -> d <= day - LEDGER_DAYS);
		data.setDirty();
	}

	/** "The Fair Ledger of <village>": the takings day by day for the last {@link #LEDGER_DAYS} days. */
	public static ItemStack ledger(ServerLevel level, BlockPos hall, Entry e) {
		long today = Chronicle.day(level);
		StringBuilder rows = new StringBuilder();
		long total = 0;
		for (long d = today - LEDGER_DAYS + 1; d <= today; d++) {
			long cents = e.takings.getOrDefault(d, 0L);
			total += cents;
			rows.append(Component.translatable("item.aliveworkplace.fair_ledger.row", d, String.format(java.util.Locale.ROOT, "%.2f", cents / 100.0)).getString())
				.append('\n');
		}
		Component village = VillageHalls.name(level, hall);
		Component page = Component.translatable("item.aliveworkplace.fair_ledger.page", village, rows.toString().trim(),
			String.format(java.util.Locale.ROOT, "%.2f", total / 100.0));
		String title = Component.translatable("item.aliveworkplace.fair_ledger.title", village).getString();
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title.length() > 32 ? title.substring(0, 32) : title),
			Component.translatable("item.aliveworkplace.fair_ledger.author").getString(), 0, List.of(Filterable.passThrough(page)), true));
		book.set(DataComponents.CUSTOM_NAME, Component.translatable("item.aliveworkplace.fair_ledger.title", village));
		return book;
	}

	// ---- the tick: the bell, the lamplit night, the drill -----------------------------------------------------------

	/** Every {@link #EVERY} ticks: the Shift Bells ring, the first lamplit night is written down, drill day runs. */
	public static void tick(ServerLevel level) {
		Data data = Data.get(level);
		if (data.halls.isEmpty()) {
			return;
		}
		long t = level.getDayTime() % 24000L;
		long today = Chronicle.day(level);
		for (Map.Entry<BlockPos, Entry> en : List.copyOf(data.halls.entrySet())) {
			BlockPos hall = en.getKey();
			Entry e = en.getValue();
			if (!level.isLoaded(hall)) {
				continue;
			}
			if (e.bell != Entry.NONE && (t >= RING_START && t < RING_START + EVERY || t >= RING_END && t < RING_END + EVERY)) {
				ring(level, BlockPos.of(e.bell));
			}
			if (e.lamplit && t >= NIGHT && t < 23000) {
				e.lamplit = false;
				data.setDirty();
				line(level, hall, Chronicle.Kind.STORY, Component.translatable("chronicle.aliveworkplace.reform_story.lamplit", VillageHalls.name(level, hall)));
			}
			if (e.drillDay == today && !e.drillDone && t >= DRILL_START) {
				if (t < DRILL_END) {
					drill(level, hall, e);
				} else {
					e.drillDone = true;
					data.setDirty();
					line(level, hall, Chronicle.Kind.STORY, io.github.jcondedata.aliveworkplace.work.Words.counted("chronicle.aliveworkplace.reform_story.drilled", e.drillers.size(),
						VillageHalls.name(level, hall), e.drillers.size()));
					e.drillers.clear();
				}
			}
		}
	}

	/** The Training Dummies round the hall. */
	public static List<BlockPos> dummies(ServerLevel level, BlockPos hall) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(hall.offset(-DUMMY_RANGE, -6, -DUMMY_RANGE), hall.offset(DUMMY_RANGE, 6, DUMMY_RANGE))) {
			if (level.getBlockState(p).is(ModBlocks.TRAINING_DUMMY)) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/** One beat of the drill: every grown villager (the guards alone with no dummies) goes to a dummy and spars. */
	static void drill(ServerLevel level, BlockPos hall, Entry e) {
		if (e.dummies == null) {
			e.dummies = dummies(level, hall);
		}
		List<BlockPos> dummies = e.dummies;
		List<Villager> who = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && !v.isBaby() && !v.isSleeping()
			&& (!dummies.isEmpty() || v.getVillagerData().getProfession() == ModVillagers.GUARD));
		BlockPos square = hall;
		for (int i = 0; i < who.size(); i++) {
			Villager v = who.get(i);
			BlockPos target = dummies.isEmpty() ? square : dummies.get(i % dummies.size());
			if (v.blockPosition().closerThan(target, 3)) {
				v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
				v.swing(InteractionHand.MAIN_HAND);
				if (level.random.nextInt(3) == 0) {
					level.playSound(null, target, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.NEUTRAL, 0.6f, 1f);
				}
				if (e.drillers.add(v.getUUID())) {
					DRILLED.incrementAndGet();
				}
			} else {
				v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.6f, 2));
			}
		}
	}

	/** Forgets the dummies found (tests that place them after a drill began). */
	public static void forget(ServerLevel level, BlockPos hall) {
		Entry e = Data.get(level).halls.get(hall);
		if (e != null) {
			e.dummies = null;
		}
	}

	// ---- saved ------------------------------------------------------------------------------------------------------

	/** What the reform stories left in one village. */
	public static final class Entry {
		static final long NONE = Long.MIN_VALUE;
		/** The Shift Bell (a BlockPos as a long), or NONE. */
		public long bell = NONE;
		public long supperDay = -1;
		public boolean grandPending;
		public long grandDay = -1;
		public boolean lamplit;
		public long drillDay = -1;
		public boolean drillDone;
		/** The treasury's takings by day, in hundredths of an emerald (the last {@link #LEDGER_DAYS} days). */
		public final Map<Long, Long> takings = new LinkedHashMap<>();
		final Set<UUID> drillers = new java.util.HashSet<>();
		@Nullable List<BlockPos> dummies;
	}

	public static final class Data extends SavedData {
		private static final String NAME = "aliveworkplace_reform_stories";
		final Map<BlockPos, Entry> halls = new LinkedHashMap<>();

		public static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		public Entry entry(BlockPos hall) {
			return halls.computeIfAbsent(hall.immutable(), k -> new Entry());
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag list = new ListTag();
			for (Map.Entry<BlockPos, Entry> en : halls.entrySet()) {
				Entry e = en.getValue();
				CompoundTag t = new CompoundTag();
				t.putLong("hall", en.getKey().asLong());
				t.putLong("bell", e.bell);
				t.putLong("supperDay", e.supperDay);
				t.putBoolean("grandPending", e.grandPending);
				t.putLong("grandDay", e.grandDay);
				t.putBoolean("lamplit", e.lamplit);
				t.putLong("drillDay", e.drillDay);
				t.putBoolean("drillDone", e.drillDone);
				long[] takings = new long[e.takings.size() * 2];
				int i = 0;
				for (Map.Entry<Long, Long> d : e.takings.entrySet()) {
					takings[i++] = d.getKey();
					takings[i++] = d.getValue();
				}
				t.putLongArray("takings", takings);
				list.add(t);
			}
			tag.put("halls", list);
			return tag;
		}

		static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			ListTag list = Nbt.getList(tag, "halls", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag t = Nbt.compoundAt(list, i);
				Entry e = data.entry(BlockPos.of(Nbt.getLong(t, "hall")));
				e.bell = Nbt.has(t, "bell", Tag.TAG_LONG) ? Nbt.getLong(t, "bell") : Entry.NONE;
				e.supperDay = Nbt.has(t, "supperDay", Tag.TAG_LONG) ? Nbt.getLong(t, "supperDay") : -1;
				e.grandPending = Nbt.getBoolean(t, "grandPending");
				e.grandDay = Nbt.has(t, "grandDay", Tag.TAG_LONG) ? Nbt.getLong(t, "grandDay") : -1;
				e.lamplit = Nbt.getBoolean(t, "lamplit");
				e.drillDay = Nbt.has(t, "drillDay", Tag.TAG_LONG) ? Nbt.getLong(t, "drillDay") : -1;
				e.drillDone = Nbt.getBoolean(t, "drillDone");
				long[] takings = Nbt.getLongArray(t, "takings");
				for (int j = 0; j + 1 < takings.length; j += 2) {
					e.takings.put(takings[j], takings[j + 1]);
				}
			}
			return data;
		}
	}

	private ReformStories() {
	}
}
