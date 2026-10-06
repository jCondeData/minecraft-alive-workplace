package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.fossil.Revival;
import io.github.jcondedata.aliveworkplace.inn.Traveller;
import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.nether.Netherworkers;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.platform.Attachment;
import io.github.jcondedata.aliveworkplace.ranch.Daycare;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Every piece of data we keep on a villager ({@code registry/ModAttachments}) survives the villager being saved and
 * loaded again (a chunk unloading, a server restart). The full check's biggest gap from {@code inventory.py}: 69 of
 * the 72 saved fields had no save-and-reload test (ROADMAP 21.2). A new field without a sample value here fails the
 * test, so it gets one when it is added.
 */
public class SavedDataGameTests implements FabricGameTest {
	private static final UUID A = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0");
	private static final UUID B = UUID.fromString("12345678-9abc-def0-1234-56789abcdef0");

	/** Sample values for the fields whose type is one of our own records (the plain types are made from the field's type). */
	private static Map<String, Object> samples() {
		Map<String, Object> samples = new LinkedHashMap<>();
		samples.put("BUILDER_JOB", new BuilderJob(A, true));
		samples.put("MINER_JOB", new BuilderJob(B, false));
		samples.put("TREE_FARM", new FieldJob(new BoundingBox(1, 60, 2, 9, 64, 11), true));
		samples.put("ORCHARD", new FieldJob(new BoundingBox(-5, 70, -6, 3, 72, 4), false));
		samples.put("BERRY_GOAL", ResourceLocation.parse("cobblemon:sitrus_berry"));
		samples.put("HABITAT_LURE", "typing/fire");
		samples.put("SIGHTINGS", List.of(new io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers.Sighting(
			Component.translatable("message.aliveworkplace.habitat_keeper.sighting.shiny", Component.translatable("cobblemon.species.eevee.name")), "shiny", new BlockPos(12, 70, -40), 12L)));
		samples.put("SIGHTED", List.of(A, B));
		samples.put("RANGER", new io.github.jcondedata.aliveworkplace.legend.PokemonRanger.State(12L, 13L, io.github.jcondedata.aliveworkplace.legend.PokemonRanger.LEAD,
			java.util.Optional.of(A), java.util.Optional.of(new BlockPos(4, 64, -9)), 240L));
		samples.put("HABITAT_TODAY", List.of(net.minecraft.network.chat.Component.literal("Lush Cenote, today: Lotad")));
		samples.put("HABITAT_DAY", 12L);
		net.minecraft.nbt.CompoundTag eevee = new net.minecraft.nbt.CompoundTag();
		eevee.putString("Species", "cobblemon:eevee");
		net.minecraft.nbt.CompoundTag ditto = new net.minecraft.nbt.CompoundTag();
		ditto.putString("Species", "cobblemon:ditto");
		samples.put("DAYCARE_PAIRS", List.of(new io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers.Pair(A, "Jesse", eevee, ditto, 2, 41L)));
		samples.put("BERRY_PLOT", new FieldJob(new BoundingBox(2, 64, -3, 9, 65, 5), false));
		samples.put("FARM_FIELD", new FieldJob(new BoundingBox(100, 63, 200, 108, 63, 209), true));
		samples.put("FOSSIL_REVIVALS", List.of(new Revival(A, "Jesse", ResourceLocation.parse("cobblemon:helix_fossil"),
			List.of(ResourceLocation.parse("minecraft:bone"), ResourceLocation.parse("minecraft:amethyst_shard")), 600, 245)));
		samples.put("TRAVELLER", new Traveller(123456L, 3));
		CompoundTag pokemon = new CompoundTag();
		pokemon.putString("Species", "cobblemon:eevee");
		pokemon.putInt("Level", 12);
		samples.put("DAYCARE", List.of(new Daycare.Boarder(B, "Alex", pokemon, 98765L)));
		samples.put("PARTNER", new Couples.Partner(A, Component.literal("Ada"), 4242L, true));
		samples.put("PARENTS", new Families.Parents(Component.literal("Mira"), Component.literal("Tom"), "minecraft:farmer",
			"aliveworkplace:builder", true));
		samples.put("NETHER_TRIP", new Netherworkers.Trip(1000L, 7000L, new BlockPos(30, 64, -12), 2));
		samples.put("BUILDER_EMPLOYER", new Employer(B, "Steve"));
		samples.put("COURIER_ROUTES", List.of(new RouteData(Optional.of(new BlockPos(1, 64, 1)), Optional.of(new BlockPos(40, 70, -3)),
			List.of(Items.WHEAT, Items.COBBLESTONE)), new RouteData(Optional.empty(), Optional.of(new BlockPos(-8, 65, 9)), List.of())));
		samples.put("JOB_SITE_HELD", new io.github.jcondedata.aliveworkplace.work.JobSiteTickets.Held(
			net.minecraft.core.GlobalPos.of(net.minecraft.world.level.Level.OVERWORLD, new BlockPos(12, 64, -7)), 3));
		samples.put("GUILD_MASTER", new io.github.jcondedata.aliveworkplace.hall.Guilds.Master(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("builders"), new net.minecraft.core.BlockPos(1, 2, 3)));
		samples.put("GIFTED", "aliveworkplace:night_owl");
		samples.put("SOCIAL_CLASS", io.github.jcondedata.aliveworkplace.AliveWorkplace.id("burgher"));
		samples.put("CLASS_PROGRESS", new io.github.jcondedata.aliveworkplace.people.SocialClasses.Progress(1, 2, 3, 57L));
		samples.put("CLASS_STANDING", new io.github.jcondedata.aliveworkplace.people.SocialClasses.Standing(58L, 2, 57L, -1));
		samples.put("LUXURIES_HAD", Map.of(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("berry_wine"), 55L));
		samples.put("LEGEND", new io.github.jcondedata.aliveworkplace.legend.LegendData(net.minecraft.resources.ResourceLocation.parse("aliveworkplace:master_architect"),
			"legend.aliveworkplace.master_architect.name.2", true, Optional.of(new BlockPos(5, 70, -9)), 12, 15, Map.of("home", 2), 14, 9, "visit"));
		samples.put("ARCHITECT", new io.github.jcondedata.aliveworkplace.legend.GrandRebuild.State(12L, true,
			Optional.of(java.util.UUID.fromString("5e1f3c4a-0b6d-4c2e-9a7f-1d2c3b4a5e6f"))));
		samples.put("PATHFINDER", new io.github.jcondedata.aliveworkplace.legend.Pathfinder.State(12L, Optional.of(java.util.UUID.fromString("5e1f3c4a-0b6d-4c2e-9a7f-1d2c3b4a5e6f")),
			Optional.of(new BlockPos(300, 40, -900)), "stronghold", Optional.of(new BlockPos(5, 70, -9)), true, 14L));
		samples.put("SAGE_RIDDLES", new io.github.jcondedata.aliveworkplace.legend.OldSage.Riddles(List.of(2, 7, 5), 1, 2));
		samples.put("SEER_BLESSING", new io.github.jcondedata.aliveworkplace.legend.Seer.Blessing(31L, 10, 7, 2, true));
		samples.put("BANQUET", new io.github.jcondedata.aliveworkplace.hall.Banquets.Feasted(40L, 20, 3));
		samples.put("WORK_SONG", new io.github.jcondedata.aliveworkplace.legend.BardLaureate.Song(40L, 1, 123456L, new net.minecraft.core.BlockPos(4, 64, -7)));
		samples.put("SONG_HEARD", 40L);
		samples.put("WAR_DOG", new io.github.jcondedata.aliveworkplace.legend.Beastmaster.Dog(java.util.Optional.of(A), 12L));
		samples.put("WAR_DOG_OF", B);
		samples.put("BRED_HORSE", 41L);
		samples.put("LAST_FOAL", 41L);
		samples.put("GOLEM_ROLE", "hauler");
		samples.put("GOLEM_POST", new net.minecraft.core.BlockPos(10, 64, -3));
		samples.put("HAULER_LOAD", java.util.Map.of(net.minecraft.world.item.Items.COBBLESTONE, 64));
		samples.put("GOLEM_FORGE", new io.github.jcondedata.aliveworkplace.legend.GolemSmith.Forge("hauler", 7L));
		samples.put("STRANGE_MOOD", new io.github.jcondedata.aliveworkplace.legend.StrangeMood(net.minecraft.resources.ResourceLocation.parse("aliveworkplace:golem_smith"),
			new BlockPos(3, 64, -2), Optional.of(new BlockPos(5, 70, -9)), List.of(net.minecraft.resources.ResourceLocation.parse("minecraft:diamond"),
			net.minecraft.resources.ResourceLocation.parse("minecraft:blaze_rod"), net.minecraft.resources.ResourceLocation.parse("minecraft:echo_shard")),
			Map.of("minecraft:diamond", 1), Map.of("00000000-0000-0000-0000-000000000007", 1), 11, 20));
		samples.put("WORN_OUT", new io.github.jcondedata.aliveworkplace.hall.WorkHorn.WornOut(48_000L, 24_000L));
		samples.put("TONIC", new io.github.jcondedata.aliveworkplace.people.Tonics.Drunk("aliveworkplace:miners_brew", 48_000L));
		samples.put("CUP_DELEGATE", new io.github.jcondedata.aliveworkplace.cup.CupDays.Delegate(new BlockPos(300, 64, -120), A, 2,
			new BlockPos(10, 64, 20), 72_000L));
		samples.put("FRIENDSHIP", new io.github.jcondedata.aliveworkplace.story.Friendship.Data(Map.of(A,
			new io.github.jcondedata.aliveworkplace.story.Friendship.Bond("Jesse", 120, 40L, 5L, 2, Map.of("chat", 41L), 900L, List.of("heart_2")))));
		return samples;
	}

