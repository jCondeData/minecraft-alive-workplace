package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Plunder;
import io.github.jcondedata.aliveworkplace.threat.SiegeReport;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-10-24), ROADMAP 32.10 from the spec, for what PiglinGameTests, QaPiglinRaidGameTests, QaRefusedThreatsGameTests
 * and QaPortalThreatsGameTests leave out: a raiding piglin brute is still a brute and a raiding hoglin still a hoglin
 * after 600 ticks in the Overworld; plunder's reach (a chest exactly 4 blocks off is taken, 5 off is not); every kind of
 * gold the spec names is taken and nothing else, one stack at most; gold carried home at dawn goes into the outpost's
 * chest; the Warlord wears gold from head to foot; the siege report for a single gold; the hoard's loot; the roster's
 * shares over many rolls; and Town-and-up with a nether link from both sides.
 */
public class QaPiglinIncursionGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PIGLINS = AliveWorkplace.id("piglin_incursion");
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	private static Culture piglins(GameTestHelper helper) {
		Optional<Culture> culture = Threats.get(PIGLINS);
		helper.assertTrue(culture.isPresent(), "piglin_incursion.json didn't load");
		return culture.get();
	}

	/** A village round {@code at} of radius 20, put back when the test ends. */
	private static BlockPos village(GameTestHelper helper, BlockPos at) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(at);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> true).forEach(ItemEntity::discard);
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetPlunder(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
		});
		return hall;
	}

	/** One of the culture's raiders as a raid makes it: the first roster line of kind {@code entity}. */
	private static Mob raider(GameTestHelper helper, Culture culture, EntityType<?> entity, BlockPos at) {
		ServerLevel level = helper.getLevel();
		Culture.Member member = culture.roster().stream().filter(m -> m.entity().equals(EntityType.getKey(entity))).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos p = helper.absolutePos(at);
		mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
		Threats.outfit(level, mob, culture, member);
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	/** A raiding piglin with nothing on its head, standing still. */
	private static Mob stillPiglin(GameTestHelper helper, Culture culture, BlockPos at) {
		Mob piglin = raider(helper, culture, EntityType.PIGLIN, at);
		piglin.setNoAi(true);
		piglin.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		return piglin;
	}

	private static void pen(GameTestHelper helper, int x0, int z0, int x1, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (x == x0 || x == x1 || z == z0 || z == z1) {
					for (int y = 2; y <= 4; y++) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
					}
				}
			}
		}
	}

	private static int count(Container chest) {
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			n += chest.getItem(i).getCount();
		}
		return n;
	}

	/**
	 * "While raiding they never turn into zombified piglins": a raiding brute is still a brute and a raiding hoglin
	 * still a hoglin (not a zoglin) after 620 ticks in the Overworld, while a plain brute and a plain hoglin penned
	 * beside them have turned (proving the test stands in the Overworld).
	 */
	//$ gametest_ticks_batch AREA '760' '"qaBruteHoglinStay"'
	@GameTest(template = AREA, timeoutTicks = 760, batch = "qaBruteHoglinStay")
	public void aRaidingBruteAndHoglinStayThemselvesInTheOverworld(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL);
		ServerLevel level = helper.getLevel();
		Culture culture = piglins(helper);
		pen(helper, 1, 1, 6, 6);
		pen(helper, 22, 1, 27, 6);
		pen(helper, 1, 22, 6, 27);
		pen(helper, 22, 22, 27, 27);
		Mob brute = raider(helper, culture, EntityType.PIGLIN_BRUTE, new BlockPos(3, 2, 3));
		Mob hoglin = raider(helper, culture, EntityType.HOGLIN, new BlockPos(24, 2, 3));
		PiglinBrute plainBrute = helper.spawn(EntityType.PIGLIN_BRUTE, new BlockPos(3, 2, 24));
		Hoglin plainHoglin = helper.spawn(EntityType.HOGLIN, new BlockPos(24, 2, 24));
		VillageRaids.track(level, hall, culture, List.of(brute, hoglin));
		helper.runAfterDelay(620, () -> {
			helper.assertTrue(plainBrute.isRemoved() && plainHoglin.isRemoved()
				&& level.getEntitiesOfClass(ZombifiedPiglin.class, helper.getBounds(), z -> true).size() == 1
				&& level.getEntitiesOfClass(Zoglin.class, helper.getBounds(), z -> true).size() == 1,
				"the plain brute and hoglin didn't turn: the test isn't in the Overworld");
			helper.assertTrue(brute.isAlive() && !brute.isRemoved() && brute.getType() == EntityType.PIGLIN_BRUTE && !((AbstractPiglin) brute).isConverting(),
				"the raiding brute turned: " + brute);
			helper.assertTrue(hoglin.isAlive() && !hoglin.isRemoved() && hoglin.getType() == EntityType.HOGLIN && !((Hoglin) hoglin).isConverting(),
				"the raiding hoglin turned: " + hoglin);
			helper.succeed();
		});
	}

	/** "Within 4 blocks of a village chest": a chest exactly 4 blocks off is plundered, one exactly 5 blocks off is not. */
	//$ gametest_ticks_batch AREA '80' '"qaPlunderReach"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaPlunderReach")
	public void aChestFourBlocksOffIsPlunderedAndFiveOffIsNot(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL);
		ServerLevel level = helper.getLevel();
		Culture culture = piglins(helper);
		helper.setBlock(new BlockPos(8, 2, 4), Blocks.CHEST);
		helper.setBlock(new BlockPos(4, 2, 16), Blocks.CHEST); // north-south this time
		helper.setBlock(new BlockPos(9, 2, 25), Blocks.CHEST);
		Container four = helper.getBlockEntity(new BlockPos(8, 2, 4));
		Container fourZ = helper.getBlockEntity(new BlockPos(4, 2, 16));
		Container five = helper.getBlockEntity(new BlockPos(9, 2, 25));
		four.setItem(0, new ItemStack(Items.GOLD_INGOT, 7));
		fourZ.setItem(0, new ItemStack(Items.GOLD_INGOT, 7));
		five.setItem(0, new ItemStack(Items.GOLD_INGOT, 7));
		Mob a = stillPiglin(helper, culture, new BlockPos(4, 2, 4));
		Mob b = stillPiglin(helper, culture, new BlockPos(4, 2, 12));
		Mob c = stillPiglin(helper, culture, new BlockPos(4, 2, 25));
		VillageRaids.track(level, hall, culture, List.of(a, b, c));
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(Plunder.carried(a).is(Items.GOLD_INGOT) && Plunder.carried(a).getCount() == 7 && four.getItem(0).isEmpty(),
				"the chest 4 blocks east wasn't plundered: the piglin carries " + Plunder.carried(a));
			helper.assertTrue(Plunder.carried(b).getCount() == 7 && fourZ.getItem(0).isEmpty(),
				"the chest 4 blocks south wasn't plundered: the piglin carries " + Plunder.carried(b));
			helper.assertTrue(Plunder.carried(c).isEmpty() && five.getItem(0).getCount() == 7,
				"the chest 5 blocks off was plundered: the piglin carries " + Plunder.carried(c));
			helper.succeed();
		});
	}

	/**
	 * "Takes the gold in it (ingots, nuggets, blocks, raw gold, golden gear; a stack at most)": every kind named counts
	 * as gold and the golden foods, the ores and the gilded blackstone don't; a chest of only those is left whole; from a
	 * chest of mixed gold one stack goes, and a piglin carrying it takes no more on later rounds.
	 */
	//$ gametest_ticks_batch AREA '120' '"qaPlunderKinds"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "qaPlunderKinds")
	public void onlyTheGoldTheSpecNamesIsTakenAndAStackAtMost(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL);
		ServerLevel level = helper.getLevel();
		Culture culture = piglins(helper);
		for (Item gold : List.of(Items.GOLD_INGOT, Items.GOLD_NUGGET, Items.GOLD_BLOCK, Items.RAW_GOLD, Items.RAW_GOLD_BLOCK, Items.GOLDEN_SWORD,
			Items.GOLDEN_AXE, Items.GOLDEN_PICKAXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_HOE, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE,
			Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS)) {
			helper.assertTrue(Plunder.isGold(new ItemStack(gold)), "not counted as gold: " + BuiltInRegistries.ITEM.getKey(gold));
		}
		ItemStack worn = new ItemStack(Items.GOLDEN_SWORD);
		worn.setDamageValue(20);
		helper.assertTrue(Plunder.isGold(worn), "a worn golden sword isn't gold");
		List<Item> notGold = List.of(Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_CARROT, Items.GLISTERING_MELON_SLICE,
			Items.GOLD_ORE, Items.DEEPSLATE_GOLD_ORE, Items.NETHER_GOLD_ORE, Items.GILDED_BLACKSTONE, Items.IRON_INGOT, Items.IRON_SWORD,
			Items.NETHERITE_INGOT, Items.COPPER_INGOT);
		for (Item item : notGold) {
			helper.assertFalse(Plunder.isGold(new ItemStack(item)), "counted as gold: " + BuiltInRegistries.ITEM.getKey(item));
		}
		helper.assertFalse(Plunder.isGold(ItemStack.EMPTY), "nothing counted as gold");
		// A chest of things that only look golden.
		helper.setBlock(new BlockPos(6, 2, 4), Blocks.CHEST);
		Container shiny = helper.getBlockEntity(new BlockPos(6, 2, 4));
		for (int i = 0; i < notGold.size(); i++) {
			shiny.setItem(i, new ItemStack(notGold.get(i), Math.min(8, notGold.get(i).getDefaultMaxStackSize())));
		}
		int shinyBefore = count(shiny);
		Mob looker = stillPiglin(helper, culture, new BlockPos(4, 2, 4));
		// A chest of mixed gold.
		helper.setBlock(new BlockPos(6, 2, 24), Blocks.CHEST);
		Container mixed = helper.getBlockEntity(new BlockPos(6, 2, 24));
		mixed.setItem(3, new ItemStack(Items.RAW_GOLD, 3));
		mixed.setItem(5, new ItemStack(Items.GOLD_NUGGET, 40));
		mixed.setItem(9, new ItemStack(Items.GOLDEN_BOOTS));
		mixed.setItem(12, new ItemStack(Items.GOLD_BLOCK, 2));
		mixed.setItem(20, new ItemStack(Items.COBBLESTONE, 30));
		Mob thief = stillPiglin(helper, culture, new BlockPos(4, 2, 24));
		VillageRaids.track(level, hall, culture, List.of(looker, thief));
		ItemStack[] first = {ItemStack.EMPTY};
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(Plunder.carried(looker).isEmpty() && count(shiny) == shinyBefore,
				"took from a chest with no gold: carries " + Plunder.carried(looker) + ", chest " + count(shiny) + " of " + shinyBefore);
			ItemStack took = Plunder.carried(thief);
			first[0] = took.copy();
			helper.assertTrue(!took.isEmpty() && Plunder.isGold(took), "took no gold from the mixed chest: " + took);
			int expected = 3 + 40 + 1 + 2 - took.getCount();
			int left = 0;
			for (int i = 0; i < mixed.getContainerSize(); i++) {
				if (Plunder.isGold(mixed.getItem(i))) {
					left += mixed.getItem(i).getCount();
				}
			}
			helper.assertTrue(left == expected, "more than one stack went: carries " + took + ", gold left " + left + " (expected " + expected + ")");
			helper.assertTrue(mixed.getItem(20).is(Items.COBBLESTONE) && mixed.getItem(20).getCount() == 30, "the cobblestone was touched");
		});
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(ItemStack.matches(Plunder.carried(thief), first[0]), "it took more on a later round: " + first[0] + " -> " + Plunder.carried(thief));
			helper.succeed();
		});
	}

	/**
	 * The outpost: the Warlord is "a brute in gold armour, +50 health" (golden helmet, chestplate, leggings and boots,
	 * a golden axe, 100 health); and a piglin "carries it home at dawn": the gold it carried goes into the outpost's chest.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaOutpostHoard"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaOutpostHoard")
	public void theWarlordWearsGoldAndPlunderGoesIntoTheOutpostsChest(GameTestHelper helper) {
		BlockPos hall = village(helper, new BlockPos(1, 2, 1));
		ServerLevel level = helper.getLevel();
		Culture culture = piglins(helper);
		boolean lairs = Lairs.ENABLED;
		Lairs.ENABLED = true;
		Leftovers.after(helper, () -> {
			Lairs.forget(level);
			Lairs.ENABLED = lairs;
		});
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(new BlockPos(15, 1, 15)), PIGLINS);
			helper.assertTrue(lair != null, "no outpost");
			Mob warlord = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(warlord != null && warlord.getType() == EntityType.PIGLIN_BRUTE, "no Warlord: " + warlord);
			helper.assertTrue(warlord.getMaxHealth() == EntityType.PIGLIN_BRUTE.create(level).getMaxHealth() + 50, "the Warlord's health: " + warlord.getMaxHealth());
			helper.assertTrue(warlord.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET) && warlord.getItemBySlot(EquipmentSlot.CHEST).is(Items.GOLDEN_CHESTPLATE)
				&& warlord.getItemBySlot(EquipmentSlot.LEGS).is(Items.GOLDEN_LEGGINGS) && warlord.getItemBySlot(EquipmentSlot.FEET).is(Items.GOLDEN_BOOTS)
				&& warlord.getMainHandItem().is(Items.GOLDEN_AXE), "the Warlord isn't in gold: " + warlord.getItemBySlot(EquipmentSlot.HEAD) + " "
				+ warlord.getItemBySlot(EquipmentSlot.CHEST) + " " + warlord.getItemBySlot(EquipmentSlot.LEGS) + " " + warlord.getItemBySlot(EquipmentSlot.FEET)
				+ " " + warlord.getMainHandItem());
			// The outpost's chests: their gold ingots before.
			Map<BlockPos, Container> chests = new HashMap<>();
			BlockPos c = lair.pos();
			for (BlockPos p : BlockPos.betweenClosed(c.offset(-14, -4, -14), c.offset(14, 8, 14))) {
				if (level.getBlockEntity(p) instanceof Container box && level.getBlockState(p).is(Blocks.CHEST)) {
					box.getItem(0); // (rolls a loot table if it has one)
					chests.put(p.immutable(), box);
				}
			}
			helper.assertFalse(chests.isEmpty(), "the outpost has no chest");
			int before = chests.values().stream().mapToInt(b -> gold(b)).sum();
			Mob thief = raider(helper, culture, EntityType.PIGLIN, new BlockPos(6, 2, 6));
			Plunder.carry(level, thief, new ItemStack(Items.GOLD_INGOT, 20));
			Threats.tactics(culture).forEach(t -> t.leave(level, hall, culture, thief));
			int after = chests.values().stream().mapToInt(b -> gold(b)).sum();
			helper.assertTrue(Plunder.carried(thief).isEmpty() && ThreatData.get(level).plundered(hall) == 20, "the plunder wasn't counted");
			helper.assertTrue(after == before + 20, "the gold carried home isn't in the outpost's chest: " + before + " -> " + after);
			helper.succeed();
		});
	}

	private static int gold(Container box) {
		int n = 0;
		for (int i = 0; i < box.getContainerSize(); i++) {
			if (box.getItem(i).is(Items.GOLD_INGOT)) {
				n += box.getItem(i).getCount();
			}
		}
		return n;
	}

	/** "The siege report counts what was lost": one gold reads "1 gold was carried off …", and none adds no sentence. */
	//$ gametest_ticks_batch AREA '20' '"qaSiegeReportGold"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaSiegeReportGold")
	public void theSiegeReportCountsOneGoldAndSaysNothingOfNone(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL);
		ServerLevel level = helper.getLevel();
		String none = SiegeReport.text(level, new ThreatData.Siege(hall, 0, 0)).getString();
		helper.assertFalse(none.contains("carried off"), "the report tells of gold none lost: " + none);
		ThreatData.get(level).plunder(hall, 1);
		String one = SiegeReport.text(level, new ThreatData.Siege(hall, 0, 0)).getString();
		helper.assertTrue(one.equals(none + " 1 gold was carried off from the village's chests."), "the report: " + one + " (without: " + none + ")");
		ThreatData.get(level).plunder(hall, 63);
		String many = SiegeReport.text(level, new ThreatData.Siege(hall, 0, 0)).getString();
		helper.assertTrue(many.endsWith(" 64 gold was carried off from the village's chests."), "the report: " + many);
		helper.succeed();
	}

	/**
	 * Loot {@code chests/piglin_hoard}: "gold, blackstone, crying obsidian, now and then netherite scrap, the snout banner
	 * pattern". In 400 chests (fixed seeds) every one has gold, blackstone and crying obsidian turn up, netherite scrap
	 * and the snout pattern turn up now and then (in some chests, not most).
	 */
	//$ gametest_ticks_batch AREA '20' '"qaHoardLoot"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaHoardLoot")
	public void thePiglinHoardHoldsWhatTheSpecSays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var table = level.getServer().reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(
			net.minecraft.core.registries.Registries.LOOT_TABLE, AliveWorkplace.id("chests/piglin_hoard")));
		helper.assertTrue(table != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "no chests/piglin_hoard");
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
			.create(LootContextParamSets.CHEST);
		int chests = 400;
		Map<Item, Integer> in = new HashMap<>();
		int noGold = 0;
		for (int i = 0; i < chests; i++) {
			Set<Item> here = new java.util.HashSet<>();
			for (ItemStack stack : table.getRandomItems(params, 3210L + i)) {
				here.add(stack.getItem());
			}
			if (!here.contains(Items.GOLD_INGOT) && !here.contains(Items.GOLD_BLOCK) && !here.contains(Items.GOLD_NUGGET)) {
				noGold++;
			}
			here.forEach(item -> in.merge(item, 1, Integer::sum));
		}
		helper.assertTrue(noGold == 0, noGold + " hoards without gold");
		helper.assertTrue(in.getOrDefault(Items.BLACKSTONE, 0) > chests / 4, "blackstone in " + in.get(Items.BLACKSTONE) + " of " + chests);
		helper.assertTrue(in.getOrDefault(Items.CRYING_OBSIDIAN, 0) > chests / 4, "crying obsidian in " + in.get(Items.CRYING_OBSIDIAN) + " of " + chests);
		int scrap = in.getOrDefault(Items.NETHERITE_SCRAP, 0);
		int snout = in.getOrDefault(Items.PIGLIN_BANNER_PATTERN, 0);
		helper.assertTrue(scrap >= chests / 20 && scrap <= chests / 2, "netherite scrap in " + scrap + " of " + chests + ": not 'now and then'");
		helper.assertTrue(snout >= chests / 20 && snout <= chests / 2, "the snout pattern in " + snout + " of " + chests);
		helper.succeed();
	}

	/**
	 * The roster: "piglins with golden swords 35%, piglins with crossbows 25%, piglin brutes with golden axes 25%, hoglin
	 * rams 15%". 20,000 picks with a fixed RandomSource land within 1.5 points of each share, and each raider made from
	 * the line picked is that kind with that weapon.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaRosterShares"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRosterShares")
	public void theRosterComesInItsShares(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Culture culture = piglins(helper);
		RandomSource random = RandomSource.create(3210L);
		int n = 20000;
		int[] got = new int[culture.roster().size()];
		for (int i = 0; i < n; i++) {
			got[culture.roster().indexOf(culture.pick(random))]++;
		}
		int[] want = {35, 25, 25, 15};
		for (int i = 0; i < want.length; i++) {
			double share = 100.0 * got[i] / n;
			helper.assertTrue(Math.abs(share - want[i]) < 1.5, "roster line " + i + " came " + share + "% of the time, not " + want[i] + "%");
		}
		Item[] weapons = {Items.GOLDEN_SWORD, Items.CROSSBOW, Items.GOLDEN_AXE, Items.AIR};
		EntityType<?>[] kinds = {EntityType.PIGLIN, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.HOGLIN};
		for (int i = 0; i < 4; i++) {
			Culture.Member member = culture.roster().get(i);
			Mob mob = Threats.create(level, member);
			helper.assertTrue(mob != null && mob.getType() == kinds[i], "line " + i + " made " + mob);
			BlockPos p = helper.absolutePos(new BlockPos(4 + 5 * i, 2, 4));
			mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
			Threats.outfit(level, mob, culture, member);
			if (weapons[i] != Items.AIR) {
				helper.assertTrue(mob.getMainHandItem().is(weapons[i]), "line " + i + " holds " + mob.getMainHandItem());
			} else {
				helper.assertTrue(Threats.role(mob) == Culture.Role.RAM || member.role() == Culture.Role.RAM, "the hoglin isn't a ram");
			}
			mob.discard();
		}
		helper.succeed();
	}

	/**
	 * "Town and up", with a nether link, from both sides: a City with a lit portal is raided, a Hamlet with one is not,
	 * and a City without one is not.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaPiglinWhere"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaPiglinWhere")
	public void piglinsComeToACityWithAPortalAndNotAHamletOrWithoutIt(GameTestHelper helper) {
		Culture culture = piglins(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper, new BlockPos(4, 2, 4));
		helper.setBlock(new BlockPos(4, 2, 4), ModBlocks.VILLAGE_HALL);
		java.util.function.Consumer<VillageRanks.Rank> rank = r -> ((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(r);
		helper.runAfterDelay(2, () -> {
			rank.accept(VillageRanks.Rank.CITY);
			helper.assertFalse(Threats.fits(level, hall, culture, () -> 40), "it came to a City without a portal");
			for (int pass = 0; pass < 2; pass++) {
				for (int x = 10; x <= 13; x++) {
					for (int y = 2; y <= 6; y++) {
						boolean frame = x == 10 || x == 13 || y == 2 || y == 6;
						if (frame == (pass == 0)) {
							helper.setBlock(new BlockPos(x, y, 10), frame ? Blocks.OBSIDIAN.defaultBlockState()
								: Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X));
						}
					}
				}
			}
		});
		helper.runAfterDelay(4, () -> {
			rank.accept(VillageRanks.Rank.CITY);
			helper.assertTrue(Threats.fits(level, hall, culture, () -> 40), "it doesn't come to a City with a lit portal: rank " + VillageRanks.of(level, hall));
			rank.accept(VillageRanks.Rank.HAMLET);
			helper.assertFalse(Threats.fits(level, hall, culture, () -> 40), "it came to a Hamlet");
			helper.succeed();
		});
	}
}
