package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.move.MovePlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * 45.1: a building's moving plan. A small house with a door, a bed, stairs, a furnace, a chest with items, a sign, a
 * banner, an item frame and an armor stand is captured, saved and loaded, and put up elsewhere unturned and turned
 * 90/180/270: block for block ({@link MaterialRules#matches}), the chest's items, the sign's text, the banner's
 * patterns, the frame and the stand, every facing turned. Captures through the hall's finished buildings and their
 * edges.
 */
public class MovePlanGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	/** The house's lowest corner and far corner (helper coordinates): 7 × 5 × 7. */
	private static final BlockPos MIN = new BlockPos(2, 2, 2);
	private static final BlockPos MAX = new BlockPos(8, 6, 8);
	private static final BlockPos DOOR = new BlockPos(5, 3, 2);
	private static final BlockPos CHEST = new BlockPos(4, 3, 4);
	private static final BlockPos STAIRS = new BlockPos(6, 3, 4);
	private static final BlockPos BED_FOOT = new BlockPos(3, 3, 6);
	private static final BlockPos BED_HEAD = new BlockPos(3, 3, 7);
	private static final BlockPos SIGN = new BlockPos(7, 3, 7);
	private static final BlockPos BANNER = new BlockPos(5, 3, 6);
	private static final BlockPos FURNACE = new BlockPos(7, 3, 3);
	private static final BlockPos FRAME = new BlockPos(3, 4, 4);
	private static final BlockPos STAND = new BlockPos(6, 3, 6);

	private static void set(ServerLevel level, BlockPos abs, BlockState state) {
		level.setBlock(abs, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	/** The house, with its contents and decorations; returns its box (absolute). */
	private static BoundingBox house(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = MIN.getX(); x <= MAX.getX(); x++) {
			for (int z = MIN.getZ(); z <= MAX.getZ(); z++) {
				for (int y = MIN.getY(); y <= MAX.getY(); y++) {
					boolean edge = x == MIN.getX() || x == MAX.getX() || z == MIN.getZ() || z == MAX.getZ();
					BlockState state = y == MIN.getY() ? Blocks.COBBLESTONE.defaultBlockState()
						: y == MAX.getY() ? Blocks.SPRUCE_PLANKS.defaultBlockState()
						: edge ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
					set(level, helper.absolutePos(new BlockPos(x, y, z)), state);
				}
			}
		}
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
		set(level, helper.absolutePos(DOOR), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		set(level, helper.absolutePos(DOOR.above()), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		set(level, helper.absolutePos(CHEST), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
		ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(CHEST));
		chest.setItem(0, new ItemStack(Items.BREAD, 12));
		chest.setItem(5, new ItemStack(Items.IRON_INGOT, 3));
		chest.setItem(26, new ItemStack(Items.EMERALD, 64));
		set(level, helper.absolutePos(STAIRS), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
		BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		set(level, helper.absolutePos(BED_FOOT), bed.setValue(BedBlock.PART, BedPart.FOOT));
		set(level, helper.absolutePos(BED_HEAD), bed.setValue(BedBlock.PART, BedPart.HEAD));
		set(level, helper.absolutePos(SIGN), Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 4));
		SignBlockEntity sign = (SignBlockEntity) level.getBlockEntity(helper.absolutePos(SIGN));
		sign.setText(sign.getFrontText().setMessage(0, Component.literal("Rose Cottage")), true);
		set(level, helper.absolutePos(BANNER), Blocks.WHITE_BANNER.defaultBlockState());
		try {
			level.getBlockEntity(helper.absolutePos(BANNER)).loadWithComponents(
				TagParser.parseTag("{patterns:[{pattern:\"minecraft:stripe_top\",color:\"blue\"},{pattern:\"minecraft:border\",color:\"black\"}]}"),
				level.registryAccess());
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new IllegalStateException(e);
		}
		set(level, helper.absolutePos(FURNACE), Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.WEST));
		ItemFrame frame = new ItemFrame(level, helper.absolutePos(FRAME), Direction.EAST);
		frame.setItem(new ItemStack(Items.DIAMOND));
		level.addFreshEntity(frame);
		ArmorStand stand = EntityType.ARMOR_STAND.create(level);
		BlockPos standAt = helper.absolutePos(STAND);
		stand.moveTo(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5, 0, 0);
		stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		level.addFreshEntity(stand);
		return BoundingBox.fromCorners(helper.absolutePos(MIN), helper.absolutePos(MAX));
	}

	private static String chestItems(ServerLevel level, BlockPos abs) {
		if (!(level.getBlockEntity(abs) instanceof ChestBlockEntity chest)) {
			return "no chest";
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < chest.getContainerSize(); i++) {
			ItemStack stack = chest.getItem(i);
			if (!stack.isEmpty()) {
				out.append(i).append('=').append(stack.getCount()).append(' ').append(stack.getItem()).append(';');
			}
		}
		return out.toString();
	}

	/** Checks the plan put up at {@code min} turned by {@code rotation} against the original house, then clears it. */
	private static void checkPutUp(GameTestHelper helper, MovePlan plan, BlockPos destination, Rotation rotation, String originalChest) {
		ServerLevel level = helper.getLevel();
		BlockPos min = helper.absolutePos(destination);
		BlockPos from = helper.absolutePos(MIN);
		int left = plan.place(level, min, rotation);
		String how = " (" + rotation + ")";
		helper.assertTrue(left == 0, left + " entities couldn't be put back" + how);
		for (MovePlan.Placed p : plan.placed(min, rotation)) {
			BlockState actual = level.getBlockState(p.pos());
			helper.assertTrue(MaterialRules.matches(actual, p.state()), "at " + p.pos() + " " + actual + " should be " + p.state() + how);
		}
		// every block of the original stands turned at its new spot
		for (MovePlan.Block b : plan.blocks()) {
			BlockState original = level.getBlockState(from.offset(b.rel()));
			BlockState moved = level.getBlockState(plan.at(b.rel(), min, rotation));
			helper.assertTrue(MaterialRules.matches(moved, MaterialRules.transformed(original, Mirror.NONE, rotation)),
				"original " + original + " at " + b.rel() + " came out as " + moved + how);
		}
		BlockPos chestAt = plan.at(CHEST.subtract(MIN), min, rotation);
		helper.assertTrue(chestItems(level, chestAt).equals(originalChest), "chest holds " + chestItems(level, chestAt) + ", not " + originalChest + how);
		helper.assertTrue(level.getBlockState(chestAt).getValue(ChestBlock.FACING) == rotation.rotate(Direction.SOUTH), "chest facing" + how);
		BlockPos stairs = plan.at(STAIRS.subtract(MIN), min, rotation);
		helper.assertTrue(level.getBlockState(stairs).getValue(StairBlock.FACING) == rotation.rotate(Direction.EAST),
			"stairs face " + level.getBlockState(stairs) + how);
		BlockPos door = plan.at(DOOR.subtract(MIN), min, rotation);
		BlockState lower = level.getBlockState(door), upper = level.getBlockState(door.above());
		helper.assertTrue(lower.is(Blocks.OAK_DOOR) && lower.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
			&& lower.getValue(DoorBlock.FACING) == rotation.rotate(Direction.NORTH), "door's lower half " + lower + how);
		helper.assertTrue(upper.is(Blocks.OAK_DOOR) && upper.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
			&& upper.getValue(DoorBlock.FACING) == rotation.rotate(Direction.NORTH), "door's upper half " + upper + how);
		BlockPos head = plan.at(BED_HEAD.subtract(MIN), min, rotation);
		BlockPos foot = plan.at(BED_FOOT.subtract(MIN), min, rotation);
		BlockState headState = level.getBlockState(head), footState = level.getBlockState(foot);
		Direction bedFacing = rotation.rotate(Direction.SOUTH);
		helper.assertTrue(headState.is(Blocks.RED_BED) && headState.getValue(BedBlock.PART) == BedPart.HEAD && headState.getValue(BedBlock.FACING) == bedFacing,
			"bed head " + headState + how);
		helper.assertTrue(footState.is(Blocks.RED_BED) && footState.getValue(BedBlock.PART) == BedPart.FOOT && footState.getValue(BedBlock.FACING) == bedFacing,
			"bed foot " + footState + how);
		helper.assertTrue(foot.relative(bedFacing).equals(head), "the bed's head is beside its foot, the way it faces" + how);
		BlockPos furnace = plan.at(FURNACE.subtract(MIN), min, rotation);
		helper.assertTrue(level.getBlockState(furnace).getValue(FurnaceBlock.FACING) == rotation.rotate(Direction.WEST),
			"furnace faces " + level.getBlockState(furnace) + how);
		BlockPos signAt = plan.at(SIGN.subtract(MIN), min, rotation);
		helper.assertTrue(level.getBlockState(signAt).getValue(StandingSignBlock.ROTATION) == rotation.rotate(4, 16),
			"sign turned " + level.getBlockState(signAt) + how);
		helper.assertTrue(level.getBlockEntity(signAt) instanceof SignBlockEntity s
			&& s.getFrontText().getMessage(0, false).getString().equals("Rose Cottage"), "the sign's text came along" + how);
		helper.assertTrue(level.getBlockEntity(plan.at(BANNER.subtract(MIN), min, rotation)) instanceof BannerBlockEntity banner
			&& banner.getPatterns().layers().size() == 2, "the banner's patterns came along" + how);
		Vec3i size = plan.size(rotation);
		AABB box = new AABB(min.getX(), min.getY(), min.getZ(), min.getX() + size.getX(), min.getY() + size.getY(), min.getZ() + size.getZ());
		List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, box);
		helper.assertTrue(frames.size() == 1, frames.size() + " item frames, not 1" + how);
		ItemFrame frame = frames.get(0);
		helper.assertTrue(frame.getItem().is(Items.DIAMOND), "the frame holds " + frame.getItem() + how);
		helper.assertTrue(frame.getDirection() == rotation.rotate(Direction.EAST), "the frame faces " + frame.getDirection() + how);
		helper.assertTrue(frame.survives(), "the frame hangs on its wall" + how);
		helper.assertTrue(frame.getPos().equals(plan.at(FRAME.subtract(MIN), min, rotation)), "the frame hangs at " + frame.getPos() + how);
		List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, box);
		helper.assertTrue(stands.size() == 1 && stands.get(0).getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
			stands.size() + " armor stands with the helmet" + how);
		helper.assertTrue(stands.get(0).blockPosition().equals(plan.at(STAND.subtract(MIN), min, rotation)), "the stand stands at " + stands.get(0).blockPosition() + how);
		helper.assertTrue(plan.front(rotation) == rotation.rotate(Direction.NORTH), "front " + plan.front(rotation) + how);
		frames.forEach(net.minecraft.world.entity.Entity::discard);
		stands.forEach(net.minecraft.world.entity.Entity::discard);
	}

	/** The house captured (no blueprint: a player's own house), saved, loaded and put up elsewhere unturned. */
	//$ gametest_batch AREA '"movePlanRebuild"'
	@GameTest(template = AREA, batch = "movePlanRebuild")
	public void aCapturedHouseRebuildsElsewhereBlockForBlockWithItsChest(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BoundingBox box = house(helper);
		String chest = chestItems(level, helper.absolutePos(CHEST));
		helper.runAfterDelay(2, () -> {
			MovePlan captured = MovePlan.capture(level, box, Direction.NORTH, null).orElseThrow();
			MovePlan plan = MovePlan.load(captured.save()).orElseThrow();
			helper.assertTrue(plan.blocks().size() == captured.blocks().size(), "a saved plan keeps every block");
			helper.assertTrue(plan.things().size() == 2, plan.things().size() + " entities in the plan, not the frame and the stand");
			helper.assertTrue(plan.beds().equals(List.of(BED_HEAD.subtract(MIN))), "the bed is " + plan.beds());
			helper.assertTrue(chestItems(level, helper.absolutePos(CHEST)).equals(chest), "capturing leaves the chest as it was");
			checkPutUp(helper, plan, new BlockPos(16, 2, 2), Rotation.NONE, chest);
			helper.succeed();
		});
	}

	/** The house put up turned a quarter, a half and three quarters: every facing turns with it. */
	//$ gametest_batch AREA '"movePlanRotate"'
	@GameTest(template = AREA, batch = "movePlanRotate")
	public void aTurnedPlanTurnsStairsDoorsBedsAndFurniture(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BoundingBox box = house(helper);
		String chest = chestItems(level, helper.absolutePos(CHEST));
		helper.runAfterDelay(2, () -> {
			MovePlan plan = MovePlan.load(MovePlan.capture(level, box, Direction.NORTH, null).orElseThrow().save()).orElseThrow();
			checkPutUp(helper, plan, new BlockPos(16, 2, 2), Rotation.CLOCKWISE_90, chest);
			checkPutUp(helper, plan, new BlockPos(16, 2, 12), Rotation.CLOCKWISE_180, chest);
			checkPutUp(helper, plan, new BlockPos(16, 2, 22), Rotation.COUNTERCLOCKWISE_90, chest);
			helper.succeed();
		});
	}

	/**
	 * The house as a building the hall knows (finished from a blueprint, here turned a quarter): found from a block inside
	 * it, with its blueprint, its turned front, its resident's bed and workstation.
	 */
	//$ gametest_batch AREA '"movePlanHall"'
	@GameTest(template = AREA, batch = "movePlanHall")
	public void aBuildingTheHallKnowsIsCapturedWithItsFrontAndResidents(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BoundingBox box = house(helper);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "move_house_" + Long.toHexString(level.getGameTime()));
		// its blueprint is its own scan, so the template's size is the box (a square footprint: any turn fits it)
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(MIN), new Vec3i(7, 5, 7), false, Blocks.STRUCTURE_VOID);
		// placed turned a quarter: the origin is the corner the template's (0,0,0) lands on
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(MAX.getX(), MIN.getY(), MIN.getZ())),
			Rotation.CLOCKWISE_90, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(id, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		Villager villager = EntityType.VILLAGER.create(level);
		BlockPos at = helper.absolutePos(new BlockPos(5, 3, 5));
		villager.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		villager.setNoAi(true);
		level.addFreshEntity(villager);
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(BED_HEAD)));
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(FURNACE)));
		helper.runAfterDelay(2, () -> {
			BlockPos hall = helper.absolutePos(new BlockPos(15, 2, 15));
			Optional<MovePlan> found = MovePlan.captureAt(level, hall, helper.absolutePos(new BlockPos(5, 3, 5)));
			helper.assertTrue(found.isPresent(), "the hall's finished building wasn't captured from a block inside it");
			MovePlan plan = found.get();
			helper.assertTrue(id.equals(plan.structure()), "its blueprint is " + plan.structure());
			helper.assertTrue(plan.front() == Direction.EAST, "a quarter-turned blueprint's front faces east, not " + plan.front());
			helper.assertTrue(plan.size().equals(new Vec3i(7, 5, 7)), "its size is " + plan.size());
			MovePlan byBox = MovePlan.capture(level, box, Direction.EAST, id).orElseThrow();
			helper.assertTrue(plan.blocks().size() == byBox.blocks().size(), plan.blocks().size() + " blocks, the box holds " + byBox.blocks().size());
			helper.assertTrue(plan.residents().equals(List.of(villager.getUUID())), "its residents are " + plan.residents());
			helper.assertTrue(plan.beds().contains(BED_HEAD.subtract(MIN)), "its beds are " + plan.beds());
			helper.assertTrue(plan.workstations().equals(List.of(FURNACE.subtract(MIN))), "its workstations are " + plan.workstations());
			helper.assertTrue(MovePlan.captureAt(level, hall, helper.absolutePos(new BlockPos(20, 3, 20))).isEmpty(), "a spot in no building gave a plan");
			villager.discard();
			helper.succeed();
		});
	}

	/** Nothing to capture: an empty box, a building whose blueprint is gone, a building the hall doesn't know. */
	//$ gametest_batch AREA '"movePlanEdges"'
	@GameTest(template = AREA, batch = "movePlanEdges")
	public void anEmptyOrUnknownBuildingGivesNoPlan(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BoundingBox empty = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(2, 3, 2)), helper.absolutePos(new BlockPos(8, 7, 8)));
		helper.assertTrue(MovePlan.capture(level, empty, Direction.NORTH, null).isEmpty(), "an empty box gave a plan");
		BlueprintData.Placement gone = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(MIN), Rotation.NONE, Mirror.NONE);
		helper.assertTrue(MovePlan.capture(level, new BuildSiteManager.Finished(
			ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "no_such_blueprint"), gone, UUID.randomUUID())).isEmpty(),
			"a building whose blueprint is gone gave a plan");
		house(helper);
		helper.assertTrue(MovePlan.captureAt(level, helper.absolutePos(new BlockPos(15, 2, 15)), helper.absolutePos(new BlockPos(5, 3, 5))).isEmpty(),
			"a house the hall doesn't know gave a plan through the hall");
		helper.assertTrue(MovePlan.load(new net.minecraft.nbt.CompoundTag()).isEmpty(), "an empty tag loaded as a plan");
		net.minecraft.nbt.CompoundTag newer = MovePlan.capture(level, BoundingBox.fromCorners(helper.absolutePos(MIN), helper.absolutePos(MAX)), Direction.NORTH, null)
			.orElseThrow().save();
		newer.putInt("version", MovePlan.VERSION + 1);
		helper.assertTrue(MovePlan.load(newer).isEmpty(), "a plan from a newer version loaded");
		helper.succeed();
	}
}
