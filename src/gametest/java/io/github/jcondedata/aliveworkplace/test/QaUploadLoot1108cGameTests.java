package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * QA (2026-11-08): can a non-operator player get free items by uploading a blueprint file at a Blueprint Table and
 * having a Builder build it? The rule (docs/wiki/builder.md; B429, B434, B449, B486, B1044): "Builders never copy what
 * is inside containers, nor what a block holds in its look"; item frames, glow item frames and armor stands are put up
 * empty. Each test hand-makes the file as a player could (a scan of a block filled through the game's own API, or
 * extra data written into the file), uploads it through {@link BlueprintImporter#importBytes}, has a Builder build
 * it, then opens or unpacks what was built as a survival player would. Each has a control: the very same data the file
 * carries, loaded straight into a placed block, does hold the item (so the check would see one).
 */
public class QaUploadLoot1108cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos U_BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SRC = new BlockPos(22, 2, 22);
	private static final BlockPos ORIGIN = new BlockPos(10, 2, 10);
	private static final BlockPos CONTROL = new BlockPos(22, 2, 6);
	private static final ResourceKey<LootTable> END_CITY = ResourceKey.create(Registries.LOOT_TABLE,
		ResourceLocation.withDefaultNamespace("chests/end_city_treasure"));
	private static final ResourceKey<LootTable> DESERT_PYRAMID = ResourceKey.create(Registries.LOOT_TABLE,
		ResourceLocation.withDefaultNamespace("archaeology/desert_pyramid"));

	/** True if a survival player opening {@code be} (or brushing it) would get an item out of it. */
	private interface Holds {
		boolean test(BlockEntity be, ServerPlayer player);
	}

	private static final Holds CONTAINER = (be, player) -> {
		if (be instanceof RandomizableContainer loot) {
			loot.unpackLootTable(player); // what opening it does
		}
		return be instanceof Container c && loot(c);
	};

	/**
	 * Any item in {@code c} but the finished build's own blueprint, which the builder hands back to the nearest store when
	 * it is done (the player's own blueprint; in these tests the built container is the store nearest the bench).
	 */
	private static boolean loot(Container c) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (!c.getItem(i).isEmpty() && !c.getItem(i).is(ModItems.BLUEPRINT)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// 1. containers with a loot table and no items

	//$ gametest_ticks_batch HUGE '2400' '"qaUpLootChest"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUpLootChest")
	public void aChestWithALootTableInAnUploadedFileIsBuiltEmpty(GameTestHelper helper) {
		contents(helper, "chest", Blocks.CHEST, be -> ((RandomizableContainer) be).setLootTable(END_CITY, 1L), null, CONTAINER, false);
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaUpLootBarrel"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUpLootBarrel")
	public void aBarrelWithALootTableInAnUploadedFileIsBuiltEmpty(GameTestHelper helper) {
		contents(helper, "barrel", Blocks.BARREL, be -> ((RandomizableContainer) be).setLootTable(END_CITY, 1L), null, CONTAINER, false);
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaUpLootShulker"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUpLootShulker")
	public void aShulkerBoxWithALootTableInAnUploadedFileIsBuiltEmpty(GameTestHelper helper) {
		contents(helper, "shulker", Blocks.SHULKER_BOX, be -> ((RandomizableContainer) be).setLootTable(END_CITY, 1L), null, CONTAINER, false);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// 2. suspicious sand and gravel

	private static final Holds BRUSHED = (be, player) -> {
		if (!(be instanceof BrushableBlockEntity brush)) {
			return false;
		}
		brush.unpackLootTable(player); // what brushing it does first
		return !brush.getItem().isEmpty();
	};

	//$ gametest_ticks_batch HUGE '2400' '"qaUpSusSandItem"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUpSusSandItem")
	public void suspiciousSandHoldingANetheriteIngotInAnUploadedFileHoldsNothingBuilt(GameTestHelper helper) {
		contents(helper, "sus_sand_item", Blocks.SUSPICIOUS_SAND, be -> { }, (nbt, level) ->
			nbt.put("item", new ItemStack(Items.NETHERITE_INGOT).save(level.registryAccess())), BRUSHED, true);
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaUpSusGravelLoot"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUpSusGravelLoot")
	public void suspiciousGravelWithALootTableInAnUploadedFileHoldsNothingBuilt(GameTestHelper helper) {
		contents(helper, "sus_gravel_loot", Blocks.SUSPICIOUS_GRAVEL, be -> ((BrushableBlockEntity) be).setLootTable(DESERT_PYRAMID, 1L), null,
			BRUSHED, true);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// 4. blocks that hold an item in their look

	//$ gametest_ticks_batch HUGE '2400' '"qaUpPot"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUpPot")
	public void aDecoratedPotHoldingADiamondBlockInAnUploadedFileIsBuiltEmpty(GameTestHelper helper) {
		contents(helper, "pot", Blocks.DECORATED_POT, be -> ((DecoratedPotBlockEntity) be).setTheItem(new ItemStack(Items.DIAMOND_BLOCK, 64)), null,
			(be, player) -> {
				if (!(be instanceof DecoratedPotBlockEntity pot)) {
					return false;
				}
				pot.unpackLootTable(player);
				return loot(pot);
			}, false);
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaUpPotLoot"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUpPotLoot")
	public void aDecoratedPotWithALootTableInAnUploadedFileIsBuiltEmpty(GameTestHelper helper) {
		contents(helper, "pot_loot", Blocks.DECORATED_POT, be -> ((DecoratedPotBlockEntity) be).setLootTable(END_CITY, 1L), null,
			(be, player) -> {
				if (!(be instanceof DecoratedPotBlockEntity pot)) {
					return false;
				}
				pot.unpackLootTable(player);
				return loot(pot);
			}, false);
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaUpCampfire"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUpCampfire")
	public void aCampfireCookingBeefInAnUploadedFileIsBuiltWithNothingOnIt(GameTestHelper helper) {
		contents(helper, "campfire", Blocks.CAMPFIRE, be -> {
			for (int i = 0; i < 4; i++) {
				((CampfireBlockEntity) be).placeFood(null, new ItemStack(Items.BEEF), 600);
			}
		}, null, (be, player) -> be instanceof CampfireBlockEntity fire && fire.getItems().stream().anyMatch(s -> !s.isEmpty()), false);
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaUpJukebox"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUpJukebox")
	public void aJukeboxWithADiscInAnUploadedFileIsBuiltEmpty(GameTestHelper helper) {
		contents(helper, "jukebox", Blocks.JUKEBOX, be -> ((JukeboxBlockEntity) be).setSongItemWithoutPlaying(new ItemStack(Items.MUSIC_DISC_PIGSTEP)),
			null, (be, player) -> be instanceof JukeboxBlockEntity box && !box.getTheItem().isEmpty(), false);
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaUpBookshelf"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUpBookshelf")
	public void aChiseledBookshelfWithBooksInAnUploadedFileIsBuiltEmpty(GameTestHelper helper) {
		contents(helper, "bookshelf", Blocks.CHISELED_BOOKSHELF, be -> {
			Container shelf = (Container) be;
			for (int i = 0; i < 6; i++) {
				shelf.setItem(i, new ItemStack(Items.ENCHANTED_BOOK));
			}
		}, null, CONTAINER, false);
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaUpBrewing"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUpBrewing")
	public void aBrewingStandWithPotionsInAnUploadedFileIsBuiltEmpty(GameTestHelper helper) {
		contents(helper, "brewing", Blocks.BREWING_STAND, be -> {
			Container stand = (Container) be;
			stand.setItem(0, new ItemStack(Items.POTION));
			stand.setItem(3, new ItemStack(Items.NETHER_WART, 64));
			stand.setItem(4, new ItemStack(Items.BLAZE_POWDER, 64));
		}, null, CONTAINER, false);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// 3. entities: frames and armor stands

	//$ gametest_ticks_batch HUGE '2400' '"qaUpFrames"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUpFrames")
	public void itemFramesHoldingDiamondBlocksInAnUploadedFileArePutUpEmpty(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, Blocks.STONE);
		helper.setBlock(SRC.east(), Blocks.STONE);
		BlockPos front = helper.absolutePos(SRC.south());
		ItemFrame frame = new ItemFrame(level, front, Direction.SOUTH);
		frame.setItem(new ItemStack(Items.DIAMOND_BLOCK));
		level.addFreshEntity(frame);
		ItemFrame glow = new GlowItemFrame(level, front.east(), Direction.SOUTH);
		glow.setItem(new ItemStack(Items.NETHERITE_BLOCK));
		level.addFreshEntity(glow);
		helper.assertTrue(frame.isAlive() && glow.isAlive() && !frame.getItem().isEmpty() && !glow.getItem().isEmpty(), "(setup) the frames hold nothing");
		CompoundTag[] file = new CompoundTag[1];
		ResourceLocation id = upload(helper, "frames", SRC, new Vec3i(2, 1, 2), true, saved -> file[0] = saved.copy(), List.of(frame, glow));
		// control: the frame data the file carries does hold its item (so a copy of it would show)
		ListTag entities = file[0].getList("entities", Tag.TAG_COMPOUND);
		helper.assertTrue(entities.size() == 2, "(setup) the file holds " + entities.size() + " entities, not the 2 frames");
		for (int i = 0; i < entities.size(); i++) {
			CompoundTag nbt = entities.getCompound(i).getCompound("nbt");
			helper.assertTrue(nbt.contains("Item", Tag.TAG_COMPOUND), "(setup) a frame in the file holds no item: " + nbt.getAllKeys());
		}
		BuildSite site = buildFree(helper, id);
		AABB around = new AABB(helper.absolutePos(ORIGIN)).inflate(4);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<ItemFrame> built = level.getEntitiesOfClass(ItemFrame.class, around, Entity::isAlive);
			helper.assertTrue(built.size() == 2, "the builder put up " + built.size() + " frames, not 2");
			for (ItemFrame f : built) {
				helper.assertTrue(f.getItem().isEmpty(), "a " + f.getType().getDescriptionId() + " from an uploaded file holds " + f.getItem());
			}
			helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, around, Entity::isAlive).isEmpty(), "an item lies about the built frames");
		});
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaUpStand"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUpStand")
	public void anArmorStandInNetheriteInAnUploadedFileIsPutUpBare(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, Blocks.OAK_PLANKS);
		BlockPos at = helper.absolutePos(SRC.above());
		ArmorStand stand = new ArmorStand(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
		stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
		stand.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
		stand.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
		stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
		stand.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		stand.setShowArms(true);
		level.addFreshEntity(stand);
		CompoundTag[] file = new CompoundTag[1];
		ResourceLocation id = upload(helper, "stand", SRC, new Vec3i(1, 3, 1), true, saved -> file[0] = saved.copy(), List.of(stand));
		ListTag entities = file[0].getList("entities", Tag.TAG_COMPOUND);
		helper.assertTrue(entities.size() == 1, "(setup) the file holds " + entities.size() + " entities, not the stand");
		// control: the stand data the file carries, loaded into a fresh stand, wears the armor
		CompoundTag standNbt = entities.getCompound(0).getCompound("nbt");
		ArmorStand control = EntityType.ARMOR_STAND.create(level);
		control.load(standNbt);
		helper.assertTrue(control.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE)
			&& control.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.NETHERITE_SWORD), "(setup) the file's stand data wears nothing, so the test can't tell");
		control.discard();
		BuildSite site = buildFree(helper, id);
		AABB around = new AABB(helper.absolutePos(ORIGIN)).inflate(4);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<ArmorStand> built = level.getEntitiesOfClass(ArmorStand.class, around, Entity::isAlive);
			helper.assertTrue(built.size() == 1, "the builder put up " + built.size() + " armor stands, not 1");
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				ItemStack worn = built.get(0).getItemBySlot(slot);
				helper.assertTrue(worn.isEmpty(), "an armor stand from an uploaded file has " + worn + " in " + slot);
			}
			helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, around, Entity::isAlive).isEmpty(), "an item lies about the built stand");
		});
	}

	// ---------------------------------------------------------------------------------------------------------------
	// helpers

	/**
	 * Planks with {@code block} on top, filled by {@code fill} (and its saved data edited by {@code edit}), as a file;
	 * control: that very data loaded into the same block placed at CONTROL holds an item; then a builder builds the file
	 * and the built block must hold none, opened (unpacked) by a survival player. {@code mayDiffer}: the builder may
	 * build something else there (plain sand for suspicious sand), which holds nothing either.
	 */
	private static void contents(GameTestHelper helper, String name, Block block, Consumer<BlockEntity> fill,
		BiConsumer<CompoundTag, ServerLevel> edit, Holds holds, boolean mayDiffer) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivor(helper);
		helper.setBlock(SRC, Blocks.OAK_PLANKS);
		helper.setBlock(SRC.above(), block);
		BlockEntity source = helper.getBlockEntity(SRC.above());
		helper.assertTrue(source != null, "(setup) no block entity in " + block);
		fill.accept(source);
		source.setChanged();
		CompoundTag[] carried = new CompoundTag[1];
		ResourceLocation id = upload(helper, name, SRC, new Vec3i(1, 2, 1), false, saved -> {
			ListTag blocks = saved.getList("blocks", Tag.TAG_COMPOUND);
			for (int i = 0; i < blocks.size(); i++) {
				CompoundTag b = blocks.getCompound(i);
				if (b.contains("nbt", Tag.TAG_COMPOUND)) {
					if (edit != null) {
						edit.accept(b.getCompound("nbt"), level);
					}
					carried[0] = b.getCompound("nbt").copy();
				}
			}
		}, List.of());
		helper.assertTrue(carried[0] != null, "(setup) the file carries no data for the " + block);
		// control: the data the file carries, loaded straight into a placed block, holds an item
		helper.setBlock(CONTROL, Blocks.OAK_PLANKS);
		helper.setBlock(CONTROL.above(), block);
		BlockEntity control = helper.getBlockEntity(CONTROL.above());
		control.loadWithComponents(carried[0], level.registryAccess());
		helper.assertTrue(holds.test(control, player), "(setup) the file's " + name + " data holds nothing even placed straight, so the test can't tell: "
			+ carried[0].getAllKeys());
		if (control instanceof net.minecraft.world.Clearable clear) {
			clear.clearContent();
		}
		helper.setBlock(CONTROL.above(), Blocks.AIR);
		BuildSite site = buildFree(helper, id);
		AABB around = new AABB(helper.absolutePos(ORIGIN)).inflate(4);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			helper.assertTrue(helper.getBlockState(ORIGIN).is(Blocks.OAK_PLANKS), "the file wasn't built: " + helper.getBlockState(ORIGIN));
			BlockEntity built = level.getBlockEntity(helper.absolutePos(ORIGIN.above()));
			if (!mayDiffer) {
				helper.assertTrue(helper.getBlockState(ORIGIN.above()).is(block) && built != null, "the " + block + " wasn't built: " + helper.getBlockState(ORIGIN.above()));
			}
			if (built != null) {
				helper.assertTrue(!holds.test(built, player), "a " + name + " built from an uploaded file holds an item a survival player can take: "
					+ built.saveWithoutMetadata(level.registryAccess()));
			}
			if (helper.getBlockState(ORIGIN.above()).is(Blocks.JUKEBOX)) {
				helper.assertTrue(!helper.getBlockState(ORIGIN.above()).getValue(BlockStateProperties.HAS_RECORD), "the built jukebox shows a disc");
			}
			helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, around, Entity::isAlive).isEmpty(),
				"an item lies about the built " + name + ": " + level.getEntitiesOfClass(ItemEntity.class, around, Entity::isAlive));
		});
	}

	private static ServerPlayer survivor(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		helper.assertTrue(!player.hasPermissions(2), "(setup) the mock player is an operator");
		Leftovers.after(helper, player::discard);
		return player;
	}

	/**
	 * Scans the box at {@code src} as a file (with its entities if asked), hands the saved data to {@code edit}, removes
	 * the source blocks and {@code sourceEntities} and imports it as a player's upload does.
	 */
	private static ResourceLocation upload(GameTestHelper helper, String name, BlockPos src, Vec3i size, boolean withEntities,
		Consumer<CompoundTag> edit, List<? extends Entity> sourceEntities) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(src), size, withEntities, Blocks.STRUCTURE_VOID);
		CompoundTag saved = template.save(new CompoundTag());
		edit.accept(saved);
		byte[] bytes;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(saved, out);
			bytes = out.toByteArray();
		} catch (IOException e) {
			throw new GameTestAssertException("(setup) couldn't write the file: " + e);
		}
		for (Entity e : sourceEntities) {
			if (e instanceof ItemFrame frame) {
				frame.setItem(ItemStack.EMPTY);
			} else if (e instanceof ArmorStand stand) {
				for (EquipmentSlot slot : EquipmentSlot.values()) {
					stand.setItemSlot(slot, ItemStack.EMPTY);
				}
			}
			e.discard();
		}
		for (int y = size.getY() - 1; y >= 0; y--) {
			for (int x = 0; x < size.getX(); x++) {
				for (int z = 0; z < size.getZ(); z++) {
					BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(src.offset(x, y, z)));
					if (be instanceof net.minecraft.world.Clearable clear) {
						clear.clearContent();
					}
					helper.setBlock(src.offset(x, y, z), Blocks.AIR);
				}
			}
		}
		// nothing the source held may lie about either
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(src)).inflate(4))) {
			item.discard();
		}
		try {
			return BlueprintImporter.importBytes(level.getServer(), "uploads/qa_uploadloot",
				"qaupl_" + name + "_" + Long.toHexString(level.getGameTime()) + ".nbt", bytes).id();
		} catch (Exception e) {
			throw new GameTestAssertException("(setup) the upload didn't import: " + e);
		}
	}

	/** A builder at a bench builds the file at ORIGIN, with free materials. */
	private static BuildSite buildFree(GameTestHelper helper, ResourceLocation id) {
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer()));
		helper.setDayTime(2000);
		helper.setBlock(U_BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, U_BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(U_BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		return site;
	}
}
