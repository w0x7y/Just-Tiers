package com.w0x7y.justtiers.gui;

import com.w0x7y.justtiers.JustTiers;
import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.config.JustTiersConfig;
import com.w0x7y.justtiers.gui.state.ControlAvailability;
import com.w0x7y.justtiers.gui.state.PreviewState;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Source;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Builds the Just-Tiers config screen. YACL owns the chrome; this class owns the
 * option set, the greying rule and the live preview.
 *
 * <p>Every option is present in every state — an option that cannot do anything useful
 * is greyed, never removed, so the screen never changes shape under the user.
 */
public final class JustTiersScreens {

    private static final int REFRESH_MIN_MINUTES = 5;
    private static final int REFRESH_MAX_MINUTES = 1440;
    private static final int REFRESH_STEP_MINUTES = 5;

    public static Screen create(Screen parent) {
        JustTiersConfig config = JustTiersClient.config();

        Option<Boolean> enabled = Option.<Boolean>createBuilder()
                .name(Component.translatable("justtiers.option.enabled"))
                .description(description("justtiers.option.enabled.desc"))
                .binding(true, config::isEnabled, config::setEnabled)
                .controller(TickBoxControllerBuilder::create)
                .build();

        Option<DisplayMode> displayMode = Option.<DisplayMode>createBuilder()
                .name(Component.translatable("justtiers.option.displayMode"))
                .description(description("justtiers.option.displayMode.desc"))
                .binding(DisplayMode.ALL, config::getDisplayMode, config::setDisplayMode)
                .controller(opt -> EnumControllerBuilder.create(opt)
                        .enumClass(DisplayMode.class)
                        .valueFormatter(JustTiersScreens::formatMode))
                .build();

        Option<Boolean> showRetired = Option.<Boolean>createBuilder()
                .name(Component.translatable("justtiers.option.showRetired"))
                .description(description("justtiers.option.showRetired.desc"))
                .binding(true, config::isShowRetired, config::setShowRetired)
                .controller(TickBoxControllerBuilder::create)
                .build();

        // Read lazily, so the pickers can hand this supplier to the grid screen even
        // though the map they live in is still being filled in below.
        Map<Source, Option<String>> pickers = new EnumMap<>(Source.class);
        Supplier<PreviewState> previewState = () -> new PreviewState(
                enabled.pendingValue(),
                displayMode.pendingValue(),
                pendingGamemodes(pickers),
                showRetired.pendingValue());

        for (Source source : Source.values()) {
            pickers.put(source, Option.<String>createBuilder()
                    .name(Component.translatable("justtiers.option.gamemode",
                            source.displayName()))
                    .description(gamemodeDescription(source))
                    .binding(JustTiersConfig.defaultGamemode(source),
                            () -> config.selectedGamemode(source),
                            slug -> config.setSelectedGamemode(source, slug))
                    .customController(opt ->
                            new GamemodePickerController(opt, source, previewState))
                    .build());
        }

        Runnable syncAvailability = () -> {
            ControlAvailability state = ControlAvailability.of(
                    enabled.pendingValue(), displayMode.pendingValue());
            displayMode.setAvailable(state.displayMode());
            showRetired.setAvailable(state.showRetired());
            pickers.forEach((source, option) -> option.setAvailable(state.gamemode(source)));
        };
        enabled.addEventListener((option, event) -> syncAvailability.run());
        displayMode.addEventListener((option, event) -> syncAvailability.run());
        syncAvailability.run();

        Option<Component> preview = NametagPreviewController.option(previewState);

        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("justtiers.config.title"))
                .category(displayCategory(preview, enabled, displayMode, showRetired, pickers))
                .category(dataCategory(config))
                .category(aboutCategory())
                .save(JustTiersClient::saveConfig)
                .build()
                .generateScreen(parent);
    }

    private static Map<Source, String> pendingGamemodes(Map<Source, Option<String>> pickers) {
        Map<Source, String> pending = new LinkedHashMap<>();
        pickers.forEach((source, option) -> pending.put(source, option.pendingValue()));
        return pending;
    }

    private static ConfigCategory displayCategory(Option<Component> preview,
                                                  Option<Boolean> enabled,
                                                  Option<DisplayMode> displayMode,
                                                  Option<Boolean> showRetired,
                                                  Map<Source, Option<String>> pickers) {
        OptionGroup.Builder gamemodes = OptionGroup.createBuilder()
                .name(Component.translatable("justtiers.group.gamemodes"));
        pickers.values().forEach(gamemodes::option);

        return ConfigCategory.createBuilder()
                .name(Component.translatable("justtiers.config.category.display"))
                .option(preview)
                .option(enabled)
                .option(displayMode)
                .option(showRetired)
                .group(gamemodes.build())
                .build();
    }

    private static ConfigCategory dataCategory(JustTiersConfig config) {
        Option<Integer> refreshMinutes = Option.<Integer>createBuilder()
                .name(Component.translatable("justtiers.option.novaRefresh"))
                .description(description("justtiers.option.novaRefresh.desc"))
                .binding(30, config::getNovaRefreshMinutes, config::setNovaRefreshMinutes)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                        .range(REFRESH_MIN_MINUTES, REFRESH_MAX_MINUTES)
                        .step(REFRESH_STEP_MINUTES)
                        .valueFormatter(minutes ->
                                Component.translatable("justtiers.option.novaRefresh.value",
                                        String.valueOf(minutes))))
                .build();

        ButtonOption refresh = ButtonOption.createBuilder()
                .name(Component.translatable("justtiers.option.refresh"))
                .text(Component.translatable("justtiers.option.refresh.text"))
                .description(description("justtiers.option.refresh.desc"))
                .action((screen, option) -> {
                    JustTiersClient.cache().invalidateAll();
                    JustTiersClient.novaSource().refresh();
                })
                .build();

        return ConfigCategory.createBuilder()
                .name(Component.translatable("justtiers.config.category.data"))
                .option(refreshMinutes)
                .option(refresh)
                .option(LabelOption.create(Component.translatable("justtiers.data.indexed",
                        String.valueOf(JustTiersClient.novaSource().indexedPlayerCount()))))
                .build();
    }

    private static ConfigCategory aboutCategory() {
        ConfigCategory.Builder about = ConfigCategory.createBuilder()
                .name(Component.translatable("justtiers.config.category.about"))
                .option(LabelOption.create(Component.translatable("justtiers.about.version",
                        JustTiers.VERSION)));
        for (Source source : Source.values()) {
            about.option(LabelOption.create(Component.literal(source.displayName())
                    .withStyle(style -> style.withColor(source.color()))));
        }
        return about
                .option(LabelOption.create(Component.translatable("justtiers.about.commands")))
                .option(LabelOption.create(Component.translatable("justtiers.about.licence")))
                .build();
    }

    /**
     * A gamemode row is greyed in three different situations, and the description is
     * fixed once at build time, so it carries the explanation for all of them rather
     * than only the one that happens to apply as the screen opens.
     */
    private static OptionDescription gamemodeDescription(Source source) {
        return OptionDescription.createBuilder()
                .text(Component.translatable("justtiers.option.gamemode.desc",
                                source.displayName()),
                        Component.empty(),
                        Component.translatable("justtiers.option.gamemode.inactive"),
                        Component.translatable("justtiers.option.gamemode.disabled"))
                .build();
    }

    private static OptionDescription description(String key) {
        return OptionDescription.createBuilder()
                .text(Component.translatable(key))
                .build();
    }

    /** The display-mode row is where the colour legend is taught, so it is coloured. */
    private static Component formatMode(DisplayMode mode) {
        MutableComponent text = Component.translatable("justtiers.mode." + mode.id());
        return mode.singleSource()
                .<Component>map(source -> text.withStyle(
                        style -> style.withColor(source.color())))
                .orElse(text);
    }

    private JustTiersScreens() {
    }
}
