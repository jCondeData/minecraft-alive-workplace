package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * QA (lane b, 2026-11-08), from the promise in BlockEntityData and the wiki's builder page: a block built from an
 * uploaded file never hands out free items (chest contents, a pot's item, a jukebox's disc...). A decorated pot's four
 * sides ("sherds") are saved as item ids, and the game takes any item there; a pot broken with a pickaxe shatters and
 * drops those four items. A player's pot is made from bricks and real pottery sherds, so it drops only those back.
 */
public class QaPotSherds1108bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SRC = new BlockPos(22, 2, 22);
	private static final BlockPos ORIGIN = new BlockPos(10, 2, 10);
	private static final BlockPos CONTROL = new BlockPos(22, 2, 6);

	//$ gametest_ticks_batch HUGE '2400' '"qaPotSherds1108b"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaPotSherds1108b")
	public void aPotFromAnUploadedFileShattersIntoNoDiamondBlocks(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		helper.assertTrue(!player.hasPermissions(2), "(setup) the mock player is an operator");
		Leftovers.after(helper, player::discard);

		// The file: planks with a decorated pot on top, written by the game, then the pot's sides rewritten as diamond blocks
		helper.setBlock(SRC, Blocks.OAK_PLANKS);
		helper.setBlock(SRC.above(), Blocks.DECORATED_POT);
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(1, 2, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(SRC.above(), Blocks.AIR);
		helper.setBlock(SRC, Blocks.AIR);
		CompoundTag file = template.save(new CompoundTag());
		ListTag blocks = file.getList("blocks", Tag.TAG_COMPOUND);
		CompoundTag potData = null;
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag b = blocks.getCompound(i);
			if (b.contains("nbt", Tag.TAG_COMPOUND)) {
				ListTag sherds = new ListTag();
				for (int s = 0; s < 4; s++) {
					sherds.add(StringTag.valueOf("minecraft:diamond_block"));
				}
				CompoundTag nbt = b.getCompound("nbt");
				nbt.put("sherds", sherds);
				b.put("nbt", nbt);
				potData = nbt.copy();
			}
		}
		helper.assertTrue(potData != null, "(setup) the file carries no data for the pot");

		// Control: a pot loaded with exactly this data, broken with a pickaxe, drops the four diamond blocks
		helper.setBlock(CONTROL, Blocks.OAK_PLANKS);
		helper.setBlock(CONTROL.above(), Blocks.DECORATED_POT);
		helper.getBlockEntity(CONTROL.above()).loadWithComponents(potData, level.registryAccess());
		int control = breakAndCount(helper, player, CONTROL.above());
		helper.assertTrue(control == 4, "(setup) a pot with this saved data dropped " + control + " diamond blocks when broken with a pickaxe, not 4,"
			+ " so the test can't tell");

		byte[] bytes;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(file, out);
			bytes = out.toByteArray();
		} catch (IOException e) {
			throw new GameTestAssertException("(setup) couldn't write the file: " + e);
		}
		ResourceLocation id;
		try {
			// the import a player's upload at the Table goes through (TableServer: folder "uploads/<player>")
			id = BlueprintImporter.importBytes(level.getServer(), "uploads/qa_potter",
				"diamond_pot_" + Long.toHexString(level.getGameTime()) + ".nbt", bytes).id();
		} catch (Exception e) {
			throw new GameTestAssertException("(setup) the upload didn't import: " + e);
		}

		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer()));
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
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
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			helper.assertTrue(helper.getBlockState(ORIGIN.above()).is(Blocks.DECORATED_POT), "the pot wasn't built: " + helper.getBlockState(ORIGIN.above()));
			int built = breakAndCount(helper, player, ORIGIN.above());
			helper.assertTrue(helper.getBlockState(ORIGIN.above()).isAir(), "(setup) the built pot wasn't broken: " + helper.getBlockState(ORIGIN.above()));
			int bricks = 0;
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(ORIGIN.above())).inflate(3), Entity::isAlive)) {
				bricks += item.getItem().is(Items.BRICK) ? item.getItem().getCount() : 0;
			}
			helper.assertTrue(bricks == 4, "(setup) the built pot didn't shatter into its 4 bricks (" + bricks + "), so the break went another way");
			helper.assertTrue(built == 0, "a decorated pot a builder built from an uploaded file shattered into " + built + " diamond blocks when a"
				+ " survival player broke it with a pickaxe (the file named them as the pot's sides); a pot from a file should hand out"
				+ " no free items");
		});
	}

	/** A survival player breaks the block at {@code rel} with a diamond pickaxe; the diamond blocks dropped round it (removed after). */
	private static int breakAndCount(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(rel);
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
		player.gameMode.destroyBlock(at);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		int count = 0;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3), Entity::isAlive)) {
			if (item.getItem().is(Items.DIAMOND_BLOCK)) {
				count += item.getItem().getCount();
				item.discard();
			}
		}
		return count;
	}
}
