package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.PatrolMapItem;
import io.github.jcondedata.aliveworkplace.guard.WallSentries;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-10-24) for the Wall Sentry (ROADMAP 29.15, "goes to the first point of a Patrol Map you give it
 * and never leaves it, with twice a golem's health"; WallSentries: "fights what comes within 6 blocks of the post ...
 * A blank map takes the post away"). GolemSmithGameTests proves the staged fight; these prove the rest through the
 * player's sneak-right-click: the post and the doubled health kept through a save and load (and the golem walking back
 * to it), a map of another dimension refused with its line, a blank map taking the post away with its line, and a foe
 * beyond 6 blocks of the post left alone.
 */
public class QaSentryGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	private static IronGolem sentry(GameTestHelper helper, BlockPos at) {
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(at), GolemSmith.Role.SENTRY);
		helper.assertTrue(golem != null, "no sentry was built");
		Leftovers.after(helper, golem::discard);
		return golem;
	}

	private static ItemStack map(Optional<net.minecraft.resources.ResourceLocation> dimension, List<BlockPos> points) {
		ItemStack map = new ItemStack(ModItems.PATROL_MAP);
		map.set(ModComponents.PATROL, new PatrolMapItem.Route(dimension, points));
		return map;
	}

	/** The player sneak-right-clicks the golem with {@code held}. */
	private static InteractionResult give(ServerPlayer player, IronGolem golem, ItemStack held) {
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.setShiftKeyDown(true);
		return UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, golem, null);
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * A posted sentry is saved and loaded (a chunk unloading, a restart): the copy has the same post, is still a sentry
	 * with 200 health, and, put down 9 blocks off, walks back to its post.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaSentryReload"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaSentryReload")
	public void aSentryKeepsItsPostThroughASaveAndWalksBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		IronGolem golem = sentry(helper, new BlockPos(4, 1, 4));
		BlockPos post = helper.absolutePos(new BlockPos(15, 2, 15));
		ServerPlayer player = RealmGameTests.listener(helper, new ArrayList<>(), "qa-sentry");
		helper.assertTrue(give(player, golem, map(Optional.of(level.dimension().location()), List.of(post))) == InteractionResult.SUCCESS, "the map wasn't taken");
		helper.assertTrue(post.equals(WallSentries.post(golem)), "not posted: " + WallSentries.post(golem));
		CompoundTag saved = golem.saveWithoutId(new CompoundTag());
		golem.discard();
		IronGolem copy = EntityType.IRON_GOLEM.create(level);
		copy.load(saved);
		copy.setUUID(java.util.UUID.randomUUID());
		BlockPos away = helper.absolutePos(new BlockPos(6, 2, 15));
		copy.moveTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5, 0f, 0f);
		level.addFreshEntity(copy);
		Leftovers.after(helper, copy::discard);
		helper.assertTrue(GolemSmith.role(copy) == GolemSmith.Role.SENTRY, "the loaded golem isn't a sentry: " + GolemSmith.role(copy));
		helper.assertTrue(post.equals(WallSentries.post(copy)), "the post after a load: " + WallSentries.post(copy));
		helper.assertTrue(copy.getMaxHealth() == 200f, "the health after a load: " + copy.getMaxHealth());
		Vec3 at = Vec3.atBottomCenterOf(post);
		helper.succeedWhen(() -> helper.assertTrue(horizontal(copy.position(), at) <= 1.5,
			"the loaded sentry didn't walk back to its post: " + horizontal(copy.position(), at) + " blocks off"));
	}

	/**
	 * A route drawn in the Nether is refused ("That route is in another dimension") and the post stays; a blank map
	 * takes the post away ("<name> has no post now"), and the golem is still a sentry, ready for a new map.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaSentryMaps"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaSentryMaps")
	public void anotherDimensionsMapIsRefusedAndABlankMapTakesThePost(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		IronGolem golem = sentry(helper, new BlockPos(4, 1, 4));
		BlockPos post = helper.absolutePos(new BlockPos(15, 2, 15));
		List<Component> said = new ArrayList<>();
		ServerPlayer player = RealmGameTests.listener(helper, said, "qa-sentry-maps");
		give(player, golem, map(Optional.of(level.dimension().location()), List.of(post)));
		helper.assertTrue(post.equals(WallSentries.post(golem)), "not posted: " + WallSentries.post(golem));
		String name = golem.getDisplayName().getString();
		helper.assertTrue(said.stream().anyMatch(c -> c.getString().equals(name + " will hold the wall at " + post.getX() + " " + post.getY() + " " + post.getZ())),
			"the posted line: " + said.stream().map(Component::getString).toList());
		said.clear();
		InteractionResult nether = give(player, golem, map(Optional.of(Level.NETHER.location()), List.of(post.offset(5, 0, 5))));
		helper.assertTrue(nether == InteractionResult.FAIL, "a Nether route was taken: " + nether);
		helper.assertTrue(post.equals(WallSentries.post(golem)), "a Nether route moved the post: " + WallSentries.post(golem));
		helper.assertTrue(said.stream().anyMatch(c -> c.getString().equals("That route is in another dimension")),
			"the refusal: " + said.stream().map(Component::getString).toList());
		said.clear();
		InteractionResult blank = give(player, golem, map(Optional.of(level.dimension().location()), List.of()));
		helper.assertTrue(blank == InteractionResult.SUCCESS, "the blank map: " + blank);
		helper.assertTrue(WallSentries.post(golem) == null && !ModAttachments.GOLEM_POST.has(golem), "a blank map left the post: " + WallSentries.post(golem));
		helper.assertTrue(GolemSmith.role(golem) == GolemSmith.Role.SENTRY, "no longer a sentry: " + GolemSmith.role(golem));
		helper.assertTrue(said.stream().anyMatch(c -> c.getString().equals(name + " has no post now")),
			"the taken line: " + said.stream().map(Component::getString).toList());
		helper.succeed();
	}

	/**
	 * A foe standing 10 blocks from the post (beyond the 6 it holds) is let alone: the sentry, even set on it, stays
	 * within its leash of the post for 200 ticks and the foe is unhurt.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaSentryRange"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaSentryRange")
	public void aFoeBeyondSixBlocksOfThePostIsLeftAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		IronGolem golem = sentry(helper, new BlockPos(14, 1, 15));
		BlockPos post = helper.absolutePos(new BlockPos(15, 2, 15));
		ServerPlayer player = RealmGameTests.listener(helper, new ArrayList<>(), "qa-sentry-range");
		give(player, golem, map(Optional.of(level.dimension().location()), List.of(post)));
		Vec3 at = Vec3.atBottomCenterOf(post);
		long[] start = {-1};
		Zombie[] foe = {null};
		helper.onEachTick(() -> {
			double d = horizontal(golem.position(), at);
			if (foe[0] == null && d < 1.5) {
				Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(25, 2, 15));
				zombie.setNoAi(true);
				zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // (no burning in the sun)
				foe[0] = zombie;
				golem.setTarget(zombie);
				start[0] = helper.getTick();
			}
			if (foe[0] != null) {
				helper.assertTrue(d <= WallSentries.LEASH + 0.5, "the sentry went after a foe 10 blocks off: " + d + " blocks from its post");
				if (helper.getTick() - start[0] >= 200) {
					helper.assertTrue(foe[0].isAlive() && foe[0].getHealth() == foe[0].getMaxHealth(), "the foe 10 blocks off was hurt: " + foe[0].getHealth());
					helper.succeed();
				}
			}
		});
	}
}
