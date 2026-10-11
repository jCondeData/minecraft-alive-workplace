package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.guard.BanditFort;
import io.github.jcondedata.aliveworkplace.guard.BanditKing;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The Bandit King's fort and the King (ROADMAP 31.15). */
public class BanditFortGameTests implements FabricGameTest {
	/** 30 blocks tall: the keep's chimney stands 16 over the ground. */
	private static final String AREA = "aliveworkplace_test:tall_area";
	/** The fort's middle on the test ground (grass at y 3 over dirt). */
	private static final BlockPos MIDDLE = new BlockPos(15, 3, 15);
	/** A player's wall just outside the fort's footprint. */
	private static final BlockPos PLAYERS_WALL = new BlockPos(0, 4, 15);

	/**
	 * Uneven ground over the whole area: grass at y 3 on dirt, a two-step hill near the front, a hole two deep at the back
	 * left, and a player's cobblestone wall just outside the footprint.
	 */
	private static void unevenGround(GameTestHelper helper) {
		openSky(helper);
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.GRASS_BLOCK);
			}
		}
		for (int x = 9; x <= 12; x++) {
			for (int z = 3; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 4, z), Blocks.DIRT);
				if (x >= 10 && x <= 11 && z >= 4 && z <= 5) {
					helper.setBlock(new BlockPos(x, 5, z), Blocks.GRASS_BLOCK);
				}
			}
		}
		for (int x = 4; x <= 7; x++) {
			for (int z = 22; z <= 25; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.AIR);
			}
		}
		helper.setBlock(PLAYERS_WALL, Blocks.COBBLESTONE);
		helper.setBlock(PLAYERS_WALL.above(), Blocks.COBBLESTONE);
	}

	/** Takes the barrier roof off the test's area, so the height map finds the ground (as ColonyArrivalGameTests do). */
	private static void openSky(GameTestHelper helper) {
		AABB box = helper.getBounds();
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	private static void begin(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
	}

	/** Nothing floats: under the whole footprint the ground and the block below it are solid; the hill is gone. */
	private static void groundHolds(GameTestHelper helper) {
		for (int x = 1; x <= 29; x++) {
			for (int z = 1; z <= 29; z++) {
				BlockPos ground = new BlockPos(x, 3, z);
				helper.assertFalse(helper.getBlockState(ground).isAir(), "the ground under the fort is open at " + ground);
				helper.assertFalse(helper.getBlockState(ground.below()).isAir(), "a hole under the fort at " + ground.below());
			}
		}
		helper.assertTrue(helper.getBlockState(new BlockPos(10, 5, 4)).isAir() && helper.getBlockState(new BlockPos(10, 4, 4)).isAir(),
			"the hill still stands in the yard");
	}

	private static void playersWallStands(GameTestHelper helper) {
		helper.assertBlockPresent(Blocks.COBBLESTONE, PLAYERS_WALL);
		helper.assertBlockPresent(Blocks.COBBLESTONE, PLAYERS_WALL.above());
	}

	private static void stageOneStands(GameTestHelper helper, ServerLevel level, BanditFort.Fort fort) {
		helper.assertTrue(level.getBlockState(fort.at(new BlockPos(0, 1, 7))).is(Blocks.SPRUCE_LOG), "no palisade");
		helper.assertTrue(level.getBlockState(fort.at(new BlockPos(14, 1, 0))).is(Blocks.SPRUCE_FENCE_GATE), "no gate");
		helper.assertTrue(level.getBlockState(fort.at(new BlockPos(14, 1, 11))).is(Blocks.CAMPFIRE), "no fire");
		helper.assertTrue(level.getBlockState(fort.at(new BlockPos(5, 3, 7))).is(Blocks.STRIPPED_DARK_OAK_LOG), "no tent");
	}

	private static void stageTwoStands(GameTestHelper helper, ServerLevel level, BanditFort.Fort fort) {
		helper.assertTrue(level.getBlockState(fort.at(new BlockPos(0, 7, 0))).is(Blocks.SPRUCE_LOG), "no watchtower post");
		helper.assertTrue(level.getBlockState(fort.at(new BlockPos(1, 3, 2))).is(Blocks.LADDER), "no ladder up the west tower");
		helper.assertTrue(level.getBlockState(fort.at(new BlockPos(27, 3, 2))).is(Blocks.LADDER), "no ladder up the east tower");
		helper.assertTrue(level.getBlockState(fort.at(new BlockPos(27, 1, 15))).is(Blocks.HAY_BLOCK), "no stable");
	}

	private static void keepStands(GameTestHelper helper, ServerLevel level, BanditFort.Fort fort) {
		helper.assertTrue(level.getBlockState(fort.at(new BlockPos(14, 1, 17))).is(Blocks.DARK_OAK_DOOR), "no keep door");
		helper.assertTrue(level.getBlockState(fort.at(new BlockPos(14, 4, 16))).is(Blocks.BLACK_WALL_BANNER), "no banner over the door");
		helper.assertTrue(level.getBlockState(fort.at(new BlockPos(14, 2, 24))).is(Blocks.DARK_OAK_STAIRS), "no throne");
		helper.assertTrue(level.getBlockEntity(fort.at(BanditFort.TREASURE)) instanceof ChestBlockEntity chest
			&& BanditFort.LOOT.equals(chest.getLootTable()), "the treasure chest isn't the King's");
		helper.assertTrue(level.getBlockState(fort.at(BanditFort.POSTERN)).getBlock() instanceof DoorBlock, "no postern");
	}

	private static long kings(ServerLevel level, BanditFort.Fort fort) {
		return BanditFort.men(level, fort.yard(), 24).stream().filter(m -> m.getTags().contains(BanditKing.TAG)).count();
	}

	/**
	 * The three stages placed in order on uneven ground: the first fills the hole and clears the hill, nothing floats, each
	 * later stage keeps what stood (the palisade, the fire, the tents), the keep has its chest and postern, a player's
	 * wall beside the fort is untouched; each stage brings its men (4, then 2 on the towers, then the King), all kept to
	 * the fort. The fort's ground test refuses a block a player built in its footprint.
	 */
	//$ gametest_ticks_batch AREA '60' '"banditFortStages"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "banditFortStages")
	public void threeStagesPlaceInOrderOnUnevenGround(GameTestHelper helper) {
		begin(helper);
		unevenGround(helper);
		ServerLevel level = helper.getLevel();
		BlockPos ground = helper.absolutePos(MIDDLE);
		helper.assertTrue(BanditFort.fits(level, ground), "the uneven ground doesn't fit the fort");
		helper.setBlock(new BlockPos(20, 4, 20), Blocks.COBBLESTONE);
		helper.assertFalse(BanditFort.fits(level, ground), "the fort fits over a player's cobblestone");
		helper.setBlock(new BlockPos(20, 4, 20), Blocks.AIR);
		BanditFort.Fort fort = BanditFort.at(ground, Rotation.NONE);
		helper.assertTrue(fort.origin().equals(helper.absolutePos(new BlockPos(1, 3, 1))), "the fort's corner: " + fort.origin());
		helper.assertTrue(BanditFort.place(level, fort, 0, false), "stage 1 missing");
		groundHolds(helper);
		stageOneStands(helper, level, fort);
		helper.assertTrue(BanditFort.men(level, fort.yard(), 24).size() == 4, "stage 1's men: " + BanditFort.men(level, fort.yard(), 24).size());
		helper.assertTrue(BanditFort.place(level, fort, 1, false), "stage 2 missing");
		stageOneStands(helper, level, fort);
		stageTwoStands(helper, level, fort);
		helper.assertTrue(BanditFort.men(level, fort.yard(), 24).size() == 6, "stage 2's men: " + BanditFort.men(level, fort.yard(), 24).size());
		helper.assertTrue(BanditFort.place(level, fort, 2, false), "stage 3 missing");
		stageOneStands(helper, level, fort);
		stageTwoStands(helper, level, fort);
		keepStands(helper, level, fort);
		groundHolds(helper);
		playersWallStands(helper);
		helper.assertFalse(level.getBlockState(fort.at(BanditFort.POSTERN)).getValue(DoorBlock.OPEN), "the postern was placed open");
		BanditFort.setPostern(level, fort, true);
		helper.assertTrue(level.getBlockState(fort.at(BanditFort.POSTERN)).getValue(DoorBlock.OPEN)
			&& level.getBlockState(fort.at(BanditFort.POSTERN).above()).getValue(DoorBlock.OPEN), "the postern won't open");
		helper.assertTrue(kings(level, fort) == 1, "kings: " + kings(level, fort));
		for (Mob man : BanditFort.men(level, fort.yard(), 24)) {
			helper.assertTrue(man.hasRestriction() && man.getRestrictCenter().distManhattan(fort.yard()) <= 30, "a man roams free: " + man);
		}
		helper.succeed();
	}

	/**
	 * The arc's way: a {@code place} of the fort's stages builds the first on fit ground round its spot and each next one
	 * at the next dawn, on the same corner; the keep's postern opens when the arc's flag says so.
	 */
	//$ gametest_ticks_batch AREA '60' '"banditFortArc"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "banditFortArc")
	public void theArcPlacesAStageADawn(GameTestHelper helper) {
		begin(helper);
		unevenGround(helper);
		ServerLevel level = helper.getLevel();
		ArcState s = new ArcState("aliveworkplace:bandit_king");
		s.flags.put(BanditFort.POSTERN_FLAG, ArcState.FOREVER);
		ArcState.ArcPlace p = new ArcState.ArcPlace("fort", helper.absolutePos(MIDDLE),
			BanditFort.STAGES.stream().map(Object::toString).toList(), 0);
		s.places.add(p);
		long today = Chronicle.day(level);
		helper.assertTrue(ArcEffects.build(level, s, p) && p.placed == 1 && !p.skipped, "stage 1 wasn't built: " + p.placed + " skipped " + p.skipped);
		helper.assertTrue(p.nextDay == today + 1, "the next stage isn't due the next dawn: " + p.nextDay);
		BanditFort.Fort fort = new BanditFort.Fort(p.origin, Rotation.NONE);
		helper.assertTrue(p.origin.equals(helper.absolutePos(new BlockPos(1, 3, 1))), "built off its ground: " + p.origin);
		groundHolds(helper);
		stageOneStands(helper, level, fort);
		helper.assertTrue(ArcEffects.build(level, s, p) && p.placed == 2, "stage 2 wasn't built");
		stageTwoStands(helper, level, fort);
		helper.assertTrue(ArcEffects.build(level, s, p) && p.placed == 3 && !p.waiting(), "stage 3 wasn't built");
		keepStands(helper, level, fort);
		playersWallStands(helper);
		helper.assertTrue(level.getBlockState(fort.at(BanditFort.POSTERN)).getValue(DoorBlock.OPEN), "the postern is shut though the flag is set");
		helper.assertTrue(kings(level, fort) == 1, "kings: " + kings(level, fort));
		helper.succeed();
	}

	private static Mob king(GameTestHelper helper) {
		BlockPos at = helper.absolutePos(new BlockPos(10, 2, 10));
		Mob king = BanditKing.spawn(helper.getLevel(), at, at, helper.absolutePos(new BlockPos(12, 2, 10)));
		helper.assertTrue(king != null, "no King");
		return king;
	}

	private static int level(ServerLevel level, ItemStack stack, net.minecraft.resources.ResourceKey<Enchantment> key) {
		return EnchantmentHelper.getItemEnchantmentLevel(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key), stack);
	}

	private static void isCrown(GameTestHelper helper, ItemStack crown) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(crown.is(Items.GOLDEN_HELMET), "the crown isn't a golden helmet: " + crown);
		helper.assertTrue(level(level, crown, Enchantments.PROTECTION) == 3 && level(level, crown, Enchantments.UNBREAKING) == 3,
			"the crown's enchantments: " + crown.getEnchantments());
		helper.assertTrue("The King's Crown".equals(crown.getHoverName().getString()), "the crown's name: " + crown.getHoverName().getString());
	}

	/**
	 * The King: "<a captain's first name>, the Bandit King", crowned, in iron, a diamond axe, 150 health, dropping none of
	 * it; his boss bar shows to a player within 32 blocks and not to one further off; he keeps to his fort after losing his
	 * leash (a reload).
	 */
	//$ gametest_ticks_batch AREA '40' '"banditKingGear"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "banditKingGear")
	public void theKingsGearHealthAndBossBar(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		Mob king = king(helper);
		String name = king.getCustomName() == null ? "" : king.getCustomName().getString();
		helper.assertTrue(name.endsWith(", the Bandit King") && Bounties.firstNames().contains(name.substring(0, name.indexOf(','))), "his name: " + name);
		isCrown(helper, king.getItemBySlot(EquipmentSlot.HEAD));
		helper.assertTrue(king.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE) && king.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS)
			&& king.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS), "his armour");
		helper.assertTrue(king.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.DIAMOND_AXE), "his axe");
		helper.assertTrue(Math.abs(king.getMaxHealth() - 150) < 0.01 && Math.abs(king.getHealth() - 150) < 0.01, "his health: " + king.getHealth() + "/" + king.getMaxHealth());
		helper.assertTrue(king.getTags().contains(BanditFort.TAG) && king.hasRestriction(), "he doesn't keep to the fort");
		ServerPlayer near = helper.makeMockServerPlayerInLevel();
		near.setGameMode(GameType.SURVIVAL);
		near.teleportTo(king.getX() + 20, king.getY(), king.getZ());
		ServerPlayer far = helper.makeMockServerPlayerInLevel();
		far.setGameMode(GameType.SURVIVAL);
		far.teleportTo(king.getX() + 40, king.getY(), king.getZ());
		king.clearRestriction();
		BanditFort.tick(level);
		ServerBossEvent bar = BanditKing.bar(king.getUUID());
		helper.assertTrue(bar != null, "no boss bar");
		helper.assertTrue(bar.getPlayers().contains(near), "the player 20 blocks off doesn't see his bar");
		helper.assertFalse(bar.getPlayers().contains(far), "the player 40 blocks off sees his bar");
		helper.assertTrue(bar.getName().getString().equals(name), "the bar's name: " + bar.getName().getString());
		helper.assertTrue(king.hasRestriction() && king.getRestrictCenter().equals(helper.absolutePos(new BlockPos(10, 2, 10))), "his leash wasn't put back");
		king.discard();
		BanditFort.tick(level);
		helper.assertTrue(BanditKing.bar(king.getUUID()) == null && !bar.getPlayers().contains(near), "his bar outlives him");
		near.discard();
		far.discard();
		helper.succeed();
	}

	/** At half health he blows his horn: 4 bandits come, kept to the fort, and never again. */
	//$ gametest_ticks_batch AREA '40' '"banditKingHorn"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "banditKingHorn")
	public void theHornCallsFourBanditsOnce(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		Mob king = king(helper);
		king.setHealth(80);
		BanditFort.tick(level);
		helper.assertTrue(called(helper).isEmpty(), "he blew his horn above half health");
		king.setHealth(70);
		BanditFort.tick(level);
		List<Mob> called = called(helper);
		helper.assertTrue(called.size() == BanditKing.CALLED, "called: " + called.size());
		helper.assertTrue(king.getTags().contains(BanditKing.HORN_TAG), "his horn isn't remembered");
		for (Mob man : called) {
			helper.assertTrue(man.hasRestriction() && man.getTags().contains(BanditFort.TAG), "a called bandit roams free");
		}
		BanditFort.tick(level);
		king.setHealth(150);
		BanditFort.tick(level);
		king.setHealth(30);
		BanditFort.tick(level);
		helper.assertTrue(called(helper).size() == BanditKing.CALLED, "he called again: " + called(helper).size());
		helper.succeed();
	}

	private static List<Mob> called(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Mob.class, helper.getBounds().inflate(8), m -> m.isAlive() && m.getTags().contains(BanditKing.CALLED_TAG));
	}

	/** The treasure chest always holds the King's Crown, the emeralds, gold and diamonds, and a sword or crossbow, enchanted. */
	//$ gametest_ticks_batch AREA '20' '"banditKingLoot"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "banditKingLoot")
	public void theTreasureChestAlwaysHasTheCrown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 2, 5))))
			.create(LootContextParamSets.CHEST);
		var table = level.getServer().reloadableRegistries().getLootTable(BanditFort.LOOT);
		for (int i = 0; i < 50; i++) {
			List<ItemStack> items = table.getRandomItems(params, 3100L + i);
			int emeralds = items.stream().filter(s -> s.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum();
			int gold = items.stream().filter(s -> s.is(Items.GOLD_INGOT)).mapToInt(ItemStack::getCount).sum();
			int diamonds = items.stream().filter(s -> s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum();
			helper.assertTrue(emeralds >= 16 && emeralds <= 32 && gold >= 4 && gold <= 8 && diamonds >= 1 && diamonds <= 3,
				"roll " + i + ": " + emeralds + " emeralds, " + gold + " gold, " + diamonds + " diamonds");
			helper.assertTrue(items.stream().anyMatch(s -> (s.is(Items.IRON_SWORD) || s.is(Items.CROSSBOW)) && s.isEnchanted()), "roll " + i + ": no enchanted sword or crossbow");
			List<ItemStack> crowns = items.stream().filter(s -> s.is(Items.GOLDEN_HELMET)).toList();
			helper.assertTrue(crowns.size() == 1, "roll " + i + ": crowns " + crowns.size());
			isCrown(helper, crowns.get(0));
			helper.assertTrue(crowns.get(0).has(DataComponents.ITEM_NAME), "the crown's name isn't its own");
		}
		helper.succeed();
	}
}
