package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.Signposts;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.FilteredText;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA for 36.4 (the Signpost's boards), the spec corners {@link SignpostGameTests} leaves: the 1,000-block reach from
 * both sides (a village first, a nearer build only when no village is in reach), nothing at all ("Welcome" on every
 * board), a waxed board refusing a player's dye and text edit, a build pulled down and a new neighbour picked up at the
 * next dawn, and boards reloaded into the world still brought up to date.
 */
public class QaSignpostGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	/** The signpost's origin; its post stands one block in, at (3, y, 9). */
	private static final BlockPos ORIGIN = new BlockPos(2, 2, 8);
	private static final BlockPos OWN_HALL = new BlockPos(3, 2, 3);
	private static final BlockPos EAST_HALL = new BlockPos(15, 2, 9);
	private static final int WEST = 300;
	private static final int SOUTH = 6;

	private record Scene(ServerLevel level, BlockPos post, BlockPos ownHall, BlockPos eastHall, BlockPos westHall,
		BlueprintData.Placement placement, BlueprintData.Placement well) {
	}

	/**
	 * Our placed hall and the signpost, always; with {@code full}, also a placed hall east, a far village west
	 * ("Westford", 300) on the caravans' list and a finished Well 6 south. The caravans' list is emptied for the test
	 * (put back after).
	 */
	private static Scene scene(GameTestHelper helper, boolean full) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(OWN_HALL, ModBlocks.VILLAGE_HALL);
		BlockPos own = helper.absolutePos(OWN_HALL);
		BlockPos east = helper.absolutePos(EAST_HALL);
		BlockPos post = helper.absolutePos(ORIGIN.offset(1, 0, 1));
		BlockPos west = post.west(WEST);
		Caravans.Data data = Caravans.Data.get(level);
		List<Caravans.Village> before = new ArrayList<>(data.villages());
		before.forEach(v -> data.remove(v.hall()));
		data.setWants(own, VillageHalls.name(level, own), List.of());
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement well = new BlueprintData.Placement(level.dimension().location(), post.south(SOUTH), Rotation.NONE, Mirror.NONE);
		if (full) {
			helper.setBlock(EAST_HALL, ModBlocks.VILLAGE_HALL);
			data.setWants(east, VillageHalls.name(level, east), List.of());
			data.setWants(west, Component.literal("Westford"), List.of());
			sites.recordFinished(StarterBlueprints.WELL.id(), well, UUID.randomUUID());
		}
		Blueprint bp = BlueprintLibrary.get(level, StarterBlueprints.SIGNPOST.id()).orElseThrow();
		BlockPos origin = helper.absolutePos(ORIGIN);
		for (Blueprint.Entry e : bp.blocks()) {
			level.setBlock(origin.offset(e.pos()), e.state(), 2);
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		sites.recordFinished(StarterBlueprints.SIGNPOST.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> {
			sites.forgetFinished(well);
			sites.forgetFinished(placement);
			data.remove(own);
			data.remove(east);
			data.remove(west);
			before.forEach(v -> data.setWants(v.hall(), v.name(), v.wants()));
			Signposts.forget();
		});
		return new Scene(level, post, own, east, west, placement, well);
	}

	private static Map<Direction, SignBlockEntity> boards(Scene s) {
		Map<Direction, SignBlockEntity> out = new HashMap<>();
		for (BlockPos p : Signposts.boards(s.level(), s.placement().origin())) {
			out.put(s.level().getBlockState(p).getValue(WallSignBlock.FACING), (SignBlockEntity) s.level().getBlockEntity(p));
		}
		return out;
	}

	private static String said(SignBlockEntity sign) {
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			lines.add(sign.getFrontText().getMessage(i, false).getString());
		}
		return String.join("|", lines);
	}

	private static String said(Scene s, Direction d) {
		return said(boards(s).get(d));
	}

	/** The hall's dawn round, the next day (as the rename test runs it). */
	private static void nextDawn(Scene s, GameTestHelper helper) {
		ServerLevel level = s.level();
		long time = level.getDayTime();
		VillageHallBlockEntity own = (VillageHallBlockEntity) level.getBlockEntity(s.ownHall());
		BlockState hallState = level.getBlockState(s.ownHall());
		level.setDayTime(time + VillageNeeds.DAY);
		try {
			own.setNeeds(null);
			VillageHallBlockEntity.serverTick(level, s.ownHall(), hallState, own);
		} finally {
			level.setDayTime(time);
		}
	}

	/** Forgets, for this test, every finished building within the boards' reach but the signpost itself (put back after). */
	private static void nothingFinishedNear(GameTestHelper helper, Scene s) {
		BuildSiteManager sites = BuildSiteManager.get(s.level());
		List<BuildSiteManager.Finished> gone = new ArrayList<>();
		for (BuildSiteManager.Finished f : sites.finishedNear(s.level(), s.post(), Signposts.VILLAGE_RANGE + 8)) {
			if (!f.placement().equals(s.placement())) {
				gone.add(f);
				sites.forgetFinished(f.placement());
			}
		}
		Leftovers.after(helper, () -> gone.forEach(f -> sites.recordFinished(f.structure(), f.placement(), f.owner())));
	}

	/**
	 * The 1,000-block reach, from both sides: a village west at 999 and at exactly 1,000 is named (over a finished
	 * build nearer that way); at 1,001 it is not, and the board names the nearer build instead.
	 */
	//$ gametest_batch AREA '"qaSignpostReach"'
	@GameTest(template = AREA, batch = "qaSignpostReach")
	public void qaTheThousandBlockReachFromBothSides(GameTestHelper helper) {
		Scene s = scene(helper, true);
		ServerLevel level = s.level();
		Caravans.Data data = Caravans.Data.get(level);
		data.remove(s.westHall());
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement shed = new BlueprintData.Placement(level.dimension().location(), s.post().west(50), Rotation.NONE, Mirror.NONE);
		sites.recordFinished(StarterBlueprints.WELL.id(), shed, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(shed));
		String well = Blueprints.displayName(StarterBlueprints.WELL.id()).getString();
		for (int far : new int[] {999, 1000}) {
			BlockPos hall = s.post().west(far);
			data.setWants(hall, Component.literal("Farwick"), List.of());
			Signposts.finished(level, StarterBlueprints.SIGNPOST.id(), s.placement());
			data.remove(hall);
			helper.assertTrue(said(s, Direction.WEST).equals("To|Farwick|" + far + " blocks|"), "a village " + far + " west: " + said(s, Direction.WEST));
		}
		BlockPos tooFar = s.post().west(1001);
		data.setWants(tooFar, Component.literal("Farwick"), List.of());
		Leftovers.after(helper, () -> data.remove(tooFar));
		Signposts.finished(level, StarterBlueprints.SIGNPOST.id(), s.placement());
		helper.assertTrue(said(s, Direction.WEST).equals("To|" + well + "|50 blocks|"), "a village 1001 west, a Well 50 west: " + said(s, Direction.WEST));
		helper.assertTrue(boards(s).get(Direction.WEST).isWaxed(), "the west board isn't waxed");
		helper.succeed();
	}

	/** Nothing at all any way (no other village, no finished build): every board says the village's own name and "Welcome". */
	//$ gametest_batch AREA '"qaSignpostWelcome"'
	@GameTest(template = AREA, batch = "qaSignpostWelcome")
	public void qaNothingAnyWaySaysWelcomeOnEveryBoard(GameTestHelper helper) {
		Scene s = scene(helper, false);
		nothingFinishedNear(helper, s);
		helper.assertTrue(Signposts.finished(s.level(), StarterBlueprints.SIGNPOST.id(), s.placement()) == 4, "the boards weren't written");
		String home = VillageHalls.name(s.level(), s.ownHall()).getString();
		helper.assertFalse(home.isEmpty(), "the village has no name");
		Map<Direction, SignBlockEntity> b = boards(s);
		helper.assertTrue(b.size() == 4, "boards: " + b.size());
		for (Direction d : Direction.Plane.HORIZONTAL) {
			helper.assertTrue(said(b.get(d)).equals("|" + home + "|Welcome|"), d + ": " + said(b.get(d)));
			helper.assertTrue(b.get(d).isWaxed(), d + " isn't waxed");
		}
		helper.succeed();
	}

	/**
	 * A waxed board can't be edited by a passer-by: dye used on it doesn't colour it, and a text edit sent the way the
	 * sign screen sends one (with the player allowed to edit) changes nothing. A plain sign takes both, so the check bites.
	 */
	//$ gametest_batch AREA '"qaSignpostWaxed"'
	@GameTest(template = AREA, batch = "qaSignpostWaxed")
	public void qaAPlayerCantEditAWrittenBoard(GameTestHelper helper) {
		Scene s = scene(helper, true);
		Signposts.finished(s.level(), StarterBlueprints.SIGNPOST.id(), s.placement());
		SignBlockEntity board = boards(s).get(Direction.WEST);
		String before = said(board);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		// The control: a plain wall sign takes dye and text from this same player.
		BlockPos plainRel = new BlockPos(10, 2, 14);
		helper.setBlock(plainRel.north(), Blocks.STONE);
		helper.setBlock(plainRel, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.SOUTH));
		SignBlockEntity plain = (SignBlockEntity) s.level().getBlockEntity(helper.absolutePos(plainRel));
		plain.setAllowedPlayerEditor(player.getUUID());
		plain.updateSignText(player, true, List.of(FilteredText.passThrough("Graffiti"), FilteredText.passThrough(""), FilteredText.passThrough(""), FilteredText.passThrough("")));
		plain.setAllowedPlayerEditor(player.getUUID());
		plain.updateSignText(player, false, List.of(FilteredText.passThrough("Graffiti"), FilteredText.passThrough(""), FilteredText.passThrough(""), FilteredText.passThrough("")));
		// Dye only takes on a side with words (vanilla), so it goes on after the text.
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RED_DYE, 4));
		helper.useBlock(plainRel, player);
		helper.assertTrue(plain.getFrontText().getColor() == DyeColor.RED || plain.getBackText().getColor() == DyeColor.RED, "control: the plain sign didn't take the dye");
		helper.assertTrue(plain.getFrontText().getMessage(0, false).getString().equals("Graffiti"), "control: the plain sign didn't take the text");
		// The written board, the player in front of its words (dye goes on the side the player faces).
		player.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(board.getBlockPos().west(2)));
		helper.assertTrue(board.isFacingFrontText(player), "the player isn't in front of the board's words");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RED_DYE, 4));
		helper.useBlock(helper.relativePos(board.getBlockPos()), player);
		board.setAllowedPlayerEditor(player.getUUID());
		board.updateSignText(player, true, List.of(FilteredText.passThrough("Graffiti"), FilteredText.passThrough(""), FilteredText.passThrough(""), FilteredText.passThrough("")));
		SignBlockEntity after = boards(s).get(Direction.WEST);
		helper.assertTrue(after.getFrontText().getColor() != DyeColor.RED && after.getBackText().getColor() != DyeColor.RED, "the dye coloured the waxed board");
		helper.assertTrue(player.getMainHandItem().getCount() == 4, "the dye was used up on the waxed board: " + player.getMainHandItem());
		helper.assertTrue(said(after).equals(before), "the board was edited: " + said(after) + " (was " + before + ")");
		helper.assertTrue(after.isWaxed(), "the board lost its wax");
		helper.succeed();
	}

	/**
	 * A build pulled down by a builder (the Well south): the board naming it is written again at the next dawn round and
	 * no longer names it.
	 */
	//$ gametest_batch AREA '"qaSignpostPulledDown"'
	@GameTest(template = AREA, batch = "qaSignpostPulledDown")
	public void qaABuildPulledDownIsDroppedAtTheNextDawn(GameTestHelper helper) {
		Scene s = scene(helper, true);
		Signposts.finished(s.level(), StarterBlueprints.SIGNPOST.id(), s.placement());
		String well = Blueprints.displayName(StarterBlueprints.WELL.id()).getString();
		helper.assertTrue(said(s, Direction.SOUTH).equals("To|" + well + "|" + SOUTH + " blocks|"), "south before: " + said(s, Direction.SOUTH));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 4));
		builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(1));
		builder.setNoAi(true);
		BuildSite down = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "Jesse", StarterBlueprints.WELL.id(), s.well());
		down.setDeconstruction();
		Builders.finish(s.level(), builder, down);
		helper.assertTrue(BuildSiteManager.get(s.level()).finishedNear(s.level(), s.well().origin(), 1).stream().noneMatch(f -> f.placement().equals(s.well())),
			"the take-down didn't forget the Well");
		nextDawn(s, helper);
		String south = said(s, Direction.SOUTH);
		helper.assertFalse(south.contains(well), "the pulled-down Well is still on the south board after the dawn: " + south);
		helper.assertTrue(boards(s).get(Direction.SOUTH).isWaxed(), "the rewritten south board isn't waxed");
		helper.assertTrue(said(s, Direction.WEST).equals("To|Westford|" + WEST + " blocks|"), "west after the dawn: " + said(s, Direction.WEST));
		helper.succeed();
	}

	/** A new neighbour nearer than Westford (west, 200): the west board names it after the next dawn round. */
	//$ gametest_batch AREA '"qaSignpostNeighbour"'
	@GameTest(template = AREA, batch = "qaSignpostNeighbour")
	public void qaANewNeighbourIsNamedAtTheNextDawn(GameTestHelper helper) {
		Scene s = scene(helper, true);
		Signposts.finished(s.level(), StarterBlueprints.SIGNPOST.id(), s.placement());
		helper.assertTrue(said(s, Direction.WEST).equals("To|Westford|" + WEST + " blocks|"), "west before: " + said(s, Direction.WEST));
		Caravans.Data data = Caravans.Data.get(s.level());
		BlockPos nearer = s.post().west(200);
		data.setWants(nearer, Component.literal("Midway"), List.of());
		Leftovers.after(helper, () -> data.remove(nearer));
		nextDawn(s, helper);
		helper.assertTrue(said(s, Direction.WEST).equals("To|Midway|200 blocks|"), "west after the dawn: " + said(s, Direction.WEST));
		helper.assertTrue(boards(s).get(Direction.WEST).isWaxed(), "the rewritten west board isn't waxed");
		helper.succeed();
	}

	/**
	 * A save and reload: each board put back in the world as loaded from its save, and the builds' record saved and
	 * loaded, keep the words (writing again changes nothing, the signpost is still on the record); the next dawn after a
	 * village east is renamed still rewrites a reloaded board.
	 */
	//$ gametest_batch AREA '"qaSignpostReload"'
	@GameTest(template = AREA, batch = "qaSignpostReload")
	public void qaReloadedBoardsKeepTheirWordsAndStayUpToDate(GameTestHelper helper) {
		Scene s = scene(helper, true);
		ServerLevel level = s.level();
		Signposts.finished(level, StarterBlueprints.SIGNPOST.id(), s.placement());
		Map<Direction, String> before = new HashMap<>();
		boards(s).forEach((d, sign) -> before.put(d, said(sign)));
		for (SignBlockEntity sign : boards(s).values()) {
			CompoundTag tag = sign.saveWithFullMetadata(level.registryAccess());
			BlockPos at = sign.getBlockPos();
			BlockState state = sign.getBlockState();
			level.removeBlockEntity(at);
			BlockEntity back = BlockEntity.loadStatic(at, state, tag, level.registryAccess());
			helper.assertTrue(back != null, "a board didn't load");
			level.setBlockEntity(back);
		}
		CompoundTag record = BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess());
		BuildSiteManager loaded = BuildSiteManager.load(record, level.registryAccess());
		helper.assertTrue(loaded.finishedNear(level, s.placement().origin(), 1).stream()
				.anyMatch(f -> f.placement().equals(s.placement()) && Signposts.isSignpost(f.structure())),
			"the signpost isn't on the reloaded builds' record");
		Map<Direction, SignBlockEntity> b = boards(s);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			helper.assertTrue(b.get(d) != null && b.get(d).isWaxed() && said(b.get(d)).equals(before.get(d)), d + " after the reload: "
				+ (b.get(d) == null ? "gone" : said(b.get(d))) + " (was " + before.get(d) + ")");
		}
		helper.assertTrue(Signposts.finished(level, StarterBlueprints.SIGNPOST.id(), s.placement()) == 0, "reloaded boards were written again though nothing changed");
		Signposts.forget(); // which halls looked today isn't saved
		((VillageHallBlockEntity) level.getBlockEntity(s.eastHall())).setCustomName(Component.literal("Newhaven"));
		nextDawn(s, helper);
		helper.assertTrue(said(s, Direction.EAST).equals("To|Newhaven|12 blocks|"), "east after a rename and the dawn: " + said(s, Direction.EAST));
		helper.succeed();
	}
}
