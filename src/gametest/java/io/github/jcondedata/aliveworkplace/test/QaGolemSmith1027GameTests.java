package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.farm.FarmhandGolems;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA for 29.15, the Golem Smith (qa/legends-1027), written from the spec, on sentences the earlier golem tests leave
 * open: a Golem Smith really mends golems twice as fast as a Tinkerer at the bench (not just the factor); in a
 * protected village a stranger's sneak-click chooses nothing while the owner's does; every hall line on the guards
 * button and the chronicle's "built a" line read as finished American English with their numbers filled in; a
 * farmhand leaves ripe crops outside the village's fields alone; a farmhand tends a farmer's field 48 blocks off and
 * not one 49 blocks off, on both sides.
 */
public class QaGolemSmith1027GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);

	private static final List<String> BRITISH = List.of("colour", "defence", "armour", "grey", "cancelled", "harbour", "favour", "neighbour", "centre", "metre");

	private static void english(GameTestHelper helper, String line) {
		helper.assertTrue(!line.contains("%") && !line.contains("{") && !line.contains("aliveworkplace.") && !line.isBlank(), "an unfilled line: " + line);
		for (String word : BRITISH) {
			helper.assertTrue(!line.toLowerCase(java.util.Locale.ROOT).contains(word), "British spelling '" + word + "' in: " + line);
		}
	}

	/**
	 * A Golem Smith and a plain Tinkerer at their own Tinker's Benches, each with iron ingots, and two badly hurt golems:
	 * once beside their golem, the smith patches it up every half as many ticks as the Tinkerer does ("mends golems
	 * twice as fast as a Tinkerer").
	 */
	//$ gametest_ticks_batch AREA '1600' '"qa1027SmithMends"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa1027SmithMends")
	public void aSmithMendsAGolemTwiceAsFastAsATinkerer(GameTestHelper helper) {
		QaGolemSmithEdgesGameTests.setUp(helper);
		Legend legend = QaGolemSmithEdgesGameTests.staged(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos smithBench = new BlockPos(6, 2, 14);
		BlockPos tinkerBench = new BlockPos(22, 2, 14);
		helper.setBlock(smithBench, ModBlocks.TINKERS_BENCH);
		helper.setBlock(tinkerBench, ModBlocks.TINKERS_BENCH);
		helper.setBlock(smithBench.south(2), Blocks.CHEST);
		helper.setBlock(tinkerBench.south(2), Blocks.CHEST);
		Container smithChest = helper.getBlockEntity(smithBench.south(2));
		Container tinkerChest = helper.getBlockEntity(tinkerBench.south(2));
		smithChest.setItem(0, new ItemStack(Items.IRON_INGOT, 8));
		tinkerChest.setItem(0, new ItemStack(Items.IRON_INGOT, 8));
		Villager smith = helper.spawn(EntityType.VILLAGER, smithBench.east());
		Legends.make(level, smith, legend, "test");
		Jobs.employ(level, smith, helper.absolutePos(smithBench), ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER);
		Villager tinkerer = helper.spawn(EntityType.VILLAGER, tinkerBench.east());
		Jobs.employ(level, tinkerer, helper.absolutePos(tinkerBench), ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER);
		helper.assertTrue(GolemSmith.mendFactor(smith) == 2f && GolemSmith.mendFactor(tinkerer) == 1f, "not a working smith beside a plain tinkerer");
		IronGolem a = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(13, 2, 14));
		IronGolem b = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(15, 2, 14));
		for (IronGolem g : List.of(a, b)) {
			g.setNoAi(true);
			g.setHealth(10f);
		}
		List<Long> smithAt = new ArrayList<>();
		List<Long> tinkerAt = new ArrayList<>();
		int[] last = {0, 0};
		helper.onEachTick(() -> {
			int s = ModAttachments.GOLEM_REPAIRS.getOrElse(smith, 0);
			int t = ModAttachments.GOLEM_REPAIRS.getOrElse(tinkerer, 0);
			if (s != last[0]) {
				smithAt.add(helper.getTick());
				last[0] = s;
			}
			if (t != last[1]) {
				tinkerAt.add(helper.getTick());
				last[1] = t;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(smithAt.size() >= 4 && tinkerAt.size() >= 4, "patches so far: smith " + smithAt + ", tinkerer " + tinkerAt);
			List<Long> smithGaps = gaps(smithAt);
			List<Long> tinkerGaps = gaps(tinkerAt);
			long smithMost = smithGaps.stream().mapToLong(Long::longValue).max().orElseThrow();
			long tinkerLeast = tinkerGaps.stream().mapToLong(Long::longValue).min().orElseThrow();
			helper.assertTrue(smithMost * 2 <= tinkerLeast + 1, "the smith's patches come every " + smithGaps + " ticks, the tinkerer's every " + tinkerGaps
				+ " (twice as fast wanted)");
			helper.assertTrue(a.getHealth() >= a.getMaxHealth() && b.getHealth() >= b.getMaxHealth(), "golems at " + a.getHealth() + " and " + b.getHealth());
		});
	}

	private static List<Long> gaps(List<Long> at) {
		List<Long> out = new ArrayList<>();
		for (int i = 1; i < at.size(); i++) {
			out.add(at.get(i) - at.get(i - 1));
		}
		return out;
	}

	/**
	 * In a protected village (the hall's owner turned it on), a stranger's empty-handed sneak-click on the smith leaves
	 * the next golem as it was; the owner's sneak-click chooses the next one.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1027SmithProtected"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1027SmithProtected")
	public void aStrangerCantChooseTheGolemInAProtectedVillage(GameTestHelper helper) {
		QaGolemSmithEdgesGameTests.setUp(helper);
		Legend legend = QaGolemSmithEdgesGameTests.staged(helper);
		ServerLevel level = helper.getLevel();
		Villager smith = QaGolemSmithEdgesGameTests.smith(helper, legend);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		hall.setOwner(owner.getUUID(), "Owner");
		hall.setProtected(true);
		Leftovers.after(helper, () -> {
			hall.setProtected(false);
			level.getServer().getPlayerList().remove(owner);
			level.getServer().getPlayerList().remove(stranger);
		});
		for (ServerPlayer p : List.of(owner, stranger)) {
			p.setShiftKeyDown(true);
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}
		UseEntityCallback.EVENT.invoker().interact(stranger, level, InteractionHand.MAIN_HAND, smith, null);
		helper.assertTrue(GolemSmith.forge(smith).role() == GolemSmith.Role.HAULER, "a stranger chose the next golem in a protected village: " + GolemSmith.forge(smith));
		UseEntityCallback.EVENT.invoker().interact(owner, level, InteractionHand.MAIN_HAND, smith, null);
		helper.assertTrue(GolemSmith.forge(smith).role() == GolemSmith.Role.FARMHAND, "the owner's sneak-click chose nothing: " + GolemSmith.forge(smith));
		helper.succeed();
	}

	/**
	 * Every line the hall shows for a golem, and the chronicle's line when one is built, read as whole American English
	 * sentences with their numbers filled in: a hauler carrying 1 item and one carrying 130, a farmhand carrying 1 item
	 * of harvest and one tending the fields, a sentry holding the wall at its post's x y z, and one fighting.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1027SmithLines"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1027SmithLines")
	public void everyGolemLineOnTheHallReadsInEnglish(GameTestHelper helper) {
		QaGolemSmithEdgesGameTests.setUp(helper);
		Legend legend = QaGolemSmithEdgesGameTests.staged(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hallPos = helper.absolutePos(HALL);
		Villager smith = QaGolemSmithEdgesGameTests.smith(helper, legend);
		QaGolemSmithEdgesGameTests.villagers(helper, 4, 4);
		Container chest = helper.getBlockEntity(new BlockPos(11, 2, 10));
		chest.setItem(0, new ItemStack(Items.IRON_BLOCK, 4));
		chest.setItem(1, new ItemStack(Items.CARVED_PUMPKIN, 1));
		chest.setItem(2, new ItemStack(Items.CHEST, 1));
		IronGolem forged = GolemSmith.forgeOne(level, hallPos, smith);
		helper.assertTrue(forged != null, "no golem forged");
		String built = smith.getName().getString() + " built a " + Component.translatable("entity.aliveworkplace.golem.hauler").getString();
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		List<String> chronicle = hall.chronicle().stream().map(e -> e.text().getString()).toList();
		helper.assertTrue(chronicle.contains(built), "the chronicle's line: " + chronicle + " (wanted '" + built + "')");
		chronicle.forEach(line -> english(helper, line));
		forged.discard();

		IronGolem one = golem(helper, 5, GolemSmith.Role.HAULER);
		ModAttachments.HAULER_LOAD.set(one, Map.of(Items.COBBLESTONE, 1));
		IronGolem many = golem(helper, 8, GolemSmith.Role.HAULER);
		ModAttachments.HAULER_LOAD.set(many, Map.of(Items.COBBLESTONE, 66, Items.OAK_LOG, 64));
		IronGolem picking = golem(helper, 11, GolemSmith.Role.FARMHAND);
		ModAttachments.HAULER_LOAD.set(picking, Map.of(Items.WHEAT, 1));
		golem(helper, 14, GolemSmith.Role.FARMHAND);
		IronGolem holding = golem(helper, 17, GolemSmith.Role.SENTRY);
		BlockPos post = helper.absolutePos(new BlockPos(17, 2, 26));
		ModAttachments.GOLEM_POST.set(holding, post);
		IronGolem fighting = golem(helper, 20, GolemSmith.Role.SENTRY);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(26, 2, 26));
		zombie.setNoAi(true);
		fighting.setTarget(zombie);
		String hauler = Component.translatable("entity.aliveworkplace.golem.hauler").getString();
		String farmhand = Component.translatable("entity.aliveworkplace.golem.farmhand").getString();
		String sentry = Component.translatable("entity.aliveworkplace.golem.sentry").getString();
		Set<String> wanted = Set.of(
			hauler + ": carrying 1 item to the Storehouse",
			hauler + ": carrying 130 items to the Storehouse",
			farmhand + ": carrying 1 item of harvest",
			farmhand + ": tending the fields",
			sentry + ": holding the wall at " + post.getX() + " " + post.getY() + " " + post.getZ(),
			sentry + ": fighting");
		List<String> lines = GolemSmith.hallLines(level, hallPos).stream().map(Component::getString).toList();
		lines.forEach(line -> english(helper, line));
		helper.assertTrue(new HashSet<>(lines).equals(wanted) && lines.size() == 6, "the hall's lines: " + lines);
		helper.succeed();
	}

	private static IronGolem golem(GameTestHelper helper, int x, GolemSmith.Role role) {
		IronGolem g = GolemSmith.build(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, 24)), role);
		helper.assertTrue(g != null, "no " + role);
		g.setNoAi(true);
		return g;
	}

	/**
	 * A farmhand by a farmer's 3x3 field of ripe wheat, with a row of ripe wheat on farmland just outside the field and
	 * ripe carrots further off: it harvests and replants the field into the chest, and for 200 ticks more leaves every
	 * crop outside the field as it was ("harvests ... ripe crops on the village's fields").
	 */
	//$ gametest_ticks_batch AREA '2000' '"qa1027FarmhandOutside"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qa1027FarmhandOutside")
	public void aFarmhandLeavesRipeCropsOutsideTheFieldsAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int ticking = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
		level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(ticking, level.getServer()));
		List<BlockPos> outside = new ArrayList<>();
		for (int x = 4; x <= 7; x++) {
			for (int z = 4; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.FARMLAND);
				helper.setBlock(new BlockPos(x, 2, z), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
				if (x == 7) {
					outside.add(new BlockPos(x, 2, z));
				}
			}
		}
		for (int z = 10; z <= 12; z++) {
			helper.setBlock(new BlockPos(5, 1, z), Blocks.FARMLAND);
			helper.setBlock(new BlockPos(5, 2, z), Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7));
			outside.add(new BlockPos(5, 2, z));
		}
		BlockPos composter = new BlockPos(16, 2, 5);
		BlockPos chestPos = new BlockPos(17, 2, 5);
		helper.setBlock(composter, Blocks.COMPOSTER);
		helper.setBlock(chestPos, Blocks.CHEST);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(16, 2, 7));
		farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
		farmer.refreshBrain(level);
		farmer.setNoAi(true);
		farmer.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(composter)));
		ModAttachments.FARM_FIELD.set(farmer, new FieldJob(BoundingBox.fromCorners(helper.absolutePos(new BlockPos(4, 1, 4)), helper.absolutePos(new BlockPos(6, 1, 6)))));
		IronGolem golem = GolemSmith.build(level, helper.absolutePos(new BlockPos(12, 1, 8)), GolemSmith.Role.FARMHAND);
		helper.assertTrue(golem != null, "a farmhand");
		Leftovers.after(helper, () -> {
			golem.discard();
			farmer.discard();
		});
		helper.onEachTick(() -> {
			for (BlockPos p : outside) {
				var state = helper.getBlockState(p);
				helper.assertTrue(state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state), "a crop outside the field was touched at " + p + ": " + state);
				helper.assertBlockPresent(Blocks.FARMLAND, p.below());
			}
		});
		long[] doneAt = {-1};
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(chestPos);
			helper.assertTrue(chest.countItem(Items.WHEAT) == 9, "the chest has " + chest.countItem(Items.WHEAT) + " wheat");
			helper.assertTrue(chest.countItem(Items.CARROT) == 0, "carrots from outside the field: " + chest.countItem(Items.CARROT));
			for (int x = 4; x <= 6; x++) {
				for (int z = 4; z <= 6; z++) {
					helper.assertBlockPresent(Blocks.WHEAT, new BlockPos(x, 2, z));
				}
			}
			if (doneAt[0] < 0) {
				doneAt[0] = helper.getTick();
			}
			helper.assertTrue(helper.getTick() >= doneAt[0] + 200, "watching the crops outside a while longer");
		});
	}

	/**
	 * "A farmhand tends the fields of farmers within 48 blocks": a farmer with a field 48 blocks east (and west) of the
	 * golem counts, one 49 blocks off doesn't.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1027FarmhandRange"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1027FarmhandRange")
	public void aFarmersFieldCountsAt48BlocksAndNotAt49(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos composter = new BlockPos(14, 2, 20);
		helper.setBlock(composter, Blocks.COMPOSTER);
		IronGolem golem = GolemSmith.build(level, helper.absolutePos(new BlockPos(15, 1, 15)), GolemSmith.Role.FARMHAND);
		helper.assertTrue(golem != null, "a farmhand");
		golem.setNoAi(true);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(15, 2, 22));
		farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
		farmer.refreshBrain(level);
		farmer.setNoAi(true);
		farmer.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(composter)));
		ModAttachments.FARM_FIELD.set(farmer, new FieldJob(BoundingBox.fromCorners(helper.absolutePos(new BlockPos(4, 1, 4)), helper.absolutePos(new BlockPos(6, 1, 6)))));
		Leftovers.after(helper, () -> {
			golem.discard();
			farmer.discard();
		});
		BlockPos g = golem.blockPosition();
		List<String> wrong = new ArrayList<>();
		for (int dx : new int[] {48, 49, -48, -49}) {
			farmer.teleportTo(g.getX() + dx + 0.5, g.getY(), g.getZ() + 0.5);
			int found = FarmhandGolems.fields(level, golem).size();
			boolean want = Math.abs(dx) <= 48;
			if ((found == 1) != want) {
				wrong.add(dx + " blocks: " + found + " fields");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the farmer's field at the reach's edge: " + wrong);
		helper.succeed();
	}
}
