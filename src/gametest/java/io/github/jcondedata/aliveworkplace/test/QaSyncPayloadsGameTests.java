package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintNames;
import io.github.jcondedata.aliveworkplace.blueprint.PreviewNetworking;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.city.CityZones;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.legend.LegendLook;
import io.github.jcondedata.aliveworkplace.mail.Mail;
import io.github.jcondedata.aliveworkplace.people.ClassLook;
import io.github.jcondedata.aliveworkplace.people.Tonics;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.trainer.MegaBracelets;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA: the payloads the server sends every player on join or on a change. A StreamCodec that writes something it can't
 * read back (or throws while writing) disconnects every player, so each one goes over the wire here, full of what a
 * real server puts in it: {@link BlueprintNames.Sync} (players' build names, unicode, at the 128-character cap),
 * {@link CityPlans.Sync} (a plan filled to every cap: 16 zones, 24 roads, 64 lanes, 8 caravan roads, a wall), and
 * {@link Tonics.Sync} (the server's own {@link Tonics#sync()}, and 512 tonics of 16 lines, its codec's limits).
 * Each one reads back equal with no byte left over, an empty one too, and the client side's handlers (common code)
 * do what the packet says. A guard then sends a sample of every payload type the mod registers with Fabric, so a
 * new payload type without a sample here fails.
 */
public class QaSyncPayloadsGameTests {
	/** The most bytes a server-to-client custom payload may have (vanilla's ClientboundCustomPayloadPacket). */
	private static final int S2C_MAX = 1048576;

	private static RegistryFriendlyByteBuf buf(ServerLevel level) {
		return new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
	}

	/** {@code value} written and read back; fails the test if any bytes are left unread. */
	private static <T> T wire(GameTestHelper helper, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, T value, String what) {
		RegistryFriendlyByteBuf buf = buf(helper.getLevel());
		codec.encode(buf, value);
		T back = codec.decode(buf);
		helper.assertTrue(buf.readableBytes() == 0, what + ": " + buf.readableBytes() + " bytes left unread");
		return back;
	}

	private static <T> byte[] bytes(ServerLevel level, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, T value) {
		RegistryFriendlyByteBuf buf = buf(level);
		codec.encode(buf, value);
		byte[] out = new byte[buf.readableBytes()];
		buf.readBytes(out);
		return out;
	}

	private static ResourceLocation testId(String path) {
		return ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_sync/" + path);
	}

	// =================================================================================================================
	// BlueprintNames.Sync
	// =================================================================================================================

	/** Many names of every kind a player types (accents, CJK, emoji, quotes, the 128-character cap) read back equal. */
	//$ gametest 'net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE)
	public void blueprintNamesReadBackEqual(GameTestHelper helper) {
		Map<ResourceLocation, String> names = new HashMap<>();
		String[] typed = {"Jesse's Café", "村の家", "Tower 🏰 of Dreams", "\"Quoted\" & <tagged>", "Ünïcødé Hôtel № 5", "a", " 2"};
		for (int i = 0; i < 300; i++) {
			names.put(ResourceLocation.fromNamespaceAndPath(i % 2 == 0 ? "aliveworkplace" : "my_pack", "player/build_" + i),
				typed[i % typed.length] + " " + i);
		}
		names.put(AliveWorkplace.id("player/longest"), "é".repeat(128)); // the cap BlueprintNames.put keeps, two bytes each
		names.put(AliveWorkplace.id("player/longest_emoji"), "🏠".repeat(64)); // 128 chars, four bytes per house
		for (boolean all : new boolean[] {true, false}) {
			BlueprintNames.Sync sent = new BlueprintNames.Sync(names, all);
			BlueprintNames.Sync back = wire(helper, BlueprintNames.Sync.CODEC, sent, "blueprint names");
			helper.assertTrue(back.equals(sent), "the names came back different (all=" + all + ")");
			helper.assertTrue(back.names().size() == names.size(), "names came back " + back.names().size() + " of " + names.size());
			helper.assertTrue(Arrays.equals(bytes(helper.getLevel(), BlueprintNames.Sync.CODEC, sent),
				bytes(helper.getLevel(), BlueprintNames.Sync.CODEC, back)), "the names don't write the same bytes twice");
		}
		BlueprintNames.Sync empty = new BlueprintNames.Sync(Map.of(), true);
		helper.assertTrue(wire(helper, BlueprintNames.Sync.CODEC, empty, "no names").equals(empty), "an empty names packet came back different");
		helper.succeed();
	}

	/**
	 * The server's own path: {@link BlueprintNames#put} keeps a long unicode name (cut to 128), the update it would send
	 * reads back the same, and the client's {@link BlueprintNames#received} takes it and forgets it on an empty name.
	 */
	//$ gametest 'net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE)
	public void blueprintNamesFromPutReachTheClientAndAreForgotten(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ResourceLocation id = testId("put_name");
		ResourceLocation other = testId("received_only");
		String name = "Château d'Été 夏の城 — " + "ü".repeat(200);
		try {
			BlueprintNames.put(server, id, name);
			String kept = BlueprintNames.get(id).orElse(null);
			helper.assertTrue(kept != null && kept.length() == 128 && name.startsWith(kept), "put kept " + kept);
			BlueprintNames.Sync update = new BlueprintNames.Sync(Map.of(id, kept), false);
			BlueprintNames.Sync back = wire(helper, BlueprintNames.Sync.CODEC, update, "a new name");
			helper.assertTrue(back.equals(update), "the new name came back as " + back.names());

			// the client's handler (common code): an update adds, an empty name forgets
			BlueprintNames.received(wire(helper, BlueprintNames.Sync.CODEC, new BlueprintNames.Sync(Map.of(other, "Ферма 🐄"), false), "a client name"));
			helper.assertTrue(BlueprintNames.get(other).equals(Optional.of("Ферма 🐄")), "the client didn't take the name: " + BlueprintNames.get(other));
			helper.assertTrue(BlueprintNames.get(id).equals(Optional.of(kept)), "an update made the client forget another name");
			BlueprintNames.received(wire(helper, BlueprintNames.Sync.CODEC, new BlueprintNames.Sync(Map.of(other, ""), false), "a forgotten name"));
			helper.assertTrue(BlueprintNames.get(other).isEmpty(), "an empty name didn't make the client forget it");
		} finally {
			BlueprintNames.put(server, id, "");
			BlueprintNames.received(new BlueprintNames.Sync(Map.of(other, ""), false));
		}
		helper.assertTrue(BlueprintNames.get(id).isEmpty(), "put with an empty name didn't forget it");
		helper.succeed();
	}

	// =================================================================================================================
	// CityPlans.Sync
	// =================================================================================================================

	/** A plan filled to every cap the way the plan screen and the Steward fill it: zones, roads routed and built, lanes, caravan roads, a wall. */
	private static CityPlan fullPlan() {
		List<CityZones.Kind> kinds = CityZones.all();
		CityPlan plan = CityPlan.EMPTY;
		String[] names = {"Market Square", "Les Jardins", "東の畑", "Workshops 🔨", "Keep clear!", "Homes of the \"North\""};
		for (int z = 0; z < CityPlan.MAX_ZONES; z++) {
			String kind = kinds.get(z % kinds.size()).id();
			CityPlan next = plan.addZone(kind, names[z % names.length] + " " + z, z % 3 == 0 ? "" : "aliveworkplace:style_" + z);
			if (next == null) {
				throw new IllegalStateException("addZone refused zone " + z + " of kind " + kind);
			}
			plan = next.editZone(z, kind, names[z % names.length] + " " + z, z % 3 == 0 ? "" : "aliveworkplace:style_" + z, z % 2 == 0);
			BitSet cells = new BitSet();
			for (int c = z; c < CityPlan.GRID * CityPlan.GRID; c += CityPlan.MAX_ZONES) {
				cells.set(c);
			}
			plan = plan.paint(z, cells);
		}
		for (int r = 0; r < CityPlan.MAX_ROADS; r++) {
			List<BlockPos> points = new ArrayList<>();
			for (int p = 0; p < CityPlan.MAX_ROAD_POINTS; p++) {
				points.add(new BlockPos(-64 + p * 2, 0, (r * 5 + p * 3) % 128 - 64));
			}
			CityPlan.Road road = new CityPlan.Road(points, r % 3 == 0 ? CityPlan.Road.LANE : r % 3 == 1 ? CityPlan.Road.STREET : CityPlan.Road.AVENUE,
				r % 2 == 0 ? "" : "aliveworkplace:cobble", r % 4 != 0);
			plan = plan.addRoad(road);
			List<BlockPos> route = new ArrayList<>();
			for (int n = 0; n < 480; n++) {
				route.add(new BlockPos(-64 + n % 128, 60 + n % 20 - 10, -64 + (n * 7) % 128));
			}
			plan = plan.withRoad(plan.roads().size() - 1, road.withRoute(route, r % 5).withBuilt(0, 3).withBuilt(r % 20, 2));
		}
		for (int l = 0; l < CityPlan.MAX_LANES; l++) {
			List<BlockPos> route = new ArrayList<>();
			for (int n = 0; n < 48; n++) {
				route.add(new BlockPos(l - 32, 64, n - 24));
			}
			plan = plan.addRoad(new CityPlan.Road(List.of(new BlockPos(l - 32, 0, -24), new BlockPos(l - 32, 0, 24)), CityPlan.Road.LANE, "",
				true, route, true, List.of(0, 1), true));
		}
		for (int c = 0; c < CityPlan.MAX_CARAVAN_ROADS; c++) {
			List<BlockPos> route = new ArrayList<>();
			for (int n = 0; n < 1200; n++) {
				route.add(new BlockPos(n * (c % 2 == 0 ? 1 : -1), 70 + n % 9, 29_999_000 - n));
			}
			plan = plan.addRoad(new CityPlan.Road(List.of(BlockPos.ZERO, new BlockPos(2400, 0, -2400)), CityPlan.Road.STREET, "aliveworkplace:caravan",
				true, route, true, List.of(0, 5, 9), false, 7, 12, Optional.of(new BlockPos(-29_999_000, 0, 29_999_000))));
		}
		List<BlockPos> wall = new ArrayList<>();
		for (int p = 0; p < CityPlan.MAX_ROAD_POINTS; p++) {
			wall.add(new BlockPos((int) (60 * Math.cos(p * Math.PI / 32)), 0, (int) (60 * Math.sin(p * Math.PI / 32))));
		}
		plan = plan.withWall(new CityPlan.Wall(wall, true).proposed("aliveworkplace:stone_kit", true).approvedWith("aliveworkplace:stone_kit"));
		return plan.withMode(CityPlan.Mode.RUN);
	}

	/** A plan at every cap reads back equal, fits in one packet, and so does an empty plan at a far-off hall. */
	//$ gametest 'net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE)
	public void cityPlanSyncReadsBackEqualAtEveryCap(GameTestHelper helper) {
		CityPlan plan = fullPlan();
		helper.assertTrue(plan.zones().size() == CityPlan.MAX_ZONES && plan.drawnRoads() == CityPlan.MAX_ROADS && plan.lanes() == CityPlan.MAX_LANES
			&& plan.caravanRoads() == CityPlan.MAX_CARAVAN_ROADS && plan.wall().isPresent(), "the test plan isn't full: " + plan.zones().size()
			+ " zones, " + plan.drawnRoads() + " roads, " + plan.lanes() + " lanes, " + plan.caravanRoads() + " caravan roads");
		CityPlans.Sync sent = new CityPlans.Sync(new BlockPos(29_999_000, -64, -29_999_000), plan, CityPlans.UNDO_STEPS);
		CityPlans.Sync back = wire(helper, CityPlans.Sync.CODEC, sent, "a full plan");
		helper.assertTrue(back.hall().equals(sent.hall()) && back.undo() == sent.undo(), "hall or undo came back as " + back.hall() + ", " + back.undo());
		helper.assertTrue(back.plan().zones().equals(plan.zones()), "the zones came back different");
		for (int i = 0; i < plan.roads().size(); i++) {
			helper.assertTrue(back.plan().roads().get(i).equals(plan.roads().get(i)), "road " + i + " came back as " + back.plan().roads().get(i));
		}
		helper.assertTrue(back.plan().wall().equals(plan.wall()) && back.plan().mode() == CityPlan.Mode.RUN, "wall or mode came back different");
		helper.assertTrue(back.equals(sent), "the plan came back different");
		int size = bytes(helper.getLevel(), CityPlans.Sync.CODEC, sent).length;
		helper.assertTrue(size < S2C_MAX, "a full plan is " + size + " bytes, over a packet's " + S2C_MAX);

		CityPlans.Sync empty = new CityPlans.Sync(BlockPos.ZERO, CityPlan.EMPTY, 0);
		helper.assertTrue(wire(helper, CityPlans.Sync.CODEC, empty, "an empty plan").equals(empty), "an empty plan came back different");
		// an emptied zone (no cells), an unrouted road and an open, unapproved wall: the codecs' optional fields left at their defaults
		CityPlan sparse = CityPlan.EMPTY.addZone(CityZones.all().get(0).id(), "", "")
			.addRoad(new CityPlan.Road(List.of(new BlockPos(1, 0, 1), new BlockPos(-1, 0, -1)), CityPlan.Road.STREET, ""))
			.withWall(new CityPlan.Wall(List.of(new BlockPos(5, 0, 5), new BlockPos(-5, 0, 5)), false))
			.withMode(CityPlan.Mode.REST);
		CityPlans.Sync few = new CityPlans.Sync(new BlockPos(-1, 319, 1), sparse, 1);
		helper.assertTrue(wire(helper, CityPlans.Sync.CODEC, few, "a sparse plan").equals(few), "a sparse plan came back different");
		helper.succeed();
	}

	// =================================================================================================================
	// Tonics.Sync
	// =================================================================================================================

	/** The server's own {@link Tonics#sync()} (every tonic loaded from data) reads back equal, and the client's handler shows its lines. */
	//$ gametest 'net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE)
	public void tonicsSyncFromTheServerReadsBackAndShowsOnTheClient(GameTestHelper helper) {
		Tonics.Sync sent = Tonics.sync();
		helper.assertTrue(!sent.entries().isEmpty() && sent.entries().size() == Tonics.all().size(),
			"the server's tonics packet has " + sent.entries().size() + " entries for " + Tonics.all().size() + " tonics");
		Tonics.Sync back = wire(helper, Tonics.Sync.CODEC, sent, "the server's tonics");
		helper.assertTrue(back.equals(sent), "the tonics came back different");
		for (int i = 0; i < sent.entries().size(); i++) {
			Tonics.Sync.Entry s = sent.entries().get(i);
			Tonics.Sync.Entry b = back.entries().get(i);
			helper.assertTrue(s.item().equals(b.item()) && s.lines().size() == b.lines().size(), "tonic " + s.item() + " came back as " + b.item());
			for (int l = 0; l < s.lines().size(); l++) {
				helper.assertTrue(s.lines().get(l).getString().equals(b.lines().get(l).getString())
						&& s.lines().get(l).getStyle().equals(b.lines().get(l).getStyle()),
					"line " + l + " of " + s.item() + " came back as \"" + b.lines().get(l).getString() + "\"");
			}
		}
		try {
			Tonics.receive(back);
			for (Tonics.Sync.Entry e : sent.entries()) {
				helper.assertTrue(Tonics.clientTooltip(e.item()).equals(e.lines()), "the client shows " + Tonics.clientTooltip(e.item()) + " for " + e.item());
			}
			helper.assertTrue(Tonics.clientTooltip(ResourceLocation.withDefaultNamespace("dirt")).isEmpty(), "dirt has a tonic tooltip on the client");
			Tonics.receive(wire(helper, Tonics.Sync.CODEC, new Tonics.Sync(List.of()), "no tonics"));
			helper.assertTrue(Tonics.clientTooltip(sent.entries().get(0).item()).isEmpty(), "an empty tonics packet left the old tooltips on the client");
		} finally {
			Tonics.receive(new Tonics.Sync(List.of()));
		}
		helper.succeed();
	}

	/** At the codec's limits, 512 tonics of 16 lines each with unicode and styled text, it reads back equal and fits in one packet. */
	//$ gametest 'net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE)
	public void tonicsSyncAtItsLimitsReadsBackEqual(GameTestHelper helper) {
		List<Tonics.Sync.Entry> entries = new ArrayList<>();
		for (int i = 0; i < 512; i++) {
			List<Component> lines = new ArrayList<>();
			for (int l = 0; l < 16; l++) {
				lines.add(switch (l % 4) {
					case 0 -> Component.translatable("tooltip.aliveworkplace.tonic.effect", 10 + l, 3).withStyle(ChatFormatting.BLUE);
					case 1 -> Component.literal("Brewed with ").append(Component.literal("Süßbeeren 🍓 甘い").withStyle(ChatFormatting.GOLD));
					case 2 -> Component.translatableWithFallback("tonic.aliveworkplace.jobs.my_pack.job_" + i, "Job " + i + "s").withStyle(ChatFormatting.GRAY);
					default -> Component.literal("x".repeat(200)).withStyle(ChatFormatting.DARK_GRAY);
				});
			}
			entries.add(new Tonics.Sync.Entry(ResourceLocation.fromNamespaceAndPath("my_pack", "tonic_" + i), lines));
		}
		Tonics.Sync sent = new Tonics.Sync(entries);
		Tonics.Sync back = wire(helper, Tonics.Sync.CODEC, sent, "512 tonics");
		helper.assertTrue(back.equals(sent), "512 tonics came back different");
		int size = bytes(helper.getLevel(), Tonics.Sync.CODEC, sent).length;
		helper.assertTrue(size < S2C_MAX, "512 tonics of 16 lines are " + size + " bytes, over a packet's " + S2C_MAX);
		Tonics.Sync empty = new Tonics.Sync(List.of());
		helper.assertTrue(wire(helper, Tonics.Sync.CODEC, empty, "no tonics").equals(empty), "an empty tonics packet came back different");
		helper.succeed();
	}

	// =================================================================================================================
	// Guard: every payload type the mod registers
	// =================================================================================================================

	/** One sample of every payload type the mod has, full of content. */
	private static List<CustomPacketPayload> samples(ServerLevel level) {
		BlockPos far = new BlockPos(29_999_000, -64, -29_999_000);
		Component styled = Component.literal("Village «Éire» 村 ").append(Component.translatable("block.minecraft.stone").withStyle(ChatFormatting.AQUA));
		byte[] colors = new byte[CityPlans.MAP * CityPlans.MAP];
		for (int i = 0; i < colors.length; i++) {
			colors[i] = (byte) (i * 31);
		}
		ItemStack named = new ItemStack(Items.WRITTEN_BOOK, 3);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Blueprint «Le Château» 🏰"));
		List<CustomPacketPayload> out = new ArrayList<>();
		out.add(new BlueprintNames.Sync(Map.of(AliveWorkplace.id("player/cafe"), "Jesse's Café"), true));
		out.add(new CityPlans.Sync(far, fullPlan(), 3));
		out.add(new CityPlans.Open(far, styled, 4, fullPlan(), 2, true, colors,
			List.of(new CityPlans.Mark(-60, 60, 14, true), new CityPlans.Mark(3, -3, 0, false)),
			List.of(new CityPlans.Outline(-10, -10, 10, 10, false), new CityPlans.Outline(20, 20, 30, 30, true)),
			List.of(new CityPlans.KindInfo("market", 0xFF00FF, 0x123456, AliveWorkplace.id("textures/gui/market.png"), true)),
			List.of(new CityPlans.StyleInfo("aliveworkplace:plains", styled, AliveWorkplace.id("textures/gui/plains.png")))));
		BitSet cells = new BitSet();
		cells.set(0);
		cells.set(1023);
		out.add(CityPlans.Edit.paint(far, 2, cells));
		out.add(CityPlans.Edit.addRoad(far, List.of(new BlockPos(1, 0, 2), new BlockPos(-30, 0, 40)), CityPlan.Road.AVENUE, "aliveworkplace:cobble"));
		out.add(CityPlans.Edit.editZone(far, 1, "homes", "Les Maisons 家", "", true));
		byte[] map = new byte[128 * 128];
		Arrays.fill(map, (byte) 34);
		out.add(new Colonies.Open(true, far, styled, map, List.of(new Colonies.Mark(far, styled), new Colonies.Mark(BlockPos.ZERO, Component.literal("Ö"))),
			Optional.of(new BlockPos(-29_999_000, 70, 29_999_000))));
		out.add(new Colonies.Answer(true, Optional.of(far), styled));
		out.add(new Colonies.Choose(false, -29_999_000, 29_999_000));
		out.add(new MegaBracelets.Bracelet(Integer.MAX_VALUE, true));
		out.add(new GolemSmith.Look(123456, "gate_warden"));
		out.add(new LegendLook.Look(77, true, LegendLook.PLACEHOLDER));
		out.add(new Mail.Send("Jesse_99", "Hälló, 世界 🌍 " + "m".repeat(200)));
		out.add(new BuilderStatusSync.Status(42, styled, 0.625f, Component.literal("Placing stone"),
			List.of(Component.literal("Missing: 3 Oak Planks"), Component.literal("Ask the Crafter"), Component.literal("Chest at 1 2 3"))));
		out.add(new PreviewNetworking.Data(AliveWorkplace.id("cottage"), List.of(0, 1, 5000, 24000), new int[] {0, 0, 0, 1, 31, 15, 31, 3}, false));
		out.add(new PreviewNetworking.Request(AliveWorkplace.id("player/cafe")));
		out.add(new TablePayloads.Open(far, List.of(new TablePayloads.Entry(AliveWorkplace.id("cottage"), 9, 7, 11, 500, 2, "Ein Häuschen 小屋",
			List.of(AliveWorkplace.id("baker"), AliveWorkplace.id("smith")))), true,
			List.of(new TablePayloads.Stored(named, 2)), 9));
		out.add(new TablePayloads.Details(AliveWorkplace.id("cottage"), List.of(new TablePayloads.Material(ResourceLocation.withDefaultNamespace("oak_planks"), 140)),
			List.of(new TablePayloads.StyleChoice("aliveworkplace:desert", styled, AliveWorkplace.id("textures/gui/desert.png"))),
			List.of(new TablePayloads.JobBlock(List.of(AliveWorkplace.id("baker")), 2)), 4));
		out.add(new TablePayloads.Preview(AliveWorkplace.id("cottage"), 9, 7, 11, 1, 2, List.of(0, 1, 7000), new int[] {0, 1, 2, 3, 2, 1}));
		out.add(new TablePayloads.UploadResult(true, styled, Optional.of(AliveWorkplace.id("player/cafe"))));
		out.add(new TablePayloads.RequestDetails(AliveWorkplace.id("cottage")));
		out.add(new TablePayloads.Take(far, AliveWorkplace.id("cottage")));
		out.add(new TablePayloads.Store(far, true));
		out.add(new TablePayloads.TakeStored(far, named));
		out.add(new TablePayloads.UploadChunk(far, "Jesse's Café ✓.nbt", 50_000, 4096, new byte[] {1, -2, 3, 127, -128, 0}));
		out.add(Tonics.sync());
		out.add(new ClassLook.Look(9, Optional.of(AliveWorkplace.id("noble")), Optional.of(AliveWorkplace.id("textures/entity/villager/class/noble.png")), 2));
		return out;
	}

	@SuppressWarnings("unchecked")
	private static Map<ResourceLocation, CustomPacketPayload.TypeAndCodec<RegistryFriendlyByteBuf, ? extends CustomPacketPayload>> registered(
			net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl<RegistryFriendlyByteBuf> registry) throws ReflectiveOperationException {
		java.lang.reflect.Field field = net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl.class.getDeclaredField("packetTypes");
		field.setAccessible(true);
		Map<ResourceLocation, CustomPacketPayload.TypeAndCodec<RegistryFriendlyByteBuf, ? extends CustomPacketPayload>> all =
			(Map<ResourceLocation, CustomPacketPayload.TypeAndCodec<RegistryFriendlyByteBuf, ? extends CustomPacketPayload>>) field.get(registry);
		Map<ResourceLocation, CustomPacketPayload.TypeAndCodec<RegistryFriendlyByteBuf, ? extends CustomPacketPayload>> ours = new LinkedHashMap<>();
		all.forEach((id, codec) -> {
			if (id.getNamespace().equals(AliveWorkplace.MOD_ID)) {
				ours.put(id, codec);
			}
		});
		return ours;
	}

	/**
	 * Every payload type the mod registers with Fabric (both ways) has a sample above, and each sample, written with the
	 * codec Fabric will use, reads back with no byte left and writes the same bytes again (nothing lost on the way). A
	 * server-to-client one also fits in one packet.
	 */
	//$ gametest 'net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE)
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void everyRegisteredPayloadReadsBackWhatItWrote(GameTestHelper helper) throws ReflectiveOperationException {
		ServerLevel level = helper.getLevel();
		Map<ResourceLocation, CustomPacketPayload.TypeAndCodec<RegistryFriendlyByteBuf, ? extends CustomPacketPayload>> s2c =
			registered(net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl.PLAY_S2C);
		Map<ResourceLocation, CustomPacketPayload.TypeAndCodec<RegistryFriendlyByteBuf, ? extends CustomPacketPayload>> c2s =
			registered(net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl.PLAY_C2S);
		helper.assertTrue(s2c.size() >= 10 && c2s.size() >= 5, "found only " + s2c.size() + " S2C and " + c2s.size() + " C2S payload types");
		Map<ResourceLocation, CustomPacketPayload> samples = new LinkedHashMap<>();
		for (CustomPacketPayload sample : samples(level)) {
			samples.put(sample.type().id(), sample);
		}
		TreeSet<String> unsampled = new TreeSet<>();
		s2c.keySet().forEach(id -> {
			if (!samples.containsKey(id)) {
				unsampled.add(id.toString());
			}
		});
		c2s.keySet().forEach(id -> {
			if (!samples.containsKey(id)) {
				unsampled.add(id.toString());
			}
		});
		helper.assertTrue(unsampled.isEmpty(), "payload types with no sample in QaSyncPayloadsGameTests#samples: " + unsampled);
		for (Map.Entry<ResourceLocation, CustomPacketPayload> e : samples.entrySet()) {
			boolean toClient = s2c.containsKey(e.getKey());
			CustomPacketPayload.TypeAndCodec<RegistryFriendlyByteBuf, ? extends CustomPacketPayload> registeredCodec =
				toClient ? s2c.get(e.getKey()) : c2s.get(e.getKey());
			helper.assertTrue(registeredCodec != null, "the sample " + e.getKey() + " isn't a registered payload type");
			StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload> codec = (StreamCodec) registeredCodec.codec();
			byte[] first;
			try {
				first = bytes(level, codec, e.getValue());
			} catch (RuntimeException ex) {
				throw new net.minecraft.gametest.framework.GameTestAssertException(e.getKey() + " can't be written: " + ex);
			}
			RegistryFriendlyByteBuf in = buf(level);
			in.writeBytes(first);
			CustomPacketPayload back;
			try {
				back = codec.decode(in);
			} catch (RuntimeException ex) {
				throw new net.minecraft.gametest.framework.GameTestAssertException(e.getKey() + " can't be read back: " + ex);
			}
			helper.assertTrue(in.readableBytes() == 0, e.getKey() + ": " + in.readableBytes() + " bytes left unread");
			helper.assertTrue(back.type().equals(e.getValue().type()), e.getKey() + " read back as " + back.type().id());
			helper.assertTrue(Arrays.equals(first, bytes(level, codec, back)), e.getKey() + " read back as something that writes other bytes");
			helper.assertTrue(!toClient || first.length < S2C_MAX, e.getKey() + " sample is " + first.length + " bytes, over a packet's " + S2C_MAX);
		}
		helper.succeed();
	}
}
