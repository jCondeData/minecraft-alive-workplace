package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (2026-11-22, qa-lane-b-1122-1538) past B1175's fix (04691f3f7), from the bug and the wiki: in the game a standing
 * banner at rotation 0 faces south (+z), the back of a blueprint, so a camp's standing banner faces the camp's front
 * (its z = 0 side, north) at rotation 8; docs/wiki/warbands.md: the War Camp's pole flies the ominous banner "which
 * faces the gate"; docs/wiki/piglins.md: the Blackstone Outpost's basalt pillar flies the piglin banner "facing the
 * front".
 * <ul>
 * <li>Every standing banner in every camp drawing (data/aliveworkplace/structure/camp) faces the drawing's front, and
 * the War Camp and the Blackstone Outpost each have one.</li>
 * <li>A War Camp, a Blackstone Outpost and a Bandit Camp set down through the mod's own entry point
 * ({@code Lairs.found}, which turns a camp at random) face their banners to the camp's turned front in all four turns.
 * The level's random gets fixed seeds so the turns seen are the same every run.</li>
 * </ul>
 */
public class QaCampBanners1122bGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final List<String> CAMPS = List.of("bandit_camp", "blackstone_outpost", "colony_camp", "coven_hut", "pirate_ship", "settlers_camp",
		"tomb_camp", "war_camp");
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos GROUND = new BlockPos(15, 1, 15);

	/** A block of a drawing: where it is in the drawing, and what. */
	record Cell(BlockPos pos, BlockState state) {
	}

	/** Every block a drawing holds (structure voids left out), read from the template as it is saved. */
	static List<Cell> cells(ServerLevel level, StructureTemplate template) {
		CompoundTag tag = template.save(new CompoundTag());
		HolderGetter<Block> blocks = level.holderLookup(Registries.BLOCK);
		ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
		List<BlockState> states = new ArrayList<>();
		for (int i = 0; i < palette.size(); i++) {
			states.add(NbtUtils.readBlockState(blocks, palette.getCompound(i)));
		}
		List<Cell> out = new ArrayList<>();
		ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag b = list.getCompound(i);
			ListTag pos = b.getList("pos", Tag.TAG_INT);
			out.add(new Cell(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), states.get(b.getInt("state"))));
		}
		return out;
	}

	/**
	 * Which way a standing banner faces (its front, the side its pattern shows on), or empty for a rotation between the
	 * four sides. The bug: "in the game a standing banner at rotation 0 faces south (+z)"; RotationSegment gives the
	 * way a rotation points (rotation 0: north, the way a player placing it looked), so the front is its opposite.
	 */
	static Optional<Direction> facing(BlockState banner) {
		return RotationSegment.convertToDirection(banner.getValue(BlockStateProperties.ROTATION_16)).map(Direction::getOpposite);
	}

	private static StructureTemplate template(GameTestHelper helper, ResourceLocation id) {
		return helper.getLevel().getStructureManager().get(id).orElseThrow(() -> new GameTestAssertException("no structure " + id));
	}

	// ------------------------------------------------------------------------------------------------ the drawings

	/** Every standing banner of every camp drawing faces the drawing's front (north, its z = 0 side). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyCampDrawingsStandingBannersFaceItsFront(GameTestHelper helper) {
		BlockState zero = Blocks.WHITE_BANNER.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 0);
		helper.assertTrue(facing(zero).equals(Optional.of(Direction.SOUTH)), "self-check: a standing banner at rotation 0 reads as facing " + facing(zero));
		List<String> wrong = new ArrayList<>();
		List<String> seen = new ArrayList<>();
		for (String camp : CAMPS) {
			StructureTemplate t = template(helper, AliveWorkplace.id("camp/" + camp));
			for (Cell c : cells(helper.getLevel(), t)) {
				if (c.state().getBlock() instanceof BannerBlock) {
					Optional<Direction> d = facing(c.state());
					seen.add(camp + " " + c.pos().toShortString() + " " + d.map(Direction::getName).orElse("rotation " + c.state().getValue(BlockStateProperties.ROTATION_16)));
					if (d.isEmpty() || d.get() != Direction.NORTH) {
						wrong.add(camp + ": " + c.state() + " at " + c.pos().toShortString() + " faces " + d.map(Direction::getName).orElse("between sides"));
					}
				}
			}
		}
		helper.assertTrue(seen.stream().anyMatch(s -> s.startsWith("war_camp ")), "the War Camp drawing has no standing banner (its ominous banner's pole): " + seen);
		helper.assertTrue(seen.stream().anyMatch(s -> s.startsWith("blackstone_outpost ")), "the Blackstone Outpost drawing has no standing banner (its piglin banner): " + seen);
		helper.assertTrue(wrong.isEmpty(), "standing banners in camp drawings that don't face the camp's front (north): " + wrong);
		LOGGER.info("qa 1122b camp banners: every standing banner faces its drawing's front: {}", seen);
		helper.succeed();
	}

	// ------------------------------------------------------------------------------------------------ set down

	/** A Town's village hall at {@link #HALL} with lairs on; everything put back when the test ends. */
	private static BlockPos town(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		Lairs.forget(level);
		VillageRaids.forget();
		int was = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean lairs = Lairs.ENABLED;
		VillageHalls.RADIUS = 20;
		Lairs.ENABLED = true;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		ThreatData.get(level).forgetClock(hall);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(64), m -> !(m instanceof Villager)
				&& (m.getTags().contains(Lairs.TAG) || m.getTags().contains(VillageRaids.TAG))).forEach(Mob::discard);
			ThreatData.get(level).end(hall);
			Lairs.forget(level);
			VillageRaids.forget();
			BanditCamps.forget(level);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = was;
			Lairs.ENABLED = lairs;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
		level.setDayTime(44 * 24000L + 2000);
		return hall;
	}

	/**
	 * The turn the camp {@code template} stands in round {@code ground} (its middle, as the lair sets it down): the turn
	 * whose blocks match the world best, and at least nine in ten of them.
	 */
	private static Rotation standing(ServerLevel level, StructureTemplate template, List<Cell> cells, BlockPos ground, String what) {
		Rotation best = null;
		int bestScore = -1;
		int solid = 0;
		for (Rotation r : Rotation.values()) {
			BlockPos origin = origin(template, ground, r);
			StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(r);
			int score = 0;
			solid = 0;
			for (Cell c : cells) {
				if (c.state().isAir()) {
					continue;
				}
				solid++;
				if (level.getBlockState(origin.offset(StructureTemplate.calculateRelativePosition(settings, c.pos()))).is(c.state().getBlock())) {
					score++;
				}
			}
			if (score > bestScore) {
				bestScore = score;
				best = r;
			}
		}
		if (bestScore * 10 < solid * 9) {
			throw new GameTestAssertException(what + ": the camp set down matches its drawing in no turn (best " + best + ": " + bestScore + " of " + solid + " blocks)");
		}
		return best;
	}

	/** Where the camp's corner is when its middle is at {@code ground} and it is turned {@code r} (Lairs.found's way). */
	private static BlockPos origin(StructureTemplate template, BlockPos ground, Rotation r) {
		Vec3i size = template.getSize(r);
		return template.getZeroPositionWithTransform(ground.offset(-size.getX() / 2, 0, -size.getZ() / 2), Mirror.NONE, r);
	}

	/**
	 * The lair of {@code culture} set down by the hall again and again (the level's random seeded 0, 1, 2, ...) until
	 * it has stood in all four turns; each time every standing banner of its drawing is in the world as a standing
	 * banner facing the camp's turned front.
	 */
	private static void bannersFaceTheFront(GameTestHelper helper, ResourceLocation culture, String camp) {
		BlockPos hall = town(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			StructureTemplate template = template(helper, AliveWorkplace.id("camp/" + camp));
			List<Cell> cells = cells(level, template);
			List<Cell> banners = cells.stream().filter(c -> c.state().getBlock() instanceof BannerBlock).toList();
			helper.assertTrue(!banners.isEmpty(), camp + ": its drawing has no standing banner");
			Set<Rotation> turns = EnumSet.noneOf(Rotation.class);
			List<String> wrong = new ArrayList<>();
			BlockPos ground = helper.absolutePos(GROUND);
			for (long seed = 0; seed < 40 && turns.size() < 4; seed++) {
				level.random.setSeed(seed);
				Lairs.Lair lair = Lairs.found(level, hall, ground, culture);
				helper.assertTrue(lair != null && lair.culture().equals(culture), culture + ": no lair (seed " + seed + "): " + lair);
				Rotation r = standing(level, template, cells, ground, camp + " seed " + seed);
				turns.add(r);
				Direction front = r.rotate(Direction.NORTH);
				BlockPos origin = origin(template, ground, r);
				StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(r);
				for (Cell c : banners) {
					BlockPos at = origin.offset(StructureTemplate.calculateRelativePosition(settings, c.pos()));
					BlockState there = level.getBlockState(at);
					Optional<Direction> d = there.getBlock() instanceof BannerBlock ? facing(there) : Optional.empty();
					if (d.isEmpty() || d.get() != front) {
						wrong.add(camp + " turned " + r + " (front " + front.getName() + "): the banner drawn at " + c.pos().toShortString() + " is " + there
							+ " at " + helper.relativePos(at).toShortString() + ", facing " + d.map(Direction::getName).orElse("no side"));
					}
				}
				// Gone again for the next turn: its band and its blocks
				List<Mob> band = new ArrayList<>(Lairs.band(level, lair));
				Entity captain = level.getEntity(lair.captain());
				if (captain != null) {
					captain.discard();
				}
				band.forEach(Mob::discard);
				Lairs.forget(level);
				for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 2, 3)), helper.absolutePos(new BlockPos(29, 20, 29)))) {
					if (!level.getBlockState(p).isAir()) {
						level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
					}
				}
			}
			helper.assertTrue(turns.size() == 4, camp + ": set down in only these turns in 40 tries: " + turns);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " banner(s) not facing the camp's front: " + wrong);
			LOGGER.info("qa 1122b {}: set down in {}, its {} standing banner(s) face the turned front each time", camp, turns, banners.size());
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch HUGE '200' '"qa1122bWarCampBanner"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1122bWarCampBanner")
	public void aWarCampSetDownInEveryTurnFacesItsBannerToItsFront(GameTestHelper helper) {
		bannersFaceTheFront(helper, AliveWorkplace.id("pillager_warband"), "war_camp");
	}

	//$ gametest_ticks_batch HUGE '200' '"qa1122bOutpostBanner"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1122bOutpostBanner")
	public void aBlackstoneOutpostSetDownInEveryTurnFacesItsBannerToItsFront(GameTestHelper helper) {
		bannersFaceTheFront(helper, AliveWorkplace.id("piglin_incursion"), "blackstone_outpost");
	}

	//$ gametest_ticks_batch HUGE '200' '"qa1122bBanditCampBanner"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1122bBanditCampBanner")
	public void aBanditCampSetDownInEveryTurnFacesItsBannersToItsFront(GameTestHelper helper) {
		bannersFaceTheFront(helper, AliveWorkplace.id("bandits"), "bandit_camp");
	}
}
