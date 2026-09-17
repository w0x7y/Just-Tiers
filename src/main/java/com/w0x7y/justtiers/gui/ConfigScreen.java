package com.w0x7y.justtiers.gui;

import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.UncheckedIOException;

/** Keeps a failed save on screen. The bindings write a draft, so Cancel stays safe. */
final class ConfigScreen extends YACLScreen {
    private final Screen parent;
    private boolean saveFailed;

    ConfigScreen(YetAnotherConfigLib library, Screen parent) {
        super(library, parent);
        this.parent = parent;
    }

    @Override
    public boolean pendingChanges() {
        return saveFailed || super.pendingChanges();
    }

    @Override
    public void undo() {
        if (saveFailed) {
            // YACL applies bindings before calling save. Rebuild the failed draft from
            // the last committed config so Undo cannot restore unsaved binding values.
            minecraft.setScreenAndShow(JustTiersScreens.create(parent));
        } else {
            super.undo();
        }
    }

    @Override
    public void tick() {
        if (saveFailed) {
            setSaveButtonMessage(Component.translatable("justtiers.config.retrySave"),
                    Component.translatable("justtiers.config.saveFailed"));
        }
        super.tick();
    }

    @Override
    public void finishOrSave() {
        try {
            super.finishOrSave();
            saveFailed = false;
            // YACL updated these while the failed-save flag still kept the draft dirty.
            // Refresh once more now that a successful retry is actually clean.
            if (tabManager.getCurrentTab() instanceof CategoryTab tab) {
                tab.updateButtons();
            }
        } catch (UncheckedIOException failure) {
            saveFailed = true;
            setSaveButtonMessage(Component.translatable("justtiers.config.retrySave"),
                    Component.translatable("justtiers.config.saveFailed"));
            triggerImmediateNarration(false);
        }
    }

    @Override
    public Component getNarrationMessage() {
        return saveFailed ? Component.translatable("justtiers.config.saveFailed")
                : super.getNarrationMessage();
    }
}
