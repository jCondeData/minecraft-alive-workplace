package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.Signposts;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/**
 * 36.4: a finished Signpost's four boards say where things are: the nearest other village each way (its name and how
 * far), else the nearest finished build that way, else the village's own name and "Welcome"; waxed, kept through a save
 * and load, written again at the next dawn when a village is renamed; and in another style too.
 */
public class SignpostGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	/** The signpost's origin; its post stands one block in, at (3, y, 9). */
	private static final BlockPos ORIGIN = new BlockPos(2, 2, 8);
	private static final BlockPos OWN_HALL = new BlockPos(3, 2, 3);
	private static final BlockPos EAST_HALL = new BlockPos(15, 2, 9);
	private static final int WEST = 300;
	private static final int SOUTH = 6;

	private record Scene(ServerLevel level, BlockPos post, BlockPos ownHall, BlockPos eastHall, BlockPos westHall, BlueprintData.Placement placement) {
	}

	/**
	 * Two placed halls (ours and one east), a far village west on the caravans' list and a finished Well just south;
	 * nothing else on the list, for this test (put back after).
	 */
	private static Scene scene(GameTestHelper helper, ResourceLocation signpost) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(OWN_HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(EAST_HALL, ModBlocks.VILLAGE_HALL);
		BlockPos own = helper.absolutePos(OWN_HALL);
		BlockPos east = helper.absolutePos(EAST_HALL);
		BlockPos post = helper.absolutePos(ORIGIN.offset(1, 0, 1));
		BlockPos west = post.west(WEST);
		Caravans.Data data = Caravans.Data.get(level);
		List<Caravans.Village> before = new ArrayList<>(data.villages());
		before.forEach(v -> data.remove(v.hall()));
		data.setWants(own, VillageHalls.name(level, own), List.of());
		data.setWants(east, VillageHalls.name(level, east), List.of());
		data.setWants(west, Component.literal("Westford"), List.of());
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement well = new BlueprintData.Placement(level.dimension().location(), post.south(SOUTH), Rotation.NONE, Mirror.NONE);
		sites.recordFinished(StarterBlueprints.WELL.id(), well, UUID.randomUUID());
		// The signpost, stamped block by block as a builder leaves it, and recorded as finished.
		Blueprint bp = BlueprintLibrary.get(level, signpost).orElseThrow();
		BlockPos origin = helper.absolutePos(ORIGIN);
		for (Blueprint.Entry e : bp.blocks()) {
			level.setBlock(origin.offset(e.pos()), e.state(), 2);
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		sites.recordFinished(signpost, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> {
			sites.forgetFinished(well);
			sites.forgetFinished(placement);
			data.remove(own);
			data.remove(east);
			data.remove(west);
			before.forEach(v -> data.setWants(v.hall(), v.name(), v.wants()));
			Signposts.forget();
		});
		return new Scene(level, post, own, east, west, placement);
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

	/** Whether anything finished (other than the signpost and the Well) lies north of the post: then north isn't "Welcome". */
	private static boolean anythingNorth(Scene s) {
		return BuildSiteManager.get(s.level()).finishedNear(s.level(), s.post(), Signposts.VILLAGE_RANGE).stream()
			.anyMatch(f -> !f.placement().equals(s.placement()) && f.placement().origin().getZ() < s.post().getZ()
				&& Math.abs(f.placement().origin().getX() - s.post().getX()) <= s.post().getZ() - f.placement().origin().getZ());
	}

	/** A finished signpost between two halls: four waxed boards naming the village east, the one west, the Well south. */
	//$ gametest_batch AREA '"signpostBoards"'
	@GameTest(template = AREA, batch = "signpostBoards")
	public void aFinishedSignpostNamesWhatLiesEachWay(GameTestHelper helper) {
		Scene s = scene(helper, StarterBlueprints.SIGNPOST.id());
		Map<Direction, SignBlockEntity> blank = boards(s);
		helper.assertTrue(blank.size() == 4, "the signpost has " + blank.size() + " boards, not 4");
		int written = Signposts.finished(s.level(), StarterBlueprints.SIGNPOST.id(), s.placement());
		helper.assertTrue(written == 4, "wrote " + written + " boards");
		Map<Direction, SignBlockEntity> b = boards(s);
		String eastName = VillageHalls.name(s.level(), s.eastHall()).getString();
		helper.assertTrue(said(b.get(Direction.EAST)).equals("To|" + eastName + "|12 blocks|"), "east: " + said(b.get(Direction.EAST)));
		helper.assertTrue(said(b.get(Direction.WEST)).equals("To|Westford|" + WEST + " blocks|"), "west: " + said(b.get(Direction.WEST)));
		helper.assertTrue(said(b.get(Direction.SOUTH)).equals("To|" + Blueprints.displayName(StarterBlueprints.WELL.id()).getString() + "|" + SOUTH + " blocks|"),
			"south: " + said(b.get(Direction.SOUTH)));
		if (!anythingNorth(s)) {
			String home = VillageHalls.name(s.level(), s.ownHall()).getString();
			helper.assertTrue(said(b.get(Direction.NORTH)).equals("|" + home + "|Welcome|"), "north: " + said(b.get(Direction.NORTH)));
		}
		helper.assertTrue(b.values().stream().allMatch(SignBlockEntity::isWaxed), "a board isn't waxed");
		// Nothing changed: writing again writes nothing.
		helper.assertTrue(Signposts.finished(s.level(), StarterBlueprints.SIGNPOST.id(), s.placement()) == 0, "rewrote boards that hadn't changed");
		// A save and load of each board keeps its words and its wax.
		for (SignBlockEntity sign : b.values()) {
			CompoundTag tag = sign.saveWithFullMetadata(s.level().registryAccess());
			BlockEntity back = BlockEntity.loadStatic(sign.getBlockPos(), sign.getBlockState(), tag, s.level().registryAccess());
			helper.assertTrue(back instanceof SignBlockEntity copy && copy.isWaxed() && said(copy).equals(said(sign)),
				"a board after a save and load: " + (back instanceof SignBlockEntity copy ? said(copy) : back));
		}
		helper.succeed();
	}

	/** The village east renamed: its board says the new name after the next dawn round, and not before. */
	//$ gametest_batch AREA '"signpostRenamed"'
	@GameTest(template = AREA, batch = "signpostRenamed")
	public void aRenamedVillageIsRenamedOnItAtTheNextDawn(GameTestHelper helper) {
		Scene s = scene(helper, StarterBlueprints.SIGNPOST.id());
		Signposts.finished(s.level(), StarterBlueprints.SIGNPOST.id(), s.placement());
		VillageHallBlockEntity east = (VillageHallBlockEntity) s.level().getBlockEntity(s.eastHall());
		east.setCustomName(Component.literal("Newhaven"));
		helper.assertFalse(said(boards(s).get(Direction.EAST)).contains("Newhaven"), "renamed before the dawn");
		VillageHallBlockEntity own = (VillageHallBlockEntity) s.level().getBlockEntity(s.ownHall());
		Signposts.forget();
		int changed = Signposts.round(s.level(), s.ownHall(), own);
		helper.assertTrue(said(boards(s).get(Direction.EAST)).equals("To|Newhaven|12 blocks|"), "east after the dawn: " + said(boards(s).get(Direction.EAST)));
		helper.assertTrue(changed == 1, changed + " boards changed, not the one");
		helper.assertTrue(Signposts.round(s.level(), s.ownHall(), own) == 0, "looked twice in a day");
		helper.succeed();
	}

	/** A Signpost built in Dark Oak gets words too. */
	//$ gametest_batch AREA '"signpostStyled"'
	@GameTest(template = AREA, batch = "signpostStyled")
	public void aSignpostInAnotherStyleGetsWords(GameTestHelper helper) {
		ResourceLocation styled = BlueprintStyles.styled(StarterBlueprints.SIGNPOST.id(), "dark_oak");
		Scene s = scene(helper, styled);
		helper.assertTrue(Signposts.isSignpost(styled), styled + " isn't taken for a signpost");
		helper.assertTrue(Signposts.finished(s.level(), styled, s.placement()) == 4, "the Dark Oak signpost's boards weren't written");
		Map<Direction, SignBlockEntity> b = boards(s);
		helper.assertTrue(b.size() == 4 && b.values().stream().allMatch(sign -> sign.isWaxed() && !sign.getFrontText().getMessage(1, false).getString().isEmpty()),
			"boards: " + b.values().stream().map(SignpostGameTests::said).toList());
		helper.assertTrue(said(b.get(Direction.WEST)).equals("To|Westford|" + WEST + " blocks|"), "west: " + said(b.get(Direction.WEST)));
		helper.succeed();
	}
}
