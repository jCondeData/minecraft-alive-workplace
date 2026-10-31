package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendSites;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.OldSage;
import io.github.jcondedata.aliveworkplace.legend.Seer;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchTree;
import io.github.jcondedata.aliveworkplace.research.ResearchTrees;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

/**
 * QA (independent, from the spec) for 29.14, the Old Sage, and 29.16, the Seer: the arrival conditions at their edges,
 * each power's numbers at level I and its limits, the player's own clicks and the chat they read, save and reload, the
 * legends switch off, and the lang lines.
 */
public class QaSageSeerGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos SAGE_HALL = new BlockPos(2, 2, 2);
	private static final BlockPos SEER_HALL = new BlockPos(15, 2, 15);
	private static final BlockPos MIDDLE = new BlockPos(15, 1, 15);
	private static final long DAY = VillageNeeds.DAY;
	private static final ResourceLocation TREE = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "ancient_lore");

	/** A fixed random whose {@code nextFloat} always answers {@code roll} (the visit odds' edge). */
	static RandomSource rolling(float roll) {
		return new LegacyRandomSource(29L) {
			@Override
			public float nextFloat() {
				return roll;
			}
		};
	}

	// ---- The Old Sage -------------------------------------------------------------------------------------------

	private static BlockPos sageHall(GameTestHelper helper) {
		return helper.absolutePos(SAGE_HALL);
	}

	private static VillageHallBlockEntity sageEntity(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getBlockEntity(SAGE_HALL);
	}

	private static void sageSetUp(GameTestHelper helper, ServerPlayer owner) {
		Leftovers.clear(helper);
		helper.setBlock(SAGE_HALL, ModBlocks.VILLAGE_HALL);
		sageEntity(helper).setOwner(owner.getUUID(), owner.getName().getString());
		ServerLevel level = helper.getLevel();
		BlockPos hall = sageHall(helper);
		Caravans.Data.get(level).setWants(hall, Component.literal("Test"), List.of());
		int radius = VillageHalls.RADIUS;
		boolean legends = Legends.ENABLED;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Legends.ENABLED = legends;
			Caravans.Data.get(level).remove(hall);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
				record.forgetCaptive(v.getUUID());
				v.discard();
			}
			for (LegendRecord.Captive c : record.captives()) {
				if (c.hall().equals(Optional.of(hall))) {
					record.forgetCaptive(c.villager());
				}
			}
			level.getEntitiesOfClass(IronGolem.class, helper.getBounds().inflate(16), g -> true).forEach(IronGolem::discard);
			OldSage.forget();
			Research.forget();
			CivicEffects.forget();
			LegendPowers.forget();
		});
	}

	private static void sageStaged(GameTestHelper helper, java.util.function.Consumer<Legend> body) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		Legend sage = Legends.get(OldSage.ID).orElse(null);
		helper.assertTrue(sage != null, "old_sage.json not loaded");
		try {
			Legends.setForTest(Map.of(sage.id(), sage));
			body.accept(sage);
		} finally {
			level.setDayTime(time);
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		}
	}

	private static void lore(GameTestHelper helper, Map<String, Integer> topics) {
		ResearchTree tree = ResearchTrees.get(TREE).orElseThrow();
		Map<String, Integer> out = new HashMap<>();
		topics.forEach((k, v) -> out.put(ResearchTrees.levelKey(tree, tree.topic(k).orElseThrow()), v));
		sageEntity(helper).setResearch(new Research.State(Map.copyOf(out), Optional.empty(), 0, false));
		CivicEffects.forget();
	}

	private static Villager sageAt(GameTestHelper helper) {
		Villager v = OldSage.placeHut(helper.getLevel(), sageHall(helper), helper.absolutePos(MIDDLE), RandomSource.create(14));
		helper.assertTrue(v != null, "the hut is set down");
		return v;
	}

	private static Villager job(GameTestHelper helper, VillagerProfession job, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(2));
		return v;
	}

	private static String lang(String key) {
		return Component.translatable(key).getString();
	}

	/** What the player read, a line each (the test client hears a chat line twice: as displayed and as sent). */
	private static List<String> seen(List<Component> seen) {
		List<String> out = new ArrayList<>();
		for (Component c : seen) {
			String s = c.getString();
			if (out.isEmpty() || !out.get(out.size() - 1).equals(s)) {
				out.add(s);
			}
		}
		return out;
	}

	/** The eight riddles say what the spec says (in the lang file), each with a hint; every Sage line is there. */
	//$ gametest_ticks AREA '20'
	@GameTest(template = AREA, timeoutTicks = 20)
	public void sageRiddlesReadAsTheSpecSays(GameTestHelper helper) {
		String[] spec = {
			"I have cities but no houses, forests but no trees, rivers but no water",
			"I point the way but never walk",
			"I follow the sun all day but never see the sky",
			"I have a spine but no bones, and leaves but no branches",
			"I'm full of holes, yet I hold water",
			"Feed me and I live; give me a drink and I die",
			"I run but never walk, and have a bed but never sleep",
			"I sleep under the mountains until a pick wakes me, and then every villager wants me"};
		ItemStack[] answers = {new ItemStack(Items.MAP), new ItemStack(Items.COMPASS), new ItemStack(Items.CLOCK), new ItemStack(Items.BOOK),
			new ItemStack(Items.SPONGE), new ItemStack(Items.TORCH), new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.EMERALD)};
		helper.assertTrue(OldSage.RIDDLES.size() == 8, "a pool of eight riddles: " + OldSage.RIDDLES.size());
		for (int n = 1; n <= 8; n++) {
			String key = "message.aliveworkplace.sage.riddle." + n;
			helper.assertTrue(!lang(key).equals(key) && lang(key).startsWith(spec[n - 1]), "riddle " + n + " reads: " + lang(key));
			String hint = "message.aliveworkplace.sage.hint." + n;
			helper.assertTrue(!lang(hint).equals(hint) && !lang(hint).isBlank(), "riddle " + n + " has no hint");
			OldSage.Riddle riddle = OldSage.RIDDLES.get(n - 1);
			helper.assertTrue(riddle.number() == n && riddle.answer().test(answers[n - 1]), "riddle " + n + " doesn't take " + answers[n - 1]);
			// the plain map (unused) answers the map riddle as well as a drawn one
		}
		for (String k : List.of("rumour", "riddle", "right", "wrong", "hint")) {
			String key = "message.aliveworkplace.sage." + k;
			helper.assertTrue(!lang(key).equals(key), key + " missing");
		}
		helper.assertTrue(lang("chronicle.aliveworkplace.iron_pact").length() > 5, "the Iron Pact's chronicle line");
		helper.succeed();
	}

	/** Every draw is three different riddles of the eight, and over many draws each of the eight is asked. */
	//$ gametest_ticks AREA '20'
	@GameTest(template = AREA, timeoutTicks = 20)
	public void sageDrawsThreeDifferentRiddlesFromAllEight(GameTestHelper helper) {
		Set<Integer> ever = new HashSet<>();
		for (long seed = 0; seed < 300; seed++) {
			OldSage.Riddles r = OldSage.draw(RandomSource.create(seed));
			helper.assertTrue(r.asked().size() == 3 && r.asked().stream().distinct().count() == 3, "seed " + seed + ": " + r.asked());
			helper.assertTrue(r.asked().stream().allMatch(n -> n >= 1 && n <= 8) && r.solved() == 0 && r.misses() == 0, "seed " + seed + ": " + r);
			ever.addAll(r.asked());
		}
		helper.assertTrue(ever.size() == 8, "riddles never asked: " + ever);
		helper.succeed();
	}

	/**
	 * Through a real player's clicks and the chat they read: a miss is a shake of the head and no hint; the second miss
	 * brings the riddle's hint; a right answer says so and asks "Riddle 2 of 3"; the misses start again on the new riddle.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaSageChat"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSageChat")
	public void sageHintComesOnTheSecondMissOfEachRiddle(GameTestHelper helper) {
		QaStageFiveGameTests.Client client = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("Sage"));
		ServerPlayer player = client.player();
		sageSetUp(helper, player);
		sageStaged(helper, legend -> {
			Villager sage = sageAt(helper);
			ModAttachments.SAGE_RIDDLES.set(sage, new OldSage.Riddles(List.of(2, 3, 4), 0, 0));
			client.seen().clear();
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			LegendSites.use(player, sage, InteractionHand.MAIN_HAND);
			helper.assertTrue(seen(client.seen()).stream().anyMatch(s -> s.contains("Riddle 1 of 3") && s.contains("I point the way but never walk")),
				"an empty hand hears riddle 1: " + seen(client.seen()));
			client.seen().clear();
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
			LegendSites.use(player, sage, InteractionHand.MAIN_HAND);
			List<String> first = seen(client.seen());
			helper.assertTrue(first.stream().anyMatch(s -> s.contains("shakes their head")), "no shake of the head: " + first);
			helper.assertTrue(first.stream().noneMatch(s -> s.contains("A hint")), "a hint after only one miss: " + first);
			client.seen().clear();
			LegendSites.use(player, sage, InteractionHand.MAIN_HAND);
			List<String> second = seen(client.seen());
			helper.assertTrue(second.stream().anyMatch(s -> s.contains("A hint") && s.contains(lang("message.aliveworkplace.sage.hint.2"))),
				"no compass hint after two misses: " + second);
			helper.assertTrue(player.getMainHandItem().is(Items.DIRT) && player.getMainHandItem().getCount() == 1, "a wrong item was taken");
			client.seen().clear();
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COMPASS));
			LegendSites.use(player, sage, InteractionHand.MAIN_HAND);
			List<String> right = seen(client.seen());
			helper.assertTrue(right.stream().anyMatch(s -> s.contains("Just so")) && right.stream().anyMatch(s -> s.contains("Riddle 2 of 3")
				&& s.contains("I follow the sun")), "the right answer and the next riddle: " + right);
			helper.assertTrue(player.getMainHandItem().isEmpty(), "the compass was handed over");
			client.seen().clear();
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
			LegendSites.use(player, sage, InteractionHand.MAIN_HAND);
			helper.assertTrue(seen(client.seen()).stream().noneMatch(s -> s.contains("A hint")), "the misses carried over to riddle 2: " + seen(client.seen()));
		});
		helper.succeed();
	}

	/** The answer in the off hand (main hand empty) is handed over; a wrong off-hand item with an empty main hand is no miss. */
	//$ gametest_ticks_batch AREA '100' '"qaSageOffhand"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSageOffhand")
	public void sageTakesTheAnswerFromTheOffHand(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		owner.setGameMode(GameType.SURVIVAL);
		sageSetUp(helper, owner);
		sageStaged(helper, legend -> {
			Villager sage = sageAt(helper);
			ModAttachments.SAGE_RIDDLES.set(sage, new OldSage.Riddles(List.of(3, 6, 8), 0, 0));
			owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			OldSage.Riddles r = ModAttachments.SAGE_RIDDLES.get(sage);
			helper.assertTrue(r.solved() == 0 && r.misses() == 0 && owner.getOffhandItem().is(Items.TORCH), "an off-hand torch for the clock riddle: " + r);
			owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.CLOCK));
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			r = ModAttachments.SAGE_RIDDLES.get(sage);
			helper.assertTrue(r.solved() == 1 && owner.getOffhandItem().isEmpty(), "the off-hand clock wasn't taken: " + r);
			// a stack of torches: one is handed over
			owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH, 5));
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			helper.assertTrue(ModAttachments.SAGE_RIDDLES.get(sage).solved() == 2 && owner.getMainHandItem().getCount() == 4, "one torch of five");
			owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 3));
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			helper.assertTrue(LegendRecord.get(helper.getLevel()).captive(sage.getUUID()).orElseThrow().freed(), "three answers didn't free the Sage");
			helper.assertTrue(owner.getMainHandItem().getCount() == 2, "the emerald handed over: " + owner.getMainHandItem());
			// clicking the freed Sage again takes nothing
			owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 2));
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			helper.assertTrue(owner.getMainHandItem().getCount() == 2, "the freed Sage took another emerald");
		});
		helper.succeed();
	}

	/** The quest's progress is on the Sage: saved and loaded with them, mid-quest, with the misses. */
	//$ gametest_ticks_batch AREA '100' '"qaSageSave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSageSave")
	public void sageQuestSurvivesSaveAndReload(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		sageSetUp(helper, owner);
		sageStaged(helper, legend -> {
			Villager sage = sageAt(helper);
			OldSage.Riddles mid = new OldSage.Riddles(List.of(5, 1, 7), 1, 1);
			ModAttachments.SAGE_RIDDLES.set(sage, mid);
			CompoundTag tag = sage.saveWithoutId(new CompoundTag());
			Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
			loaded.load(tag);
			helper.assertTrue(mid.equals(ModAttachments.SAGE_RIDDLES.get(loaded)), "after reload: " + ModAttachments.SAGE_RIDDLES.get(loaded));
			helper.assertTrue(ModAttachments.SAGE_RIDDLES.get(loaded).current().number() == 1, "the riddle being asked after reload");
			// an old save with no fields
			OldSage.Riddles empty = OldSage.Riddles.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).result().orElseThrow();
			helper.assertTrue(empty.asked().isEmpty() && empty.solved() == 0 && empty.misses() == 0, "defaults: " + empty);
			loaded.discard();
		});
		helper.succeed();
	}

	/** 5 research levels qualify, 4 don't; with the legends switch off, 5 levels don't either. */
	//$ gametest_ticks_batch AREA '100' '"qaSageSwitch"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSageSwitch")
	public void sageHutNeedsFiveLevelsAndTheSwitchOn(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		sageSetUp(helper, owner);
		ServerLevel level = helper.getLevel();
		sageStaged(helper, legend -> {
			BlockPos hall = sageHall(helper);
			// one topic at 5 counts the same as five topics at 1
			sageEntity(helper).setResearch(new Research.State(Map.of("swift_hands", 4), Optional.empty(), 0, false));
			helper.assertFalse(OldSage.qualifies(level, hall), "4 levels in one topic qualify");
			sageEntity(helper).setResearch(new Research.State(Map.of("swift_hands", 5), Optional.empty(), 0, false));
			helper.assertTrue(OldSage.qualifies(level, hall), "5 levels in one topic don't qualify");
			Legends.ENABLED = false;
			helper.assertFalse(OldSage.qualifies(level, hall), "the hut with the legends switch off");
			Legends.ENABLED = true;
		});
		helper.succeed();
	}

	/** Level I of each two-level topic: Old Harvests 10% (beekeepers too), Herb Lore a day, Deep Memory 15%, Runes 20%. */
	//$ gametest_ticks_batch AREA '100' '"qaLoreLevelOne"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLoreLevelOne")
	public void ancientLoreLevelOneNumbers(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		sageSetUp(helper, owner);
		ServerLevel level = helper.getLevel();
		Villager farmer = job(helper, VillagerProfession.FARMER, new BlockPos(6, 2, 8));
		Villager beekeeper = job(helper, ModVillagers.BEEKEEPER, new BlockPos(7, 2, 8));
		Villager orchard = job(helper, ModVillagers.ORCHARD_KEEPER, new BlockPos(8, 2, 8));
		Villager librarian = job(helper, VillagerProfession.LIBRARIAN, new BlockPos(9, 2, 8));
		Villager nurse = job(helper, ModVillagers.NURSE, new BlockPos(10, 2, 8));
		helper.runAfterDelay(2, () -> {
			BlockPos hall = sageHall(helper);
			float raidBefore = VillageRaids.chance(level, hall, 30);
			int xpBefore = Traits.xp(librarian, 100, RandomSource.create(7));
			long sickBefore = Sickness.recovery(nurse);
			helper.assertTrue(Pace.ANCIENT_LORE.factor().of(beekeeper) == 1f, "beekeeper faster with nothing researched");
			lore(helper, Map.of("old_harvests", 1, "herb_lore", 1, "deep_memory", 1, "runes_of_warding", 1));
			float f = 1f / 1.1f;
			helper.assertTrue(Math.abs(Pace.ANCIENT_LORE.factor().of(farmer) - f) < 1e-4f, "farmer at Old Harvests I: " + Pace.ANCIENT_LORE.factor().of(farmer));
			helper.assertTrue(Math.abs(Pace.ANCIENT_LORE.factor().of(orchard) - f) < 1e-4f, "orchard keeper at Old Harvests I: " + Pace.ANCIENT_LORE.factor().of(orchard));
			helper.assertTrue(Math.abs(Pace.ANCIENT_LORE.factor().of(beekeeper) - f) < 1e-4f, "beekeeper at Old Harvests I: " + Pace.ANCIENT_LORE.factor().of(beekeeper));
			helper.assertTrue(Pace.ANCIENT_LORE.factor().of(librarian) == 1f, "a librarian under Old Harvests");
			helper.assertTrue(Sickness.recovery(nurse) == sickBefore - DAY, "Herb Lore I: " + sickBefore + " -> " + Sickness.recovery(nurse));
			int xp = Traits.xp(librarian, 100, RandomSource.create(7));
			helper.assertTrue(xp == xpBefore + 15, "Deep Memory I xp " + xpBefore + " -> " + xp);
			float raid = VillageRaids.chance(level, hall, 30);
			helper.assertTrue(Math.abs(raid - raidBefore * 0.8f) < 1e-5f, "Runes of Warding I: " + raidBefore + " -> " + raid);
			helper.succeed();
		});
	}

	/** The Iron Pact's golem limit at its edge: 7 villagers no golem, 8 one, 16 two; guards take 25% less, others full. */
	//$ gametest_ticks_batch AREA '100' '"qaIronPact"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaIronPact")
	public void ironPactGolemLimitAndGuards(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		sageSetUp(helper, owner);
		ServerLevel level = helper.getLevel();
		List<Villager> folk = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			folk.add(job(helper, VillagerProfession.NITWIT, new BlockPos(5 + i, 2, 10)));
		}
		helper.runAfterDelay(2, () -> {
			BlockPos hall = sageHall(helper);
			lore(helper, Map.of("iron_pact", 1));
			helper.assertTrue(OldSage.pactGolem(level, hall) == null, "a golem for 7 villagers");
			Villager guard = job(helper, ModVillagers.GUARD, new BlockPos(13, 2, 10));
			IronGolem golem = OldSage.pactGolem(level, hall);
			helper.assertTrue(golem != null && golem.getTags().contains(OldSage.PACT_TAG), "no golem for 8 villagers");
			helper.assertTrue(OldSage.pactGolem(level, hall) == null, "a second golem for 8 villagers");
			for (int i = 0; i < 8; i++) {
				folk.add(job(helper, VillagerProfession.NITWIT, new BlockPos(5 + i, 2, 12)));
			}
			helper.assertTrue(OldSage.pactGolem(level, hall) != null, "no second golem for 16 villagers");
			helper.assertTrue(OldSage.damage(guard, 8f) == 6f, "a guard under the pact takes " + OldSage.damage(guard, 8f));
			helper.assertTrue(OldSage.damage(folk.get(0), 8f) == 8f, "a nitwit under the pact takes " + OldSage.damage(folk.get(0), 8f));
			lore(helper, Map.of("golden_age", 1));
			helper.assertTrue(OldSage.damage(guard, 8f) == 8f, "a guard without the pact takes " + OldSage.damage(guard, 8f));
			helper.succeed();
		});
	}

	/** The Iron Pact's golem walks in only on every 5th day, through the hall's morning round. */
	//$ gametest_ticks_batch AREA '100' '"qaIronPactDays"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaIronPactDays")
	public void ironPactGolemEveryFiveDays(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		sageSetUp(helper, owner);
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < 16; i++) {
			job(helper, VillagerProfession.NITWIT, new BlockPos(5 + i % 8, 2, 10 + i / 8 * 2));
		}
		helper.runAfterDelay(2, () -> sageStaged(helper, legend -> {
			BlockPos hall = sageHall(helper);
			OldSage.forget();
			lore(helper, Map.of("iron_pact", 1));
			long base = level.getDayTime() - level.getDayTime() % DAY + 100 * DAY;
			level.setDayTime(base + 2000);
			long d = Chronicle.day(level);
			long k = Math.floorMod(-d, OldSage.PACT_DAYS); // days until a 5th
			int[] golems = new int[OldSage.PACT_DAYS + 1];
			for (int i = 0; i <= OldSage.PACT_DAYS; i++) {
				level.setDayTime(base + (k + i) * DAY + 2000);
				OldSage.round(level, hall);
				OldSage.round(level, hall); // twice the same morning
				golems[i] = level.getEntitiesOfClass(IronGolem.class, VillageHalls.area(hall), g -> g.getTags().contains(OldSage.PACT_TAG)).size();
			}
			helper.assertTrue(golems[0] == 1 && golems[OldSage.PACT_DAYS - 1] == 1 && golems[OldSage.PACT_DAYS] == 2,
				"golems by day from a 5th day: " + java.util.Arrays.toString(golems));
		}));
		helper.runAfterDelay(4, helper::succeed);
	}

	// ---- The Seer -----------------------------------------------------------------------------------------------

	private static VillageHallBlockEntity seerEntity(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getBlockEntity(SEER_HALL);
	}

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	private static void seerSetUp(GameTestHelper helper, List<BlueprintData.Placement> recorded) {
		Leftovers.clear(helper);
		helper.setBlock(SEER_HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			ServerLevel level = helper.getLevel();
			recorded.forEach(p -> BuildSiteManager.get(level).forgetFinished(p));
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			LegendPowers.forget();
		});
	}

	private static void seerStaged(GameTestHelper helper, java.util.function.Consumer<Legend> body) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		int radius = VillageHalls.RADIUS;
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		boolean raids = VillageRaids.ENABLED;
		boolean markets = MarketDays.ENABLED;
		boolean festivals = Festivals.ENABLED;
		boolean legends = Legends.ENABLED;
		Legend seer = Legends.get(Seer.ID).orElse(null);
		helper.assertTrue(seer != null, "seer.json not loaded");
		try {
			VillageHalls.RADIUS = 20;
			Moods.ENABLED = false;
			LegendNeeds.ENABLED = true;
			Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
			map.put(seer.id(), seer);
			Legends.setForTest(map);
			FriendPerksGameTests.titled(level, helper.absolutePos(SEER_HALL), 50);
			body.accept(seer);
		} finally {
			VillageHalls.RADIUS = radius;
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			VillageRaids.ENABLED = raids;
			MarketDays.ENABLED = markets;
			Festivals.ENABLED = festivals;
			Legends.ENABLED = legends;
			BlockPos hall = helper.absolutePos(SEER_HALL);
			VillageRaids.raiders(level, hall).forEach(Mob::discard);
			VillageRaids.forget(hall);
			level.setDayTime(time);
			Moods.forget();
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		}
	}

	private static void building(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos origin, List<BlueprintData.Placement> recorded) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(entry.id(), placement, UUID.randomUUID());
		recorded.add(placement);
	}

	/** Rolls the Chapel's night on full-moon {@code day} at {@code time} with {@code random}; returns whether the Seer came (and sends them off). */
	private static boolean comes(GameTestHelper helper, long day, long time, RandomSource random) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(SEER_HALL);
		seerEntity(helper).setLegendGuests(LegendGuests.State.EMPTY);
		at(level, day, time);
		LegendGuests.round(level, hall, random);
		Villager guest = LegendGuests.guest(level, hall);
		if (guest != null) {
			LegendGuests.leave(level, hall, guest);
			LegendRecord.get(level).forget(guest.getUUID());
			guest.discard();
			seerEntity(helper).setLegendGuests(LegendGuests.State.EMPTY);
			return true;
		}
		return false;
	}

	/**
	 * The midnight window at its edges with a die that always says yes; a full moon only; a finished cottage isn't a
	 * Chapel; the odds are exactly 1 in 2 (a roll just under 0.5 comes, 0.5 doesn't); the legends switch off: nobody.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaSeerArrives"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSeerArrives")
	public void seerArrivalAtItsEdges(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		seerSetUp(helper, recorded);
		helper.runAfterDelay(2, () -> seerStaged(helper, seer -> {
			ServerLevel level = helper.getLevel();
			RandomSource yes = rolling(0f);
			long full = 1 + 8L * 30;
			building(helper, StarterBlueprints.STARTER_COTTAGE, new BlockPos(2, 1, 2), recorded);
			helper.assertFalse(comes(helper, full, 18000, yes), "the Seer came to a cottage, with no Chapel");
			building(helper, StarterBlueprints.CHAPEL, new BlockPos(16, 1, 2), recorded);
			helper.assertTrue(LegendGuests.chapel(level, helper.absolutePos(SEER_HALL)).isPresent(), "the Chapel isn't found");
			helper.assertTrue(comes(helper, full, 18000, yes), "the Seer didn't come at a full moon's midnight with a sure roll");
			helper.assertFalse(comes(helper, full + 8, 12000, yes), "the Seer came at dusk");
			helper.assertFalse(comes(helper, full + 16, 23000, yes), "the Seer came before dawn");
			helper.assertFalse(comes(helper, full + 24, 6000, yes), "the Seer came at noon");
			for (long other = full + 33; other < full + 40; other++) {
				helper.assertFalse(comes(helper, other, 18000, yes), "the Seer came at midnight on day " + other + ", moon phase " + level.getMoonPhase());
			}
			helper.assertTrue(comes(helper, full + 48, 18000, rolling(0.4999f)), "a roll of 0.4999 is inside 1 in 2");
			helper.assertFalse(comes(helper, full + 56, 18000, rolling(0.5f)), "a roll of 0.5 is outside 1 in 2");
			Legends.ENABLED = false;
			helper.assertFalse(comes(helper, full + 64, 18000, yes), "the Seer came with the legends switch off");
			Legends.ENABLED = true;
			helper.succeed();
		}));
	}

	/** The window's edges: 17500 to 19000 (around midnight, 18000). Reported separately so the bound is visible. */
	//$ gametest_ticks_batch AREA '100' '"qaSeerMidnight"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSeerMidnight")
	public void seerMidnightWindowEdges(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		seerSetUp(helper, recorded);
		helper.runAfterDelay(2, () -> seerStaged(helper, seer -> {
			RandomSource yes = rolling(0f);
			building(helper, StarterBlueprints.CHAPEL, new BlockPos(16, 1, 2), recorded);
			long full = 1 + 8L * 40;
			helper.assertTrue(comes(helper, full, LegendGuests.MIDNIGHT_FROM, yes), "not at the window's start");
			helper.assertTrue(comes(helper, full + 8, LegendGuests.MIDNIGHT_TO - 1, yes), "not at the window's last tick");
			helper.assertFalse(comes(helper, full + 16, LegendGuests.MIDNIGHT_FROM - 1, yes), "a tick before the window");
			helper.assertFalse(comes(helper, full + 24, LegendGuests.MIDNIGHT_TO, yes), "at the window's end");
			helper.assertTrue(LegendGuests.MIDNIGHT_FROM <= 18000 && 18000 < LegendGuests.MIDNIGHT_TO, "the window misses midnight");
			helper.succeed();
		}));
	}

	/**
	 * The dawn foretelling through the hall's round, read by a real player in chat: the opening line and four lines,
	 * once a day; not from the dawn window's end; and not at all with the legends switch off.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaSeerDawn"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSeerDawn")
	public void seerForetellsOnceAtDawnInChat(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		seerSetUp(helper, recorded);
		QaStageFiveGameTests.Client client = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("Seer"));
		helper.runAfterDelay(2, () -> seerStaged(helper, seer -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(SEER_HALL);
			Villager s = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
			s.setNoAi(true);
			Legends.make(level, s, seer, "test");
			RandomSource random = RandomSource.create(161L);
			client.seen().clear();
			at(level, 50, Seer.DAWN_TO);
			Seer.round(level, hall, random);
			helper.assertTrue(seerEntity(helper).seer().toldDay() != Chronicle.day(level), "foretold at the dawn window's end");
			at(level, 51, Seer.DAWN_TO - 1);
			Seer.round(level, hall, random);
			List<String> lines = seen(client.seen());
			helper.assertTrue(seerEntity(helper).seer().toldDay() == Chronicle.day(level), "not foretold at dawn's last tick");
			helper.assertTrue(lines.size() == 5 && lines.get(0).contains("foretells for"), "the dawn's lines: " + lines);
			helper.assertTrue(lines.stream().anyMatch(l -> l.contains("raiders")), "no word of raiders: " + lines);
			helper.assertTrue(lines.stream().anyMatch(l -> l.contains("festival")), "no word of the festival: " + lines);
			helper.assertTrue(lines.stream().anyMatch(l -> l.contains("market")), "no word of the market: " + lines);
			helper.assertTrue(lines.stream().anyMatch(l -> l.contains("Legend") || l.contains("Tomorrow")), "no word of tomorrow's guest: " + lines);
			client.seen().clear();
			at(level, 51, 500);
			Seer.round(level, hall, random);
			helper.assertTrue(client.seen().isEmpty(), "foretold twice in a day: " + seen(client.seen()));
			Legends.ENABLED = false;
			at(level, 52, 500);
			LegendGuests.round(level, hall, random);
			helper.assertTrue(client.seen().isEmpty() && seerEntity(helper).seer().toldDay() != Chronicle.day(level),
				"foretold with the legends switch off: " + seen(client.seen()));
			Legends.ENABLED = true;
			s.discard();
			helper.succeed();
		}));
	}

	/** The foretelling saved on the hall survives a save and reload; an old hall with no foretelling loads empty. */
	//$ gametest_ticks_batch AREA '100' '"qaSeerSave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSeerSave")
	public void seerForetellingSurvivesSaveAndReload(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		seerSetUp(helper, recorded);
		ServerLevel level = helper.getLevel();
		Seer.State state = new Seer.State(12, true, 1.25, 13500, false, 14, 15, Map.of("chapel@13", new Seer.Told(13, Optional.of(Seer.ID)),
			"hall@12", new Seer.Told(12, Optional.empty())));
		seerEntity(helper).setSeer(state);
		CompoundTag tag = seerEntity(helper).saveWithFullMetadata(level.registryAccess());
		BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(SEER_HALL), helper.getBlockState(SEER_HALL), tag, level.registryAccess());
		helper.assertTrue(loaded instanceof VillageHallBlockEntity e && e.seer().equals(state), "after reload: "
			+ (loaded instanceof VillageHallBlockEntity e ? e.seer() : loaded));
		CompoundTag none = new CompoundTag();
		Seer.State.EMPTY.save(none);
		helper.assertTrue(!none.contains("seer") && Seer.State.load(none).equals(Seer.State.EMPTY), "an empty foretelling written");
		CompoundTag partial = new CompoundTag();
		CompoundTag inner = new CompoundTag();
		inner.putLong("told_day", 3);
		partial.put("seer", inner);
		Seer.State p = Seer.State.load(partial);
		helper.assertTrue(p.toldDay() == 3 && !p.raid() && p.festival() == -1 && p.market() == -1 && p.guests().isEmpty(), "defaults: " + p);
		CompoundTag b = new CompoundTag();
		b.putLong("day", 4);
		Seer.Blessing blessing = Seer.Blessing.CODEC.parse(NbtOps.INSTANCE, b).result().orElseThrow();
		helper.assertTrue(blessing.mood() == 10 && blessing.days() == 7 && blessing.babyDays() == 2, "a blessing's defaults: " + blessing);
		helper.succeed();
	}

	/** A Seer more than 32 blocks from the Chapel (still in the village) doesn't bless; one at 31 does; another venue doesn't. */
	//$ gametest_ticks_batch AREA '100' '"qaSeerReach"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSeerReach")
	public void seerBlessesOnlyNearTheChapel(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		seerSetUp(helper, recorded);
		helper.runAfterDelay(2, () -> seerStaged(helper, seer -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(SEER_HALL);
			VillageHalls.RADIUS = 80;
			building(helper, StarterBlueprints.CHAPEL, new BlockPos(2, 1, 2), recorded);
			BlockPos chapel = LegendGuests.chapel(level, hall).orElseThrow();
			Villager s = helper.spawn(EntityType.VILLAGER, new BlockPos(20, 2, 20));
			s.setNoAi(true);
			Legends.make(level, s, seer, "test", Optional.of(hall));
			Villager a = helper.spawn(EntityType.VILLAGER, new BlockPos(20, 2, 22));
			Villager b = helper.spawn(EntityType.VILLAGER, new BlockPos(21, 2, 22));
			a.setNoAi(true);
			b.setNoAi(true);
			s.teleportTo(chapel.getX() + 0.5, chapel.getY(), chapel.getZ() + 0.5 + 33);
			LegendPowers.forget();
			helper.assertFalse(Seer.bless(level, hall, a, b, chapel), "blessed with the Seer 33 blocks from the Chapel");
			s.teleportTo(chapel.getX() + 0.5, chapel.getY(), chapel.getZ() + 0.5 + 31);
			helper.assertFalse(Seer.bless(level, hall, a, b, chapel.offset(5, 0, 0)), "blessed a wedding away from the Chapel");
			helper.assertTrue(Seer.bless(level, hall, a, b, chapel), "not blessed with the Seer 31 blocks from the Chapel");
			helper.assertTrue(Seer.blessedMood(level, a) != null && Seer.blessedMood(level, a).equals(Seer.blessedMood(level, b)), "the couple's mood");
			helper.succeed();
		}));
	}

	/** "today", "tomorrow", "in 2 days"; the power lines read their numbers; Seer's lines are in the lang file. */
	//$ gametest_ticks AREA '20'
	@GameTest(template = AREA, timeoutTicks = 20)
	public void seerWordsReadRight(GameTestHelper helper) {
		helper.assertTrue(Seer.when(10, 10).getString().equals("today") && Seer.when(11, 10).getString().equals("tomorrow")
			&& Seer.when(12, 10).getString().equals("in 2 days"), "when: " + Seer.when(11, 10).getString() + ", " + Seer.when(12, 10).getString());
		Legend seer = Legends.get(Seer.ID).orElseThrow();
		Seer.ForetellPower fp = seer.powers(Seer.ForetellPower.class).get(0);
		Seer.BlessWeddingsPower bp = seer.powers(Seer.BlessWeddingsPower.class).get(0);
		helper.assertTrue(fp.warningDays() == 2 && bp.mood() == 10 && bp.days() == 7 && bp.babyDays() == 2, "the Seer's numbers: " + fp + " " + bp);
		String b = bp.describe().getString();
		helper.assertTrue(b.contains("10") && b.contains("7 days") && b.contains("2 days"), "bless_weddings reads: " + b);
		helper.assertTrue(fp.describe().getString().contains("2 days"), "foretell reads: " + fp.describe().getString());
		Pattern british = Pattern.compile("(?i)\\b(rumour|colour|grey|honour|favour|armour|defence|centre|travell)");
		for (String key : List.of("message.aliveworkplace.seer.dawn", "message.aliveworkplace.seer.raid", "message.aliveworkplace.seer.calm",
			"message.aliveworkplace.seer.festival", "message.aliveworkplace.seer.no_festival", "message.aliveworkplace.seer.market",
			"message.aliveworkplace.seer.no_market", "message.aliveworkplace.seer.guest", "message.aliveworkplace.seer.no_guest",
			"message.aliveworkplace.seer.blessed", "chronicle.aliveworkplace.blessed_wedding", "mood.aliveworkplace.reason.blessed",
			"advice.aliveworkplace.seer_raid", "advice.aliveworkplace.seer_calm", "advice.aliveworkplace.seer_days", "advice.aliveworkplace.seer_guest",
			"advice.aliveworkplace.seer_no_guest", "message.aliveworkplace.sage.rumour", "message.aliveworkplace.sage.wrong",
			"message.aliveworkplace.sage.hint", "message.aliveworkplace.sage.riddle", "legend.aliveworkplace.seer.title",
			"legend.aliveworkplace.seer.lore", "legend.aliveworkplace.old_sage.title", "legend.aliveworkplace.old_sage.lore")) {
			String text = lang(key);
			helper.assertTrue(!text.equals(key), key + " missing");
			helper.assertFalse(british.matcher(text).find(), key + " isn't American English: " + text);
		}
		helper.succeed();
	}
}
