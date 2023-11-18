package com.minenash.customhud;

import com.google.gson.*;
import com.minenash.customhud.complex.ComplexData;
import com.minenash.customhud.data.Crosshairs;
import com.minenash.customhud.data.DisableElement;
import com.minenash.customhud.data.Profile;
import com.minenash.customhud.gui.ErrorScreen;
import com.minenash.customhud.errors.Errors;
import com.minenash.customhud.mod_compat.BuiltInModCompat;
import com.minenash.customhud.render.CustomHudRenderer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CustomHud implements ModInitializer {

	//Debug: LD_PRELOAD=/home/jakob/Programs/renderdoc_1.25/lib/librenderdoc.so
	public static final MinecraftClient CLIENT = MinecraftClient.getInstance();
	public static final Logger LOGGER = LogManager.getLogger("CustomHud");

	public static final Path CONFIG_FOLDER = FabricLoader.getInstance().getConfigDir().resolve("custom-hud");
	public static final Path PROFILE_FOLDER = FabricLoader.getInstance().getConfigDir().resolve("custom-hud/profiles");
	public static WatchService profileWatcher;

	private static final KeyBinding kb_enable = registerKeyBinding("enable", GLFW.GLFW_KEY_UNKNOWN);
	private static final KeyBinding kb_showErrors = registerKeyBinding("show_errors", GLFW.GLFW_KEY_B);

	private static KeyBinding registerKeyBinding(String binding, int defaultKey) {
		return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.custom_hud." + binding, InputUtil.Type.KEYSYM, defaultKey, "category.custom_hud"));
	}

	@Override
	public void onInitialize() {
		BuiltInModCompat.register();

		UpdateChecker.check();

		HudRenderCallback.EVENT.register(CustomHudRenderer::render);

		ClientTickEvents.END_CLIENT_TICK.register(CustomHud::onTick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (UpdateChecker.updateMessage != null)
				client.getMessageHandler().onGameMessage(UpdateChecker.updateMessage, false);
		});

	}

	public static void delayedInitialize() {
		readProfiles();
		onProfileChangeOrUpdate();
		try {
			profileWatcher = FileSystems.getDefault().newWatchService();
			PROFILE_FOLDER.register(profileWatcher, StandardWatchEventKinds.ENTRY_CREATE,StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
		} catch (IOException e) {
			e.printStackTrace();
		}
		ConfigManager.load();
	}

	public static void readProfiles() {
		try(Stream<Path> pathsStream = Files.list(PROFILE_FOLDER)) {
			for (Path path : pathsStream.collect(Collectors.toSet()))
				if (!Files.isDirectory(path))
					ProfileManager.add( Profile.parseProfile(path, path.getFileName().toString()) );
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	private static ComplexData.Enabled previousEnabled = ComplexData.Enabled.DISABLED;
	public static boolean justSaved = false;
	private static int saveDelay = -1;
	private static void onTick(MinecraftClient client) {
		if (saveDelay > 0)
			saveDelay--;
		else if (saveDelay == 0) {
			ConfigManager.save();
			saveDelay = -1;
		}

		updateProfiles();
		Profile profile = ProfileManager.getActive();
		if (profile != null && client.cameraEntity != null) {
			if (!Objects.equals(previousEnabled,profile.enabled)) {
				ComplexData.reset();
				previousEnabled = profile.enabled;
			}
			ComplexData.update(profile);
		}


		//TODO: Redo KeyBinds!
//		if (kb_enable.wasPressed()) {
//			enabled = !enabled;
//		}
//		else
			return;

//		onProfileChangeOrUpdate();
//		CustomHud.justSaved = true;
//		saveDelay = 100;
	}

	public static boolean isDisabled(DisableElement element) {
		return ProfileManager.getActive() != null && ProfileManager.getActive().disabled.contains(element);
	}

	private static void updateProfiles() {
		WatchKey key = CustomHud.profileWatcher.poll();
		if (key == null)
			return;
		for (WatchEvent<?> event : key.pollEvents()) {
			if (CustomHud.justSaved) {
				CustomHud.justSaved = false;
				break;
			}

			Path path = CustomHud.PROFILE_FOLDER.resolve((Path) event.context());
			String profileName = path.getFileName().toString();
			if (event.kind().name().equals("ENTRY_DELETE")) {
				Profile p = ProfileManager.get(profileName);
				if (p != null)
					ProfileManager.remove(p);
				return;
			}
			if (event.kind().name().equals("ENTRY_CREATE")) {
				if (ProfileManager.exists(profileName)) {
					System.out.println("CustomHud ENTRY CREATE: You Exist?");
					return;
				}
			}
			if (event.kind().name().equals("ENTRY_MODIFY")) {
				if (!ProfileManager.exists(profileName)) {
					System.out.println("CustomHud ENTRY MODIFY: You Don't Exist?");
					return;
				}
			}
			ProfileManager.add( Profile.parseProfile(path, profileName) );

			LOGGER.info("Updated Profile " + profileName);
			showToast(profileName, false);
			if (CLIENT.currentScreen instanceof ErrorScreen screen)
				screen.changeProfile(profileName);

		}

		onProfileChangeOrUpdate();
		key.reset();
	}

	public static void onProfileChangeOrUpdate() {
		FabricLoader.getInstance().getObjectShare().put("customhud:crosshair",
				ProfileManager.getActive() == null ? "normal" : ProfileManager.getActive().crosshair.getName());
	}

	public static void showToast(String profileName, boolean mainMenu) {
		CLIENT.getToastManager().add(new SystemToast(SystemToast.Type.TUTORIAL_HINT,
				Text.translatable("gui.custom_hud.profile_updated", profileName).formatted(Formatting.WHITE),
				Errors.hasErrors(profileName) ?
						Text.literal("§cFound " + Errors.getErrors(profileName).size() + " errors")
							.append(CLIENT.currentScreen instanceof TitleScreen ?
								Text.literal("§7, view in config screen via modmenu ")
								: Text.literal("§7, press ")
									.append(((MutableText)kb_showErrors.getBoundKeyLocalizedText()).formatted(Formatting.AQUA))
									.append("§7 to view"))
						: Text.literal("§aNo errors found")
		));
	}


}
