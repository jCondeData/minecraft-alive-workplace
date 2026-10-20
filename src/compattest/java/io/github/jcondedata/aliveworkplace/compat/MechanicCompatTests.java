package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mechanic.MechanicRound;
import io.github.jcondedata.aliveworkplace.mechanic.MechanicWork;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.WorkerLimits;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * ROADMAP 39.2 with the real Create: the Mechanic. A jobless villager takes a Create Toolbox by themselves; a shaft (and
 * a cogwheel) gone from a finished build is put back from its blueprint with the store's parts, shafts first; a
 * millstone is refilled with wheat while it turns and its flour taken to the store, and one that doesn't turn is left
 * alone; config {@code mechanics} off, the toolbox is no job for the jobless and a hired Mechanic mends nothing; the
 * round survives a save and load.
 */
public class MechanicCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos TOOLBOX = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos LINE = new BlockPos(6, 2, 6);
	private static final AtomicInteger BLUEPRINTS = new AtomicInteger();

	static Block create(String path) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path));
	}

	static Item createItem(String path) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", path));
	}

	/** {@code state} with its property {@code name} set to the value written {@code value}. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	static BlockState with(BlockState state, String name, String value) {
		Property property = state.getBlock().getStateDefinition().getProperty(name);
		return state.setValue(property, (Comparable) property.getValue(value).orElseThrow());
	}

	private static String valueOf(BlockState state, String name) {
		Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
		return property == null ? "?" : valueName(state, property);
	}

	private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	private static Villager mechanic(GameTestHelper helper) {
		helper.setBlock(TOOLBOX, create("brown_toolbox").defaultBlockState());
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Stations.assign(helper.getLevel(), villager, helper.absolutePos(TOOLBOX), ModVillagers.MECHANIC);
		helper.assertTrue(MechanicWork.isMechanic(villager), "no Mechanic at the toolbox");
		return villager;
	}

	private static Container chest(GameTestHelper helper, ItemStack... stacks) {
		helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
		Container chest = (Container) helper.getBlockEntity(CHEST);
		for (int i = 0; i < stacks.length; i++) {
			chest.setItem(i, stacks[i]);
		}
		return chest;
	}

	private static int count(Container container, Item item) {
		int n = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(item)) {
				n += container.getItem(i).getCount();
			}
		}
		return n;
	}

	/**
	 * A small drive line (a stone footing, three shafts along x and a cogwheel at the end) built at {@link #LINE}, saved
	 * as a blueprint and recorded as a finished build; returns the line's blocks' positions (shafts 0-2, cogwheel 3).
	 */
	private static List<BlockPos> finishedLine(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// The lines of earlier tests (their areas cleared, their records kept) would be holes within this Mechanic's reach.
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : sites.finishedIn(level)) {
			if (f.structure().getPath().startsWith("compat_test/mechanic_line_")) {
				sites.forgetFinished(f.placement());
			}
		}
		BlockState shaft = with(create("shaft").defaultBlockState(), "axis", "x");
		BlockState cog = with(create("cogwheel").defaultBlockState(), "axis", "x");
		List<BlockPos> parts = List.of(LINE.offset(0, 1, 0), LINE.offset(1, 1, 0), LINE.offset(2, 1, 0), LINE.offset(3, 1, 0));
		for (int x = 0; x < 4; x++) {
			helper.setBlock(LINE.offset(x, 0, 0), Blocks.STONE_BRICKS);
			helper.setBlock(parts.get(x), x < 3 ? shaft : cog);
		}
		ResourceLocation id = AliveWorkplace.id("compat_test/mechanic_line_" + BLUEPRINTS.incrementAndGet());
		StructureTemplate template = level.getStructureManager().getOrCreate(id);
		template.fillFromWorld(level, helper.absolutePos(LINE), new Vec3i(4, 2, 1), false, Blocks.STRUCTURE_VOID);
		helper.assertTrue(BlueprintLibrary.get(level, id).isPresent(), "could not register the test blueprint");
		BuildSiteManager.get(level).recordFinished(id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(LINE),
			Rotation.NONE, Mirror.NONE), UUID.randomUUID());
		return parts;
	}

	/** Create's Toolbox is a job site of ours, and a jobless villager by it becomes the Mechanic by themselves. */
	//$ gametest_ticks_batch AREA '1800' '"mechanic_takes_toolbox"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "mechanic_takes_toolbox")
	public void aJoblessVillagerTakesTheToolbox(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.assertTrue(ModVillagers.MECHANIC != null, "no Mechanic profession with Create installed");
		PoiType poi = helper.getLevel().registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).get(ModVillagers.TOOLBOX_ID);
		helper.assertTrue(poi != null, "the toolbox POI isn't registered");
		for (ResourceLocation colour : ModVillagers.TOOLBOX_BLOCKS) {
			BlockState toolbox = BuiltInRegistries.BLOCK.get(colour).defaultBlockState();
			helper.assertTrue(poi.is(toolbox), colour + " isn't a Mechanic's job block");
		}
		helper.setBlock(TOOLBOX, create("red_toolbox").defaultBlockState()); // a dyed one counts too
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
		helper.setDayTime(2000);
		helper.succeedWhen(() -> helper.assertTrue(ModVillagers.isMechanic(villager.getVillagerData().getProfession()),
			"villager is still " + villager.getVillagerData().getProfession() + " at " + helper.relativePos(villager.blockPosition())));
	}

	/** A shaft and a cogwheel gone from a finished build go back from its blueprint, shaft first, with the store's parts. */
	//$ gametest_ticks_batch AREA '1800' '"mechanic_mends_shaft"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "mechanic_mends_shaft")
	public void aBrokenShaftInAFinishedBuildIsReplaced(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		List<BlockPos> line = finishedLine(helper);
		Container chest = chest(helper, new ItemStack(createItem("shaft"), 2), new ItemStack(createItem("cogwheel"), 1));
		Villager mechanic = mechanic(helper);
		helper.setBlock(line.get(3), Blocks.AIR); // the cogwheel
		helper.setBlock(line.get(1), Blocks.AIR); // a shaft in the middle
		List<MechanicWork.Part> missing = MechanicWork.missingParts(level, helper.absolutePos(TOOLBOX));
		helper.assertTrue(missing.size() == 2, "expected 2 missing parts, found " + missing.stream().map(p -> helper.relativePos(p.pos()) + " " + p.state()).toList());
		helper.assertTrue(missing.get(0).pos().equals(helper.absolutePos(line.get(1))) && missing.get(0).cost() == createItem("shaft"),
			"the shaft doesn't go back first: " + missing.get(0));
		helper.assertTrue(SupplyContainers.find(level, helper.absolutePos(TOOLBOX), null).equals(List.of(helper.absolutePos(CHEST))),
			"the toolbox counts as a supply chest: " + SupplyContainers.find(level, helper.absolutePos(TOOLBOX), null));
		helper.setDayTime(2000);
		helper.succeedWhen(() -> {
			BlockState shaft = helper.getBlockState(line.get(1));
			BlockState cog = helper.getBlockState(line.get(3));
			helper.assertTrue(shaft.is(create("shaft")) && valueOf(shaft, "axis").equals("x"), "the shaft isn't back: " + shaft);
			helper.assertTrue(cog.is(create("cogwheel")) && valueOf(cog, "axis").equals("x"), "the cogwheel isn't back: " + cog);
			helper.assertTrue(count(chest, createItem("shaft")) == 1 && count(chest, createItem("cogwheel")) == 0,
				"the parts didn't come from the store: " + count(chest, createItem("shaft")) + " shafts, "
					+ count(chest, createItem("cogwheel")) + " cogwheels left");
			helper.assertTrue(mechanic.getMainHandItem().isEmpty(), "the Mechanic still holds " + mechanic.getMainHandItem());
		});
	}

	/** Without the part in the store nothing is put back, and the Mechanic says what's needed. */
	//$ gametest_ticks_batch AREA '400' '"mechanic_needs_part"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "mechanic_needs_part")
	public void withoutAShaftInTheStoreTheHoleWaits(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		List<BlockPos> line = finishedLine(helper);
		chest(helper, new ItemStack(Items.WHEAT, 4));
		Villager mechanic = mechanic(helper);
		helper.setBlock(line.get(0), Blocks.AIR);
		helper.setDayTime(2000);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(helper.getBlockState(line.get(0)).isAir(), "a shaft came from nowhere");
			var status = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(mechanic, helper.getLevel().getGameTime());
			helper.assertTrue(status != null && status.line().getString().contains("needs"), "the Mechanic doesn't say what's needed: "
				+ (status == null ? "no status" : status.line().getString()));
			helper.succeed();
		});
	}

	/**
	 * A millstone turned by a creative motor gets the store's wheat and its flour goes to the store; one that doesn't turn
	 * is fed nothing. Neither counts as a supply chest.
	 */
	//$ gametest_ticks_batch AREA '1800' '"mechanic_millstone"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "mechanic_millstone")
	public void aTurningMillstoneIsRefilledAndEmptied(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos post = helper.absolutePos(TOOLBOX);
		helper.setBlock(TOOLBOX, create("brown_toolbox").defaultBlockState());
		Container chest = chest(helper, new ItemStack(Items.WHEAT, 20));
		BlockPos mill = new BlockPos(6, 3, 2);
		BlockPos still = new BlockPos(6, 2, 5);
		helper.setBlock(mill.below(), with(create("creative_motor").defaultBlockState(), "facing", Direction.UP.getSerializedName()));
		helper.setBlock(mill, create("millstone").defaultBlockState());
		helper.setBlock(still, create("millstone").defaultBlockState());
		helper.assertTrue(MechanicWork.findMachines(level, post).containsAll(List.of(helper.absolutePos(mill), helper.absolutePos(still))),
			"the round doesn't find both millstones: " + MechanicWork.findMachines(level, post));
		helper.assertTrue(!SupplyContainers.find(level, post, null).contains(helper.absolutePos(mill)), "a millstone counts as a supply chest");
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(MechanicWork.tend(level, post, helper.absolutePos(still)) == MechanicWork.Tended.STOPPED,
				"a millstone that doesn't turn wasn't reported as stopped");
			helper.assertTrue(count(chest, Items.WHEAT) == 20, "wheat went into a millstone that doesn't turn");
			helper.assertTrue(MechanicWork.turning(level, helper.absolutePos(mill)), "the motor doesn't turn the millstone");
			helper.assertTrue(MechanicWork.tend(level, post, helper.absolutePos(mill)) == MechanicWork.Tended.WORKED, "the turning millstone wasn't fed");
			helper.assertTrue(count(chest, Items.WHEAT) == 4, "expected 16 wheat taken to the millstone, " + count(chest, Items.WHEAT) + " left");
			helper.succeedWhen(() -> {
				MechanicWork.tend(level, post, helper.absolutePos(mill));
				helper.assertTrue(count(chest, createItem("wheat_flour")) > 0, "no flour in the store yet");
			});
		});
	}

	/** Config {@code mechanics} off: the toolbox is hidden from the jobless, and a hired Mechanic mends nothing. */
	//$ gametest_ticks_batch AREA '600' '"mechanics_off"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "mechanics_off")
	public void mechanicsSwitchedOff(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		List<BlockPos> line = finishedLine(helper);
		chest(helper, new ItemStack(createItem("shaft"), 2));
		Villager mechanic = mechanic(helper);
		Villager jobless = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 2));
		jobless.setVillagerData(jobless.getVillagerData().setProfession(VillagerProfession.NONE));
		boolean before = MechanicWork.ENABLED;
		MechanicWork.ENABLED = false;
		var toolbox = level.getPoiManager().getType(helper.absolutePos(TOOLBOX)).orElseThrow();
		WorkerLimits.thinking(jobless);
		long seen;
		try {
			seen = WorkerLimits.hideFull(java.util.stream.Stream.of(com.mojang.datafixers.util.Pair.of(toolbox, helper.absolutePos(TOOLBOX)))).count();
		} finally {
			WorkerLimits.thinking(null);
		}
		if (seen != 0) {
			MechanicWork.ENABLED = before;
			helper.fail("mechanics off: a jobless villager still sees the toolbox as a workstation");
		}
		helper.setBlock(line.get(1), Blocks.AIR);
		helper.setDayTime(2000);
		helper.runAfterDelay(400, () -> {
			MechanicWork.ENABLED = before;
			helper.assertTrue(helper.getBlockState(line.get(1)).isAir(), "mechanics off, the shaft was put back");
			helper.assertTrue(!ModVillagers.isMechanic(jobless.getVillagerData().getProfession()), "mechanics off, a jobless villager became a Mechanic");
			helper.assertTrue(MechanicWork.isMechanic(mechanic), "the hired Mechanic lost the job");
			helper.succeed();
		});
	}

	/** The round and the job are saved with the villager and come back after a load. */
	//$ gametest_batch AREA '"mechanic_saved"'
	@GameTest(template = AREA, batch = "mechanic_saved")
	public void theRoundSurvivesASaveAndLoad(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Villager mechanic = mechanic(helper);
		helper.setBlock(new BlockPos(6, 2, 2), create("millstone").defaultBlockState());
		helper.setBlock(new BlockPos(6, 2, 5), create("millstone").defaultBlockState());
		MechanicRound round = MechanicWork.round(level, mechanic, helper.absolutePos(TOOLBOX)).advance();
		ModAttachments.MECHANIC_ROUND.set(mechanic, round);
		helper.assertTrue(round.machines().size() == 2 && round.next() == 1, "unexpected round " + round);
		CompoundTag saved = new CompoundTag();
		mechanic.saveWithoutId(saved);
		Villager copy = EntityType.VILLAGER.create(level);
		copy.load(saved);
		helper.assertTrue(ModVillagers.isMechanic(copy.getVillagerData().getProfession()), "the job didn't survive: " + copy.getVillagerData().getProfession());
		helper.assertTrue(round.equals(ModAttachments.MECHANIC_ROUND.get(copy)), "the round didn't survive: " + ModAttachments.MECHANIC_ROUND.get(copy));
		copy.discard();
		helper.succeed();
	}

	/**
	 * A part fetched but not put back when the work stops (the day ends) goes back to the store, and out of the
	 * Mechanic's hand: one shaft, never two.
	 */
	//$ gametest_batch AREA '"mechanic_stopped"'
	@GameTest(template = AREA, batch = "mechanic_stopped")
	public void aPartInHandWhenTheWorkStopsGoesBackOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Villager mechanic = mechanic(helper);
		Container chest = chest(helper);
		mechanic.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(createItem("shaft")));
		new MechanicWork().doStop(level, mechanic, level.getGameTime());
		helper.assertTrue(count(chest, createItem("shaft")) == 1, "the shaft in hand didn't go back to the store: " + count(chest, createItem("shaft")));
		helper.assertTrue(mechanic.getMainHandItem().isEmpty(), "the shaft went back to the store and stayed in the Mechanic's hand too: "
			+ mechanic.getMainHandItem());
		helper.succeed();
	}

	/**
	 * 39.5's crew lead: the Mechanic whose toolbox is near a build leads its crew; one whose toolbox is
	 * out of reach doesn't.
	 */
	//$ gametest_batch AREA '"mechanic_crew_lead"'
	@GameTest(template = AREA, batch = "mechanic_crew_lead")
	public void theMechanicNearABuildLeadsItsCrew(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Villager mechanic = mechanic(helper);
		BlockPos site = helper.absolutePos(LINE);
		helper.assertTrue(MechanicWork.crewLead(level, site).filter(v -> v == mechanic).isPresent(),
			"the Mechanic by the build doesn't lead its crew: " + MechanicWork.crewLead(level, site));
		BlockPos far = helper.absolutePos(TOOLBOX).offset(MechanicWork.REACH + 4, 0, 0);
		helper.assertTrue(MechanicWork.crewLead(level, far).isEmpty(), "a Mechanic leads the crew of a build out of reach");
		helper.succeed();
	}

	/**
	 * A round of one machine is back at its start every visit, so a machine built since then is found and joins it: the
	 * Mechanic of a village's first millstone sees to its second one too.
	 */
	//$ gametest_batch AREA '"mechanic_round_grows"'
	@GameTest(template = AREA, batch = "mechanic_round_grows")
	public void aMachineBuiltLaterJoinsARoundOfOne(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Villager mechanic = mechanic(helper);
		helper.setBlock(new BlockPos(6, 2, 2), create("millstone").defaultBlockState());
		MechanicRound one = MechanicWork.round(level, mechanic, helper.absolutePos(TOOLBOX));
		helper.assertTrue(one.machines().size() == 1, "unexpected round " + one);
		ModAttachments.MECHANIC_ROUND.set(mechanic, one.advance()); // the first millstone seen to: round again
		helper.setBlock(new BlockPos(6, 2, 5), create("millstone").defaultBlockState());
		MechanicRound two = MechanicWork.round(level, mechanic, helper.absolutePos(TOOLBOX));
		helper.assertTrue(two.machines().size() == 2 && two.machines().contains(helper.absolutePos(new BlockPos(6, 2, 5))),
			"the millstone built later never joined the round of one: " + two);
		helper.succeed();
	}

	/**
	 * A belt gone from a finished build goes back controller segment first (the segment that holds the belt together),
	 * even when it's the farthest from the toolbox; the other segments follow, nearest first.
	 */
	//$ gametest_batch AREA '"mechanic_belt_order"'
	@GameTest(template = AREA, batch = "mechanic_belt_order")
	public void aGoneBeltGoesBackControllerFirst(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : sites.finishedIn(level)) {
			if (f.structure().getPath().startsWith("compat_test/mechanic_line_")) {
				sites.forgetFinished(f.placement());
			}
		}
		// A three-segment belt along x, saved where its controller (the far end, x = 2) stood at 12 64 10
		BlockState belt = create("belt").defaultBlockState();
		CompoundTag structure = new CompoundTag();
		structure.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		structure.put("size", ints(3, 1, 1));
		net.minecraft.nbt.ListTag palette = new net.minecraft.nbt.ListTag();
		palette.add(net.minecraft.nbt.NbtUtils.writeBlockState(belt));
		structure.put("palette", palette);
		net.minecraft.nbt.ListTag blocks = new net.minecraft.nbt.ListTag();
		for (int x = 0; x < 3; x++) {
			CompoundTag data = new CompoundTag();
			data.putString("id", "create:belt");
			data.putBoolean("IsController", x == 2);
			data.putInt("Index", 2 - x);
			data.putInt("Length", 3);
			data.putIntArray("Controller", new int[] {12, 64, 10});
			CompoundTag block = new CompoundTag();
			block.put("pos", ints(x, 0, 0));
			block.putInt("state", 0);
			block.put("nbt", data);
			blocks.add(block);
			helper.setBlock(LINE.offset(x, 0, 0), Blocks.AIR); // all three gone
		}
		structure.put("blocks", blocks);
		structure.put("entities", new net.minecraft.nbt.ListTag());
		ResourceLocation id = AliveWorkplace.id("compat_test/mechanic_line_belt_" + BLUEPRINTS.incrementAndGet());
		level.getStructureManager().getOrCreate(id).load(BuiltInRegistries.BLOCK.asLookup(), structure);
		helper.assertTrue(BlueprintLibrary.get(level, id).isPresent(), "could not register the test blueprint");
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(LINE), Rotation.NONE, Mirror.NONE);
		sites.recordFinished(id, placement, UUID.randomUUID());
		try {
			List<MechanicWork.Part> belts = MechanicWork.missingParts(level, helper.absolutePos(TOOLBOX)).stream().filter(p -> p.structure().equals(id)).toList();
			helper.assertTrue(belts.size() == 3, "expected the 3 belt segments missing, found " + belts.stream().map(p -> helper.relativePos(p.pos())).toList());
			MechanicWork.Part first = belts.get(0);
			helper.assertTrue(first.pos().equals(helper.absolutePos(LINE.offset(2, 0, 0))) && first.nbt() != null && first.nbt().getBoolean("IsController")
				&& first.cost() == createItem("belt_connector"), "the belt's controller segment doesn't go back first: "
				+ belts.stream().map(p -> helper.relativePos(p.pos()) + " " + p.nbt()).toList());
			helper.assertTrue(belts.get(1).pos().equals(helper.absolutePos(LINE)) && belts.get(2).pos().equals(helper.absolutePos(LINE.offset(1, 0, 0))),
				"after the controller the segments don't go back nearest first: " + belts.stream().map(p -> helper.relativePos(p.pos())).toList());
		} finally {
			sites.forgetFinished(placement);
		}
		helper.succeed();
	}

	private static net.minecraft.nbt.ListTag ints(int... values) {
		net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
		for (int v : values) {
			list.add(net.minecraft.nbt.IntTag.valueOf(v));
		}
		return list;
	}

	/**
	 * B239/B240: what a player puts in a Create Toolbox stays in it as the same item and count, with a Mechanic at it and
	 * a chest of goods by it through hundreds of ticks of work; and none of our storage code ever lists, fills or empties
	 * the toolbox as a supply chest. (Put in and taken out through Fabric's item storage, the way hoppers and menus do.)
	 */
	//$ gametest_ticks_batch AREA '1800' '"toolbox_keeps_items"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "toolbox_keeps_items")
	public void aToolboxKeepsWhatIsPutInIt(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		chest(helper, new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(createItem("shaft"), 2));
		Villager mechanic = mechanic(helper);
		BlockPos toolbox = helper.absolutePos(TOOLBOX);
		var storage = net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level, toolbox, null);
		if (storage == null) {
			helper.fail("no item storage on the toolbox");
			return;
		}
		ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
		long in;
		try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
			in = storage.insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(pick), 1, tx);
			tx.commit();
		}
		if (in == 0) {
			helper.fail("toolbox takes no pickaxe via storage");
			return;
		}
		helper.setDayTime(2000);
		helper.runAfterDelay(400, () -> {
			long found = 0;
			boolean other = false;
			for (var view : storage) {
				if (view.isResourceBlank()) {
					continue;
				}
				if (view.getResource().getItem() == Items.DIAMOND_PICKAXE) {
					found += view.getAmount();
				} else {
					other = true;
				}
			}
			helper.assertTrue(found == in, "the toolbox holds " + found + " of the " + in + " pickaxes put in");
			helper.assertTrue(!other, "the toolbox holds something that wasn't put in");
			helper.assertTrue(!SupplyContainers.find(level, toolbox, null).contains(toolbox), "the toolbox is listed as a supply chest");
			try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
				long out = storage.extract(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(pick), in, tx);
				helper.assertTrue(out == in, "only " + out + " of " + in + " pickaxes come back out");
			}
			helper.succeed();
		});
	}

	/**
	 * Create's blocks that hold items for their own work are never a worker's supply chest: a train station's schedule
	 * slot, a saw's or a drill's work, an ejector's launch, a portable storage interface (a contraption's chests), the
	 * packager's and the package ports' packages, the stock ticker's and the creative crate's endless stock. Otherwise a
	 * farmer next to one fills it with wheat or a builder takes from a creative crate without end. An item vault is
	 * storage and stays one.
	 */
	//$ gametest_ticks_batch AREA '100' '"create_not_storage"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "create_not_storage")
	public void createWorkBlocksAreNoSupplyChests(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		List<String> ids = List.of("track_station", "mechanical_saw", "mechanical_drill", "weighted_ejector", "portable_storage_interface",
			"packager", "repackager", "package_frogport", "white_postbox", "stock_ticker", "creative_crate", "andesite_tunnel", "brass_tunnel");
		var stores = io.github.jcondedata.aliveworkplace.platform.Platform.get().items();
		java.util.Map<BlockPos, String> placed = new java.util.LinkedHashMap<>();
		for (int i = 0; i < ids.size(); i++) {
			BlockPos at = new BlockPos(i % 7, 2, 5 + i / 7 * 2);
			helper.setBlock(at, create(ids.get(i)).defaultBlockState());
			placed.put(helper.absolutePos(at), ids.get(i));
		}
		BlockPos vault = new BlockPos(6, 2, 2);
		helper.setBlock(vault, create("item_vault").defaultBlockState());
		List<BlockPos> found = SupplyContainers.find(level, helper.absolutePos(new BlockPos(3, 2, 3)), null);
		List<String> listed = new java.util.ArrayList<>();
		int stores0 = 0;
		for (var e : placed.entrySet()) {
			if (stores.isStore(level, e.getKey())) {
				stores0++;
			}
			if (found.contains(e.getKey())) {
				listed.add(e.getValue());
			}
		}
		helper.assertTrue(stores0 > 0, "setup: none of Create's work blocks has item storage");
		helper.assertTrue(listed.isEmpty(), "Create's work blocks listed as supply chests: " + listed);
		helper.assertTrue(found.contains(helper.absolutePos(vault)), "an item vault isn't listed as a supply chest");
		MachineWorkCompatTests.clearMachines(helper);
		helper.succeed();
	}

	/**
	 * A Create item vault built of several blocks is one store: every block of it hands out the same inventory, so it's
	 * listed once and its items counted once (as a double chest is), not once per block.
	 */
	//$ gametest_ticks_batch AREA '200' '"create_vault_once"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "create_vault_once")
	public void aBigVaultIsCountedOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		for (int x = 4; x <= 6; x++) {
			helper.setBlock(new BlockPos(x, 2, 0), with(create("item_vault").defaultBlockState(), "axis", "x"));
		}
		helper.runAfterDelay(20, () -> {
			var stores = io.github.jcondedata.aliveworkplace.platform.Platform.get().items();
			BlockPos first = helper.absolutePos(new BlockPos(4, 2, 0));
			helper.assertTrue(stores.insert(level, List.of(first), new ItemStack(Items.DIAMOND, 10)).isEmpty(), "setup: the vault takes no diamonds");
			List<BlockPos> found = SupplyContainers.find(level, helper.absolutePos(new BlockPos(3, 2, 3)), null);
			long vaults = found.stream().filter(p -> level.getBlockState(p).is(create("item_vault"))).count();
			long counted = SupplyContainers.count(level, found, Items.DIAMOND);
			helper.assertTrue(counted == 10, "10 diamonds in a three-block vault counted as " + counted + " (" + vaults + " vault blocks listed)");
			MachineWorkCompatTests.clearMachines(helper); // the vault, without spilling its diamonds for later tests
			helper.succeed();
		});
	}
}
