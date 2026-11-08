package io.github.jcondedata.aliveworkplace.build;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Map;

/**
 * Cleans the words a blueprint's block data carries (a sign's lines, a banner's or a chest's name, any mod's text saved
 * as a text component) before a builder copies them (B1044, B1047). A blueprint can be any player's file, so its words
 * keep only what a player could write by hand: the letters, colors and glow. Gone are click events (a sign runs a
 * run_command with permission level 2 for whoever clicks it), the nbt, selector and score parts the game works out with
 * permission level 2 when a sign loads (they show other players' data), and lines longer than the 384 characters a player
 * can write on a sign (huge lines make the chunk's packet too big to send). Works on the text's saved JSON, so it covers
 * every mod's text the same way.
 */
final class BlockText {
	/** The most characters a player can write on a sign's line (vanilla trims each edited line to this). */
	static final int MAX_LINE = 384;
	/** A name keeps at most as many characters as a sign's line. */
	static final int MAX_NAME = MAX_LINE;
	/** A line's saved JSON longer than this many characters a letter is cut back to its plain words. */
	private static final int JSON_PER_CHAR = 40;
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	/** Cleans every text in {@code tag}, at any depth, in place. */
	static void cleanAll(CompoundTag tag) {
		for (String key : Nbt.keys(tag).toArray(String[]::new)) {
			Tag child = tag.get(key);
			if (child instanceof StringTag) {
				int max = key.equals("CustomName") ? MAX_NAME : -1;
				String value = Nbt.getString(tag, key);
				String clean = clean(value, max);
				if (!clean.equals(value)) {
					tag.putString(key, clean);
				}
			} else if (child instanceof CompoundTag compound) {
				cleanAll(compound);
			} else if (child instanceof ListTag list) {
				// a sign's lines (SignText's "messages" and "filtered_messages", in vanilla and in mods built on it)
				cleanList(list, key.equals("messages") || key.equals("filtered_messages") ? MAX_LINE : -1);
			}
		}
	}

	private static void cleanList(ListTag list, int max) {
		for (int i = 0; i < list.size(); i++) {
			Tag element = list.get(i);
			if (element instanceof StringTag) {
				String value = Nbt.stringAt(list, i);
				String clean = clean(value, max);
				if (!clean.equals(value)) {
					list.set(i, StringTag.valueOf(clean));
				}
			} else if (element instanceof CompoundTag compound) {
				cleanAll(compound);
			} else if (element instanceof ListTag inner) {
				cleanList(inner, -1);
			}
		}
	}

	/**
	 * {@code text} (a text component's saved JSON) without click events or worked-out parts and, when {@code max} is not
	 * negative, at most {@code max} characters long: a longer one becomes its plain words, cut to {@code max}. Text that is
	 * no JSON, or not a text component, comes back as it was.
	 */
	static String clean(String text, int max) {
		String trimmed = text.trim();
		boolean structured = trimmed.startsWith("{") || trimmed.startsWith("[");
		if (!structured && max < 0) {
			return text;
		}
		JsonElement json;
		try {
			json = JsonParser.parseString(text);
		} catch (JsonParseException | IllegalStateException e) {
			return text;
		}
		if (json == null || json.isJsonNull()) {
			return text;
		}
		boolean changed = structured && strip(json);
		if (max >= 0) {
			String plain = plain(json);
			if (plain.length() > max || text.length() > (long) max * JSON_PER_CHAR) {
				return GSON.toJson(new JsonPrimitive(plain.substring(0, Math.min(max, plain.length()))));
			}
		}
		return changed ? GSON.toJson(json) : text;
	}

	/** Removes click events and nbt, selector and score parts from {@code json}, at any depth. True if it changed. */
	private static boolean strip(JsonElement json) {
		boolean changed = false;
		if (json instanceof JsonObject object) {
			for (String click : new String[] {"clickEvent", "click_event"}) {
				if (object.remove(click) != null) {
					changed = true;
				}
			}
			boolean computed = false;
			if (isString(object.get("nbt")) && (object.has("entity") || object.has("block") || object.has("storage"))) {
				for (String key : new String[] {"nbt", "entity", "block", "storage", "source", "interpret", "separator"}) {
					object.remove(key);
				}
				computed = true;
			}
			if (isString(object.get("selector"))) {
				object.remove("selector");
				object.remove("separator");
				computed = true;
			}
			if (object.get("score") instanceof JsonObject) {
				object.remove("score");
				computed = true;
			}
			if (computed) {
				// the part shows nothing; its style and the words after it stay
				object.remove("type");
				if (!object.has("text") && !object.has("translate") && !object.has("keybind")) {
					object.addProperty("text", "");
				}
				changed = true;
			}
			for (Map.Entry<String, JsonElement> entry : new ArrayList<>(object.entrySet())) {
				changed |= strip(entry.getValue());
			}
		} else if (json instanceof JsonArray array) {
			for (JsonElement element : array) {
				changed |= strip(element);
			}
		}
		return changed;
	}

	private static boolean isString(JsonElement json) {
		return json instanceof JsonPrimitive primitive && primitive.isString();
	}

	/** About the words {@code json} shows: its text, translation keys and their arguments, and the parts after it. */
	private static String plain(JsonElement json) {
		StringBuilder out = new StringBuilder();
		plain(json, out);
		return out.toString();
	}

	private static void plain(JsonElement json, StringBuilder out) {
		if (json instanceof JsonPrimitive primitive) {
			out.append(primitive.getAsString());
		} else if (json instanceof JsonArray array) {
			for (JsonElement element : array) {
				plain(element, out);
			}
		} else if (json instanceof JsonObject object) {
			for (String key : new String[] {"text", "translate", "keybind"}) {
				if (isString(object.get(key))) {
					out.append(object.get(key).getAsString());
				}
			}
			for (String key : new String[] {"with", "extra"}) {
				if (object.get(key) instanceof JsonArray parts) {
					plain(parts, out);
				}
			}
		}
	}

	private BlockText() {
	}
}
