package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem;
import io.github.jcondedata.aliveworkplace.colony.ColonyArrival;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmTreasury;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.realm.RoyalHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * ROADMAP 33.14, the realm's two builds. The Colony Camp places whole (its wagons, awning, table, chest, campfire and
 * flag) and leaves the hall's and the post's places open; the Royal Hall is hidden from the Blueprint Table, drawn on the
 * capital's Realm tab for a Blank Blueprint (the II once a Royal Hall stands), marked as a hall on the village map, and a
 * finished one within 32 blocks of the capital's hall doubles the realm treasury's cap (the II triples it; any style;
 * not from further off; not with realms off). A builder puts up both tiers with the materials stocked. (The colony's use
 * of the camp is checked in {@link ColonyArrivalGameTests}.)
 */
public class RealmBuildsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String TALL = "aliveworkplace_test:tall_area";

	private static BlueprintData.Placement at(ServerLevel level, BlockPos pos) {
		return new BlueprintData.Placement(level.dimension().location(), pos, Rotation.NONE, Mirror.NONE);
	}

	private static int count(GameTestHelper helper, BoundingBox box, Block block) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (helper.getLevel().getBlockState(p).is(block)) {
				n++;
			}
		}
		return n;
	}

	/** The Colony Camp placed at once: 15 x 6 x 13, two wagons, the awning, the table, one chest, the campfire and pot, three bedrolls, the flag; the hall's and post's places open. */
	//$ gametest_ticks_batch AREA '100' '"realmBuildsCamp"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmBuildsCamp")
	public void theColonyCampPlacesWhole(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos front = helper.absolutePos(new BlockPos(14, 2, 4));
		BoundingBox box = SettlersWagonItem.placeCamp(level, ColonyArrival.COLONY_CAMP, front, Rotation.NONE);
		helper.assertTrue(box != null && box.getXSpan() == 15 && box.getYSpan() == 6 && box.getZSpan() == 13, "the camp's box: " + box);
		helper.assertTrue(SettlersWagonItem.table(level, box) != null, "no Blueprint Table in the camp");
		helper.assertTrue(count(helper, box, Blocks.CHEST) == 1 && count(helper, box, Blocks.CAMPFIRE) == 1 && count(helper, box, Blocks.CAULDRON) == 1,
			"the chest, campfire and pot");
		// two hay bales, a pumpkin and a barrel aboard each wagon (the barrel at the tail since B424), one bale by the chest, two barrels among the crates
		helper.assertTrue(count(helper, box, Blocks.HAY_BLOCK) == 5 && count(helper, box, Blocks.PUMPKIN) == 2 && count(helper, box, Blocks.BARREL) == 4,
			"two loaded wagons and a hay bale by the chest");
		helper.assertTrue(count(helper, box, Blocks.RED_WOOL) > 10 && count(helper, box, Blocks.WHITE_BANNER) == 1, "the awning and the flag");
		helper.assertTrue(count(helper, box, Blocks.BROWN_BED) + count(helper, box, Blocks.GREEN_BED) == 6, "three bedrolls");
		helper.assertTrue(count(helper, box, ModBlocks.VILLAGE_HALL) == 0 && count(helper, box, ModBlocks.TRAVEL_POST) == 0, "the camp itself has no hall or post");
		helper.assertTrue(BlueprintLibrary.isWorldgenPiece(ColonyArrival.COLONY_CAMP), "the camp is in the Blueprint Table");
		helper.succeed();
	}

	/** The capital's Realm tab draws the Royal Hall for a Blank Blueprint (refused without one); hidden from the table; a hall on the map; a member's tab has no button. */
	//$ gametest_ticks_batch AREA '100' '"realmBuildsDraw"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmBuildsDraw")
	public void theCapitalDrawsTheRoyalHall(GameTestHelper helper) {
		RealmGameTests.Land land = RealmTreasuryGameTests.realm(helper);
		Leftovers.finished(helper);
		ChoiceMenu menu = RealmGameTests.realmTab(helper, land, 0, land.owner());
		ItemStack icon = menu.icon(RoyalHalls.BUTTON);
		List<String> lore = RealmGameTests.lore(icon);
		helper.assertTrue(icon.is(Items.LECTERN) && icon.getHoverName().getString().equals("Royal Hall"), "no Royal Hall button: " + icon);
		helper.assertTrue(lore.contains("No Royal Hall stands by the capital yet") && lore.contains("Click with a Blank Blueprint in your inventory to draw it"),
			"the button's lines: " + lore);
		menu.press(RoyalHalls.BUTTON, land.owner());
		helper.assertTrue(RealmGameTests.heard(land, "Drawing a Royal Hall takes a Blank Blueprint (Builders sell them)"), "no refusal: " + land.heard());
		land.owner().getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
		menu.press(RoyalHalls.BUTTON, land.owner());
		ItemStack drawn = ItemStack.EMPTY;
		int blanks = 0;
		for (int i = 0; i < land.owner().getInventory().getContainerSize(); i++) {
			ItemStack s = land.owner().getInventory().getItem(i);
			if (BlueprintItem.data(s).isPresent()) {
				drawn = s;
			}
			if (s.is(ModItems.BLANK_BLUEPRINT)) {
				blanks += s.getCount();
			}
		}
		Optional<BlueprintData> data = BlueprintItem.data(drawn);
		helper.assertTrue(data.isPresent() && BlueprintStyles.base(data.get().structure()).equals(StarterBlueprints.ROYAL_HALL.id()) && blanks == 1,
			"the drawn blueprint: " + data + ", blanks left " + blanks);
		helper.assertTrue(RealmGameTests.heard(land, "Drawn: Royal Hall. Have a builder put it up within 32 blocks of " + land.name(0) + "'s hall"),
			"no drawn line: " + land.heard());
		// hidden from the Blueprint Table, a hall on the map
		helper.assertTrue(BlueprintLibrary.isWorldgenPiece(StarterBlueprints.ROYAL_HALL.id()) && BlueprintLibrary.isWorldgenPiece(StarterBlueprints.ROYAL_HALL_2.id()),
			"the Royal Hall is in the Blueprint Table");
		helper.assertTrue(VillageMaps.kindOf(StarterBlueprints.ROYAL_HALL_2.id()).orElse(null) == VillageMaps.Kind.HALL
			&& VillageMaps.kindOf(BlueprintStyles.styled(StarterBlueprints.ROYAL_HALL.id(), "stonework")).orElse(null) == VillageMaps.Kind.HALL, "not a hall on the map");
		// a member's tab has "Put forward" there instead
		ChoiceMenu member = RealmGameTests.realmTab(helper, land, 1, land.owner());
		helper.assertTrue(!member.icon(RoyalHalls.BUTTON).is(Items.LECTERN), "a member's tab draws the Royal Hall");
		// with a Royal Hall standing, the tab draws the II; with the II, nothing
		BuildSiteManager sites = BuildSiteManager.get(land.level());
		BlueprintData.Placement by = at(land.level(), land.hall(0).offset(4, 0, 4));
		sites.recordFinished(StarterBlueprints.ROYAL_HALL.id(), by, land.owner().getUUID());
		menu.press(RoyalHalls.BUTTON, land.owner());
		boolean second = false;
		for (int i = 0; i < land.owner().getInventory().getContainerSize(); i++) {
			second |= BlueprintItem.data(land.owner().getInventory().getItem(i))
				.map(d -> BlueprintStyles.base(d.structure()).equals(StarterBlueprints.ROYAL_HALL_2.id())).orElse(false);
		}
		helper.assertTrue(second, "the Royal Hall II wasn't drawn");
		sites.recordFinished(StarterBlueprints.ROYAL_HALL_2.id(), by, land.owner().getUUID());
		helper.assertTrue(RoyalHalls.draw(land.level(), land.hall(0), land.owner()).refusal() == Realms.Refusal.TAKEN, "a third hall drawn");
		List<String> standing = RealmGameTests.lore(RealmGameTests.realmTab(helper, land, 0, land.owner()).icon(RoyalHalls.BUTTON));
		helper.assertTrue(standing.contains("A Royal Hall II stands: three times the cap"), "the button with the II standing: " + standing);
		sites.forgetFinished(by);
		helper.succeed();
	}

	/** A finished Royal Hall by the capital doubles the cap, the II triples it (any style); not from 40 blocks off, and not with realms off; gifts go up to the new cap. */
	//$ gametest_ticks_batch AREA '100' '"realmBuildsCap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmBuildsCap")
	public void theRoyalHallRaisesTheCap(GameTestHelper helper) {
		RealmGameTests.Land land = RealmTreasuryGameTests.realm(helper);
		Leftovers.finished(helper);
		ServerLevel level = land.level();
		BuildSiteManager sites = BuildSiteManager.get(level);
		RealmData.Realm realm = RealmTreasuryGameTests.theRealm(land);
		helper.assertTrue(RealmTreasury.cap(level.getServer(), realm) == 256, "a Barony's cap: " + RealmTreasury.cap(level.getServer(), realm));
		// 40 blocks off: nothing
		BlueprintData.Placement far = at(level, land.hall(0).offset(40, 0, 0));
		sites.recordFinished(StarterBlueprints.ROYAL_HALL.id(), far, land.owner().getUUID());
		helper.assertTrue(RealmTreasury.cap(level.getServer(), realm) == 256, "a Royal Hall 40 blocks off counted");
		sites.forgetFinished(far);
		// by the capital: twice, in the treasury's lines too
		BlueprintData.Placement by = at(level, land.hall(0).offset(6, 0, -3));
		sites.recordFinished(BlueprintStyles.styled(StarterBlueprints.ROYAL_HALL.id(), "stonework"), by, land.owner().getUUID());
		helper.assertTrue(RealmTreasury.cap(level.getServer(), realm) == 512, "a Royal Hall's cap: " + RealmTreasury.cap(level.getServer(), realm));
		List<String> lore = RealmGameTests.lore(RealmGameTests.realmTab(helper, land, 0, land.owner()).icon(RealmTreasury.TREASURY));
		helper.assertTrue(lore.contains("Holds 0.00 of 512 emeralds") && lore.contains("The Royal Hall by the capital: 2 times the cap"), "the treasury's lines: " + lore);
		// gifts fit up to the new cap: 300 emeralds go in
		ItemStack emeralds = new ItemStack(Items.EMERALD, 64);
		for (int i = 0; i < 5; i++) {
			emeralds.setCount(60);
			helper.assertTrue(RealmTreasury.give(level, land.hall(0), land.owner(), emeralds, true).ok(), "a gift under the doubled cap was refused at " + i);
		}
		helper.assertTrue(RealmTreasuryGameTests.theRealm(land).treasury() == 300 * 100L, "the treasury: " + RealmTreasuryGameTests.theRealm(land).treasury());
		// the II: three times
		sites.recordFinished(StarterBlueprints.ROYAL_HALL_2.id(), by, land.owner().getUUID());
		helper.assertTrue(RealmTreasury.cap(level.getServer(), realm) == 768, "a Royal Hall II's cap: " + RealmTreasury.cap(level.getServer(), realm));
		// realms off: the rank's cap
		Realms.ENABLED = false;
		int off = RealmTreasury.cap(level.getServer(), realm);
		Realms.ENABLED = true;
		helper.assertTrue(off == 256, "with realms off the cap was " + off);
		// taken down: back to the rank's cap
		sites.forgetFinished(by);
		helper.assertTrue(RealmTreasury.cap(level.getServer(), realm) == 256, "the cap after the hall came down");
		helper.succeed();
	}

	/** ROADMAP 33.14: a builder puts up the Royal Hall from bare ground with the materials stocked. */
	//$ gametest_ticks_batch AREA '80000' '"realmBuildsRoyalHall"'
	@GameTest(template = AREA, timeoutTicks = 80000, batch = "realmBuildsRoyalHall")
	public void buildsTheRoyalHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, StarterBlueprints.ROYAL_HALL, new BlockPos(5, 2, 5));
	}

	/** ROADMAP 33.14: the Royal Hall II (22 tall, so the tall area) from bare ground. */
	//$ gametest_ticks_batch TALL '140000' '"realmBuildsRoyalHall2"'
	@GameTest(template = TALL, timeoutTicks = 140000, batch = "realmBuildsRoyalHall2")
	public void buildsTheRoyalHallII(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, StarterBlueprints.ROYAL_HALL_2, new BlockPos(2, 2, 5));
	}
}
