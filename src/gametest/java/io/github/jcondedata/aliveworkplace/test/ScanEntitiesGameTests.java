package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariants;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * B441: the Scan Tool keeps a scanned room's item frames, paintings and armor stands, empty (no framed item, no armor),
 * and nothing else that moves (no mob, villager, dropped item or loaded cart); a builder puts them up again the way it
 * does a starter blueprint's frames, each paid with its item from the chest, and leaves out the one it has no item for.
 */
public class ScanEntitiesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos SRC = new BlockPos(18, 2, 18);
	private static final BlockPos ORIGIN = new BlockPos(9, 2, 5);
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos FRAME_AT = new BlockPos(1, 2, 2);
	private static final BlockPos PAINTING_AT = new BlockPos(3, 2, 2);
	private static final BlockPos STAND_AT = new BlockPos(2, 1, 1);

	//$ gametest_ticks_batch HUGE '3000' '"b441ScanEntities"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "b441ScanEntities")
	public void b441AScanKeepsFramesPaintingsAndStandsEmptyAndABuilderPutsThemUpPaid(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation id = scanRoom(helper);

		// The blueprint: the three, empty; the cow, the villager, the dropped diamond and the loaded cart left out
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow();
		Set<String> kinds = new TreeSet<>();
		blueprint.entities().forEach(e -> kinds.add(Nbt.getString(e.nbt(), "id")));
		helper.assertTrue(blueprint.entities().size() == 3 && kinds.equals(Set.of("minecraft:item_frame", "minecraft:painting", "minecraft:armor_stand")),
			"the scan kept " + blueprint.entities().size() + " entities: " + kinds);
		helper.assertTrue(blueprint.entities().stream().noneMatch(e -> e.nbt().contains("Item") || e.nbt().contains("ArmorItems") || e.nbt().contains("HandItems")),
			"the scan copied a frame's item or a stand's armor: " + blueprint.entities());
		// The saved file itself holds nothing else either (a villager's or a cart's data never goes into a scan)
		CompoundTag file = level.getServer().getStructureManager().get(id).orElseThrow().save(new CompoundTag());
		ListTag saved = Nbt.getList(file, "entities", Tag.TAG_COMPOUND);
		helper.assertTrue(saved.size() == 3, "the scan's file has " + saved.size() + " entities");
		for (int i = 0; i < saved.size(); i++) {
			CompoundTag nbt = Nbt.getCompound(Nbt.compoundAt(saved, i), "nbt");
			helper.assertTrue(!nbt.contains("Item") && !nbt.contains("ArmorItems") && !nbt.contains("UUID"), "the scan's file copied " + nbt);
		}

		// Built again a quarter turned, from the chest: a frame and a painting there, no armor stand
		BuildSite site = startBuild(helper, id, new ItemStack(Items.STONE_BRICKS, 64), new ItemStack(Items.COBBLESTONE, 64),
			new ItemStack(Items.ITEM_FRAME), new ItemStack(Items.PAINTING));
		Container chest = helper.getBlockEntity(CHEST);
		BlockPos origin = helper.absolutePos(ORIGIN);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status());
			List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, around(origin, FRAME_AT));
			List<Painting> paintings = level.getEntitiesOfClass(Painting.class, around(origin, PAINTING_AT));
			List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, around(origin, STAND_AT));
			helper.assertTrue(frames.size() == 1 && frames.get(0).getItem().isEmpty(), "frames built: " + frames.size()
				+ (frames.isEmpty() ? "" : ", holding " + frames.get(0).getItem()));
			helper.assertTrue(frames.get(0).getDirection() == Direction.EAST, "the turned frame faces " + frames.get(0).getDirection());
			helper.assertTrue(paintings.size() == 1, paintings.size() + " paintings built");
			helper.assertTrue(stands.isEmpty(), "an armor stand went up with none in the chest");
			helper.assertTrue(chest.countItem(Items.ITEM_FRAME) == 0 && chest.countItem(Items.PAINTING) == 0,
				"not paid for: " + chest.countItem(Items.ITEM_FRAME) + " frame(s), " + chest.countItem(Items.PAINTING) + " painting(s) left in the chest");
			helper.assertTrue(level.getEntitiesOfClass(Mob.class, new AABB(origin).inflate(8), e -> !(e instanceof Villager)).isEmpty(),
				"a mob came with the build");
			helper.assertTrue(level.getEntitiesOfClass(MinecartChest.class, new AABB(origin).inflate(8)).isEmpty(), "the cart came with the build");
		});
	}

	/** With an armor stand in the chest, it goes up too, bare, and the stand is taken from the chest. */
	//$ gametest_ticks_batch HUGE '3000' '"b441ScanStand"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "b441ScanStand")
	public void b441AScannedArmorStandIsBuiltBarePaidFromTheChest(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation id = scanRoom(helper);
		BuildSite site = startBuild(helper, id, new ItemStack(Items.STONE_BRICKS, 64), new ItemStack(Items.COBBLESTONE, 64),
			new ItemStack(Items.ITEM_FRAME), new ItemStack(Items.PAINTING), new ItemStack(Items.ARMOR_STAND), new ItemStack(Items.DIAMOND_HELMET));
		Container chest = helper.getBlockEntity(CHEST);
		BlockPos origin = helper.absolutePos(ORIGIN);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status());
			List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, around(origin, STAND_AT));
			helper.assertTrue(stands.size() == 1, stands.size() + " armor stands built");
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				helper.assertTrue(stands.get(0).getItemBySlot(slot).isEmpty(), "the stand came with " + stands.get(0).getItemBySlot(slot));
			}
			helper.assertTrue(chest.countItem(Items.ARMOR_STAND) == 0, "the armor stand wasn't taken from the chest");
			helper.assertTrue(chest.countItem(Items.DIAMOND_HELMET) == 1, "the helmet was taken from the chest");
		});
	}

	/** A box seen from its world spot: the entity at {@code rel} of a room built at {@code origin}, a quarter turned. */
	private static AABB around(BlockPos origin, BlockPos rel) {
		return new AABB(origin.offset(StructureTemplate.transform(rel, Mirror.NONE, Rotation.CLOCKWISE_90, BlockPos.ZERO))).inflate(0.7);
	}

	/**
	 * Builds a room (a floor and a back wall) at {@link #SRC} with a diamond in a frame, a painting and an armor stand in
	 * a diamond helmet on it, and a cow, a villager, a dropped diamond and a chest minecart with a diamond in it, then
	 * scans it with the Scan Tool's own clicks. Returns the blueprint's id.
	 */
	private static ResourceLocation scanRoom(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 4; z++) {
				helper.setBlock(SRC.offset(x, 0, z), Blocks.STONE_BRICKS);
			}
			for (int y = 1; y < 4; y++) {
				helper.setBlock(SRC.offset(x, y, 3), Blocks.COBBLESTONE);
			}
		}
		ItemFrame frame = new ItemFrame(level, helper.absolutePos(SRC.offset(FRAME_AT)), Direction.NORTH);
		frame.setItem(new ItemStack(Items.DIAMOND));
		helper.assertTrue(frame.survives(), "the frame doesn't hang on the back wall");
		level.addFreshEntity(frame);
		Painting painting = new Painting(level, helper.absolutePos(SRC.offset(PAINTING_AT)), Direction.NORTH,
			Lookup.holderOrThrow(Lookup.registry(level.registryAccess(), Registries.PAINTING_VARIANT), PaintingVariants.KEBAB));
		helper.assertTrue(painting.survives(), "the painting doesn't hang on the back wall");
		level.addFreshEntity(painting);
		ArmorStand stand = EntityType.ARMOR_STAND.create(level);
		BlockPos standAt = helper.absolutePos(SRC.offset(STAND_AT));
		stand.moveTo(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5, 0, 0);
		stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		level.addFreshEntity(stand);
		Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, SRC.offset(0, 1, 0));
		cow.setPersistenceRequired();
		Villager resident = helper.spawnWithNoFreeWill(EntityType.VILLAGER, SRC.offset(4, 1, 1));
		ItemEntity dropped = new ItemEntity(level, standAt.getX() + 1.5, standAt.getY(), standAt.getZ() + 0.5, new ItemStack(Items.DIAMOND));
		dropped.setNeverPickUp();
		dropped.setUnlimitedLifetime();
		level.addFreshEntity(dropped);
		MinecartChest cart = EntityType.CHEST_MINECART.create(level);
		BlockPos cartAt = helper.absolutePos(SRC.offset(4, 1, 0));
		cart.moveTo(cartAt.getX() + 0.5, cartAt.getY(), cartAt.getZ() + 0.5, 0, 0);
		cart.setItem(0, new ItemStack(Items.DIAMOND));
		level.addFreshEntity(cart);
		Leftovers.after(helper, () -> {
			for (net.minecraft.world.entity.Entity e : List.of(frame, painting, stand, dropped, cart, cow, resident)) {
				e.discard();
			}
		});

		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
		QaStageThreeGameTests.click(helper, player, SRC);
		QaStageThreeGameTests.click(helper, player, SRC.offset(4, 3, 3));
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.saved"), "the scan said " + QaStageThreeGameTests.last(seen));
		for (ItemStack s : player.getInventory().items) {
			if (s.is(ModItems.BLUEPRINT)) {
				return BlueprintItem.data(s).orElseThrow().structure();
			}
		}
		throw new GameTestAssertException("the scan gave no blueprint");
	}

	/** A builder at the bench starts the scan a quarter turned at {@link #ORIGIN}, from a chest of {@code items}. */
	private static BuildSite startBuild(GameTestHelper helper, ResourceLocation id, ItemStack... items) {
		ServerLevel level = helper.getLevel();
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < items.length; i++) {
			chest.setItem(i, items[i]);
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.CLOCKWISE_90, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException("the builder didn't take the scanned blueprint " + id);
		}
		return site;
	}
}
