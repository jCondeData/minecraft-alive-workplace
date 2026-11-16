package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.threat.Covens;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-16): a mutation pass on {@code threat/Covens} (ROADMAP 32.11, witch covens). Of 10 mutants the
 * coven tests let 5 through; two change what a player sees and are covered here, the rest are equivalent or cosmetic
 * (a particle's height, the loop bound past the fastest throw, the "standing on the hall" guard).
 * <ul>
 * <li>"witches ... throw splash potions over [the walls] at villagers": the throw's speed comes from flying it as the
 *     game does; a villager 14 blocks off behind a 3-high wall, and one 20 blocks off on a 4-high platform, are each hit
 *     (the potion's weakness reaches them). A wrong gravity or a wrong landing check in that flight throws long or short.</li>
 * <li>"The Coven Mother ... blinks 8-12 blocks away": with the coven's own blink and a fixed random, every blink lands
 *     between near and far blocks (flat) from where she stood.</li>
 * </ul>
 */
public class QaCovensMutants1116d2GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation COVEN = AliveWorkplace.id("witch_coven");

	private static Witch witch(GameTestHelper helper, BlockPos at) {
		Witch witch = helper.spawn(EntityType.WITCH, at);
		witch.setNoAi(true);
		return witch;
	}

	private static Villager villager(GameTestHelper helper, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setNoAi(true);
		return villager;
	}

	/** Throws once at {@code target} on tick 2 and waits for the potion's weakness to reach it, reporting where the potion broke. */
	private static void throwAndWatch(GameTestHelper helper, Mob witch, Villager target, String what) {
		List<Vec3> last = new ArrayList<>();
		List<ThrownPotion> flying = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			Covens.throwAt(helper.getLevel(), witch, target, Potions.WEAKNESS);
			flying.addAll(helper.getLevel().getEntitiesOfClass(ThrownPotion.class, helper.getBounds().inflate(16), p -> p.getOwner() == witch));
			helper.assertTrue(flying.size() == 1, "setup: expected one thrown potion, got " + flying.size());
		});
		helper.onEachTick(() -> {
			if (!flying.isEmpty() && !flying.get(0).isRemoved()) {
				last.clear();
				last.add(flying.get(0).position());
			}
		});
		helper.runAfterDelay(90, () -> {
			helper.assertTrue(target.hasEffect(MobEffects.WEAKNESS),
				what + ": the witch's potion didn't reach the villager at " + target.position() + "; it was last seen at "
					+ (last.isEmpty() ? "nowhere" : last.get(0)) + (flying.isEmpty() || flying.get(0).isRemoved() ? "" : " (still flying)"));
			helper.succeed();
		});
	}

	/** Over the wall: a villager 14 blocks east, a 3-high glass wall halfway, is hit by the witch's throw. */
	//$ gametest_ticks_batch AREA '120' '"qaCovenThrowWall1116d2"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "qaCovenThrowWall1116d2")
	public void aWitchsPotionFliesOverAWallOntoAVillagerFourteenBlocksOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		for (int z = 10; z <= 20; z++) {
			for (int y = 2; y <= 4; y++) {
				helper.setBlock(new BlockPos(10, y, z), Blocks.GLASS);
			}
		}
		Witch witch = witch(helper, new BlockPos(3, 2, 15));
		Villager villager = villager(helper, new BlockPos(17, 2, 15));
		throwAndWatch(helper, witch, villager, "14 blocks over a wall");
	}

	/**
	 * Far and high: a villager 20 blocks east in the middle of a 5x5 platform 4 high (like a guard on a station) is hit
	 * by the witch's throw. (On a 1x1 pillar about 1 throw in 6 sails just over and breaks on the ground beyond, out of
	 * the splash's reach: the throw's own spread, as a witch's in the game, so the platform gives it room.)
	 */
	//$ gametest_ticks_batch AREA '120' '"qaCovenThrowHigh1116d2"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "qaCovenThrowHigh1116d2")
	public void aWitchsPotionReachesAVillagerTwentyBlocksOffOnAPlatform(GameTestHelper helper) {
		Leftovers.clear(helper);
		for (int x = 22; x <= 26; x++) {
			for (int z = 13; z <= 17; z++) {
				for (int y = 2; y <= 5; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		Witch witch = witch(helper, new BlockPos(4, 2, 15));
		Villager villager = villager(helper, new BlockPos(24, 6, 15));
		throwAndWatch(helper, witch, villager, "20 blocks off, 4 up");
	}

	/** The Coven Mother's blink, as her file says it: 16 blinks with a fixed random each land near to far blocks from where she stood. */
	//$ gametest_ticks_batch AREA '40' '"qaCovenBlinkReach1116d2"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaCovenBlinkReach1116d2")
	public void theCovenMotherAlwaysBlinksNearToFarBlocksAway(GameTestHelper helper) {
		Leftovers.clear(helper);
		Culture coven = Threats.get(COVEN).orElse(null);
		helper.assertTrue(coven != null, "setup: expected witch_coven.json loaded");
		Culture.Blink blink = coven.captain().flatMap(Culture.Captain::blinks).orElse(null);
		helper.assertTrue(blink != null && blink.near() == 8 && blink.far() == 12, "the Coven Mother's blink, expected 8 to 12 blocks: " + blink);
		Witch mother = witch(helper, new BlockPos(15, 2, 15));
		BlockPos middle = helper.absolutePos(new BlockPos(15, 2, 15));
		RandomSource random = RandomSource.create(1116L);
		helper.runAfterDelay(2, () -> {
			int blinked = 0;
			for (int i = 0; i < 16; i++) {
				mother.teleportTo(middle.getX() + 0.5, middle.getY(), middle.getZ() + 0.5);
				Vec3 from = mother.position();
				if (!Covens.blink(helper.getLevel(), mother, blink, random)) {
					continue;
				}
				blinked++;
				double dx = mother.getX() - from.x;
				double dz = mother.getZ() - from.z;
				double flat = Math.sqrt(dx * dx + dz * dz);
				helper.assertTrue(flat >= blink.near() - 0.01 && flat <= blink.far() + 0.01,
					"blink " + (i + 1) + " took her " + String.format("%.2f", flat) + " blocks (flat) from where she stood, expected " + blink.near() + " to " + blink.far());
			}
			helper.assertTrue(blinked >= 12, "setup: only " + blinked + " of 16 blinks found somewhere to land on the open floor");
			helper.succeed();
		});
	}
}
