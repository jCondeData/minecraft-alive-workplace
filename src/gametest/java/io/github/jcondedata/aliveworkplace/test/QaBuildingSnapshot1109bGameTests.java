package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MovePlan;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariants;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * QA (qa-lane-b-1109-2137) for 45.1, a building's snapshot, written from the spec: "every block state, container
 * contents, signs, banners, item frames, armor stands ... with its front and its residents' beds and workstations", a
 * captured plan rebuilt elsewhere matches block for block with every container's items, and turning it 90/180/270 turns
 * stairs, doors, beds and furniture. Beyond {@link MovePlanGameTests}' small house: a non-square room (7 × 9) with a
 * double chest, a named chest, an empty chest, a barrel, a shulker box and a furnace with all three slots filled, a
 * sign with front and back text (waxed), a wall sign, a wall banner, a lectern with its book, an open right-hinged door,
 * a top trapdoor, upside-down stairs, a sideways log, a lever, a wall torch, a turned item frame, a two-wide painting
 * and an armor stand in full gear. Also: capturing changes nothing (blocks, block data, entities), a plan written to
 * disk and read back is the same plan, and only the building's own things and people go into it.
 */
public class QaBuildingSnapshot1109bGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	/** The room's lowest and far corners (helper coordinates): 7 wide (x), 6 tall, 9 deep (z). Its front is z = 2. */
	private static final BlockPos MIN = new BlockPos(2, 2, 2);
	private static final BlockPos MAX = new BlockPos(8, 7, 10);
	private static final BlockPos CHEST_RIGHT = new BlockPos(3, 3, 3);
	private static final BlockPos CHEST_LEFT = new BlockPos(4, 3, 3);
	private static final BlockPos NAMED_CHEST = new BlockPos(6, 3, 3);
	private static final BlockPos BARREL = new BlockPos(7, 3, 3);
	private static final BlockPos SHULKER = new BlockPos(7, 3, 5);
	private static final BlockPos FURNACE = new BlockPos(7, 3, 7);
	private static final BlockPos EMPTY_CHEST = new BlockPos(3, 3, 5);
	private static final BlockPos DOOR = new BlockPos(5, 3, 2);
	private static final BlockPos TRAPDOOR = new BlockPos(5, 5, 5);
	private static final BlockPos STAIRS = new BlockPos(5, 3, 7);
	private static final BlockPos LOG = new BlockPos(4, 3, 9);
	private static final BlockPos BED_FOOT = new BlockPos(6, 3, 8);
	private static final BlockPos BED_HEAD = new BlockPos(6, 3, 9);
	private static final BlockPos SIGN = new BlockPos(5, 3, 5);
	private static final BlockPos WALL_SIGN = new BlockPos(3, 3, 8);
	private static final BlockPos WALL_BANNER = new BlockPos(3, 4, 7);
	private static final BlockPos LEVER = new BlockPos(4, 4, 3);
	private static final BlockPos TORCH = new BlockPos(6, 4, 3);
	private static final BlockPos LECTERN = new BlockPos(4, 3, 7);
	private static final BlockPos FRAME = new BlockPos(3, 4, 5);
	private static final BlockPos PAINTING = new BlockPos(5, 5, 9);
	private static final BlockPos STAND = new BlockPos(4, 3, 5);
	private static final float STAND_YAW = 30f;
	/** Every container of the room. */
	private static final List<BlockPos> CONTAINERS = List.of(CHEST_RIGHT, CHEST_LEFT, NAMED_CHEST, BARREL, SHULKER, FURNACE, EMPTY_CHEST);

	private static void set(ServerLevel level, BlockPos abs, BlockState state) {
		level.setBlock(abs, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	private static Container container(ServerLevel level, BlockPos abs) {
		return level.getBlockEntity(abs) instanceof Container c ? c : null;
	}

	/** A container's items slot by slot ("0=12 bread;..."), "" when empty, "none" when there's no container. */
	private static String items(ServerLevel level, BlockPos abs) {
		Container c = container(level, abs);
		if (c == null) {
			return "none";
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack stack = c.getItem(i);
			if (!stack.isEmpty()) {
				out.append(i).append('=').append(stack.getCount()).append(' ').append(stack.getItem()).append(';');
			}
		}
		return out.toString();
	}

	private static void put(ServerLevel level, BlockPos abs, int slot, ItemStack stack) {
		container(level, abs).setItem(slot, stack);
	}

	/** The room with its furniture, contents and decorations; returns its box (absolute). */
	private static BoundingBox room(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = MIN.getX(); x <= MAX.getX(); x++) {
			for (int z = MIN.getZ(); z <= MAX.getZ(); z++) {
				for (int y = MIN.getY(); y <= MAX.getY(); y++) {
					boolean edge = x == MIN.getX() || x == MAX.getX() || z == MIN.getZ() || z == MAX.getZ();
					BlockState state = y == MIN.getY() ? Blocks.STONE_BRICKS.defaultBlockState()
						: y == MAX.getY() ? Blocks.DARK_OAK_PLANKS.defaultBlockState()
						: edge ? Blocks.BIRCH_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
					set(level, helper.absolutePos(new BlockPos(x, y, z)), state);
				}
			}
		}
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
		set(level, helper.absolutePos(CHEST_RIGHT), chest.setValue(ChestBlock.TYPE, ChestType.RIGHT));
		set(level, helper.absolutePos(CHEST_LEFT), chest.setValue(ChestBlock.TYPE, ChestType.LEFT));
		put(level, helper.absolutePos(CHEST_LEFT), 0, new ItemStack(Items.APPLE, 10));
		put(level, helper.absolutePos(CHEST_LEFT), 13, new ItemStack(Items.REDSTONE, 33));
		put(level, helper.absolutePos(CHEST_RIGHT), 26, new ItemStack(Items.GOLD_INGOT, 5));
		set(level, helper.absolutePos(NAMED_CHEST), chest);
		put(level, helper.absolutePos(NAMED_CHEST), 4, new ItemStack(Items.DIAMOND_SWORD));
		BlockEntity named = level.getBlockEntity(helper.absolutePos(NAMED_CHEST));
		CompoundTag withName = named.saveWithoutMetadata(level.registryAccess());
		withName.putString("CustomName", "\"Pantry\"");
		named.loadWithComponents(withName, level.registryAccess());
		set(level, helper.absolutePos(BARREL), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.NORTH));
		put(level, helper.absolutePos(BARREL), 1, new ItemStack(Items.WHEAT, 40));
		set(level, helper.absolutePos(SHULKER), Blocks.BLUE_SHULKER_BOX.defaultBlockState().setValue(ShulkerBoxBlock.FACING, Direction.EAST));
		put(level, helper.absolutePos(SHULKER), 7, new ItemStack(Items.ENDER_PEARL, 16));
		set(level, helper.absolutePos(FURNACE), Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.WEST));
		put(level, helper.absolutePos(FURNACE), 0, new ItemStack(Items.RAW_IRON, 4));
		put(level, helper.absolutePos(FURNACE), 1, new ItemStack(Items.COAL, 7));
		put(level, helper.absolutePos(FURNACE), 2, new ItemStack(Items.IRON_INGOT, 2));
		set(level, helper.absolutePos(EMPTY_CHEST), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
		BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
			.setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT).setValue(DoorBlock.OPEN, true);
		set(level, helper.absolutePos(DOOR), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		set(level, helper.absolutePos(DOOR.above()), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		set(level, helper.absolutePos(TRAPDOOR), Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.EAST)
			.setValue(TrapDoorBlock.HALF, Half.TOP).setValue(TrapDoorBlock.OPEN, true));
		set(level, helper.absolutePos(STAIRS), Blocks.BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST)
			.setValue(StairBlock.HALF, Half.TOP));
		set(level, helper.absolutePos(LOG), Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		BlockState bed = Blocks.BLUE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		set(level, helper.absolutePos(BED_FOOT), bed.setValue(BedBlock.PART, BedPart.FOOT));
		set(level, helper.absolutePos(BED_HEAD), bed.setValue(BedBlock.PART, BedPart.HEAD));
		set(level, helper.absolutePos(SIGN), Blocks.SPRUCE_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 2));
		SignBlockEntity sign = (SignBlockEntity) level.getBlockEntity(helper.absolutePos(SIGN));
		sign.setText(sign.getFrontText().setMessage(0, Component.literal("Front line")).setMessage(3, Component.literal("Bottom")), true);
		sign.setText(sign.getBackText().setMessage(1, Component.literal("Back line")), false);
		sign.setWaxed(true);
		set(level, helper.absolutePos(WALL_SIGN), Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.EAST));
		SignBlockEntity wallSign = (SignBlockEntity) level.getBlockEntity(helper.absolutePos(WALL_SIGN));
		wallSign.setText(wallSign.getFrontText().setMessage(1, Component.literal("Wall")), true);
		set(level, helper.absolutePos(WALL_BANNER), Blocks.RED_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.EAST));
		try {
			level.getBlockEntity(helper.absolutePos(WALL_BANNER)).loadWithComponents(
				TagParser.parseTag("{patterns:[{pattern:\"minecraft:cross\",color:\"yellow\"},{pattern:\"minecraft:creeper\",color:\"black\"},"
					+ "{pattern:\"minecraft:border\",color:\"white\"}]}"),
				level.registryAccess());
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new IllegalStateException(e);
		}
		set(level, helper.absolutePos(LEVER), Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
			.setValue(LeverBlock.FACING, Direction.SOUTH));
		set(level, helper.absolutePos(TORCH), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
		set(level, helper.absolutePos(LECTERN), Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH)
			.setValue(LecternBlock.HAS_BOOK, true));
		((LecternBlockEntity) level.getBlockEntity(helper.absolutePos(LECTERN))).setBook(new ItemStack(Items.WRITABLE_BOOK));
		ItemFrame frame = new ItemFrame(level, helper.absolutePos(FRAME), Direction.EAST);
		frame.setItem(new ItemStack(Items.COMPASS));
		frame.setRotation(3);
		helper.assertTrue(frame.survives(), "setup: the frame doesn't hang on its wall");
		level.addFreshEntity(frame);
		Painting painting = new Painting(level, helper.absolutePos(PAINTING), Direction.NORTH,
			level.registryAccess().registryOrThrow(Registries.PAINTING_VARIANT).getHolderOrThrow(PaintingVariants.POOL));
		helper.assertTrue(painting.survives(), "setup: the painting doesn't hang on its wall");
		level.addFreshEntity(painting);
		ArmorStand stand = EntityType.ARMOR_STAND.create(level);
		BlockPos standAt = helper.absolutePos(STAND);
		stand.moveTo(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5, STAND_YAW, 0);
		stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
		stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		stand.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
		stand.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
		stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
		stand.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		level.addFreshEntity(stand);
		BlockPos left = helper.absolutePos(CHEST_LEFT);
		helper.assertTrue(left.relative(ChestBlock.getConnectedDirection(level.getBlockState(left))).equals(helper.absolutePos(CHEST_RIGHT)),
			"setup: the two chest halves aren't one double chest");
		return BoundingBox.fromCorners(helper.absolutePos(MIN), helper.absolutePos(MAX));
	}

	/** Every container's items in the room as it stands. */
	private static Map<BlockPos, String> contents(GameTestHelper helper) {
		Map<BlockPos, String> out = new HashMap<>();
		for (BlockPos c : CONTAINERS) {
			out.put(c, items(helper.getLevel(), helper.absolutePos(c)));
		}
		return out;
	}

	private static String gear(ArmorStand stand) {
		StringBuilder out = new StringBuilder();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack s = stand.getItemBySlot(slot);
			if (!s.isEmpty()) {
				out.append(slot.getName()).append('=').append(s.getItem()).append(';');
			}
		}
		return out.toString();
	}

	/** The plan put up at {@code destination} turned by {@code rotation}, checked against the room still standing. */
	private static void checkPutUp(GameTestHelper helper, MovePlan plan, BlockPos destination, Rotation rotation, Map<BlockPos, String> contents,
								   ArmorStand original) {
		ServerLevel level = helper.getLevel();
		BlockPos min = helper.absolutePos(destination);
		BlockPos from = helper.absolutePos(MIN);
		String how = " (" + rotation + ")";
		int left = plan.place(level, min, rotation);
		helper.assertTrue(left == 0, left + " entities couldn't be put back" + how);
		Vec3i size = plan.size(rotation);
		helper.assertTrue(size.equals(quarter(rotation) ? new Vec3i(9, 6, 7) : new Vec3i(7, 6, 9)), "turned size " + size + how);
		BoundingBox turned = BoundingBox.fromCorners(min, min.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1));
		// every block of the room stands at its turned spot, exactly as the room's block turned (no neighbour reshaped it)
		int checked = 0;
		for (MovePlan.Block b : plan.blocks()) {
			BlockPos to = plan.at(b.rel(), min, rotation);
			helper.assertTrue(turned.isInside(to), b.state() + " at " + b.rel() + " lands at " + to + ", outside the turned box " + turned + how);
			BlockState was = level.getBlockState(from.offset(b.rel()));
			BlockState wanted = was.rotate(rotation);
			BlockState moved = level.getBlockState(to);
			helper.assertTrue(moved == wanted, "the room's " + was + " at " + b.rel() + " came out as " + moved + ", not " + wanted + how);
			checked++;
		}
		helper.assertTrue(checked == 7 * 6 * 9 - 5 * 4 * 7 + 18, checked + " blocks in the plan" + how); // the shell plus 18 inside
		// every container with its items, slot for slot; the empty chest stays empty
		for (BlockPos c : CONTAINERS) {
			BlockPos to = plan.at(c.subtract(MIN), min, rotation);
			String now = items(level, to);
			helper.assertTrue(now.equals(contents.get(c)), "the container from " + c + " holds '" + now + "', not '" + contents.get(c) + "'" + how);
		}
		helper.assertTrue(contents.get(EMPTY_CHEST).isEmpty() && items(level, plan.at(EMPTY_CHEST.subtract(MIN), min, rotation)).isEmpty(),
			"the empty chest came out with something in it" + how);
		helper.assertTrue(level.getBlockEntity(plan.at(NAMED_CHEST.subtract(MIN), min, rotation)) instanceof Nameable n && n.hasCustomName()
			&& n.getCustomName().getString().equals("Pantry"), "the named chest lost its name" + how);
		// the double chest is still one double chest: its halves face the same new way and join each other
		BlockPos l = plan.at(CHEST_LEFT.subtract(MIN), min, rotation), r = plan.at(CHEST_RIGHT.subtract(MIN), min, rotation);
		BlockState ls = level.getBlockState(l), rs = level.getBlockState(r);
		Direction chestFacing = rotation.rotate(Direction.SOUTH);
		helper.assertTrue(ls.getValue(ChestBlock.TYPE) == ChestType.LEFT && rs.getValue(ChestBlock.TYPE) == ChestType.RIGHT
			&& ls.getValue(ChestBlock.FACING) == chestFacing && rs.getValue(ChestBlock.FACING) == chestFacing, "double chest halves " + ls + " / " + rs + how);
		helper.assertTrue(l.relative(ChestBlock.getConnectedDirection(ls)).equals(r) && r.relative(ChestBlock.getConnectedDirection(rs)).equals(l),
			"the double chest's halves don't join each other" + how);
		Container both = ChestBlock.getContainer((ChestBlock) Blocks.CHEST, ls, level, l, true);
		helper.assertTrue(both != null && both.getContainerSize() == 54, "the double chest opens as " + (both == null ? "nothing" : both.getContainerSize() + " slots") + how);
		// things that point
		helper.assertTrue(level.getBlockState(plan.at(FURNACE.subtract(MIN), min, rotation)).getValue(FurnaceBlock.FACING) == rotation.rotate(Direction.WEST),
			"furnace facing" + how);
		helper.assertTrue(level.getBlockState(plan.at(SHULKER.subtract(MIN), min, rotation)).getValue(ShulkerBoxBlock.FACING) == rotation.rotate(Direction.EAST),
			"shulker box facing" + how);
		helper.assertTrue(level.getBlockState(plan.at(BARREL.subtract(MIN), min, rotation)).getValue(BarrelBlock.FACING) == rotation.rotate(Direction.NORTH),
			"barrel facing" + how);
		BlockPos door = plan.at(DOOR.subtract(MIN), min, rotation);
		for (BlockPos half : List.of(door, door.above())) {
			BlockState d = level.getBlockState(half);
			helper.assertTrue(d.is(Blocks.SPRUCE_DOOR) && d.getValue(DoorBlock.FACING) == rotation.rotate(Direction.NORTH)
				&& d.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT && d.getValue(DoorBlock.OPEN), "door half " + d + how);
		}
		helper.assertTrue(level.getBlockState(door).getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
			&& level.getBlockState(door.above()).getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER, "the door's halves swapped" + how);
		BlockState trap = level.getBlockState(plan.at(TRAPDOOR.subtract(MIN), min, rotation));
		helper.assertTrue(trap.getValue(TrapDoorBlock.FACING) == rotation.rotate(Direction.EAST) && trap.getValue(TrapDoorBlock.HALF) == Half.TOP
			&& trap.getValue(TrapDoorBlock.OPEN), "trapdoor " + trap + how);
		BlockState stairs = level.getBlockState(plan.at(STAIRS.subtract(MIN), min, rotation));
		helper.assertTrue(stairs.getValue(StairBlock.FACING) == rotation.rotate(Direction.WEST) && stairs.getValue(StairBlock.HALF) == Half.TOP,
			"stairs " + stairs + how);
		Direction.Axis axis = level.getBlockState(plan.at(LOG.subtract(MIN), min, rotation)).getValue(RotatedPillarBlock.AXIS);
		helper.assertTrue(axis == (quarter(rotation) ? Direction.Axis.Z : Direction.Axis.X), "a sideways log lies along " + axis + how);
		BlockPos head = plan.at(BED_HEAD.subtract(MIN), min, rotation), foot = plan.at(BED_FOOT.subtract(MIN), min, rotation);
		Direction bedFacing = rotation.rotate(Direction.SOUTH);
		helper.assertTrue(level.getBlockState(head).getValue(BedBlock.PART) == BedPart.HEAD && level.getBlockState(foot).getValue(BedBlock.PART) == BedPart.FOOT
			&& level.getBlockState(foot).getValue(BedBlock.FACING) == bedFacing && foot.relative(bedFacing).equals(head), "bed halves" + how);
		BlockState lever = level.getBlockState(plan.at(LEVER.subtract(MIN), min, rotation));
		helper.assertTrue(lever.getValue(LeverBlock.FACING) == rotation.rotate(Direction.SOUTH) && lever.getValue(LeverBlock.FACE) == AttachFace.WALL, "lever " + lever + how);
		helper.assertTrue(level.getBlockState(plan.at(TORCH.subtract(MIN), min, rotation)).getValue(WallTorchBlock.FACING) == rotation.rotate(Direction.SOUTH),
			"wall torch facing" + how);
		helper.assertTrue(level.getBlockState(plan.at(SIGN.subtract(MIN), min, rotation)).getValue(StandingSignBlock.ROTATION) == rotation.rotate(2, 16),
			"standing sign turned" + how);
		helper.assertTrue(level.getBlockState(plan.at(WALL_SIGN.subtract(MIN), min, rotation)).getValue(WallSignBlock.FACING) == rotation.rotate(Direction.EAST),
			"wall sign facing" + how);
		helper.assertTrue(level.getBlockState(plan.at(WALL_BANNER.subtract(MIN), min, rotation)).getValue(WallBannerBlock.FACING) == rotation.rotate(Direction.EAST),
			"wall banner facing" + how);
		// block data: both sides of a sign, waxed; the wall sign; the banner's patterns in order; the lectern's book
		helper.assertTrue(level.getBlockEntity(plan.at(SIGN.subtract(MIN), min, rotation)) instanceof SignBlockEntity s
			&& s.getFrontText().getMessage(0, false).getString().equals("Front line") && s.getFrontText().getMessage(3, false).getString().equals("Bottom")
			&& s.getBackText().getMessage(1, false).getString().equals("Back line") && s.isWaxed(), "the sign's front, back or wax didn't come along" + how);
		helper.assertTrue(level.getBlockEntity(plan.at(WALL_SIGN.subtract(MIN), min, rotation)) instanceof SignBlockEntity ws
			&& ws.getFrontText().getMessage(1, false).getString().equals("Wall"), "the wall sign's text didn't come along" + how);
		helper.assertTrue(level.getBlockEntity(plan.at(WALL_BANNER.subtract(MIN), min, rotation)) instanceof BannerBlockEntity banner
			&& banner.getPatterns().layers().size() == 3
			&& banner.getPatterns().layers().get(1).pattern().unwrapKey().map(k -> k.location().getPath()).orElse("").equals("creeper"),
			"the banner's patterns didn't come along in order" + how);
		BlockPos lectern = plan.at(LECTERN.subtract(MIN), min, rotation);
		helper.assertTrue(level.getBlockState(lectern).getValue(LecternBlock.HAS_BOOK) && level.getBlockEntity(lectern) instanceof LecternBlockEntity lb
			&& lb.getBook().is(Items.WRITABLE_BOOK), "the lectern's book didn't come along" + how);
		// entities: the frame (item and its turn), the two-wide painting, the stand in full gear, turned with the room
		AABB box = AABB.of(turned);
		List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, box);
		helper.assertTrue(frames.size() == 1, frames.size() + " item frames, not 1" + how);
		ItemFrame frame = frames.get(0);
		helper.assertTrue(frame.getItem().is(Items.COMPASS) && frame.getRotation() == 3, "the frame holds " + frame.getItem() + " turned " + frame.getRotation() + how);
		helper.assertTrue(frame.getDirection() == rotation.rotate(Direction.EAST) && frame.survives(), "the frame faces " + frame.getDirection() + how);
		List<Painting> paintings = level.getEntitiesOfClass(Painting.class, box);
		helper.assertTrue(paintings.size() == 1, paintings.size() + " paintings, not 1" + how);
		Painting painting = paintings.get(0);
		helper.assertTrue(painting.getVariant().is(PaintingVariants.POOL), "the painting is " + painting.getVariant() + how);
		helper.assertTrue(painting.getDirection() == rotation.rotate(Direction.NORTH) && painting.survives(),
			"the painting faces " + painting.getDirection() + ", hangs: " + painting.survives() + how);
		List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, box);
		helper.assertTrue(stands.size() == 1, stands.size() + " armor stands, not 1" + how);
		ArmorStand stand = stands.get(0);
		helper.assertTrue(gear(stand).equals(gear(original)), "the stand wears " + gear(stand) + ", not " + gear(original) + how);
		float turnedYaw = STAND_YAW + switch (rotation) {
			case NONE -> 0;
			case CLOCKWISE_90 -> 90;
			case CLOCKWISE_180 -> 180;
			case COUNTERCLOCKWISE_90 -> 270;
		};
		helper.assertTrue(Math.abs(Mth.wrapDegrees(stand.getYRot() - turnedYaw)) < 0.01f, "the stand looks at " + stand.getYRot() + ", not " + turnedYaw + how);
		helper.assertTrue(stand.blockPosition().equals(plan.at(STAND.subtract(MIN), min, rotation)), "the stand stands at " + stand.blockPosition() + how);
		helper.assertTrue(!stand.getUUID().equals(original.getUUID()) && original.isAlive(), "the rebuilt stand is the original one" + how);
		helper.assertTrue(plan.front(rotation) == rotation.rotate(Direction.NORTH), "front " + plan.front(rotation) + how);
	}

	private static boolean quarter(Rotation rotation) {
		return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
	}

	/** The plan through a real write to bytes and back, as a save would keep it. */
	private static MovePlan throughDisk(GameTestHelper helper, MovePlan plan) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(plan.save(), out);
			CompoundTag read = NbtIo.readCompressed(new ByteArrayInputStream(out.toByteArray()), NbtAccounter.unlimitedHeap());
			helper.assertTrue(read.equals(plan.save()), "a plan read back from disk isn't the plan written");
			MovePlan loaded = MovePlan.load(read).orElseThrow(() -> new AssertionError("a plan read back from disk didn't load"));
			helper.assertTrue(loaded.save().equals(plan.save()), "a loaded plan saves differently from the plan it was");
			return loaded;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static ArmorStand theStand(GameTestHelper helper, BoundingBox box) {
		List<ArmorStand> stands = helper.getLevel().getEntitiesOfClass(ArmorStand.class, AABB.of(box));
		helper.assertTrue(stands.size() == 1, "setup: " + stands.size() + " armor stands in the room");
		return stands.get(0);
	}

	private static void clearArea(GameTestHelper helper) {
		for (Entity e : helper.getLevel().getEntitiesOfClass(Entity.class, helper.getBounds().inflate(4), e -> !(e instanceof Player))) {
			e.discard();
		}
	}

	/** The room captured, written to disk and read back, put up 12 blocks away unturned: every block, item and thing. */
	//$ gametest_batch AREA '"qaSnapshotRebuild1109b"'
	@GameTest(template = AREA, batch = "qaSnapshotRebuild1109b")
	public void qaARoomRebuiltElsewhereKeepsEveryContainerSignBannerAndThing(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BoundingBox box = room(helper);
		helper.runAfterDelay(2, () -> {
			Map<BlockPos, String> contents = contents(helper);
			helper.assertTrue(contents.get(CHEST_LEFT).contains("apple") && contents.get(FURNACE).contains("coal") && contents.get(SHULKER).contains("ender_pearl"),
				"setup: the containers hold " + contents);
			MovePlan plan = throughDisk(helper, MovePlan.capture(level, box, Direction.NORTH, null).orElseThrow());
			helper.assertTrue(plan.size().equals(new Vec3i(7, 6, 9)) && plan.front() == Direction.NORTH, "size " + plan.size() + ", front " + plan.front());
			helper.assertTrue(plan.things().size() == 3, plan.things().size() + " entities in the plan, not the frame, the painting and the stand");
			helper.assertTrue(plan.beds().equals(List.of(BED_HEAD.subtract(MIN))), "the beds are " + plan.beds());
			checkPutUp(helper, plan, new BlockPos(14, 2, 2), Rotation.NONE, contents, theStand(helper, box));
			helper.assertTrue(contents(helper).equals(contents), "capturing or putting up changed the room's own containers: " + contents(helper));
			clearArea(helper);
			helper.succeed();
		});
	}

	/** The non-square room turned a quarter, a half and three quarters: everything turns, pairs and points the new way. */
	//$ gametest_batch AREA '"qaSnapshotTurns1109b"'
	@GameTest(template = AREA, batch = "qaSnapshotTurns1109b")
	public void qaATurnedRoomKeepsDoubleChestsHingesBedsAndEverythingThatPoints(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BoundingBox box = room(helper);
		helper.runAfterDelay(2, () -> {
			Map<BlockPos, String> contents = contents(helper);
			MovePlan plan = throughDisk(helper, MovePlan.capture(level, box, Direction.NORTH, null).orElseThrow());
			ArmorStand original = theStand(helper, box);
			checkPutUp(helper, plan, new BlockPos(14, 2, 14), Rotation.CLOCKWISE_90, contents, original);
			checkPutUp(helper, plan, new BlockPos(2, 2, 16), Rotation.CLOCKWISE_180, contents, original);
			checkPutUp(helper, plan, new BlockPos(20, 2, 22), Rotation.COUNTERCLOCKWISE_90, contents, original);
			clearArea(helper);
			helper.succeed();
		});
	}

	/** Everything that can be compared in the box: each block's state and data, and each entity's data, by UUID. */
	private static Map<String, Object> snapshot(GameTestHelper helper, BoundingBox box) {
		ServerLevel level = helper.getLevel();
		Map<String, Object> out = new HashMap<>();
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockEntity be = level.getBlockEntity(pos);
			out.put(pos.toShortString(), level.getBlockState(pos));
			if (be != null) {
				out.put(pos.toShortString() + " data", be.saveWithoutMetadata(level.registryAccess()));
			}
		}
		for (Entity e : level.getEntitiesOfClass(Entity.class, AABB.of(box).inflate(2), e -> !(e instanceof Player))) {
			out.put(e.getUUID().toString(), e.saveWithoutId(new CompoundTag()));
		}
		return out;
	}

	/**
	 * Capturing is only a look: every block, its data and every entity in and around the room are the same after two
	 * captures, nothing is removed or dropped, the two captures are the same plan, and changing a placed copy's data
	 * doesn't change the plan.
	 */
	//$ gametest_batch AREA '"qaSnapshotLooksOnly1109b"'
	@GameTest(template = AREA, batch = "qaSnapshotLooksOnly1109b")
	public void qaCapturingARoomChangesNothingInIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BoundingBox box = room(helper);
		helper.runAfterDelay(2, () -> {
			Map<String, Object> before = snapshot(helper, box);
			int entities = level.getEntitiesOfClass(Entity.class, helper.getBounds(), e -> !(e instanceof Player)).size();
			MovePlan first = MovePlan.capture(level, box, Direction.NORTH, null).orElseThrow();
			MovePlan second = MovePlan.capture(level, box, Direction.NORTH, null).orElseThrow();
			Map<String, Object> after = snapshot(helper, box);
			List<String> changed = new ArrayList<>();
			for (String key : before.keySet()) {
				if (!before.get(key).equals(after.get(key))) {
					changed.add(key);
				}
			}
			helper.assertTrue(changed.isEmpty() && before.size() == after.size(), "capturing changed " + changed + " (" + before.size() + " -> " + after.size() + ")");
			int now = level.getEntitiesOfClass(Entity.class, helper.getBounds(), e -> !(e instanceof Player)).size();
			helper.assertTrue(now == entities && entities == 3, "capturing left " + now + " entities, there were " + entities);
			helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, helper.getBounds()).isEmpty(), "capturing dropped items");
			helper.assertTrue(first.save().equals(second.save()), "two captures of the same room are different plans");
			// a placed copy is a copy: emptying it leaves the plan's chest full
			BlockPos rel = CHEST_LEFT.subtract(MIN);
			for (MovePlan.Placed p : first.placed(helper.absolutePos(new BlockPos(14, 2, 2)), Rotation.NONE)) {
				if (p.data() != null) {
					p.data().remove("Items");
				}
			}
			String chestData = first.blocks().stream().filter(b -> b.rel().equals(rel)).findFirst().orElseThrow().data().toString();
			helper.assertTrue(chestData.contains("apple"), "emptying a placed copy emptied the plan's chest: " + chestData);
			clearArea(helper);
			helper.succeed();
		});
	}

	/**
	 * Only the building's own things and people: a cow, a dropped item, a minecart and a villager inside it are not its
	 * things; a villager far away whose bed is in it and one whose workstation is in it are its residents, the villager
	 * standing inside whose home is elsewhere isn't, and nobody with a bed in another building.
	 */
	//$ gametest_batch AREA '"qaSnapshotPeople1109b"'
	@GameTest(template = AREA, batch = "qaSnapshotPeople1109b")
	public void qaOnlyTheBuildingsOwnThingsAndResidentsAreInItsPlan(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BoundingBox box = room(helper);
		Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(6, 3, 6));
		cow.setPersistenceRequired();
		BlockPos itemAt = helper.absolutePos(new BlockPos(5, 3, 4));
		ItemEntity dropped = new ItemEntity(level, itemAt.getX() + 0.5, itemAt.getY(), itemAt.getZ() + 0.5, new ItemStack(Items.EMERALD));
		dropped.setNeverPickUp();
		dropped.setUnlimitedLifetime();
		level.addFreshEntity(dropped);
		BlockPos cartAt = helper.absolutePos(new BlockPos(6, 3, 5));
		Minecart cart = new Minecart(level, cartAt.getX() + 0.5, cartAt.getY(), cartAt.getZ() + 0.5);
		level.addFreshEntity(cart);
		Villager visitor = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(5, 3, 6));
		visitor.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(25, 2, 25))));
		Villager sleeper = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(24, 2, 24));
		sleeper.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(BED_HEAD)));
		Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(20, 2, 4));
		worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(LECTERN)));
		Villager neighbour = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(20, 2, 20));
		neighbour.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(20, 2, 22))));
		neighbour.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(MAX.getX() + 1, 3, 5))));
		helper.runAfterDelay(2, () -> {
			MovePlan plan = MovePlan.capture(level, box, Direction.NORTH, null).orElseThrow();
			Set<String> things = plan.things().stream().map(t -> t.data().getString("id")).collect(Collectors.toSet());
			helper.assertTrue(plan.things().size() == 3 && things.equals(Set.of("minecraft:item_frame", "minecraft:painting", "minecraft:armor_stand")),
				"the plan's things are " + plan.things().stream().map(t -> t.data().getString("id")).toList());
			helper.assertTrue(plan.things().stream().noneMatch(t -> t.data().hasUUID("UUID")), "a thing kept its UUID");
			Set<UUID> residents = Set.copyOf(plan.residents());
			helper.assertTrue(residents.equals(Set.of(sleeper.getUUID(), worker.getUUID())) && plan.residents().size() == 2,
				"residents " + plan.residents() + ": sleeper " + sleeper.getUUID() + ", worker " + worker.getUUID() + ", visitor " + visitor.getUUID()
					+ ", neighbour " + neighbour.getUUID());
			helper.assertTrue(plan.workstations().equals(List.of(LECTERN.subtract(MIN))), "workstations " + plan.workstations());
			helper.assertTrue(plan.beds().equals(List.of(BED_HEAD.subtract(MIN))), "beds " + plan.beds());
			MovePlan loaded = MovePlan.load(plan.save()).orElseThrow();
			helper.assertTrue(loaded.residents().equals(plan.residents()) && loaded.workstations().equals(plan.workstations())
				&& loaded.beds().equals(plan.beds()), "a saved plan lost its residents, beds or workstations");
			helper.assertTrue(cow.isAlive() && dropped.isAlive() && cart.isAlive() && visitor.isAlive(), "capturing removed something living or loose");
			clearArea(helper);
			helper.succeed();
		});
	}
}
