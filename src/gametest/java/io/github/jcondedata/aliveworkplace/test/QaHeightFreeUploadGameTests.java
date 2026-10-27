package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1026-0338) for three of the builder's switches and rules, from the wiki (builder.md) and the guide
 * (game-rules.md, blueprint-table.md), not from the code:
 * <ul>
 * <li>Safe at height (B120): "A Builder with a build (lead or helper) takes no fall damage ... and can step, jump or be
 * shoved off them. Any other harm still hurts him, and a Builder with no build falls like any villager." Real drops
 * from ten blocks up, not only a fall damage source handed in (B120GameTests does that).</li>
 * <li>{@code workplaceFreeMaterials} (false): "Builders place blocks without needing materials".</li>
 * <li>{@code workplaceAllowUploads} (true): "Anyone may upload blueprint files at a Blueprint Table (operators always
 * can)".</li>
 * </ul>
 */
public class QaHeightFreeUploadGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos ORIGIN = new BlockPos(6, 2, 9);
	private static final int DROP = 10; // blocks above standing height: 7 health of fall damage for a villager (20)

	// --- safe at height (B120) ----------------------------------------------------------------------------------------

	/**
	 * A lead waiting for materials (he has a build) is dropped from ten blocks up and lands unhurt; once his build is
	 * finished and he has had time to notice, the same drop hurts him like any villager.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaHfuLeadFall"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaHfuLeadFall")
	public void aLeadFallsUnhurtMidBuildAndHurtOnceItIsDone(GameTestHelper helper) {
		ServerLevel level = prepare(helper, false);
		BlockPos origin = new BlockPos(10, 2, 10);
		ResourceLocation id = row(helper, origin, new Vec3i(3, 1, 1), Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.COBBLESTONE);
		Villager lead = builder(helper, BENCH, CHEST);
		BuildSite site = Builders.start(level, lead, null, id, placement(helper, origin));
		helper.assertTrue(site != null, "the builder took no build");
		BlockPos spot = new BlockPos(22, 2, 22);
		when(helper, () -> site.status() == BuildSite.Status.WAITING_FOR_MATERIALS, 1200, () -> "the lead never waited for the cobblestone: " + site.status(), () ->
			drop(helper, lead, spot, hurt -> {
				helper.assertTrue(ModAttachments.BUILDER_JOB.has(lead), "the lead lost his build while falling");
				helper.assertTrue(hurt == 0, "a lead with a build (waiting for materials) took " + hurt + " fall damage from " + DROP + " blocks");
				set(level, ModGameRules.FREE_MATERIALS, true);
				when(helper, () -> BuildSiteManager.get(level).get(site.id()) == null, 2000,
					() -> "the build never finished with free materials: status=" + site.status() + " missing=" + site.missing(), () ->
						helper.runAfterDelay(100, () -> {
							helper.assertFalse(ModAttachments.BUILDER_JOB.has(lead), "the builder still holds a job 100 ticks after his only build finished");
							drop(helper, lead, spot.west(4), hurt2 -> {
								helper.assertTrue(hurt2 >= 4, "a builder with no build (his build done) took only " + hurt2 + " damage from a " + DROP + "-block fall");
								helper.succeed();
							});
						}));
			}));
	}

	/** A builder at work is spared only the fall: a zombie's blow and standing in lava still hurt him mid-build. */
	//$ gametest_ticks_batch HUGE '1200' '"qaHfuOtherHarm"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaHfuOtherHarm")
	public void aBuilderAtWorkIsStillHurtByAMobAndByLava(GameTestHelper helper) {
		ServerLevel level = prepare(helper, false);
		BlockPos origin = new BlockPos(10, 2, 10);
		ResourceLocation id = row(helper, origin, new Vec3i(2, 1, 1), Blocks.COBBLESTONE, Blocks.COBBLESTONE);
		Villager lead = builder(helper, BENCH, CHEST);
		BuildSite site = Builders.start(level, lead, null, id, placement(helper, origin));
		helper.assertTrue(site != null && ModAttachments.BUILDER_JOB.has(lead), "the builder took no build");
		helper.runAfterDelay(20, () -> {
			Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(25, 2, 25));
			zombie.setNoAi(true);
			float full = lead.getHealth();
			lead.invulnerableTime = 0;
			lead.hurt(level.damageSources().mobAttack(zombie), 3f);
			helper.assertTrue(lead.getHealth() < full, "a zombie's blow didn't hurt a builder at work: " + lead.getHealth() + " of " + full);
			zombie.discard();
			lead.setHealth(lead.getMaxHealth());
			lead.invulnerableTime = 0;
			BlockPos pool = new BlockPos(20, 2, 4);
			helper.setBlock(pool, Blocks.LAVA);
			BlockPos abs = helper.absolutePos(pool);
			lead.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
			float before = lead.getHealth();
			helper.runAfterDelay(10, () -> {
				float after = lead.getHealth();
				helper.setBlock(pool, Blocks.AIR);
				lead.clearFire();
				helper.assertTrue(ModAttachments.BUILDER_JOB.has(lead), "the builder lost his build");
				helper.assertTrue(after < before, "lava didn't hurt a builder at work: " + after + " of " + before);
				helper.succeed();
			});
		});
	}

	/** A helper in a crew (a second builder who joined the lead's tower) is spared the fall too. */
	//$ gametest_ticks_batch HUGE '2400' '"qaHfuHelperFall"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaHfuHelperFall")
	public void aHelperInACrewFallsUnhurt(GameTestHelper helper) {
		ServerLevel level = prepare(helper, true);
		set(level, ModGameRules.BUILDERS_HELP, true);
		Villager lead = builder(helper, BENCH, CHEST);
		Villager mate = builder(helper, new BlockPos(2, 2, 8), new BlockPos(2, 2, 10));
		BuildSite site = Builders.start(level, lead, null, StarterBlueprints.PALISADE_TOWER.id(), placement(helper, new BlockPos(12, 2, 12)));
		helper.assertTrue(site != null, "the Palisade Tower didn't start");
		when(helper, () -> Builders.isHelping(mate), 1200, () -> "the second builder never joined the crew", () ->
			drop(helper, mate, new BlockPos(26, 2, 3), hurt -> {
				helper.assertTrue(Builders.isHelping(mate) || BuildSiteManager.get(level).get(site.id()) == null, "the helper left the crew mid-fall");
				helper.assertTrue(hurt == 0, "a helper mid-crew took " + hurt + " fall damage from " + DROP + " blocks");
				helper.succeed();
			}));
	}

	// --- workplaceFreeMaterials -------------------------------------------------------------------------------------

	/**
	 * Free materials, empty chest: a row of cobblestone and planks with a spawner, bedrock and a command block in it is
	 * finished; the ordinary blocks are placed and the never-placed ones still aren't.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaHfuFreeEmpty"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaHfuFreeEmpty")
	public void freeMaterialsFinishWithEmptyChestsButNeverPlaceTheForbidden(GameTestHelper helper) {
		ServerLevel level = prepare(helper, true);
		Block[] blocks = {Blocks.COBBLESTONE, Blocks.SPAWNER, Blocks.OAK_PLANKS, Blocks.BEDROCK, Blocks.COBBLESTONE, Blocks.COMMAND_BLOCK};
		ResourceLocation id = row(helper, ORIGIN, new Vec3i(blocks.length, 1, 1), blocks);
		Villager v = builder(helper, BENCH, CHEST);
		BuildSite site = Builders.start(level, v, null, id, placement(helper, ORIGIN));
		helper.assertTrue(site != null, "the builder took no build");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "not finished with free materials and an empty chest: status="
				+ site.status() + " missing=" + site.missing());
			for (int x = 0; x < blocks.length; x++) {
				Block b = blocks[x];
				boolean ordinary = b == Blocks.COBBLESTONE || b == Blocks.OAK_PLANKS;
				helper.assertBlockPresent(ordinary ? b : Blocks.AIR, ORIGIN.offset(x, 0, 0));
			}
		});
	}

	/** Free materials with a chest that does hold everything: the build finishes and nothing is taken from it. */
	//$ gametest_ticks_batch AREA '2400' '"qaHfuFreeStocked"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaHfuFreeStocked")
	public void freeMaterialsLeaveAStockedChestUntouched(GameTestHelper helper) {
		ServerLevel level = prepare(helper, true);
		ResourceLocation id = row(helper, ORIGIN, new Vec3i(4, 1, 1), Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.COBBLESTONE, Blocks.OAK_PLANKS);
		Villager v = builder(helper, BENCH, CHEST, new ItemStack(Items.COBBLESTONE, 2), new ItemStack(Items.OAK_PLANKS, 2), new ItemStack(Items.BREAD, 5));
		BuildSite site = Builders.start(level, v, null, id, placement(helper, ORIGIN));
		helper.assertTrue(site != null, "the builder took no build");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "not finished with free materials: status=" + site.status());
			for (int x = 0; x < 4; x++) {
				helper.assertBlockPresent(x % 2 == 0 ? Blocks.COBBLESTONE : Blocks.OAK_PLANKS, ORIGIN.offset(x, 0, 0));
			}
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 2 && chest.countItem(Items.OAK_PLANKS) == 2 && chest.countItem(Items.BREAD) == 5,
				"free materials took from the chest: cobblestone=" + chest.countItem(Items.COBBLESTONE) + " planks=" + chest.countItem(Items.OAK_PLANKS)
					+ " bread=" + chest.countItem(Items.BREAD) + " (2, 2, 5 put in)");
		});
	}

	/** Turned on while the builder waits for materials he hasn't got, he carries on and finishes. */
	//$ gametest_ticks_batch AREA '3000' '"qaHfuFreeOnMid"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaHfuFreeOnMid")
	public void freeMaterialsTurnedOnMidWaitLetsTheBuilderCarryOn(GameTestHelper helper) {
		ServerLevel level = prepare(helper, false);
		ResourceLocation id = row(helper, ORIGIN, new Vec3i(3, 1, 1), Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.COBBLESTONE);
		Villager v = builder(helper, BENCH, CHEST);
		BuildSite site = Builders.start(level, v, null, id, placement(helper, ORIGIN));
		helper.assertTrue(site != null, "the builder took no build");
		AtomicBoolean on = new AtomicBoolean();
		when(helper, () -> site.status() == BuildSite.Status.WAITING_FOR_MATERIALS && !site.missing().isEmpty(), 1200,
			() -> "the builder never waited for materials with an empty chest: " + site.status(), () -> {
				helper.assertTrue(site.missing().getOrDefault(Items.COBBLESTONE, 0) == 2, "should be short of 2 cobblestone: " + site.missing());
				set(level, ModGameRules.FREE_MATERIALS, true);
				on.set(true);
			});
		helper.succeedWhen(() -> {
			helper.assertTrue(on.get(), "free materials not turned on yet");
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still waiting after free materials were turned on: status="
				+ site.status() + " missing=" + site.missing());
			helper.assertBlockPresent(Blocks.COBBLESTONE, ORIGIN);
			helper.assertBlockPresent(Blocks.STONE_BRICKS, ORIGIN.east());
			helper.assertBlockPresent(Blocks.COBBLESTONE, ORIGIN.east(2));
		});
	}

	/**
	 * Turned off mid-build, the builder needs materials again: he stops placing, waits and names what's missing, and
	 * once the chest has it he finishes the build from it.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaHfuFreeOffMid"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaHfuFreeOffMid")
	public void freeMaterialsTurnedOffMidBuildNeedsMaterialsAgain(GameTestHelper helper) {
		ServerLevel level = prepare(helper, true);
		set(level, ModGameRules.BUILD_DELAY, 8);
		Block[] blocks = new Block[10];
		java.util.Arrays.fill(blocks, Blocks.COBBLESTONE);
		ResourceLocation id = row(helper, ORIGIN, new Vec3i(5, 1, 2), blocks);
		Villager v = builder(helper, BENCH, CHEST);
		BuildSite site = Builders.start(level, v, null, id, placement(helper, ORIGIN));
		helper.assertTrue(site != null, "the builder took no build");
		AtomicInteger atSwitch = new AtomicInteger(-1);
		AtomicBoolean stocked = new AtomicBoolean();
		when(helper, () -> placed(helper) >= 2, 1500, () -> "the builder never placed 2 blocks with free materials", () -> {
			set(level, ModGameRules.FREE_MATERIALS, false);
			atSwitch.set(placed(helper));
			helper.assertTrue(atSwitch.get() < 10, "the whole build was done before the switch: the test proves nothing");
			helper.runAfterDelay(600, () -> {
				int now = placed(helper);
				helper.assertTrue(now <= atSwitch.get() + 1, "with free materials off and an empty chest the builder still placed "
					+ (now - atSwitch.get()) + " block(s) (had " + atSwitch.get() + ")");
				helper.assertTrue(BuildSiteManager.get(level).get(site.id()) != null, "the build finished without materials after free materials were turned off");
				helper.assertTrue(site.status() == BuildSite.Status.WAITING_FOR_MATERIALS, "not waiting for materials: " + site.status());
				Map<net.minecraft.world.item.Item, Integer> missing = site.missing();
				helper.assertTrue(missing.getOrDefault(Items.COBBLESTONE, 0) == 10 - now, "should be short of " + (10 - now) + " cobblestone: " + missing);
				Container chest = helper.getBlockEntity(CHEST);
				chest.setItem(0, new ItemStack(Items.COBBLESTONE, 10 - now));
				stocked.set(true);
			});
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stocked.get(), "not stocked yet");
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "not finished from the stocked chest: status=" + site.status()
				+ " missing=" + site.missing());
			helper.assertTrue(placed(helper) == 10, "only " + placed(helper) + " of 10 cobblestone placed");
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 0, "the builder didn't use the chest's cobblestone: " + chest.countItem(Items.COBBLESTONE) + " left");
		});
	}

	// --- workplaceAllowUploads --------------------------------------------------------------------------------------

	/**
	 * Off, a non-operator's upload is refused with the lang file's line (in English, word for word) and makes nothing,
	 * in creative too; turned back on, the same player is offered the upload and it goes through.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHfuUploads"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHfuUploads")
	public void uploadsOffRefuseAPlayerAndOnAgainLetHimUpload(GameTestHelper helper) {
		Leftovers.clear(helper);
		MinecraftServer server = helper.getLevel().getServer();
		var rule = helper.getLevel().getGameRules().getRule(ModGameRules.ALLOW_UPLOADS);
		boolean was = rule.get();
		rule.set(false, server);
		Leftovers.after(helper, () -> rule.set(was, server));
		BlockPos rel = new BlockPos(3, 2, 3);
		helper.setBlock(rel, ModBlocks.BLUEPRINT_TABLE);
		BlockPos table = helper.absolutePos(rel);
		String name = QaStageSixGameTests.name("qahfu");
		ImportGameTests.clearGenerated(helper, "uploads/" + name);
		Leftovers.after(helper, () -> ImportGameTests.clearGenerated(helper, "uploads/" + name));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.CREATIVE, name);
		helper.assertFalse(c.player().hasPermissions(2), "the player is an operator; the check would prove nothing");
		byte[] hut = ImportGameTests.bytes("hut.litematic");
		QaStageFourGameTests.press(helper, c.player(), rel);
		List<TablePayloads.Open> open = c.sent(TablePayloads.Open.class);
		helper.assertTrue(open.size() == 1 && !open.get(0).canUpload(), "the table offers a creative non-operator the upload with uploads off: " + open);
		QaStageSixGameTests.upload(c, table, "Switch Hut.litematic", hut, 1000);
		List<TablePayloads.UploadResult> refused = c.sent(TablePayloads.UploadResult.class);
		helper.assertTrue(!refused.isEmpty() && refused.stream().noneMatch(TablePayloads.UploadResult::ok), "uploaded with uploads off: " + refused);
		String said = JewellerGameTests.english(refused.get(0).message());
		helper.assertTrue(said.equals("Uploading is turned off on this server (gamerule workplaceAllowUploads)"), "the refusal reads: " + said);
		ResourceLocation made = AliveWorkplace.id("uploads/" + name + "/switch_hut");
		helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), made).isEmpty(), "a blueprint was made with uploads off");
		rule.set(true, server);
		helper.runAfterDelay(3, () -> {
			// Uploads on again: another non-operator is offered the upload at the table and his file goes through.
			String other = QaStageSixGameTests.name("qahfu");
			ImportGameTests.clearGenerated(helper, "uploads/" + other);
			Leftovers.after(helper, () -> ImportGameTests.clearGenerated(helper, "uploads/" + other));
			QaStageFiveGameTests.Client d = QaStageSixGameTests.client(helper, GameType.SURVIVAL, other);
			helper.assertFalse(d.player().hasPermissions(2), "the second player is an operator; the check would prove nothing");
			QaStageFourGameTests.press(helper, d.player(), rel);
			List<TablePayloads.Open> again = d.sent(TablePayloads.Open.class);
			helper.assertTrue(again.size() == 1 && again.get(0).canUpload(), "uploads on again, but the table doesn't offer the upload: " + again);
			QaStageSixGameTests.upload(d, table, "Switch Hut.litematic", hut, 1000);
			List<TablePayloads.UploadResult> results = d.sent(TablePayloads.UploadResult.class);
			helper.assertTrue(results.size() == 1 && results.get(0).ok() && results.get(0).id().isPresent(), "uploads on again, a non-operator's upload: " + results);
			helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), results.get(0).id().get()).isPresent(), "not in the library: " + results.get(0).id());
			QaStageFourGameTests.assertRendered(helper, results.get(0).message());
			// The player refused earlier isn't stuck: the same file from him goes through now.
			QaStageSixGameTests.upload(c, table, "Switch Hut.litematic", hut, 1000);
			helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), made).isPresent(), "the player refused while uploads were off still can't upload with them on: no " + made);
			helper.succeed();
		});
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private static ServerLevel prepare(GameTestHelper helper, boolean free) {
		QaCrafterFurnitureGameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		boolean wasFree = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			set(level, ModGameRules.FREE_MATERIALS, wasFree);
			set(level, ModGameRules.BUILDERS_HELP, help);
			set(level, ModGameRules.BUILD_DELAY, 2);
		});
		set(level, ModGameRules.FREE_MATERIALS, free);
		return level;
	}

	private static void set(ServerLevel level, GameRules.Key<GameRules.BooleanValue> key, boolean value) {
		level.getGameRules().getRule(key).set(value, level.getServer());
	}

	private static void set(ServerLevel level, GameRules.Key<GameRules.IntegerValue> key, int value) {
		level.getGameRules().getRule(key).set(value, level.getServer());
	}

	/** A blueprint made from {@code blocks} laid x first, then z, at {@code origin} (cleared again afterwards). */
	private static ResourceLocation row(GameTestHelper helper, BlockPos origin, Vec3i size, Block... blocks) {
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < blocks.length; i++) {
			helper.setBlock(origin.offset(i % size.getX(), 0, i / size.getX()), blocks[i]);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qahfu_" + Long.toHexString(level.getGameTime())
			+ "_" + Integer.toHexString(helper.absolutePos(origin).hashCode()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), size, false, Blocks.STRUCTURE_VOID);
		for (int i = 0; i < blocks.length; i++) {
			helper.setBlock(origin.offset(i % size.getX(), 0, i / size.getX()), Blocks.AIR);
		}
		return id;
	}

	private static Villager builder(GameTestHelper helper, BlockPos bench, BlockPos chest, ItemStack... items) {
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chest, Blocks.CHEST);
		Container container = helper.getBlockEntity(chest);
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
		Villager v = helper.spawn(EntityType.VILLAGER, bench.east());
		Builders.employ(helper.getLevel(), v, helper.absolutePos(bench));
		return v;
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	/** Cobblestone placed in the 5 by 2 row at {@link #ORIGIN}. */
	private static int placed(GameTestHelper helper) {
		int n = 0;
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 2; z++) {
				n += helper.getBlockState(ORIGIN.offset(x, 0, z)).is(Blocks.COBBLESTONE) ? 1 : 0;
			}
		}
		return n;
	}

	/** Lifts the villager {@link #DROP} blocks above {@code over} (helper y = 2 is standing height) and reports the damage once he lands. */
	private static void drop(GameTestHelper helper, Villager v, BlockPos over, java.util.function.IntConsumer landed) {
		v.setHealth(v.getMaxHealth());
		v.invulnerableTime = 0;
		v.fallDistance = 0;
		BlockPos abs = helper.absolutePos(over.above(DROP));
		v.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		v.setDeltaMovement(0, 0, 0);
		float full = v.getHealth();
		AtomicBoolean left = new AtomicBoolean();
		when(helper, () -> {
			if (!v.onGround()) {
				left.set(true);
			}
			return left.get() && v.onGround();
		}, 200, () -> "the villager never landed", () -> helper.runAfterDelay(2, () -> {
			helper.assertTrue(v.getY() < helper.absolutePos(over).getY() + 2, "landed on something at " + v.blockPosition().toShortString() + ", not the floor");
			landed.accept(Math.round(full - v.getHealth()));
		}));
	}

	/** Runs {@code then} on the first tick {@code condition} holds; fails if it doesn't within {@code limit} ticks. */
	private static void when(GameTestHelper helper, BooleanSupplier condition, int limit, java.util.function.Supplier<String> failure, Runnable then) {
		poll(helper, condition, helper.getTick() + limit, failure, then);
	}

	private static void poll(GameTestHelper helper, BooleanSupplier condition, long deadline, java.util.function.Supplier<String> failure, Runnable then) {
		helper.runAfterDelay(1, () -> {
			if (condition.getAsBoolean()) {
				then.run();
			} else if (helper.getTick() > deadline) {
				helper.fail(failure.get());
			} else {
				poll(helper, condition, deadline, failure, then);
			}
		});
	}
}
