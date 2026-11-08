package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.bounty.Rogues;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * QA, lane D (2026-11-08), from 31.14's spec: with Cobblemon, a hall's round posts a rogue Pokémon instead of a bandit
 * captain half the time, and a captain when no spot round the village has a rogue. Mutation of Bounties' round (:434)
 * left alive a change to that choice (a captain as well as a rogue, or none when the rogue can't be posted); no test
 * ran the round with Cobblemon and counted what it posted. The rogue groups are swapped for one covering every biome
 * (or none) through the package-private {@code Rogues.setGroups}, so the land under the test world doesn't decide.
 */
public class QaRogueOrCaptain1108dCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final int ROUNDS = 16;

	private static void setGroups(Map<ResourceLocation, Rogues.Group> groups) {
		try {
			Method m = Rogues.class.getDeclaredMethod("setGroups", Map.class);
			m.setAccessible(true);
			m.invoke(null, groups);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Rogues.setGroups(Map) not callable: " + e, e);
		}
	}

	/** A Village-rank hall, bounties and rogues on, posting by itself; the rogue groups as given; all put back after. */
	private static BlockPos village(GameTestHelper helper, Map<ResourceLocation, Rogues.Group> groups) {
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean enabled = Bounties.ENABLED;
		boolean auto = Bounties.AUTO;
		boolean rogues = Rogues.ENABLED;
		Map<ResourceLocation, Rogues.Group> was = Rogues.groups();
		VillageHalls.RADIUS = 10;
		Bounties.ENABLED = true;
		Bounties.AUTO = true;
		Rogues.ENABLED = true;
		Bounties.forget(level);
		Lairs.forget(level);
		setGroups(groups);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		hall.setLastQuestDay(Long.MAX_VALUE / 2);
		hall.setRank(VillageRanks.Rank.VILLAGE);
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Bounties.ENABLED = enabled;
			Bounties.AUTO = auto;
			Rogues.ENABLED = rogues;
			setGroups(was);
			Bounties.forget(level);
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
		});
		helper.assertTrue(Rogues.available(), "rogues aren't available with Cobblemon loaded");
		return helper.absolutePos(HALL);
	}

	/** A rogue group naming every biome the world knows, with a species Cobblemon has. */
	private static Map<ResourceLocation, Rogues.Group> everywhere(ServerLevel level) {
		ResourceLocation id = AliveWorkplace.id("qa_everywhere");
		List<ResourceLocation> biomes = List.copyOf(level.registryAccess().registryOrThrow(Registries.BIOME).keySet());
		return Map.of(id, new Rogues.Group(id, biomes, List.of(), List.of(ResourceLocation.parse("cobblemon:tauros"))));
	}

	private static List<Bounties.Bounty> posters(ServerLevel level, BlockPos hall) {
		return Bounties.open(level, hall).stream().filter(b -> !b.lair).toList();
	}

	/**
	 * Over 16 rounds (each on a fresh board), every round posts exactly one poster, a rogue or a captain, never both and
	 * never none; both kinds come up (each is half the time, so all 16 one kind is a 1 in 32,768 chance).
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_rogue_or_captain_1108d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_rogue_or_captain_1108d")
	public void eachRoundPostsOneRogueOrOneCaptainNeverBoth(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper, everywhere(level));
		int rogues = 0;
		int captains = 0;
		for (int i = 0; i < ROUNDS; i++) {
			Bounties.forget(level);
			Bounties.round(level, hall);
			List<Bounties.Bounty> posted = posters(level, hall);
			int r = (int) posted.stream().filter(b -> b.rogue != null).count();
			int c = posted.size() - r;
			helper.assertTrue(posted.size() == 1, "round " + i + " posted " + r + " rogue(s) and " + c + " captain(s), not exactly one poster");
			rogues += r;
			captains += c;
		}
		helper.assertTrue(rogues > 0, "with Cobblemon, " + ROUNDS + " rounds posted no rogue (" + captains + " captains)");
		helper.assertTrue(captains > 0, "with Cobblemon, " + ROUNDS + " rounds posted no captain (" + rogues + " rogues)");
		helper.succeed();
	}

	/** With no biome round the village holding rogues, every round still posts its captain (one, never nothing). */
	//$ gametest_ticks_batch AREA '200' '"qa_no_rogue_captain_1108d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_no_rogue_captain_1108d")
	public void withNoRogueToPostTheRoundPostsACaptain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper, Map.of());
		for (int i = 0; i < ROUNDS / 2; i++) {
			Bounties.forget(level);
			Bounties.round(level, hall);
			List<Bounties.Bounty> posted = posters(level, hall);
			helper.assertTrue(posted.size() == 1 && posted.get(0).rogue == null,
				"round " + i + " with no rogue to post: " + posted.stream().map(b -> b.rogue == null ? "captain " + b.quirk : "rogue " + b.rogue).toList()
					+ ", not one captain");
		}
		helper.succeed();
	}
}