	/** A sample for a field of a plain type (numbers, flags, lists of ids or places, player maps), different per field. */
	private static Object plain(Type type, int n) {
		if (type == Integer.class) return 7 + n;
		if (type == Long.class) return 1_000_000_000_000L + n;
		if (type == Float.class) return 2.5f + n;
		if (type == Boolean.class) return true;
		if (type instanceof ParameterizedType p) {
			Type raw = p.getRawType();
			Type[] args = p.getActualTypeArguments();
			if (raw == List.class && args[0] == ResourceLocation.class) {
				return List.of(ResourceLocation.parse("minecraft:bread"), ResourceLocation.parse("aliveworkplace:item_" + n));
			}
			if (raw == List.class && args[0] == BlockPos.class) return List.of(new BlockPos(n, 64, -n), new BlockPos(-3, 70, 12));
			if (raw == Map.class && args[0] == UUID.class && args[1] == Long.class) return Map.of(A, 500L + n, B, 24000L);
		}
		return null;
	}

	private record Entry(String name, Attachment<Object> attachment, Object value) {}

	@SuppressWarnings("unchecked")
	private static List<Entry> entries(GameTestHelper helper) {
		Map<String, Object> samples = samples();
		List<Entry> entries = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		int n = 0;
		for (Field field : ModAttachments.class.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers()) || field.getType() != Attachment.class) continue;
			n++;
			Attachment<Object> attachment;
			try {
				attachment = (Attachment<Object>) field.get(null);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
			if (field.getName().equals("BUILDER_BAG")) continue; // checked on its own: BuilderBag has no equals
			Object value = samples.containsKey(field.getName()) ? samples.get(field.getName())
				: plain(((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0], n);
			if (value == null) missing.add(field.getName());
			else entries.add(new Entry(field.getName(), attachment, value));
		}
		helper.assertTrue(missing.isEmpty(), "no sample value for " + missing + ": add one to SavedDataGameTests.samples()");
		helper.assertTrue(entries.size() >= 70, "only " + entries.size() + " saved fields found in ModAttachments");
		return entries;
	}

