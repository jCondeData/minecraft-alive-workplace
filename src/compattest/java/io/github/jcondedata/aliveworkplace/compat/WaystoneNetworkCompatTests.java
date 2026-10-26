package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * ROADMAP 41.2 with the real Waystones mod: the Village Hall's Map lists the owner's villages that have a waystone, with
 * distance and direction, marked discovered by the player looking or not; a broken stone is listed as broken; a village
 * that never raised one isn't listed, and the Map says so.
 */
public class WaystoneNetworkCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL_A = new BlockPos(1, 2, 1);
	private static final BlockPos HALL_B = new BlockPos(28, 2, 28);
	private static final BlockPos PLAZA_A = new BlockPos(3, 2, 3);
	private static final BlockPos PLAZA_B = new BlockPos(19, 2, 19);
	/** The stone's spot in the plaza (lower half), as tools/blueprints/waystones.py draws it. */
	private static final BlockPos STONE = new BlockPos(3, 2, 3);

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at, String name, ServerPlayer owner) {
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(at);
		hall.setCustomName(Component.literal(name));
		hall.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		// What the hall's round keeps of a village (RealmData.Known): the Map reads the owner's other villages from it.
		ServerLevel level = helper.getLevel();
		RealmData.get(level.getServer()).putKnown(new RealmData.Known(GlobalPos.of(level.dimension(), helper.absolutePos(at)), Component.literal(name),
			Optional.of(owner.getUUID()), owner.getGameProfile().getName(), "hamlet", 0, 0, level.getGameTime()));
		return hall;
	}

	/** Sets every block of the plaza at {@code origin} as a finished one stands, and records it finished. */
	private static void plaza(GameTestHelper helper, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("no plaza blueprint"));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildPlan plan = BuildPlan.create(blueprint, placement);
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), 2);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
				}
			}
		}
		BuildSiteManager.get(level).recordFinished(VillageWaystones.PLAZA, placement, UUID.randomUUID());
	}

	private static BlockPos stone(GameTestHelper helper, BlockPos plaza) {
		return helper.absolutePos(plaza).offset(STONE);
	}

	/** The Map button's lore on the hall's screen, as the player sees it. */
	private static List<Component> mapLore(ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		return menu.icon(VillageHallScreen.MAP).getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
	}

	private static Optional<Component> line(List<Component> lore, String key) {
		return lore.stream().filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals(key)).findFirst();
	}

	private static List<String> keys(List<Component> lore) {
		return lore.stream().map(c -> c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString()).toList();
	}

	private static void cleanUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (BlockPos at : List.of(HALL_A, HALL_B)) {
			RealmData.get(level.getServer()).forgetKnown(GlobalPos.of(level.dimension(), helper.absolutePos(at)));
			VillageWaystones.Data.get(level).forget(helper.absolutePos(at));
		}
	}

	/**
	 * Two villages of one owner, each with its plaza's waystone: the Map of the first lists both, this one first; the player
	 * discovered this village's stone and not the other's, which is listed with its distance and direction ("31 blocks
	 * south-east") as not discovered yet. Another player has discovered neither. The second village's stone broken, the Map
	 * lists it as broken, and its waystone is gone from the mod's list.
	 */
	//$ gametest_ticks_batch AREA '200' '"waystoneNetwork"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "waystoneNetwork")
	public void theMapListsTheVillagesWithAWaystoneDiscoveredOrNotAndABrokenOne(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			hall(helper, HALL_A, "Ashford", player);
			hall(helper, HALL_B, "Brookmere", player);
			plaza(helper, PLAZA_A);
			plaza(helper, PLAZA_B);
			BlockPos a = helper.absolutePos(HALL_A);
			BlockPos b = helper.absolutePos(HALL_B);
			VillageWaystones.round(level, a);
			VillageWaystones.round(level, b);
			helper.assertTrue(VillageWaystones.stoneOf(level, a).equals(Optional.of(stone(helper, PLAZA_A))), "Ashford's stone: " + VillageWaystones.stoneOf(level, a));
			helper.assertTrue(VillageWaystones.stoneOf(level, b).equals(Optional.of(stone(helper, PLAZA_B))), "Brookmere's stone: " + VillageWaystones.stoneOf(level, b));
			helper.assertTrue(VillageWaystones.standing(level, a) && VillageWaystones.standing(level, b), "the stones aren't standing waystones");

			Waystone ashford = WaystonesAPI.getWaystoneAt(level, stone(helper, PLAZA_A)).orElseThrow(() -> new GameTestAssertException("Ashford has no waystone"));
			WaystonesAPI.activateWaystone(player, ashford); // the player used Ashford's stone, not Brookmere's

			List<VillageWaystones.Village> network = VillageWaystones.network(level, a, player);
			helper.assertTrue(network.size() == 2, "the network: " + network);
			helper.assertTrue(network.get(0).hall().equals(a) && network.get(0).here() && network.get(0).state() == VillageWaystones.State.DISCOVERED,
				"Ashford (here, discovered): " + network.get(0));
			helper.assertTrue(network.get(1).hall().equals(b) && !network.get(1).here() && network.get(1).state() == VillageWaystones.State.UNDISCOVERED,
				"Brookmere (not discovered yet): " + network.get(1));

			List<Component> lore = mapLore(player, a);
			Component heading = line(lore, "screen.aliveworkplace.hall.waystones").orElseThrow(() -> new GameTestAssertException("no network heading: " + keys(lore)));
			helper.assertTrue(heading.getString().equals("Waystone network (2):"), "the heading: " + heading.getString());
			Component here = line(lore, "screen.aliveworkplace.hall.waystone.discovered").orElseThrow(() -> new GameTestAssertException("no discovered line: " + keys(lore)));
			helper.assertTrue(here.getString().equals("Ashford, this village: discovered"), "this village's line: " + here.getString());
			Component far = line(lore, "screen.aliveworkplace.hall.waystone.undiscovered").orElseThrow(() -> new GameTestAssertException("no undiscovered line: " + keys(lore)));
			String where = VillageHallScreen.where(a, stone(helper, PLAZA_B)).getString();
			helper.assertTrue(where.endsWith("blocks south-east") && far.getString().equals("Brookmere, " + where + ": not discovered yet"),
				"Brookmere's line: " + far.getString());

			// Discovery is the player's: another player has discovered neither stone.
			ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
			helper.assertTrue(VillageWaystones.network(level, a, stranger).stream().allMatch(v -> v.state() == VillageWaystones.State.UNDISCOVERED),
				"another player's network: " + VillageWaystones.network(level, a, stranger));

			// Brookmere's stone broken: listed as broken, gone from the mod's list.
			BlockPos broken = stone(helper, PLAZA_B);
			level.setBlock(broken.above(), Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(broken, Blocks.AIR.defaultBlockState(), 3);
			VillageWaystones.round(level, b); // a round meanwhile: the plaza is still there, so the village stays listed
			helper.assertFalse(VillageWaystones.standing(level, b), "a broken stone still stands");
			List<Component> after = mapLore(player, a);
			Component brokenLine = line(after, "screen.aliveworkplace.hall.waystone.broken").orElseThrow(() -> new GameTestAssertException("no broken line: " + keys(after)));
			helper.assertTrue(brokenLine.getString().equals("Brookmere, " + where + ": its waystone is broken"), "the broken line: " + brokenLine.getString());
			helper.assertTrue(line(after, "screen.aliveworkplace.hall.waystone.discovered").isPresent(), "Ashford's line went: " + keys(after));
			helper.assertTrue(line(after, "screen.aliveworkplace.hall.waystone.undiscovered").isEmpty(), "Brookmere is still undiscovered: " + keys(after));
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

	/**
	 * A village that never raised a waystone isn't on the network, and its Map says none has one yet; a village of another
	 * owner isn't listed either. The stones kept survive a save and reload.
	 */
	//$ gametest_batch AREA '"waystoneNetworkNone"'
	@GameTest(template = AREA, batch = "waystoneNetworkNone")
	public void aVillageWithoutAWaystoneIsNotListed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerPlayer other = helper.makeMockServerPlayerInLevel();
		try {
			hall(helper, HALL_A, "Ashford", player);
			BlockPos a = helper.absolutePos(HALL_A);
			VillageWaystones.round(level, a);
			helper.assertTrue(VillageWaystones.stoneOf(level, a).isEmpty(), "a village with no plaza has a stone: " + VillageWaystones.stoneOf(level, a));
			List<Component> lore = mapLore(player, a);
			Component none = line(lore, "screen.aliveworkplace.hall.waystones_none").orElseThrow(() -> new GameTestAssertException("no 'none yet' line: " + keys(lore)));
			helper.assertTrue(none.getString().equals("Waystone network: no village has a waystone yet"), "the line: " + none.getString());
			helper.assertTrue(line(lore, "screen.aliveworkplace.hall.waystones").isEmpty(), "a heading with no village: " + keys(lore));

			// Another owner's village with a stone: not on Ashford's network.
			hall(helper, HALL_B, "Brookmere", other);
			plaza(helper, PLAZA_B);
			BlockPos b = helper.absolutePos(HALL_B);
			VillageWaystones.round(level, b);
			helper.assertTrue(VillageWaystones.standing(level, b), "Brookmere's stone isn't a standing waystone");
			helper.assertTrue(VillageWaystones.network(level, a, player).isEmpty(), "another owner's village is listed: " + VillageWaystones.network(level, a, player));
			helper.assertTrue(VillageWaystones.network(level, b, other).size() == 1, "Brookmere's own network: " + VillageWaystones.network(level, b, other));

			// Saved and loaded: the stone kept for Brookmere comes back.
			CompoundTag saved = VillageWaystones.Data.get(level).save(new CompoundTag(), level.registryAccess());
			VillageWaystones.Data loaded = VillageWaystones.Data.load(saved, level.registryAccess());
			helper.assertTrue(loaded.stone(b).equals(Optional.of(stone(helper, PLAZA_B))), "Brookmere's stone after a reload: " + loaded.stone(b) + " from " + saved);
			helper.assertTrue(VillageWaystones.Data.load(new CompoundTag(), level.registryAccess()).stone(b).isEmpty(), "an older world's empty data has a stone");
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}
}
