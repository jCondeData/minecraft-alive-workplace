package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Piglins;
import io.github.jcondedata.aliveworkplace.threat.Plunder;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane D, 2026-11-02), verifying B829 (f3f3cea52: a plunderer turned zombified by the Warlord's fall keeps its
 * plunder) from 32.10's text, beyond the bug tests (killed after turning, and saved and loaded):
 * <ul>
 * <li>at dawn the zombified plunderer carries the gold home: the hall counts all 12 lost, and the ingot it showed on
 *     its head comes off with the plunder;</li>
 * <li>a plunderer whose head wasn't bare (its own golden helmet, so the plunder isn't shown) keeps its helmet once
 *     turned, carries the 12, and killed drops the 12 ingots and no extra shown ingot;</li>
 * <li>(32.10, "the siege report says it the morning after, or the chronicle when there was no siege") a raid without a
 *     siege that loses a single nugget at dawn: the chronicle says the 1 gold, through the raid's own dawn round. Kills
 *     the mutant {@code lost > 1} in Plunder's end, which every other plunder test lets live;</li>
 * <li>(32.10, "within 4 blocks of a chest (any storage block) in the village") a chest 3 blocks from a raider but just
 *     outside the village keeps its gold, while one just inside is taken. Kills the mutant that lets a chest in reach
 *     but outside the village count;</li>
 * <li>(32.10, "into a chest of its lair") the gold carried home goes into the outpost's own chest, the one nearest its
 *     middle, not an empty chest set out at the edge of its ground. Kills the mutant that picks the farthest.</li>
 * </ul>
 */
