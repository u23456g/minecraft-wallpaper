package dev.wallpapermenu;

import dev.wallpapermenu.wallpaper.SteamWallpaperLocator;
import dev.wallpapermenu.wallpaper.WallpaperEntry;
import dev.wallpapermenu.wallpaper.WallpaperConfig;
import dev.wallpapermenu.wallpaper.WallpaperScanner;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class WallpaperSettingsScreen extends Screen {
    private final Screen parent;
    private int page;
    private boolean scanning;
    private String status = "自动读取 Steam Workshop 与本地壁纸目录";
    private final java.util.Map<String, Preview> previewTextures = new java.util.HashMap<>();

    public WallpaperSettingsScreen(Screen parent) {
        super(Text.literal("Wallpaper Menu 设置"));
        this.parent = parent;
    }

    private record Preview(Identifier id, int width, int height) { }
    private int gridLeft, gridWidth, cardWidth, cardHeight;

    @Override
    protected void init() {
        releasePreviews();
        int controlsWidth = Math.min(170, width / 3 + 12);
        int controlsX = width - controlsWidth - 10;
        int columns = 3;
        int rows = 3;
        int gap = 6;
        gridLeft = 10;
        gridWidth = controlsX - gridLeft - 10;
        cardWidth = (gridWidth - gap * (columns - 1)) / columns;
        cardHeight = (height - 86 - gap * (rows - 1)) / rows;
        int pages = Math.max(1, (WallpaperMenuClient.WALLPAPERS.size() + 8) / 9);
        page = Math.clamp(page, 0, pages - 1);
        for (int slot = 0; slot < 9 && page * 9 + slot < WallpaperMenuClient.WALLPAPERS.size(); slot++) {
            WallpaperEntry entry = WallpaperMenuClient.WALLPAPERS.get(page * 9 + slot);
            int x = gridLeft + slot % 3 * (cardWidth + 6);
            int y = 42 + slot / 3 * (cardHeight + 6);
            ButtonWidget choice = ButtonWidget.builder(Text.literal(entry.title()), button -> {
                WallpaperMenuClient.CONFIG.selectedWallpaperId = entry.id();
                WallpaperBackground.close();
                saveConfig();
                clearAndInit();
            }).dimensions(x, y, cardWidth, cardHeight).build();
            choice.active = entry.supported();
            // Keep keyboard/mouse selection without drawing a second centered label or button frame.
            addSelectableChild(choice);
            loadPreview(entry);
        }
        int pagingY = height - 26;
        ButtonWidget previous = ButtonWidget.builder(Text.literal("上一页"), button -> { page--; clearAndInit(); })
                .dimensions(gridLeft, pagingY, cardWidth, 20).build();
        previous.active = page > 0;
        addDrawableChild(previous);
        ButtonWidget next = ButtonWidget.builder(Text.literal("下一页 " + (page + 1) + "/" + pages), button -> { page++; clearAndInit(); })
                .dimensions(gridLeft + cardWidth + 6, pagingY, cardWidth, 20).build();
        next.active = page + 1 < pages;
        addDrawableChild(next);
        ButtonWidget export = ButtonWidget.builder(Text.literal("设为整合包默认"), button -> {
            try {
                var selected = WallpaperMenuClient.WALLPAPERS.stream()
                        .filter(entry -> entry.supported() && entry.id().equals(WallpaperMenuClient.CONFIG.selectedWallpaperId))
                        .findFirst().orElse(null);
                WallpaperMenuClient.CONFIG.exportPackDefault(FabricLoader.getInstance().getConfigDir(), selected);
                status = "已生成预设；随包保留 config/wallpaper-menu，玩家需自备壁纸";
            } catch (IOException exception) {
                status = "预设保存失败：" + exception.getMessage();
                WallpaperMenuClient.LOGGER.warn("Could not export wallpaper pack default", exception);
            }
        }).dimensions(gridLeft + 2 * (cardWidth + 6), pagingY, cardWidth, 20).build();
        export.active = WallpaperMenuClient.WALLPAPERS.stream().anyMatch(entry -> entry.supported()
                && entry.id().equals(WallpaperMenuClient.CONFIG.selectedWallpaperId));
        addDrawableChild(export);

        int step = Math.min(24, (height - 48) / 9);
        int y = 42;
        ButtonWidget scan = ButtonWidget.builder(Text.literal(scanning ? "扫描中…" : "扫描壁纸目录"), button -> rescan())
                .dimensions(controlsX, y, controlsWidth, 20).build();
        scan.active = !scanning;
        addDrawableChild(scan);
        y += step;
        addSlider(controlsX, y, controlsWidth, "捕获帧率", 30, 120, WallpaperMenuClient.CONFIG.wallpaperFps,
                v -> WallpaperMenuClient.CONFIG.wallpaperFps = v);
        y += step;
        int half = (controlsWidth - 4) / 2;
        addSlider(controlsX, y, half, "音乐响度", 0, 100, WallpaperMenuClient.CONFIG.audioVolume(),
                WallpaperBackground::setAudioVolume);
        addDrawableChild(ButtonWidget.builder(Text.literal("暗化：" + WallpaperMenuClient.CONFIG.dimPercent + "%"), button -> {
            WallpaperMenuClient.CONFIG.dimPercent = (WallpaperMenuClient.CONFIG.dimPercent + 10) % 110;
            saveConfig(); clearAndInit();
        }).dimensions(controlsX + half + 4, y, controlsWidth - half - 4, 20).build());
        y += step;
        addDrawableChild(ButtonWidget.builder(Text.literal("按钮毛玻璃：" + (WallpaperMenuClient.CONFIG.glassButtonsEnabled ? "开" : "关")), button -> {
            WallpaperMenuClient.CONFIG.glassButtonsEnabled = !WallpaperMenuClient.CONFIG.glassButtonsEnabled;
            if (!WallpaperMenuClient.CONFIG.glassButtonsEnabled) GlassButtons.close();
            saveConfig(); clearAndInit();
        }).dimensions(controlsX, y, controlsWidth, 20).build());
        y += step;
        addSlider(controlsX, y, controlsWidth, "按钮透明度", 0, 100, WallpaperMenuClient.CONFIG.glassButtonTransparency,
                v -> WallpaperMenuClient.CONFIG.glassButtonTransparency = v);
        y += step;
        addSlider(controlsX, y, controlsWidth, "标题大小", 50, 150, WallpaperMenuClient.CONFIG.titleScalePercent,
                v -> WallpaperMenuClient.CONFIG.titleScalePercent = v);
        y += step;
        addSlider(controlsX, y, controlsWidth, "标题透明度", 0, 100, WallpaperMenuClient.CONFIG.titleTransparency,
                v -> WallpaperMenuClient.CONFIG.titleTransparency = v);
        y += step;
        addDrawableChild(ButtonWidget.builder(Text.literal("恢复原版背景"), button -> {
            WallpaperMenuClient.CONFIG.selectedWallpaperId = "";
            WallpaperMenuClient.CONFIG.packDefaultDisabled = true;
            WallpaperBackground.close(); saveConfig(); clearAndInit();
        }).dimensions(controlsX, y, controlsWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close())
                .dimensions(controlsX, height - 26, controlsWidth, 20).build());
    }

    private void addSlider(int x, int y, int w, String label, int min, int max, int current,
                           java.util.function.IntConsumer change) {
        addDrawableChild(new SliderWidget(x, y, w, 20, Text.literal(label + "：" + Math.clamp(current, min, max)),
                (Math.clamp(current, min, max) - min) / (double) (max - min)) {
            @Override protected void updateMessage() {
                setMessage(Text.literal(label + "：" + (min + (int) Math.round(value * (max - min)))));
            }
            @Override protected void applyValue() {
                change.accept(min + (int) Math.round(value * (max - min)));
                saveConfig();
            }
        });
    }

    private void releasePreviews() {
        if (client != null) previewTextures.values().forEach(preview -> client.getTextureManager().destroyTexture(preview.id()));
        previewTextures.clear();
    }

    @Override
    public void removed() {
        releasePreviews();
        super.removed();
    }

    private void loadPreview(WallpaperEntry entry) {
        if (entry.previewFile() == null) return;
        try {
            // ImageIO handles JPEG and the first GIF frame, unlike NativeImage's PNG-only reader.
            var source = javax.imageio.ImageIO.read(entry.previewFile().toFile());
            if (source == null) return;
            int w = source.getWidth(), h = source.getHeight();
            double scale = Math.min(1.0, 512.0 / Math.max(w, h));
            int tw = Math.max(1, (int) (w * scale)), th = Math.max(1, (int) (h * scale));
            NativeImage image = new NativeImage(tw, th, false);
            for (int py = 0; py < th; py++) for (int px = 0; px < tw; px++) {
                int argb = source.getRGB(px * w / tw, py * h / th);
                image.setColor(px, py, (argb & 0xff00ff00) | ((argb >> 16) & 255) | ((argb & 255) << 16));
            }
            Identifier id = Identifier.of("wallpaper_menu", "preview/" + previewTextures.size());
            client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
            previewTextures.put(entry.id(), new Preview(id, tw, th));
        } catch (IOException | RuntimeException exception) {
            WallpaperMenuClient.LOGGER.warn("Could not load wallpaper preview {}", entry.previewFile(), exception);
        }
    }

    private void rescan() {
        if (scanning) return;
        scanning = true;
        status = "正在后台扫描…";
        clearAndInit();
        var minecraft = client;
        CompletableFuture.supplyAsync(() -> {
            List<Path> discovered = new java.util.ArrayList<>(SteamWallpaperLocator.discover(WallpaperMenuClient.defaultSteamRoots()));
            discovered.addAll(WallpaperMenuClient.CONFIG.wallpaperRoots(FabricLoader.getInstance().getConfigDir()));
            discovered = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(discovered));
            return new ScanResult(discovered, new WallpaperScanner().scan(discovered));
        }).whenComplete((result, error) -> minecraft.execute(() -> {
            scanning = false;
            if (error != null) {
                status = "扫描失败，保留原列表";
                WallpaperMenuClient.LOGGER.warn("Wallpaper rescan failed", error);
            } else {
                WallpaperMenuClient.CONFIG.workshopDirectory = result.roots.stream()
                        .filter(path -> path.toString().replace('\\', '/').contains("workshop/content/431960"))
                        .map(Path::toString).collect(Collectors.joining(";"));
                WallpaperMenuClient.CONFIG.localDirectory = result.roots.stream()
                        .filter(path -> !path.toString().replace('\\', '/').contains("workshop/content/431960"))
                        .map(Path::toString).collect(Collectors.joining(";"));
                WallpaperMenuClient.WALLPAPERS = List.copyOf(result.entries);
                status = "扫描完成：" + result.entries.stream().filter(WallpaperEntry::supported).count() + " 个支持的壁纸";
                page = 0;
                saveConfig();
            }
            if (minecraft.currentScreen == this) clearAndInit();
        }));
    }

    private record ScanResult(List<Path> roots, List<WallpaperEntry> entries) { }

    private void saveConfig() {
        try {
            WallpaperMenuClient.CONFIG.save(FabricLoader.getInstance().getConfigDir().resolve("wallpaper-menu.json"));
        } catch (IOException exception) {
            status = "配置保存失败";
            WallpaperMenuClient.LOGGER.warn("Could not save settings", exception);
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        List<WallpaperEntry> entries = WallpaperMenuClient.WALLPAPERS;
        for (int slot = 0; slot < 9 && page * 9 + slot < entries.size(); slot++) {
            WallpaperEntry entry = entries.get(page * 9 + slot);
            int x = gridLeft + slot % 3 * (cardWidth + 6);
            int y = 42 + slot / 3 * (cardHeight + 6);
            Preview preview = previewTextures.get(entry.id());
            // Leave unused image space transparent; the title is drawn only below the cover.
            if (preview != null) {
                // Contain, never crop: reserve a separate bottom strip so the name hides no image pixels.
                double scale = Math.min((cardWidth - 4.0) / preview.width(), (cardHeight - 17.0) / preview.height());
                int w = Math.max(1, (int) (preview.width() * scale));
                int h = Math.max(1, (int) (preview.height() * scale));
                context.drawTexture(preview.id(), x + (cardWidth - w) / 2, y + (cardHeight - 15 - h) / 2,
                        w, h, 0.0f, 0.0f, preview.width(), preview.height(), preview.width(), preview.height());
            } else {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal("暂无封面"), x + cardWidth / 2, y + 6, 0xcccccc);
            }
            boolean selected = entry.id().equals(WallpaperMenuClient.CONFIG.selectedWallpaperId);
            boolean highlighted = mouseX >= x && mouseX < x + cardWidth && mouseY >= y && mouseY < y + cardHeight;
            for (var child : children()) {
                if (child instanceof ButtonWidget button && button.getX() == x && button.getY() == y && button.isFocused()) highlighted = true;
            }
            String name = (selected ? "✓ " : "") + entry.title();
            if (!entry.supported()) name = "[不可用] " + name;
            String visible = textRenderer.trimToWidth(name, cardWidth - 8);
            if (!visible.equals(name)) visible = textRenderer.trimToWidth(name, cardWidth - 8 - textRenderer.getWidth("…")) + "…";
            context.drawTextWithShadow(textRenderer, visible, x + 4, y + cardHeight - 12,
                    !entry.supported() ? 0xaaaaaa : selected || highlighted ? 0x80d8ff : 0xffffff);
        }
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, 24, 0xCCCCCC);
        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("未找到壁纸，请点击扫描"), gridLeft + gridWidth / 2, 64, 0xFFCC66);
        }
    }
}
