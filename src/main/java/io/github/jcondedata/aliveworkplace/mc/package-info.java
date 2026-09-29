/**
 * Version adapters: one small static method per Minecraft behaviour that changes between the versions we may build
 * for (chat, saved data, registries, recipes, damage...). The rest of the mod calls these instead of the changing
 * API, so a new Minecraft version is fixed here, once. On 1.21.1 every method is exactly the call it replaces; the
 * {@code checkLayers} build task keeps version switches ({@code //?}) out of the feature packages and in here.
 */
package io.github.jcondedata.aliveworkplace.mc;
