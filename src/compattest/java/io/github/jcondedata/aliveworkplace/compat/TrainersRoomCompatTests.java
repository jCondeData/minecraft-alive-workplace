package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnishingTable;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.PokemonPartners;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 37.4, the owner's answer of 2026-10-18: a home with a Pokémon partner (one kept in a pasture by the plot) gets
 * CobbleFurnies' Poké Ball chair and desk in its Trainer's room even with Handcrafted first; a home without one keeps
 * Handcrafted's chair and table there.
 */
public class TrainersRoomCompatTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final ResourceLocation COTTAGE = AliveWorkplace.id("starter_cottage");

	//$ gametest_ticks_batch AREA '200' '"trainers_room"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "trainers_room")
	public void aPartneredHomeGetsThePokeBallChairAndDesk(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(2, 2, 2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PastureCompatTests.pastured(helper, pasture, player, "pikachu", Direction.NORTH);
		// The Pikachu comes out of the pasture and lands within a few ticks; checked until it is there.
		helper.succeedWhen(() -> {
			try {
				new WorkplaceConfig().apply();
				Furnishings.MODS = List.of("handcrafted", "beautify", "cobblefurnies");
				List<FurnitureSpot> spots = BlueprintFurniture.of(COTTAGE);
				Map<Integer, FurnishingTable.Piece> room = FurnitureChoice.trainersRoom(spots);
				helper.assertTrue(room.size() == 2, "the cottage's Trainer's room should be a chair and a desk: " + room.keySet());
				helper.assertTrue(FurnitureChoice.hasPartner(level, helper.absolutePos(new BlockPos(4, 4, 4))), "the pastured Pikachu isn't a partner");

				// Partnered: the Poké Ball chair and desk, though Handcrafted comes first.
				BuildSite home = site(helper, helper.absolutePos(new BlockPos(4, 4, 4)));
				home.plan(level);
				FurnitureChoice partnered = home.furniture();
				room.forEach((i, piece) -> {
					FurnitureChoice.Placed placed = partnered.spots().get(i);
					helper.assertTrue(placed != null && placed.mod().equals("cobblefurnies"), "spot " + i + " (" + spots.get(i).kind().id() + ") is " + (placed == null ? "vanilla" : placed.mod()));
					String want = piece == FurnitureChoice.POKE_BALL_CHAIR ? "cobblefurnies:poke_ball_chair" : "cobblefurnies:poke_ball_desk";
					helper.assertTrue(blocks(placed).contains(want), "spot " + i + " has " + blocks(placed) + ", not " + want);
				});
				helper.assertTrue(blocks(partnered).contains("cobblefurnies:poke_ball_chair") && blocks(partnered).contains("cobblefurnies:poke_ball_desk"),
					"no Poké Ball pieces planned");
				// The rest of the house still follows the order: the second chair is Handcrafted's.
				for (int i = 0; i < spots.size(); i++) {
					if (!room.containsKey(i) && spots.get(i).kind() == FurnitureSpot.Kind.CHAIR) {
						helper.assertTrue("handcrafted".equals(partnered.spots().get(i).mod()), "another chair left the order: " + partnered.spots().get(i).mod());
					}
				}

				// Unpartnered (far from any pasture): Handcrafted's chair and table in the same spots.
				BlockPos far = helper.absolutePos(new BlockPos(4, 4, 4)).offset(Partners.RADIUS * 4, 0, 0);
				helper.assertTrue(!FurnitureChoice.hasPartner(level, far), "a partner far from the pasture");
				FurnitureChoice plain = FurnitureChoice.forNewBuild(COTTAGE, level, far);
				room.keySet().forEach(i -> helper.assertTrue("handcrafted".equals(plain.spots().get(i).mod()),
					"unpartnered spot " + i + " is " + plain.spots().get(i).mod()));
				helper.assertTrue(!blocks(plain).contains("cobblefurnies:poke_ball_chair"), "a Poké Ball chair with no partner");

				// CobbleFurnies left out of the list: no Poké Ball pieces even with a partner.
				Furnishings.MODS = List.of("handcrafted", "beautify");
				helper.assertTrue(!blocks(FurnitureChoice.forNewBuild(COTTAGE, level, helper.absolutePos(new BlockPos(4, 4, 4)))).contains("cobblefurnies:poke_ball_chair"),
					"a Poké Ball chair though CobbleFurnies isn't in furnitureMods");
				// Done: the Pikachu goes, so it doesn't wander into a later batch.
				PokemonPartners.EXTENSION.call(p -> p.pastured(level, pasture, 16), List.<Entity>of()).forEach(Entity::discard);
			} finally {
				new WorkplaceConfig().apply();
			}
		});
	}


	/**
	 * The Trainer's room is the home's first chair spot even when that is the very first spot of the file, and the desk
	 * nearest it; a later chair isn't taken instead (mutation check, 37.4).
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void theTrainersRoomTakesTheFirstChairEvenAtSpotZero(GameTestHelper helper) {
		List<FurnitureSpot> spots = List.of(
			spot(FurnitureSpot.Kind.CHAIR, new BlockPos(1, 1, 1)),
			spot(FurnitureSpot.Kind.BED, new BlockPos(6, 1, 6)),
			spot(FurnitureSpot.Kind.CHAIR, new BlockPos(12, 1, 12)),
			spot(FurnitureSpot.Kind.DESK, new BlockPos(2, 1, 1)),
			spot(FurnitureSpot.Kind.DESK, new BlockPos(13, 1, 12)));
		Map<Integer, FurnishingTable.Piece> room = FurnitureChoice.trainersRoom(spots);
		helper.assertTrue(room.get(0) == FurnitureChoice.POKE_BALL_CHAIR, "the first chair (spot 0) isn't the Trainer's chair: " + room.keySet());
		helper.assertTrue(room.get(3) == FurnitureChoice.POKE_BALL_DESK, "the desk beside it isn't the Trainer's desk: " + room.keySet());
		helper.assertTrue(room.size() == 2, "the Trainer's room should be one chair and one desk: " + room.keySet());
		// The desk may be the file's first spot too.
		Map<Integer, FurnishingTable.Piece> deskFirst = FurnitureChoice.trainersRoom(List.of(
			spot(FurnitureSpot.Kind.DESK, new BlockPos(2, 1, 1)),
			spot(FurnitureSpot.Kind.BED, new BlockPos(6, 1, 6)),
			spot(FurnitureSpot.Kind.CHAIR, new BlockPos(1, 1, 1))));
		helper.assertTrue(deskFirst.get(2) == FurnitureChoice.POKE_BALL_CHAIR && deskFirst.get(0) == FurnitureChoice.POKE_BALL_DESK,
			"a desk at spot 0 was left out of the Trainer's room: " + deskFirst.keySet());
		helper.succeed();
	}

	/**
	 * The Trainer's desk is in the chair's room: within three blocks each way across the floor (one at three is taken) and
	 * on the chair's own floor; a desk further off along one side only, or a floor up, is another room's, so the room is
	 * the chair alone (mutation check, 37.4).
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void theTrainersDeskIsInTheChairsRoom(GameTestHelper helper) {
		BlockPos chair = new BlockPos(5, 1, 5);
		BlockPos bed = new BlockPos(12, 1, 12);
		Map<String, BlockPos> elsewhere = Map.of(
			"five blocks along x", chair.offset(5, 0, 0),
			"five blocks along z", chair.offset(0, 0, 5),
			"a floor up", chair.offset(1, 2, 0));
		elsewhere.forEach((where, desk) -> {
			Map<Integer, FurnishingTable.Piece> room = FurnitureChoice.trainersRoom(List.of(
				spot(FurnitureSpot.Kind.CHAIR, chair), spot(FurnitureSpot.Kind.BED, bed), spot(FurnitureSpot.Kind.DESK, desk)));
			helper.assertTrue(room.keySet().equals(java.util.Set.of(0)), "a desk " + where + " was taken into the Trainer's room: " + room.keySet());
		});
		Map<String, BlockPos> near = Map.of(
			"three blocks along z", chair.offset(0, 0, 3),
			"three blocks along x", chair.offset(-3, 0, 0),
			"a step down", chair.offset(1, -1, 1));
		near.forEach((where, desk) -> {
			Map<Integer, FurnishingTable.Piece> room = FurnitureChoice.trainersRoom(List.of(
				spot(FurnitureSpot.Kind.CHAIR, chair), spot(FurnitureSpot.Kind.BED, bed), spot(FurnitureSpot.Kind.DESK, desk)));
			helper.assertTrue(room.get(2) == FurnitureChoice.POKE_BALL_DESK, "the desk " + where + " was left out of the Trainer's room: " + room.keySet());
		});
		// A home with a bed and no chair has no Trainer's room
		helper.assertTrue(FurnitureChoice.trainersRoom(List.of(spot(FurnitureSpot.Kind.BED, bed), spot(FurnitureSpot.Kind.DESK, chair))).isEmpty(),
			"a Trainer's room in a home without a chair");
		helper.succeed();
	}

	private static FurnitureSpot spot(FurnitureSpot.Kind kind, BlockPos pos) {
		return new FurnitureSpot(kind, pos, Direction.NORTH, "oak", null, List.of());
	}

	private static BuildSite site(GameTestHelper helper, BlockPos origin) {
		return new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", COTTAGE,
			new BlueprintData.Placement(helper.getLevel().dimension().location(), origin, Rotation.NONE, Mirror.NONE));
	}

	private static List<String> blocks(FurnitureChoice.Placed placed) {
		return placed.cells().values().stream().map(BlockState::getBlock).map(b -> BuiltInRegistries.BLOCK.getKey(b).toString()).toList();
	}

	private static List<String> blocks(FurnitureChoice choice) {
		return choice.spots().values().stream().flatMap(p -> blocks(p).stream()).toList();
	}
}