public class QaPlunderVerify1102dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PIGLINS = AliveWorkplace.id("piglin_incursion");
	private static final BlockPos HALL = new BlockPos(3, 2, 3);

	private static Culture piglins(GameTestHelper helper) {
		Optional<Culture> found = Threats.get(PIGLINS);
		helper.assertTrue(found.isPresent(), "setup: expected piglin_incursion.json loaded");
		return found.get();
	}

	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> true).forEach(ItemEntity::discard);
			VillageRaids.forget(hall);
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPlunder(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
		});
		ThreatData.get(level).forgetPlunder(hall);
		return hall;
	}

	/** A piglin of the band, as a raid makes it, at {@code at}, its head bare. */
	private static Mob piglin(GameTestHelper helper, Culture culture, BlockPos at) {
		ServerLevel level = helper.getLevel();
		Culture.Member member = culture.roster().stream().filter(m -> m.entity().equals(EntityType.getKey(EntityType.PIGLIN))).findFirst().orElseThrow();
		Mob piglin = Threats.create(level, member);
		helper.assertTrue(piglin != null, "setup: no piglin made");
		BlockPos p = helper.absolutePos(at);
		piglin.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		piglin.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
		Threats.outfit(level, piglin, culture, member);
		piglin.addTag(Lairs.TAG);
		piglin.addTag(VillageRaids.TAG);
		piglin.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		level.addFreshEntityWithPassengers(piglin);
		piglin.setNoAi(true);
		return piglin;
	}

	/**
	 * Dawn: the zombified plunderer leaves with the band and carries its 12 ingots home. The hall counts the 12 lost,
	 * and the raider carries nothing and shows nothing on its head after.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaPlunderVerifyDawn1102d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPlunderVerifyDawn1102d")
	public void aZombifiedPlundererCarriesItsGoldHomeAtDawnAndTheHallCountsIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		Culture culture = piglins(helper);
		Mob piglin = piglin(helper, culture, new BlockPos(12, 2, 12));
		Plunder.carry(level, piglin, new ItemStack(Items.GOLD_INGOT, 12));
		Mob turned = Piglins.zombify(level, piglin);
		helper.assertTrue(turned != null && turned.getType() == EntityType.ZOMBIFIED_PIGLIN, "setup: expected the piglin turned zombified, got " + turned);
		turned.setNoAi(true);
		Threats.tactics(culture).forEach(t -> t.leave(level, hall, culture, turned));
		int lost = ThreatData.get(level).plundered(hall);
		helper.assertTrue(lost == 12, "the zombified plunderer went home at dawn and the hall counts " + lost + " gold lost, expected the 12 it carried");
		helper.assertTrue(Plunder.carried(turned).isEmpty(), "after going home it still carries " + Plunder.carried(turned));
		helper.assertTrue(turned.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !turned.getTags().contains(Plunder.SHOWN_TAG),
			"after going home it still shows " + turned.getItemBySlot(EquipmentSlot.HEAD) + " on its head");
		helper.succeed();
	}

	/**
	 * A plunderer in its own golden helmet (so its plunder isn't shown) is zombified: it keeps the helmet and the 12
	 * ingots; killed, exactly the 12 ingots drop where it fell.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaPlunderVerifyHelmet1102d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPlunderVerifyHelmet1102d")
	public void aHelmetedPlundererZombifiedKeepsItsHelmetAndDropsJustItsGold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		Culture culture = piglins(helper);
		Mob piglin = piglin(helper, culture, new BlockPos(12, 2, 12));
		piglin.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
		piglin.setDropChance(EquipmentSlot.HEAD, 0f);
		Plunder.carry(level, piglin, new ItemStack(Items.GOLD_INGOT, 12));
		helper.assertTrue(!piglin.getTags().contains(Plunder.SHOWN_TAG), "setup: a helmeted plunderer shouldn't show its plunder");
		Mob turned = Piglins.zombify(level, piglin);
		helper.assertTrue(turned != null && turned.getType() == EntityType.ZOMBIFIED_PIGLIN, "setup: expected the piglin turned zombified, got " + turned);
		turned.setNoAi(true);
		helper.assertTrue(turned.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET), "the zombified plunderer wears "
			+ turned.getItemBySlot(EquipmentSlot.HEAD) + " instead of its own golden helmet");
		helper.assertTrue(Plunder.carried(turned).is(Items.GOLD_INGOT) && Plunder.carried(turned).getCount() == 12,
			"the zombified plunderer carries " + Plunder.carried(turned) + " instead of the 12 gold ingots");
		BlockPos where = turned.blockPosition();
		turned.kill();
		helper.runAfterDelay(2, () -> {
			int ingots = level.getEntitiesOfClass(ItemEntity.class, new AABB(where).inflate(3), e -> e.getItem().is(Items.GOLD_INGOT))
				.stream().mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(ingots == 12, "the helmeted zombified plunderer, killed, dropped " + ingots + " gold ingots, expected the 12 it carried");
			helper.succeed();
		});
	}

	/**
	 * A piglin raid begun the night before, no walls (no siege): at dawn its one raider leaves carrying a single gold
	 * nugget. The raid's own dawn round ends it, and the chronicle says the village lost 1 gold; the count is spent.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaPlunderVerifyOneNugget1102d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPlunderVerifyOneNugget1102d")
	public void aSingleNuggetCarriedOffAtDawnIsInTheChronicle(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity != null, "setup: no Village Hall");
		long time = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(time));
		Culture culture = piglins(helper);
		Mob piglin = piglin(helper, culture, new BlockPos(12, 2, 12));
		ThreatData.get(level).begin(new ThreatData.Under(hall, PIGLINS, 1, level.getGameTime() - 6000));
		helper.assertTrue(ThreatData.get(level).siege(hall).isEmpty(), "setup: expected no siege at a village without walls");
		Plunder.carry(level, piglin, new ItemStack(Items.GOLD_NUGGET, 1));
		level.setDayTime((level.getDayTime() / 24000) * 24000 + 23000 + 24000); // dawn: piglins' hours (night) are over
		VillageRaids.tick(level, hall, 0, 0, Long.MIN_VALUE / 2, d -> { });
		helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty(), "setup: expected the raid over at dawn");
		List<String> lines = entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.RAID).map(e -> e.text().getString()).toList();
		helper.assertTrue(lines.contains("The piglins carried off 1 gold from the village's chests"),
			"the chronicle after a raid that carried off one nugget: " + lines);
		helper.assertTrue(ThreatData.get(level).plundered(hall) == 0, "the count wasn't spent once told: " + ThreatData.get(level).plundered(hall));
		helper.succeed();
	}

	/**
	 * A village of radius 8: one piglin stands just inside its east edge with a chest of gold 3 blocks further east,
	 * outside the village; another stands inside with a chest a block off, just inside the south edge. Two plunder
	 * rounds later the outside chest still holds its gold and the inside one is emptied.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaPlunderVerifyEdge1102d"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaPlunderVerifyEdge1102d")
	public void aChestInReachButOutsideTheVillageKeepsItsGold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		VillageHalls.RADIUS = 8; // (village() puts the radius back) the village's box: x and z from -5 to 11 here
		Culture culture = piglins(helper);
		helper.setBlock(new BlockPos(13, 2, 3), Blocks.CHEST);
		helper.setBlock(new BlockPos(3, 2, 11), Blocks.CHEST);
		Container outside = helper.getBlockEntity(new BlockPos(13, 2, 3));
		Container inside = helper.getBlockEntity(new BlockPos(3, 2, 11));
		outside.setItem(0, new ItemStack(Items.GOLD_INGOT, 7));
		inside.setItem(0, new ItemStack(Items.GOLD_INGOT, 7));
		Mob east = piglin(helper, culture, new BlockPos(10, 2, 3));
		Mob south = piglin(helper, culture, new BlockPos(3, 2, 10));
		helper.assertTrue(VillageHalls.area(hall).contains(east.position()) && VillageHalls.area(hall).contains(south.position()),
			"setup: expected both piglins inside the village");
		helper.assertTrue(!VillageHalls.area(hall).contains(helper.absolutePos(new BlockPos(13, 2, 3)).getCenter())
			&& VillageHalls.area(hall).contains(helper.absolutePos(new BlockPos(3, 2, 11)).getCenter()), "setup: expected one chest outside the village, one inside");
		VillageRaids.track(level, hall, culture, List.of(east, south));
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(Plunder.carried(south).getCount() == 7 && inside.getItem(0).isEmpty(),
				"setup: the chest just inside the village wasn't plundered: the piglin carries " + Plunder.carried(south));
			helper.assertTrue(Plunder.carried(east).isEmpty() && outside.getItem(0).getCount() == 7,
				"a chest 3 blocks off but outside the village was plundered: the piglin carries " + Plunder.carried(east)
				+ ", the chest holds " + outside.getItem(0));
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

	/**
	 * A piglin outpost; an empty chest is set at the far corner of its ground (14 out, 8 up). A piglin carries 20 gold
	 * ingots home at dawn: they go into the outpost's own chest, nearest its middle, and the far chest stays empty.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaPlunderVerifyHoard1102d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPlunderVerifyHoard1102d")
	public void theGoldCarriedHomeGoesIntoTheChestNearestTheOutpostsMiddle(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		Culture culture = piglins(helper);
		boolean lairs = Lairs.ENABLED;
		Lairs.ENABLED = true;
		Leftovers.after(helper, () -> {
			Lairs.forget(level);
			Lairs.ENABLED = lairs;
		});
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(new BlockPos(15, 1, 15)), PIGLINS);
			helper.assertTrue(lair != null, "setup: no outpost");
			BlockPos c = lair.pos();
			BlockPos far = c.offset(14, 8, 14);
			java.util.Map<BlockPos, Container> own = new java.util.HashMap<>();
			for (BlockPos p : BlockPos.betweenClosed(c.offset(-14, -4, -14), c.offset(14, 8, 14))) {
				if (level.getBlockEntity(p) instanceof Container box && level.getBlockState(p).is(Blocks.CHEST)) {
					box.getItem(0); // (rolls a loot table if it has one)
					own.put(p.immutable(), box);
				}
			}
			helper.assertTrue(!own.isEmpty() && !own.containsKey(far), "setup: expected the outpost's own chest, and the far corner free");
			level.setBlock(far, Blocks.CHEST.defaultBlockState(), 3);
			Container farChest = (Container) level.getBlockEntity(far);
			int before = own.values().stream().mapToInt(QaPlunderVerify1102dGameTests::gold).sum();
			Mob thief = piglin(helper, culture, new BlockPos(6, 2, 6));
			Plunder.carry(level, thief, new ItemStack(Items.GOLD_INGOT, 20));
			Threats.tactics(culture).forEach(t -> t.leave(level, hall, culture, thief));
			int after = own.values().stream().mapToInt(QaPlunderVerify1102dGameTests::gold).sum();
			helper.assertTrue(gold(farChest) == 0, "the gold carried home went into the empty chest at the far edge of the outpost's ground: "
				+ gold(farChest) + " ingots there");
			helper.assertTrue(after == before + 20, "the outpost's own chest didn't get the 20 ingots: " + before + " -> " + after);
			level.setBlock(far, Blocks.AIR.defaultBlockState(), 3);
			helper.succeed();
		});
	}
}
