package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1024-0938) of handling a blueprint, from the player guide (docs/guide/builder.md, "Building something":
 * "Hold it and right-click the ground where the front should go ... Sneak-right-click the ground to turn it, and
 * sneak-right-click the air to pick it back up"; "Right-click the air with a placed blueprint to switch off the leveling
 * for that build") and the lines en_us.json gives each click, all through the player's own clicks, and the switch
 * carried through placing, turning and the hand-over to the build the builder starts.
 */
public class QaBlueprintHandlingGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos GROUND = new BlockPos(9, 1, 9);

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	private static BlueprintData data(ServerPlayer p) {
		return BlueprintItem.data(p.getMainHandItem()).orElseThrow();
	}

	/**
	 * The guide's clicks in a row: a right-click on the ground places the hut facing the player ("Test Hut placed, facing
	 * north. ..."); four sneak-right-clicks on the ground turn it a quarter each (east, south, west, north) round the same
	 * spot, and the fourth brings back the first placement exactly; a sneak-right-click on the air picks it back up
	 * ("Blueprint picked back up"); right-clicks on the air switch the levelling off, on and off again, each with its
	 * line; placed again, the switch stays off, and the builder handed it starts a build that leaves the ground as it is.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaBlueprintHandling"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaBlueprintHandling")
	public void placeTurnPickUpAndTheLevellingSwitchReachTheBuild(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean protection = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = protection);
		ServerLevel level = helper.getLevel();
		String hut = Blueprints.displayName(TEST_HUT).getString();
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qabH"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(TEST_HUT, null));
		p.setYRot(0); // looking south: the front should face north, toward the player
		p.setYHeadRot(0);

		QaStageFourGameTests.press(helper, p, GROUND, Direction.UP);
		Optional<BlueprintData.Placement> first = data(p).placement();
		helper.assertTrue(first.isPresent(), "a right-click on the ground placed nothing: saw " + texts(c.seen()));
		helper.assertTrue(first.get().rotation() == Rotation.NONE, "placed looking south, turned " + first.get().rotation());
		helper.assertTrue(texts(c.seen()).contains(hut + " placed, facing north. Sneak-right-click to turn it, then hand it to a Builder."),
			"placing said " + texts(c.seen()));

		List<String> fronts = List.of("east", "south", "west", "north");
		BlueprintData.Placement before = first.get();
		p.setShiftKeyDown(true);
		for (String front : fronts) {
			c.seen().clear();
			QaStageFourGameTests.press(helper, p, GROUND, Direction.UP);
			BlueprintData.Placement now = data(p).placement().orElseThrow();
			helper.assertTrue(now.rotation() == before.rotation().getRotated(Rotation.CLOCKWISE_90), "a sneak-click turned " + before.rotation() + " to " + now.rotation());
			helper.assertTrue(texts(c.seen()).contains(hut + " placed, facing " + front + ". Sneak-right-click to turn it, then hand it to a Builder."),
				"turning to " + front + " said " + texts(c.seen()));
			before = now;
		}
		helper.assertTrue(before.equals(first.get()), "four turns didn't come back where it was: " + first.get() + " then " + before);

		c.seen().clear();
		QaStageFourGameTests.pressAir(helper, p);
		helper.assertTrue(data(p).placement().isEmpty(), "a sneak-click on the air didn't pick it up: " + data(p).placement());
		helper.assertTrue(texts(c.seen()).contains("Blueprint picked back up"), "picking up said " + texts(c.seen()));
		p.setShiftKeyDown(false);

		String off = "The builder will leave the ground around this build as it is (right-click the air again to switch back).";
		String on = "The builder will level the ground around this build.";
		helper.assertTrue(data(p).levelGround(), "a new blueprint doesn't level the ground");
		for (boolean expected : List.of(false, true, false)) {
			c.seen().clear();
			QaStageFourGameTests.pressAir(helper, p);
			helper.assertTrue(data(p).levelGround() == expected, "a right-click on the air left levelling " + data(p).levelGround());
			helper.assertTrue(texts(c.seen()).contains(expected ? on : off), "switching levelling to " + expected + " said " + texts(c.seen()));
		}

		QaStageFourGameTests.press(helper, p, GROUND, Direction.UP);
		helper.assertTrue(data(p).placement().isPresent() && !data(p).levelGround(), "placed again: placement " + data(p).placement()
			+ ", levelling " + data(p).levelGround());
		p.setShiftKeyDown(true);
		QaStageFourGameTests.press(helper, p, GROUND, Direction.UP);
		p.setShiftKeyDown(false);
		helper.assertTrue(!data(p).levelGround(), "turning it switched levelling back on");

		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (builder.getUUID().equals(site.builder())) {
					sites.remove(site.id());
				}
			}
		});
		BlueprintData.Placement handed = data(p).placement().orElseThrow();
		c.seen().clear();
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		BuildSite site = Builders.activeSite(level, builder);
		helper.assertTrue(site != null, "the builder took no build: saw " + texts(c.seen()));
		helper.assertTrue(site.placement().equals(handed), "the build stands at " + site.placement() + ", the blueprint was placed at " + handed);
		helper.assertFalse(site.levelGround(), "the blueprint was switched to leave the ground, the build levels it");
		LOGGER.info("qa blueprint handling: placed {}, turned 4x, picked up, levelling off/on/off, handed over at {}", first.get(), handed);
		helper.succeed();
	}

	/**
	 * Guide: "Place the blueprint over the finished building and sneak while you hand it to a builder: they take it down
	 * and put the blocks back in the chests." The player sneak-hands the placed blueprint over a standing test hut
	 * ("<builder> will take down Test Hut and put the blocks in the chests within 8 blocks ..."), but the only chest by
	 * the table is full of dirt. The hut comes down and nothing is lost: its 25 cobblestone, 55 planks (its material list), door and torch,
	 * and the blueprint, are all on the ground by the table or in the builder's bag, and the chest still holds its dirt.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaBlueprintTakeDownFull"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaBlueprintTakeDownFull")
	public void aBuildingTakenDownWithTheChestFullLosesNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		var rules = level.getGameRules();
		int delay = rules.getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.BUILD_DELAY).get();
		boolean help = rules.getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.BUILDERS_HELP).get();
		rules.getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.BUILD_DELAY).set(2, level.getServer());
		rules.getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		boolean protection = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> {
			rules.getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			rules.getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = protection;
		});
		BlockPos hutRel = new BlockPos(6, 2, 6);
		BlockPos origin = helper.absolutePos(hutRel);
		level.getStructureManager().get(TEST_HUT).orElseThrow().placeInWorld(level, origin, origin,
			new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), net.minecraft.util.RandomSource.create(7),
			net.minecraft.world.level.block.Block.UPDATE_ALL);
		BlockPos chestRel = new BlockPos(2, 2, 4);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(chestRel, net.minecraft.world.level.block.Blocks.CHEST);
		net.minecraft.world.Container chest = helper.getBlockEntity(chestRel);
		for (int i = 0; i < chest.getContainerSize(); i++) {
			chest.setItem(i, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT, 64));
		}
		int dirt = chest.countItem(net.minecraft.world.item.Items.DIRT);
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (builder.getUUID().equals(site.builder())) {
					sites.remove(site.id());
				}
			}
		});
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qabD"));
		ServerPlayer p = c.player();
		net.minecraft.world.item.ItemStack blueprint = BlueprintItem.create(TEST_HUT, null);
		blueprint.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow()
			.withPlacement(Optional.of(new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE))));
		p.setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		p.setShiftKeyDown(true);
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		p.setShiftKeyDown(false);
		BuildSite site = Builders.activeSite(level, builder);
		helper.assertTrue(site != null && site.isDeconstruction(), "the sneak-hand-over didn't start a take-down: " + texts(c.seen()));
		String hut = Blueprints.displayName(TEST_HUT).getString();
		helper.assertTrue(texts(c.seen()).stream().anyMatch(t -> t.startsWith(builder.getDisplayName().getString() + " will take down " + hut
				+ " and put the blocks in the chests within 8 blocks of their Blueprint Table.")), "the take-down said " + texts(c.seen()));
		var plan = site.plan(level);
		net.minecraft.world.phys.AABB around = helper.getBounds().inflate(8);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still taking it down: " + site.stage() + ", " + site.status());
			helper.assertTrue(plan.unfinished(level).isEmpty(), plan.unfinished(level).size() + " block(s) of the hut still stand");
			java.util.Map<net.minecraft.world.item.Item, Integer> found = new java.util.HashMap<>();
			for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, around)) {
				found.merge(item.getItem().getItem(), item.getItem().getCount(), Integer::sum);
			}
			var bag = io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG.getOrCreate(builder);
			for (var item : List.of(net.minecraft.world.item.Items.COBBLESTONE, net.minecraft.world.item.Items.OAK_PLANKS, net.minecraft.world.item.Items.OAK_DOOR,
				net.minecraft.world.item.Items.TORCH, io.github.jcondedata.aliveworkplace.registry.ModItems.BLUEPRINT)) {
				found.merge(item, bag.count(item) + chest.countItem(item), Integer::sum);
			}
			helper.assertTrue(found.getOrDefault(net.minecraft.world.item.Items.COBBLESTONE, 0) == 25 && found.getOrDefault(net.minecraft.world.item.Items.OAK_PLANKS, 0) == 55
					&& found.getOrDefault(net.minecraft.world.item.Items.OAK_DOOR, 0) == 1 && found.getOrDefault(net.minecraft.world.item.Items.TORCH, 0) == 1,
				"the hut's blocks went missing: found " + found);
			helper.assertTrue(found.getOrDefault(io.github.jcondedata.aliveworkplace.registry.ModItems.BLUEPRINT, 0) == 1, "the blueprint went missing: " + found);
			helper.assertTrue(chest.countItem(net.minecraft.world.item.Items.DIRT) == dirt, "the dirt in the chest changed");
		});
	}
}
