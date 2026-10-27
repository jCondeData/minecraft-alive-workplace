package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Pathfinder;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * QA for 29.13, the Pathfinder, written from the spec: a Master explorer qualifies like an Expert, an Expert of another
 * trade doesn't; the 8 food counts only real food and takes exactly 8; a strike refuses an expedition; only the player
 * on it can ask for Home; a death cancels it, and a new one may start the next day; the state survives a save and
 * reload; the three places' tags hold the vanilla structures; a pick without an offer is refused.
 */
public class QaPathfinderGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final ResourceLocation ID = AliveWorkplace.id("pathfinder");

	private static void setUp(GameTestHelper helper, ServerPlayer owner) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		LegendRecord stale = LegendRecord.get(level);
		stale.entries().stream().filter(e -> e.id().equals(ID)).map(LegendRecord.Entry::villager).toList().forEach(stale::forget);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		if (owner != null) {
			((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setOwner(owner.getUUID(), owner.getName().getString());
		}
		Caravans.Data.get(level).setWants(hall, Component.literal("Test"), List.of());
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			Caravans.Data.get(level).remove(hall);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(96), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			Leftovers.players(helper);
			level.setDayTime(time);
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
	}

	/** Only the real Pathfinder loaded, moods and needs off while {@code body} runs. */
	private static void staged(GameTestHelper helper, java.util.function.Consumer<Legend> body) {
		ServerLevel level = helper.getLevel();
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		try {
			Moods.ENABLED = false;
			LegendNeeds.ENABLED = false;
			Legends.reload(level.getServer().getResourceManager());
			Legend pathfinder = Legends.get(ID).orElseThrow(() -> new AssertionError("pathfinder.json didn't load"));
			Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
			map.put(ID, pathfinder);
			Legends.setForTest(map);
			body.accept(pathfinder);
		} finally {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
		}
	}

	private static Villager pathfinder(GameTestHelper helper, Legend legend, BlockPos at, BlockPos table) {
		helper.setBlock(table, Blocks.CARTOGRAPHY_TABLE);
		helper.setBlock(table.east(), Blocks.CHEST);
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		Legends.make(helper.getLevel(), v, legend, "test");
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(table)));
		return v;
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		BlockPos a = helper.absolutePos(at);
		p.moveTo(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
		return p;
	}

	private static void strike(Villager v, boolean on, long today) {
		LegendData data = ModAttachments.LEGEND.get(v);
		ModAttachments.LEGEND.set(v, on ? data.checkedOn(today, Map.of("luxury", 3), today, -1) : data.checkedOn(today, Map.of(), -1, -1));
		LegendPowers.forget();
	}

	/** A Master explorer is enough (Expert or better); a Novice isn't, nor a Master of another trade. */
	//$ gametest_ticks_batch AREA '100' '"qaPathfinderMaster"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPathfinderMaster")
	public void aMasterExplorerCountsAnotherTradeDoesnt(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(15, 2, 15));
		setUp(helper, owner);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, legend -> {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
			v.setNoAi(true);
			v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(5));
			helper.assertFalse(legend.conditions().stream().allMatch(c -> c.met(level, hall)), "a Master Librarian counted as an explorer");
			v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.CARTOGRAPHER).setLevel(1));
			helper.assertFalse(legend.conditions().stream().allMatch(c -> c.met(level, hall)), "a Novice explorer counted");
			v.setVillagerData(v.getVillagerData().setLevel(5));
			helper.assertTrue(legend.conditions().stream().allMatch(c -> c.met(level, hall)), "a Master explorer didn't count");
		});
		helper.succeed();
	}

	/** Only real food counts toward the 8 (rotten flesh doesn't), mixed kinds do, and exactly 8 go. */
	//$ gametest_ticks_batch AREA '100' '"qaPathfinderFood"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPathfinderFood")
	public void eightRealFoodsOfAnyKindAndExactlyEightGo(GameTestHelper helper) {
		ServerPlayer player = player(helper, new BlockPos(4, 2, 3));
		setUp(helper, player);
		ServerLevel level = helper.getLevel();
		staged(helper, legend -> {
			Villager v = pathfinder(helper, legend, new BlockPos(3, 2, 4), new BlockPos(1, 2, 8));
			v.setNoAi(true);
			Container chest = (Container) helper.getBlockEntity(new BlockPos(2, 2, 8));
			chest.setItem(0, new ItemStack(Items.BREAD, 4));
			chest.setItem(1, new ItemStack(Items.COOKED_BEEF, 3));
			chest.setItem(2, new ItemStack(Items.ROTTEN_FLESH, 5));
			BlockPos target = helper.absolutePos(new BlockPos(27, 2, 27));
			helper.assertTrue(Pathfinder.food(level, v) == 7, "rotten flesh counted as food: " + Pathfinder.food(level, v));
			helper.assertFalse(Pathfinder.start(level, player, v, "stronghold", target, null), "set out with 7 food and rotten flesh");
			chest.setItem(3, new ItemStack(Items.COOKED_BEEF, 1));
			helper.assertTrue(Pathfinder.start(level, player, v, "stronghold", target, null), "didn't set out with 8 mixed food");
			helper.assertTrue(Pathfinder.food(level, v) == 0 && chest.countItem(Items.ROTTEN_FLESH) == 5, "took the wrong things: food "
				+ Pathfinder.food(level, v) + ", rotten flesh " + chest.countItem(Items.ROTTEN_FLESH));
		});
		helper.succeed();
	}

	/** On strike: no expedition. Off strike it sets out, and another player can't ask for Home. */
	//$ gametest_ticks_batch AREA '100' '"qaPathfinderStrike"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPathfinderStrike")
	public void aStrikeRefusesAndOnlyTheirPlayerAsksForHome(GameTestHelper helper) {
		ServerPlayer player = player(helper, new BlockPos(4, 2, 3));
		ServerPlayer other = player(helper, new BlockPos(5, 2, 3));
		setUp(helper, player);
		ServerLevel level = helper.getLevel();
		staged(helper, legend -> {
			Villager v = pathfinder(helper, legend, new BlockPos(3, 2, 4), new BlockPos(1, 2, 8));
			v.setNoAi(true);
			((Container) helper.getBlockEntity(new BlockPos(2, 2, 8))).setItem(0, new ItemStack(Items.BREAD, 8));
			BlockPos target = helper.absolutePos(new BlockPos(27, 2, 27));
			long today = Chronicle.day(level);
			strike(v, true, today);
			helper.assertFalse(Pathfinder.start(level, player, v, "stronghold", target, null), "set out on strike");
			helper.assertTrue(Pathfinder.food(level, v) == 8, "food taken on strike");
			strike(v, false, today);
			helper.assertTrue(Pathfinder.start(level, player, v, "stronghold", target, null), "didn't set out after the strike");
			helper.assertFalse(Pathfinder.askHome(other, v), "another player asked for Home");
			helper.assertTrue(Pathfinder.isLeading(v), "the expedition stopped for another player");
		});
		helper.succeed();
	}

	/** A death cancels the expedition (the chronicle says so); the next day a new one may start. */
	//$ gametest_ticks_batch AREA '300' '"qaPathfinderDeath"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPathfinderDeath")
	public void aDeathCancelsItAndTomorrowIsANewDay(GameTestHelper helper) {
		ServerPlayer player = player(helper, new BlockPos(22, 2, 22));
		setUp(helper, player);
		ServerLevel level = helper.getLevel();
		Villager[] v = new Villager[1];
		long[] day = new long[1];
		staged(helper, legend -> {
			v[0] = pathfinder(helper, legend, new BlockPos(23, 2, 22), new BlockPos(20, 2, 18));
			((Container) helper.getBlockEntity(new BlockPos(21, 2, 18))).setItem(0, new ItemStack(Items.BREAD, 16));
			day[0] = Chronicle.day(level);
			helper.assertTrue(Pathfinder.start(level, player, v[0], "trial_chambers", helper.absolutePos(new BlockPos(5, 2, 26)), null), "sets out");
			player.kill();
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertFalse(Pathfinder.isLeading(v[0]), "a death didn't cancel the expedition"))
			.thenExecute(() -> {
				helper.assertTrue(Pathfinder.state(v[0]).strandedDay() == day[0], "not out until the next morning: " + Pathfinder.state(v[0]));
				boolean line = ((VillageHallBlockEntity) helper.getBlockEntity(HALL)).chronicle().stream().anyMatch(e ->
					e.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
						&& t.getKey().equals("chronicle.aliveworkplace.legend.expedition_cancelled"));
				helper.assertTrue(line, "the chronicle doesn't say they turned back");
				ServerPlayer again = player(helper, new BlockPos(22, 2, 22));
				helper.assertFalse(Pathfinder.start(level, again, v[0], "trial_chambers", helper.absolutePos(new BlockPos(5, 2, 26)), null), "two in a day");
				level.setDayTime((level.getDayTime() / 24000L + 1) * 24000L + 1000);
				// the table as their job site again (an unclaimed table's memory can lapse while they walk; that's the staging, not the trip)
				v[0].getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(20, 2, 18))));
				helper.assertTrue(Pathfinder.start(level, again, v[0], "trial_chambers", helper.absolutePos(new BlockPos(5, 2, 26)), null),
					"no expedition the next day: food " + Pathfinder.food(level, v[0]) + ", state " + Pathfinder.state(v[0]) + ", day " + Chronicle.day(level)
						+ ", legend " + ModAttachments.LEGEND.get(v[0]));
			})
			.thenSucceed();
	}

	/**
	 * On the way they fight what attacks the player: a monster after the player is hit; a monster after nobody, and a
	 * cow, are left alone.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaPathfinderFight"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPathfinderFight")
	public void theyFightWhatAttacksThePlayerAndNothingElse(GameTestHelper helper) {
		ServerPlayer player = player(helper, new BlockPos(14, 2, 14));
		setUp(helper, player);
		ServerLevel level = helper.getLevel();
		Villager[] v = new Villager[1];
		net.minecraft.world.entity.Mob[] mobs = new net.minecraft.world.entity.Mob[3];
		staged(helper, legend -> {
			v[0] = pathfinder(helper, legend, new BlockPos(15, 2, 14), new BlockPos(20, 2, 20));
			((Container) helper.getBlockEntity(new BlockPos(21, 2, 20))).setItem(0, new ItemStack(Items.BREAD, 8));
			helper.assertTrue(Pathfinder.start(level, player, v[0], "stronghold", helper.absolutePos(new BlockPos(28, 2, 2)), null), "sets out");
			mobs[0] = helper.spawn(EntityType.VINDICATOR, new BlockPos(16, 2, 15));
			mobs[1] = helper.spawn(EntityType.VINDICATOR, new BlockPos(13, 2, 12));
			mobs[2] = helper.spawn(EntityType.COW, new BlockPos(16, 2, 12));
			for (net.minecraft.world.entity.Mob m : mobs) {
				m.setNoAi(true);
			}
			mobs[0].setTarget(player);
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(mobs[0].getHealth() <= mobs[0].getMaxHealth() - 4.9f || !mobs[0].isAlive(),
				"the monster after the player wasn't fought (" + mobs[0].getHealth() + ")"))
			.thenExecute(() -> {
				helper.assertTrue(mobs[1].getHealth() == mobs[1].getMaxHealth(), "a monster after nobody was hit");
				helper.assertTrue(mobs[2].getHealth() == mobs[2].getMaxHealth(), "a cow was hit");
				helper.assertTrue(Pathfinder.isLeading(v[0]), "the expedition ended in the fight");
				mobs[0].setTarget(null);
				for (net.minecraft.world.entity.Mob m : mobs) {
					m.discard();
				}
			})
			.thenSucceed();
	}

	/** An expedition under way survives a save and reload of the Pathfinder. */
	//$ gametest_ticks_batch AREA '100' '"qaPathfinderSaved"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPathfinderSaved")
	public void anExpeditionSurvivesASaveAndReload(GameTestHelper helper) {
		ServerPlayer player = player(helper, new BlockPos(4, 2, 3));
		setUp(helper, player);
		ServerLevel level = helper.getLevel();
		staged(helper, legend -> {
			Villager v = pathfinder(helper, legend, new BlockPos(3, 2, 4), new BlockPos(1, 2, 8));
			v.setNoAi(true);
			((Container) helper.getBlockEntity(new BlockPos(2, 2, 8))).setItem(0, new ItemStack(Items.BREAD, 8));
			BlockPos target = helper.absolutePos(new BlockPos(27, 2, 27));
			helper.assertTrue(Pathfinder.start(level, player, v, "ancient_city", target, null), "sets out");
			Pathfinder.State before = Pathfinder.state(v);
			CompoundTag tag = v.saveWithoutId(new CompoundTag());
			Villager loaded = EntityType.VILLAGER.create(level);
			loaded.load(tag);
			Pathfinder.State after = Pathfinder.state(loaded);
			helper.assertTrue(after.equals(before) && after.active() && after.target().equals(java.util.Optional.of(target)) && after.kind().equals("ancient_city"),
				"after a reload: " + after + " (was " + before + ")");
			loaded.discard();
		});
		helper.succeed();
	}

	/** The three places' tags hold vanilla's Stronghold, Ancient City and Trial Chambers; a pick with no offer is refused. */
	//$ gametest_ticks_batch AREA '60' '"qaPathfinderTags"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPathfinderTags")
	public void thePlacesTagsAndAPickWithoutAnOffer(GameTestHelper helper) {
		ServerPlayer player = player(helper, new BlockPos(4, 2, 3));
		setUp(helper, player);
		ServerLevel level = helper.getLevel();
		var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
		Map<String, ResourceKey<Structure>> vanilla = Map.of("stronghold", BuiltinStructures.STRONGHOLD, "ancient_city", BuiltinStructures.ANCIENT_CITY,
			"trial_chambers", BuiltinStructures.TRIAL_CHAMBERS);
		helper.assertTrue(Pathfinder.KINDS.size() == 3 && vanilla.keySet().containsAll(Pathfinder.KINDS), "kinds: " + Pathfinder.KINDS);
		vanilla.forEach((kind, key) -> {
			helper.assertTrue(Pathfinder.tag(kind).location().equals(AliveWorkplace.id("expedition/" + kind)), "tag id " + Pathfinder.tag(kind).location());
			var set = registry.getTag(Pathfinder.tag(kind));
			helper.assertTrue(set.isPresent() && set.get().stream().anyMatch(h -> h.is(key)), "the " + kind + " tag lacks " + key.location());
		});
		Pathfinder.forgetOffer(player.getUUID());
		helper.assertFalse(Pathfinder.choose(player, "stronghold"), "a pick with no offer set out");
		helper.assertTrue(Component.translatable("message.aliveworkplace.pathfinder.no_offer").getString().length() > 0
			&& !Component.translatable("message.aliveworkplace.pathfinder.no_offer").getString().startsWith("message."), "no_offer line");
		helper.succeed();
	}
}
