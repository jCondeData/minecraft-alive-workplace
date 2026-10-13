package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints;
import io.github.jcondedata.aliveworkplace.wonder.WonderCrews;
import io.github.jcondedata.aliveworkplace.wonder.WonderGoods;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.wonder.powers.MoodPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.WeddingsPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.WonderBells;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Wonder time-lapse (ROADMAP 35.8) every Wonder's scene uses: a Town round its hall, a crew of {@link #CREW} builders
 * with a lead (the first, an Expert, handed the plan; every builder called from the stone), free materials, each stage's
 * goods handed in at the stone as it waits for them, the server sped up ({@code /tick rate}) and held at mid-morning
 * while it goes up; a slow orbit round it, a frame every {@link #ORBIT_EVERY} client ticks, and a still as each stage is
 * done ({@code 1N_<scene>_stage_N}). Once finished, the evening's festival: time is moved on to dusk, the server slowed
 * to its usual rate, and the Wonder's bigger fireworks are filmed ({@code 19_<scene>_fireworks}). Each check is the
 * scene's: the crew raised it, the server was told and the festival planned, its powers came on, its rockets went up.
 * Each Wonder's own scene makes one with its id ({@code new WonderTimelapse(id, "wonder_great_cathedral")}), and may
 * ready the ground ({@link #ground}) and the village ({@link #village}), look on every second ({@link #watching}) and end
 * with a still at night ({@link #night}, from its own view: {@link #nightView}).
 *
 * <p>An ending of its own replaces the fireworks: {@link Ending#WEDDING} (the Great Cathedral, 35.10) moves time on to
 * dusk, has a couple wed at the altar (blessed, the bells ringing) and films them from the nave
 * ({@code 19_<scene>_wedding}); its checks: the wedding was at the altar and blessed, and the bells hang in the belfries.
 */
final class WonderTimelapse {
	/** Builders on the crew, the lead included. */
	static final int CREW = 10;
	/** Client ticks between the orbit's frames (and how far it turns each: a full turn in 24). */
	static final int ORBIT_EVERY = 100;
	private static final int ORBIT_STEPS = 24;
	/** The server's rate while the Wonder goes up (ticks a second; 20 is the usual). */
	static final int RATE = 100;
	/** Client ticks before giving up: 13 minutes, under the 15 a scene has on GitHub's runner with the client's start. */
	private static final int GIVE_UP = 15600;
	private static final long MORNING = 3000;

	/** How the scene ends once the Wonder is finished. */
	enum Ending { FIREWORKS, WEDDING }

	private final ResourceLocation wonder;
	private final String scene;
	private final Ending ending;
	private volatile BlueprintData.Placement placement;
	private volatile boolean wed;
	/** The hall, away from the other scenes; the superflat ground's top is at y -61. */
	private final BlockPos hall = new BlockPos(900, -60, 900);
	private int tick;
	private int frame;
	private int stillsTaken;
	private int finishedAt = -1;
	private int showAt = -1;
	private boolean over;
	private volatile UUID site;
	private volatile int stage;
	private volatile boolean done;
	private volatile int mostCrew;
	private volatile int rockets;
	private int watched;
	private volatile Vec3 centre = Vec3.ZERO;
	private volatile Vec3i size = Vec3i.ZERO;
	/** The whole Wonder's box as placed (35.22: where its scaffolding is counted). */
	private volatile BoundingBox box;

	WonderTimelapse(ResourceLocation wonder, String scene) {
		this(wonder, scene, Ending.FIREWORKS);
	}

	WonderTimelapse(ResourceLocation wonder, String scene, Ending ending) {
		this.wonder = wonder;
		this.scene = scene;
		this.ending = ending;
	}

	/** The ground a Wonder's scene readies once its plan is placed (35.12: the Lighthouse's sea), given the level and where the plan went. */
	private java.util.function.BiConsumer<ServerLevel, BlueprintData.Placement> ground = (level, placement) -> {
	};
	/** A last still at night after the fireworks ({@code 20_<scene>_night}), with the scene's own night check. */
	@org.jetbrains.annotations.Nullable
	private java.util.function.Consumer<ServerLevel> night;

	/**
	 * A last scene inside the finished Wonder after the fireworks (35.14: the Great Library's rotunda on the scholar's
	 * screen): {@link Finale#ready} on the server thread, the GUI shown, the cursor on {@link Finale#point}, then a still
	 * ({@code 20_<scene>_<name>}) and {@link Finale#check}.
	 */
	interface Finale {
		String name();

		void ready(ServerLevel level, ServerPlayer player, BlueprintData.Placement placement, BlockPos hall);

		int point();

		void check(ServerLevel level, ServerPlayer player);
	}

	private Finale finale;
	/** A second finale after the first, with a still of its own (35.18: the Sky Chart, then the Coming days). */
	private Finale then;

	/** After the finale, {@code then} (35.18). */
	WonderTimelapse then(Finale then) {
		this.then = then;
		return this;
	}

	/** Ends inside the Wonder with {@code finale} (35.14). */
	WonderTimelapse finale(Finale finale) {
		this.finale = finale;
		return this;
	}

	/** Readies the ground before the crew begins (35.12). */
	WonderTimelapse ground(java.util.function.BiConsumer<ServerLevel, BlueprintData.Placement> ready) {
		this.ground = ready;
		return this;
	}

	/** Ends at night with a still and {@code check} (35.12: the Lighthouse's lamps lit). */
	WonderTimelapse night(java.util.function.Consumer<ServerLevel> check) {
		this.night = check;
		return this;
	}

	/** The village readied once its hall stands (35.22: the Colossus's village named, its founding in the chronicle). */
	private java.util.function.BiConsumer<ServerLevel, VillageHallBlockEntity> village = (level, entity) -> {
	};
	/** Called every second while the Wonder goes up (35.22: its scaffolding counted). */
	private java.util.function.Consumer<ServerLevel> watcher = level -> {
	};
	/** The night still's eye and what it looks at, in the Wonder's own blueprint; null: the usual view from its back corner. */
	@org.jetbrains.annotations.Nullable
	private double[] nightEye;
	private double[] nightAt;

	/** Readies the village once its hall stands, before the plan is drawn (35.22). */
	WonderTimelapse village(java.util.function.BiConsumer<ServerLevel, VillageHallBlockEntity> ready) {
		this.village = ready;
		return this;
	}

	/** What a scene films after the time-lapse's end (35.24: a Stadium Day), {@link #length} client ticks of it. */
	interface Epilogue {
		int length();

		/** Client tick {@code t} (from 0) of the epilogue, on the client thread. */
		void tick(Minecraft mc, MinecraftServer server, int t);
	}

	@org.jetbrains.annotations.Nullable
	private Epilogue epilogue;

	/** Films {@code then} after the fireworks (and the night still, if any) before the scene ends (35.24). */
	WonderTimelapse then(Epilogue then) {
		this.epilogue = then;
		return this;
	}

	/** Called at midnight, before the night still (35.16: the Great Wall's night raid sent at the village). */
	private java.util.function.Consumer<ServerLevel> nightfall = level -> {
	};

	/** Runs {@code at} at midnight, once the night still's view is set and a little before it is taken (35.16). */
	WonderTimelapse nightfall(java.util.function.Consumer<ServerLevel> at) {
		this.nightfall = at;
		return this;
	}

	/** Runs {@code each} every second while the Wonder goes up (35.22). */
	WonderTimelapse watching(java.util.function.Consumer<ServerLevel> each) {
		this.watcher = each;
		return this;
	}

	/** The night still from {@code eye} looking at {@code at}, both {x, y, z} in the Wonder's own blueprint (35.22: from the road). */
	WonderTimelapse nightView(double[] eye, double[] at) {
		this.nightEye = eye;
		this.nightAt = at;
		return this;
	}

	/** The whole Wonder's box as placed, once the plan is down (null before). */
	@org.jetbrains.annotations.Nullable
	BoundingBox box() {
		return box;
	}

	/** The village's hall. */
	BlockPos hall() {
		return hall;
	}

	/** The last still's name, time of day (past 24000: the next day), the scene's readying for it, and the client ticks it waits. */
	private String lastName = "night";
	private long lastTime = 18000;
	private java.util.function.Consumer<ServerLevel> lastStage = level -> {
	};
	private int lastWait = 130;

	/**
	 * Ends after the fireworks with a still {@code 20_<scene>_<name>} at {@code time} (35.20: the next morning, the farmers
	 * on the Hanging Gardens' terraces): {@code stage} readies it, {@code wait} client ticks pass, then the still and {@code check}.
	 */
	WonderTimelapse finale(String name, long time, int wait, java.util.function.Consumer<ServerLevel> stage, java.util.function.Consumer<ServerLevel> check) {
		this.night = check;
		this.lastName = name;
		this.lastTime = time;
		this.lastStage = stage;
		this.lastWait = wait;
		return this;
	}

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (over) {
			return;
		}
		if (tick == 1) {
			mc.options.renderDistance().set(8);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.graphicsMode().set(GraphicsStatus.FAST);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> stage(server));
		}
		if (tick == 120) {
			server.execute(() -> {
				run(server, "tick rate " + RATE);
				orbit(server, 0);
			});
		}
		if (tick > 120 && finishedAt < 0) {
			if (tick % 20 == 0) {
				server.execute(() -> watch(server));
			}
			if (tick % ORBIT_EVERY == 0) {
				ScreenshotHarness.shot(mc, String.format("frame_%03d", frame++));
				server.execute(() -> orbit(server, frame));
			}
			if (stillsTaken < stage - 1 || done && stillsTaken < stage) {
				stillsTaken++;
				ScreenshotHarness.shot(mc, String.format("1%d_%s_stage_%d", stillsTaken, scene, stillsTaken));
			}
			if (done && stillsTaken >= stage) {
				finishedAt = tick;
				server.execute(() -> evening(server));
			}
		}
		if (ending == Ending.WEDDING) {
			if (finishedAt > 0 && tick == finishedAt + 60) {
				server.execute(() -> wedding(server));
			}
			if (finishedAt > 0 && tick == finishedAt + 130) {
				ScreenshotHarness.shot(mc, String.format("19_%s_wedding", scene));
			}
			if (finishedAt > 0 && tick == finishedAt + 200) {
				over = true;
				mc.stop();
			}
			if (tick >= GIVE_UP && !over) {
				over = true;
				Showcase.check(false, "the " + wonder + " went up and a wedding was held at it in time (stage " + stage + ", finished " + done + ", wed " + wed + ")");
				mc.stop();
			}
			return;
		}
		if (finishedAt > 0 && showAt < 0 && tick % 10 == 0) {
			server.execute(() -> countRockets(server));
			if (rockets > 0) {
				showAt = tick;
				server.execute(() -> {
					run(server, "tick rate 20"); // the show at its own pace
					ScreenshotHarness.hoverLookingAt(player(server), centre.add(size.getX() * 0.9 + 14, size.getY() * 0.4 + 6, -size.getZ() * 0.9 - 14),
						centre.add(0, size.getY() * 0.6, 0));
				});
			}
		}
		if (showAt > 0 && tick == showAt + 70) {
			ScreenshotHarness.shot(mc, String.format("19_%s_fireworks", scene));
			server.execute(() -> countRockets(server));
		}
		if (showAt > 0 && tick == showAt + 200) {
			server.execute(() -> finalChecks(server));
		}
		if (showAt > 0 && tick == showAt + 230 && night != null) {
			server.execute(() -> {
				server.overworld().setDayTime((Chronicle.day(server.overworld()) - 1) * 24000L + lastTime); // midnight, or the scene's own time
				if (nightEye != null && placement != null) { // the scene's own view, in the Wonder's blueprint (35.22)
					ScreenshotHarness.hoverLookingAt(player(server), placed(nightEye[0], nightEye[1], nightEye[2]), placed(nightAt[0], nightAt[1], nightAt[2]));
				} else {
					ScreenshotHarness.hoverLookingAt(player(server), centre.add(size.getX() * 0.9 + 16, size.getY() * 0.5 + 6, size.getZ() * 0.9 + 16),
						centre.add(0, size.getY() * 0.6, 0));
				}
				lastStage.accept(server.overworld());
			});
		}
		if (showAt > 0 && night != null && tick == showAt + 270) {
			server.execute(() -> nightfall.accept(server.overworld()));
		}
		if (showAt > 0 && night != null && tick == showAt + 230 + lastWait) {
			ScreenshotHarness.shot(mc, String.format("20_%s_%s", scene, lastName));
			server.execute(() -> night.accept(server.overworld()));
		}
		if (showAt > 0 && finale != null && tick == showAt + 230) {
			mc.options.hideGui = false;
			server.execute(() -> finale.ready(server.overworld(), player(server), placement, hall));
		}
		if (showAt > 0 && finale != null && tick == showAt + 270) {
			ScreenshotHarness.pointAt(mc, finale.point());
		}
		if (showAt > 0 && finale != null && tick == showAt + 300) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, String.format("20_%s_%s", scene, finale.name()));
			server.execute(() -> finale.check(server.overworld(), player(server)));
		}
		if (showAt > 0 && then != null && tick == showAt + 330) {
			server.execute(() -> then.ready(server.overworld(), player(server), placement, hall));
		}
		if (showAt > 0 && then != null && tick == showAt + 370) {
			ScreenshotHarness.pointAt(mc, then.point());
		}
		if (showAt > 0 && then != null && tick == showAt + 400) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, String.format("21_%s_%s", scene, then.name()));
			server.execute(() -> then.check(server.overworld(), player(server)));
		}
		int end = showAt + (night != null ? 260 + lastWait : then != null ? 430 : finale != null ? 330 : 230);
		if (showAt > 0 && epilogue != null && tick >= end && tick < end + epilogue.length()) {
			epilogue.tick(mc, server, tick - end); // 35.24: the scene's own ending after the time-lapse
		}
		if (showAt > 0 && tick == end + (epilogue != null ? epilogue.length() : 0)) {
			over = true;
			mc.stop();
		}
		if (tick >= GIVE_UP && !over) {
			over = true;
			Showcase.check(false, "the " + wonder + " went up and was celebrated in time (stage " + stage + ", finished " + done + ", rockets " + rockets + ")");
			mc.stop();
		}
	}

	/** The village, its crew and the plan handed to the lead, on the server thread. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		GameRules rules = level.getGameRules();
		rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
		rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		rules.getRule(ModGameRules.FREE_MATERIALS).set(true, server);
		rules.getRule(ModGameRules.BUILD_DELAY).set(6, server);
		level.setDayTime(MORNING);
		run(server, "forceload add " + (hall.getX() - 64) + " " + (hall.getZ() - 112) + " " + (hall.getX() + 64) + " " + (hall.getZ() + 32));
		ServerPlayer player = player(server);
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, -1, 0, false, false));
		level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.NORTH));
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(player.getUUID(), player.getName().getString());
		entity.setRank(VillageRanks.Rank.TOWN);
		village.accept(level, entity);
		String[] names = {"Odo", "Pia", "Bram", "Ilse", "Tam", "Wren", "Edda", "Hob", "Mags", "Rolf"};
		List<Villager> crew = new ArrayList<>();
		for (int i = 0; i < CREW; i++) {
			BlockPos bench = hall.offset(-9 + 2 * i, 0, 4);
			level.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
			Villager builder = EntityType.VILLAGER.create(level);
			builder.moveTo(bench.getX() + 0.5, bench.getY(), bench.getZ() + 1.5, 180, 0);
			builder.setCustomName(Component.literal(names[i % names.length]));
			builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(i == 0 ? 4 : 2)); // the lead: an Expert
			level.addFreshEntity(builder);
			Builders.employ(level, builder, bench);
			crew.add(builder);
		}
		Wonder w = Wonders.loaded().get(wonder);
		Showcase.check(w != null, "the " + wonder + " is loaded: " + Wonders.problems());
		if (w == null) {
			return;
		}
		ItemStack plan = WonderPlanItem.of(level, hall, w, Component.literal("Testholm"));
		BlueprintData data = BlueprintItem.data(plan).orElseThrow();
		BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), data.size().orElseThrow(), hall.offset(0, 0, -8),
			Rotation.CLOCKWISE_180); // its front faces the hall
		plan.set(ModComponents.BLUEPRINT, data.withPlacement(Optional.of(placement)));
		ground.accept(level, placement);
		this.placement = placement;
		player.getInventory().clearContent();
		player.setItemInHand(InteractionHand.MAIN_HAND, plan);
		InteractionResult result = WonderStages.handOver(player, crew.get(0), plan);
		Showcase.check(result == InteractionResult.SUCCESS, "the plan handed to the lead, " + crew.get(0).getName().getString() + ": " + result);
		List<WonderSite> sites = WonderManager.get(level).in(hall);
		if (sites.isEmpty()) {
			return;
		}
		site = sites.get(0).id();
		WonderCrews.callEveryone(level, sites.get(0));
		Optional<Blueprint> whole = BlueprintLibrary.get(level, WonderBlueprints.idOf(wonder));
		whole.ifPresent(b -> {
			BoundingBox box = BlueprintOutline.bounds(placement, b.size());
			this.box = box;
			centre = new Vec3(box.getCenter().getX() + 0.5, box.minY(), box.getCenter().getZ() + 0.5);
			size = new Vec3i(box.getXSpan(), box.getYSpan(), box.getZSpan());
		});
	}

	/** Every second: the stage's goods handed in as it waits for them, the crew counted, the stage and whether it's finished. */
	private void watch(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Optional<WonderSite> found = site == null ? Optional.empty() : WonderManager.get(level).site(site);
		if (found.isEmpty()) {
			return;
		}
		WonderSite now = found.get();
		if (now.goingUp() && !now.started()) {
			List<Wonder.Good> goods = WonderGoods.goods(now);
			for (int i = 0; i < goods.size(); i++) {
				ItemStack one = WonderGoods.icon(goods.get(i));
				for (int left = goods.get(i).count(); left > 0; left -= one.getMaxStackSize()) {
					WonderGoods.deliver(level, now.id(), i, one.copyWithCount(Math.min(left, one.getMaxStackSize())));
				}
			}
		}
		if (now.goingUp()) {
			level.setDayTime(MORNING); // held at mid-morning: the crew's working hours, goods dedicated by day
			mostCrew = Math.max(mostCrew, WonderCrews.crew(level, now).size());
			watcher.accept(level);
		}
		stage = now.done() ? (Wonders.loaded().containsKey(wonder) ? Wonders.loaded().get(wonder).stages().size() : now.stage()) : now.stage();
		done = now.done();
		if (++watched % 30 == 0) { // every half minute, the build's progress in the log (a scene that runs out of time shows where it stood)
			StringBuilder parts = new StringBuilder();
			for (UUID part : now.parts()) {
				io.github.jcondedata.aliveworkplace.build.BuildSite s = io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).get(part);
				parts.append(s == null ? " gone" : " " + s.stage() + " placed " + s.placed() + " skipped " + s.skipped());
			}
			io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[{}] stage {} at game tick {} ({} ms a tick), crew {}:{}", scene, now.stage(), level.getGameTime(),
				server.getAverageTickTimeNanos() / 1_000_000, WonderCrews.crew(level, now).size(), parts);
		}
	}

	/** Finished: the checks so far, and on to the evening's festival, the day moving again. */
	private void evening(MinecraftServer server) {
		ServerLevel level = server.overworld();
		WonderSite now = WonderManager.get(level).site(site).orElseThrow();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		long today = Chronicle.day(level);
		Showcase.check(mostCrew >= CREW - 2, "a crew of " + mostCrew + " raised the " + Wonders.name(wonder).getString() + " (of " + CREW + " builders)");
		Showcase.check(entity.festivalDay() == today && now.kept().festival() == today, "its festival planned for this evening (day " + entity.festivalDay()
			+ ", today " + today + ")");
		Showcase.check(Wonders.power(level, hall, MoodPower.TYPE).isPresent() || Wonders.loaded().get(wonder).powers().stream()
			.noneMatch(p -> p.type().equals(MoodPower.TYPE)), "its powers came on: " + Wonders.loaded().get(wonder).powers().stream().map(Wonder.Power::type).toList());
		if (ending == Ending.WEDDING) {
			level.setDayTime((today - 1) * 24000L + DUSK); // held at dusk: the wedding at the altar
			return;
		}
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, server);
		level.setDayTime((today - 1) * 24000L + Festivals.FIREWORKS - 300); // the feast's last hour, then dusk and the fireworks
		ScreenshotHarness.hoverLookingAt(player(server), centre.add(-size.getX() * 0.8 - 12, size.getY() * 0.5 + 6, -size.getZ() * 0.8 - 12),
			centre.add(0, size.getY() * 0.4, 0));
	}

	/** Dusk: the time of day the wedding is held at (the sun on the horizon). */
	private static final long DUSK = 12600;

	/** A point of the Wonder's own blueprint ({@code local}), placed as the Wonder was. */
	private Vec3 placed(double x, double y, double z) {
		BlockPos at = placement.origin().offset(StructureTemplate.transform(BlockPos.containing(x, y, z), placement.mirror(), placement.rotation(), BlockPos.ZERO));
		return Vec3.atBottomCenterOf(at);
	}

	/**
	 * The ending at dusk (35.10): a courting couple at the Cathedral's altar wed, the wedding blessed and its bells ringing;
	 * the camera in the nave, looking up the red runner at them.
	 */
	private void wedding(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Optional<BlockPos> altar = WeddingsPower.venue(level, hall);
		Showcase.check(altar.isPresent() && Couples.venue(level, hall).equals(altar.get()), "weddings are held at the altar of the " + Wonders.name(wonder).getString()
			+ " (" + altar + ", venue " + Couples.venue(level, hall) + ")");
		if (altar.isEmpty() || placement == null) {
			return;
		}
		float toAltar = placement.rotation().rotate(Direction.SOUTH).toYRot(); // facing the gold
		Villager bride = couple(level, altar.get(), "Ilse", toAltar);
		Villager groom = couple(level, altar.get().relative(placement.rotation().rotate(Direction.EAST)), "Bram", toAltar);
		long today = Chronicle.day(level);
		io.github.jcondedata.aliveworkplace.registry.ModAttachments.PARTNER.set(bride, new Couples.Partner(groom.getUUID(), groom.getDisplayName(), today - 2, false));
		io.github.jcondedata.aliveworkplace.registry.ModAttachments.PARTNER.set(groom, new Couples.Partner(bride.getUUID(), bride.getDisplayName(), today - 2, false));
		int[][] rows = {{16, 36}, {17, 36}, {21, 36}, {22, 36}, {15, 34}, {23, 34}};
		for (int[] row : rows) { // the guests between the front pews (the pews are on the odd rows)
			Vec3 seat = placed(row[0], 1, row[1]);
			Villager guest = EntityType.VILLAGER.create(level);
			float yaw = (float) (Math.toDegrees(Math.atan2(altar.get().getZ() + 0.5 - seat.z, altar.get().getX() + 0.5 - seat.x)) - 90); // facing the altar
			guest.moveTo(seat.x, seat.y, seat.z, yaw, 0);
			guest.setYHeadRot(yaw);
			guest.setNoAi(true);
			level.addFreshEntity(guest);
		}
		Wonders.Granted granted = WeddingsPower.granted(level, hall).orElse(null);
		List<BlockPos> bells = granted == null ? List.of() : WonderBells.bells(level, granted);
		Showcase.check(!bells.isEmpty() && bells.stream().allMatch(p -> level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.BELL)),
			"the bells hang in its belfries (" + bells.size() + ")");
		Couples.wed(level, hall, bride, groom);
		wed = io.github.jcondedata.aliveworkplace.registry.ModAttachments.WONDER_BLESSING.has(bride)
			&& ((VillageHallBlockEntity) level.getBlockEntity(hall)).wonderWedding() != null;
		Showcase.check(wed, "the wedding at the altar was blessed: the couple's first baby to come, the whole village 15 happier for 3 days");
		Vec3 target = Vec3.atBottomCenterOf(altar.get()).add(0, 1.2, 0);
		ScreenshotHarness.hoverLookingAt(player(server), placed(19, 5, 29), target);
	}

	private static Villager couple(ServerLevel level, BlockPos at, String name, float yaw) {
		Villager v = EntityType.VILLAGER.create(level);
		v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
		v.setYHeadRot(yaw);
		v.setCustomName(Component.literal(name));
		v.setNoAi(true);
		level.addFreshEntity(v);
		return v;
	}

	private void countRockets(MinecraftServer server) {
		ServerLevel level = server.overworld();
		AABB around = new AABB(hall).inflate(64, 96, 120);
		rockets = Math.max(rockets, level.getEntitiesOfClass(FireworkRocketEntity.class, around, r -> r.getY() > centre.y + size.getY() * 0.5).size());
	}

	private void finalChecks(MinecraftServer server) {
		Showcase.check(rockets > 0, "the Wonder's fireworks went up from its top at the festival (" + rockets + " rockets above it at once)");
	}

	/** The camera {@code step} steps round the Wonder, from its front-right, looking at its middle. */
	private void orbit(MinecraftServer server, int step) {
		double angle = Math.PI / 4 + step * 2 * Math.PI / ORBIT_STEPS;
		double radius = Math.max(size.getX(), size.getZ()) * 0.9 + 16;
		Vec3 eye = centre.add(Math.sin(angle) * radius, size.getY() * 0.6 + 8, Math.cos(angle) * radius);
		ScreenshotHarness.hoverLookingAt(player(server), eye, centre.add(0, size.getY() * 0.4, 0));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	private static void run(MinecraftServer server, String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
	}
}
