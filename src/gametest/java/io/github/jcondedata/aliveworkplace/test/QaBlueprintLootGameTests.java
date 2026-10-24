package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/**
 * QA (lane b, 2026-10-24), from the builder's promise (CLAUDE.md, "Builders never copy container contents"; builder.md:
 * a blueprint's block data carries looks, never free items): a scanned build whose blocks hold items in every way vanilla
 * 1.21.1 has besides chests (a lectern's book, a jukebox's disc, a chiseled bookshelf's books, a decorated pot's
 * diamonds, a campfire's beef) and a named chest of gold is built by a real builder, and every block comes out empty, while
 * a sign keeps its words and the chest its name.
 */
public class QaBlueprintLootGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SRC = new BlockPos(20, 2, 20);
	private static final BlockPos ORIGIN = new BlockPos(8, 2, 8);

	//$ gametest_ticks_batch HUGE '2400' '"qaBlueprintLoot"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaBlueprintLoot")
	public void aBuildOfBlocksHoldingItemsComesOutEmptyWithItsWordsAndNames(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		// The scanned build: a row of six blocks, each holding something (or, the sign, words)
		helper.setBlock(SRC, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true));
		((LecternBlockEntity) helper.getBlockEntity(SRC)).setBook(new ItemStack(Items.WRITABLE_BOOK));
		helper.setBlock(SRC.east(1), Blocks.JUKEBOX);
		((Container) helper.getBlockEntity(SRC.east(1))).setItem(0, new ItemStack(Items.MUSIC_DISC_CAT));
		helper.setBlock(SRC.east(2), Blocks.CHISELED_BOOKSHELF);
		((Container) helper.getBlockEntity(SRC.east(2))).setItem(0, new ItemStack(Items.ENCHANTED_BOOK));
		helper.setBlock(SRC.east(3), Blocks.DECORATED_POT);
		((Container) helper.getBlockEntity(SRC.east(3))).setItem(0, new ItemStack(Items.DIAMOND, 64));
		helper.setBlock(SRC.east(4), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
		((CampfireBlockEntity) helper.getBlockEntity(SRC.east(4))).placeFood(null, new ItemStack(Items.BEEF), 600);
		helper.setBlock(SRC.east(5), Blocks.OAK_SIGN);
		SignBlockEntity sign = (SignBlockEntity) helper.getBlockEntity(SRC.east(5));
		sign.setText(sign.getFrontText().setMessage(0, Component.literal("Welcome")), true);
		helper.setBlock(SRC.east(6), Blocks.CHEST);
		BlockEntity pantry = helper.getBlockEntity(SRC.east(6));
		net.minecraft.nbt.CompoundTag named = pantry.saveWithoutMetadata(level.registryAccess());
		named.putString("CustomName", "\"Pantry\"");
		pantry.loadWithComponents(named, level.registryAccess());
		((Container) pantry).setItem(0, new ItemStack(Items.GOLD_INGOT, 64));
		helper.assertTrue(pantry instanceof net.minecraft.world.Nameable n && n.hasCustomName(), "the chest took no name");
		helper.assertTrue(((CampfireBlockEntity) helper.getBlockEntity(SRC.east(4))).getItems().stream().anyMatch(s -> !s.isEmpty()), "the campfire took no beef");
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_loot_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(7, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int dx = 0; dx < 7; dx++) {
			if (helper.getBlockEntity(SRC.east(dx)) instanceof Clearable c) {
				Clearable.tryClear(c);
			}
			helper.setBlock(SRC.east(dx), Blocks.AIR);
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
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			BlockPos at = helper.absolutePos(ORIGIN);
			helper.assertTrue(level.getBlockState(at).is(Blocks.LECTERN), "no lectern built: " + level.getBlockState(at));
			helper.assertTrue(!((LecternBlockEntity) level.getBlockEntity(at)).hasBook(), "the built lectern holds the blueprint's book");
			for (int dx = 1; dx <= 3; dx++) {
				BlockEntity be = level.getBlockEntity(at.east(dx));
				helper.assertTrue(be instanceof Container, "no " + level.getBlockState(at.east(dx)).getBlock() + " built at +" + dx + ": " + be);
				helper.assertTrue(((Container) be).isEmpty(), "the built " + level.getBlockState(at.east(dx)).getBlock() + " holds the blueprint's "
					+ ((Container) be).getItem(0));
			}
			BlockEntity fire = level.getBlockEntity(at.east(4));
			helper.assertTrue(fire instanceof CampfireBlockEntity, "no campfire built: " + level.getBlockState(at.east(4)));
			helper.assertTrue(((CampfireBlockEntity) fire).getItems().stream().allMatch(ItemStack::isEmpty), "the built campfire cooks the blueprint's beef: "
				+ ((CampfireBlockEntity) fire).getItems());
			BlockEntity built = level.getBlockEntity(at.east(5));
			helper.assertTrue(built instanceof SignBlockEntity s && s.getFrontText().getMessage(0, false).getString().equals("Welcome"),
				"the built sign lost its words: " + (built instanceof SignBlockEntity s2 ? s2.getFrontText().getMessage(0, false).getString() : built));
			BlockEntity chest = level.getBlockEntity(at.east(6));
			helper.assertTrue(chest instanceof Container c && c.isEmpty(), "the built chest holds the blueprint's gold: " + chest);
			helper.assertTrue(chest instanceof net.minecraft.world.Nameable n && n.hasCustomName() && n.getCustomName().getString().equals("Pantry"),
				"the built chest lost its name: " + (chest instanceof net.minecraft.world.Nameable n2 ? n2.getCustomName() : chest));
		});
	}
}