	private static Villager reload(GameTestHelper helper, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(tag);
		return copy;
	}

	/** Every saved field set on a villager is the same after the villager is saved and loaded again. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everySavedFieldSurvivesSaveAndReload(GameTestHelper helper) {
		List<Entry> entries = entries(helper);
		Villager villager = EntityType.VILLAGER.create(helper.getLevel());
		for (Entry entry : entries) entry.attachment().set(villager, entry.value());
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		bag.add(new ItemStack(Items.OAK_PLANKS, 40));
		bag.add(new ItemStack(Items.GLASS_PANE, 3));

		Villager copy = reload(helper, villager);
		List<String> lost = new ArrayList<>();
		for (Entry entry : entries) {
			Object back = entry.attachment().get(copy);
			if (!entry.value().equals(back)) lost.add(entry.name() + ": " + entry.value() + " came back as " + back);
		}
		helper.assertTrue(lost.isEmpty(), lost.size() + " saved fields changed in a save and reload: " + lost);
		BuilderBag bagBack = ModAttachments.BUILDER_BAG.get(copy);
		helper.assertTrue(bagBack != null, "the builder's bag was lost in a save and reload");
		helper.assertTrue(bagBack.count(Items.OAK_PLANKS) == 40 && bagBack.count(Items.GLASS_PANE) == 3,
			"the builder's bag came back with " + bagBack.stacks());

		// A second round trip keeps them too (nothing is only kept by being freshly set).
		Villager again = reload(helper, copy);
		for (Entry entry : entries) {
			helper.assertTrue(entry.value().equals(entry.attachment().get(again)), entry.name() + " lost in a second save and reload");
		}
		helper.succeed();
	}

	/**
	 * A villager that never had our data (one from an old save, or a plain vanilla one) loads with none of it, and a
	 * field removed before saving stays removed: nothing comes back from defaults.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aVillagerWithoutOurDataLoadsWithNone(GameTestHelper helper) {
		List<Entry> entries = entries(helper);
		Villager plainVillager = reload(helper, EntityType.VILLAGER.create(helper.getLevel()));
		for (Entry entry : entries) {
			helper.assertTrue(!entry.attachment().has(plainVillager), entry.name() + " appeared on a villager that never had it");
		}
		helper.assertTrue(!ModAttachments.BUILDER_BAG.has(plainVillager), "a builder's bag appeared on a plain villager");

		Villager villager = EntityType.VILLAGER.create(helper.getLevel());
		for (Entry entry : entries) entry.attachment().set(villager, entry.value());
		for (Entry entry : entries) entry.attachment().remove(villager);
		Villager copy = reload(helper, villager);
		for (Entry entry : entries) {
			helper.assertTrue(!entry.attachment().has(copy), entry.name() + " came back after being removed");
		}
		helper.succeed();
	}
}
