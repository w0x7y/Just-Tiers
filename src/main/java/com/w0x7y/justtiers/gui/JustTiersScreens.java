package com.w0x7y.justtiers.gui;

import com.w0x7y.justtiers.render.SiteColors;
import com.w0x7y.justtiers.JustTiers;
import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.config.JustTiersConfig;
import com.w0x7y.justtiers.config.Palette;
import com.w0x7y.justtiers.gui.state.ControlAvailability;
import com.w0x7y.justtiers.render.model.BadgePosition;
import com.w0x7y.justtiers.render.model.NametagSettings;
import com.w0x7y.justtiers.render.model.NametagStyle;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Source;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.awt.Color;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
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

        Option<Boolean> enabled = tickBox("justtiers.option.enabled",
                config::isEnabled, config::setEnabled);

        Option<DisplayMode> displayMode = Option.<DisplayMode>createBuilder()
                .name(Component.translatable("justtiers.option.displayMode"))
                .description(description("justtiers.option.displayMode.desc"))
                .binding(DisplayMode.ALL, config::getDisplayMode, config::setDisplayMode)
                .controller(opt -> EnumControllerBuilder.create(opt)
                        .enumClass(DisplayMode.class)
                        .valueFormatter(JustTiersScreens::formatMode))
                .build();

        Option<Boolean> showRetired = tickBox("justtiers.option.showRetired",
                config::isShowRetired, config::setShowRetired);

        Option<BadgePosition> badgePosition = Option.<BadgePosition>createBuilder()
                .name(Component.translatable("justtiers.option.badgePosition"))
                .description(description("justtiers.option.badgePosition.desc"))
                .binding(BadgePosition.BEFORE, config::getBadgePosition, config::setBadgePosition)
                .controller(opt -> EnumControllerBuilder.create(opt)
                        .enumClass(BadgePosition.class)
                        .valueFormatter(position ->
                                Component.translatable("justtiers.badge." + position.id())))
                .build();

        Option<Boolean> showIcons = tickBox("justtiers.option.showIcons",
                config::isShowIcons, config::setShowIcons);

        Option<Boolean> showBrackets = tickBox("justtiers.option.showBrackets",
                config::isShowBrackets, config::setShowBrackets);

        Option<Boolean> hideOwnBadge = tickBox("justtiers.option.hideOwnBadge", false,
                config::isHideOwnBadge, config::setHideOwnBadge);

        Option<Palette> palette = Option.<Palette>createBuilder()
                .name(Component.translatable("justtiers.option.palette"))
                .description(description("justtiers.option.palette.desc"))
                .binding(Palette.DEFAULT, config::getPalette, config::setPalette)
                .controller(opt -> EnumControllerBuilder.create(opt)
                        .enumClass(Palette.class)
                        .valueFormatter(value -> Component.translatable(value.displayKey())))
                .build();

        // Read lazily, so the pickers can hand this supplier to the grid screen even
        // though the maps they live in are still being filled in below.
        Map<Source, Option<String>> pickers = new EnumMap<>(Source.class);
        Map<Source, Option<Color>> colorPickers = new EnumMap<>(Source.class);
        // Always the pending values, never the saved config, so the preview agrees with
        // what Save would write and Cancel discards it along with everything else.
        Supplier<NametagSettings> previewState = () -> new NametagSettings(
                enabled.pendingValue(),
                displayMode.pendingValue(),
                pendingGamemodes(pickers),
                showRetired.pendingValue(),
                new NametagStyle(badgePosition.pendingValue(),
                        showIcons.pendingValue(), showBrackets.pendingValue(),
                        pendingColors(palette, colorPickers)));

        for (Source source : Source.ALL) {
            colorPickers.put(source, Option.<Color>createBuilder()
                    .name(Component.translatable("justtiers.option.customColor",
                            source.displayName()))
                    .description(description("justtiers.option.customColor.desc"))
                    .binding(new Color(source.defaultColor()),
                            () -> new Color(config.getCustomColor(source)),
                            color -> config.setCustomColor(source, color.getRGB()))
                    .controller(ColorControllerBuilder::create)
                    .build());
        }

        for (Source source : Source.ALL) {
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
                    enabled.pendingValue(), displayMode.pendingValue(), palette.pendingValue());
            displayMode.setAvailable(state.displayMode());
            showRetired.setAvailable(state.showRetired());
            badgePosition.setAvailable(state.appearance());
            showIcons.setAvailable(state.appearance());
            showBrackets.setAvailable(state.appearance());
            hideOwnBadge.setAvailable(state.appearance());
            palette.setAvailable(state.appearance());
            pickers.forEach((source, option) -> option.setAvailable(state.gamemode(source)));
            colorPickers.values().forEach(option -> option.setAvailable(state.customColors()));
        };
        enabled.addEventListener((option, event) -> syncAvailability.run());
        displayMode.addEventListener((option, event) -> syncAvailability.run());
        // A palette change greys or ungreys the three pickers the moment it is made,
        // rather than waiting for Save.
        palette.addEventListener((option, event) -> syncAvailability.run());
        syncAvailability.run();

        Option<Component> preview = NametagPreviewController.option(previewState);

        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("justtiers.config.title"))
                .category(displayCategory(preview, enabled, displayMode, showRetired,
                        badgePosition, showIcons, showBrackets, hideOwnBadge, palette,
                        colorPickers, pickers))
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

    /**
     * The colors the preview should draw right now: the pending palette's own, or the
     * pending contents of the three pickers when that palette is Custom. Read from the
     * pending values rather than the config, so the preview recolors as the palette is
     * cycled instead of waiting for Save.
     */
    private static Map<Source, Integer> pendingColors(Option<Palette> palette,
                                                      Map<Source, Option<Color>> pickers) {
        return palette.pendingValue().colors(source -> pendingCustomColor(pickers, source));
    }

    /**
     * One picker's live value. The pickers are filled in after this supplier is built, so
     * a missing one means the screen is still being assembled, not that a color is gone.
     */
    private static int pendingCustomColor(Map<Source, Option<Color>> pickers, Source source) {
        Option<Color> picker = pickers.get(source);
        return picker == null
                ? source.defaultColor()
                : picker.pendingValue().getRGB() & 0xFFFFFF;
    }

    private static ConfigCategory displayCategory(Option<Component> preview,
                                                  Option<Boolean> enabled,
                                                  Option<DisplayMode> displayMode,
                                                  Option<Boolean> showRetired,
                                                  Option<BadgePosition> badgePosition,
                                                  Option<Boolean> showIcons,
                                                  Option<Boolean> showBrackets,
                                                  Option<Boolean> hideOwnBadge,
                                                  Option<Palette> palette,
                                                  Map<Source, Option<Color>> colorPickers,
                                                  Map<Source, Option<String>> pickers) {
        // Appearance sits above the gamemode pickers because every one of its rows shows
        // up in the preview immediately, whatever else the screen is set to.
        OptionGroup.Builder appearance = OptionGroup.createBuilder()
                .name(Component.translatable("justtiers.group.appearance"))
                .option(badgePosition)
                .option(showIcons)
                .option(showBrackets)
                .option(hideOwnBadge)
                .option(palette);
        // The three pickers sit under the palette they belong to, greyed until it is
        // Custom rather than hidden, like everything else on this screen.
        colorPickers.values().forEach(appearance::option);

        OptionGroup.Builder gamemodes = OptionGroup.createBuilder()
                .name(Component.translatable("justtiers.group.gamemodes"));
        pickers.values().forEach(gamemodes::option);

        return ConfigCategory.createBuilder()
                .name(Component.translatable("justtiers.config.category.display"))
                .option(preview)
                .option(enabled)
                .option(displayMode)
                .option(showRetired)
                .group(appearance.build())
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

        Option<Integer> tierCacheMinutes = Option.<Integer>createBuilder()
                .name(Component.translatable("justtiers.option.tierCache"))
                .description(description("justtiers.option.tierCache.desc"))
                .binding(60, config::getTierCacheMinutes, config::setTierCacheMinutes)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                        .range(REFRESH_MIN_MINUTES, REFRESH_MAX_MINUTES)
                        .step(REFRESH_STEP_MINUTES)
                        .valueFormatter(minutes ->
                                Component.translatable("justtiers.option.tierCache.value",
                                        String.valueOf(minutes))))
                .build();

        Option<Boolean> showProgress = tickBox("justtiers.option.downloadProgress",
                config::isShowDownloadProgress, config::setShowDownloadProgress);

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
                .option(tierCacheMinutes)
                .option(showProgress)
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
        for (Source source : Source.ALL) {
            about.option(LabelOption.create(Component.literal(source.displayName())
                    .withStyle(style -> style.withColor(SiteColors.of(source)))));
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

    /**
     * A tick box named and described by one translation key, defaulting to on. Every
     * boolean on this screen is that shape.
     */
    private static Option<Boolean> tickBox(String key, Supplier<Boolean> get,
                                           Consumer<Boolean> set) {
        return tickBox(key, true, get, set);
    }

    /** As {@link #tickBox(String, Supplier, Consumer)}, for a setting that defaults off. */
    private static Option<Boolean> tickBox(String key, boolean fallback,
                                           Supplier<Boolean> get, Consumer<Boolean> set) {
        return Option.<Boolean>createBuilder()
                .name(Component.translatable(key))
                .description(description(key + ".desc"))
                .binding(fallback, get, set)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static OptionDescription description(String key) {
        return OptionDescription.createBuilder()
                .text(Component.translatable(key))
                .build();
    }

    /** The display-mode row is where the color legend is taught, so it is colored. */
    private static Component formatMode(DisplayMode mode) {
        MutableComponent text = Component.translatable("justtiers.mode." + mode.id());
        return mode.singleSource()
                .<Component>map(source -> text.withStyle(
                        style -> style.withColor(SiteColors.of(source))))
                .orElse(text);
    }

    private JustTiersScreens() {
    }
}
