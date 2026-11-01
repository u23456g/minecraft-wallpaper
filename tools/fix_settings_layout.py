from pathlib import Path
import subprocess
p=Path('src/main/java/dev/wallpapermenu/WallpaperSettingsScreen.java')
s=p.read_text(encoding='utf-8')
a=s.index('    @Override\n    protected void init()')
b=s.index('    private void rescan()',a)
s=s[:a]+'''    private record Preview(Identifier id, int width, int height) { }
    private int gridLeft, gridWidth, cardWidth, cardHeight;

    @Override
    protected void init() {
        releasePreviews();
        int controlsWidth = Math.min(170, width / 3 + 12);
        int controlsX = width - controlsWidth - 10;
        gridLeft = 10;
        gridWidth = controlsX - gridLeft - 10;
        cardWidth = (gridWidth - 6) / 2;
        cardHeight = (height - 82) / 2;
        int pages = Math.max(1, (WallpaperMenuClient.WALLPAPERS.size() + 3) / 4);
        page = Math.clamp(page, 0, pages - 1);
        for (int slot = 0; slot < 4 && page * 4 + slot < WallpaperMenuClient.WALLPAPERS.size(); slot++) {
            WallpaperEntry entry = WallpaperMenuClient.WALLPAPERS.get(page * 4 + slot);
            int x = gridLeft + slot % 2 * (cardWidth + 6);
            int y = 42 + slot / 2 * (cardHeight + 6);
            ButtonWidget choice = ButtonWidget.builder(Text.literal(entry.title()), button -> {
                WallpaperMenuClient.CONFIG.selectedWallpaperId = entry.id();
                WallpaperBackground.close();
                saveConfig();
                clearAndInit();
            }).dimensions(x, y, cardWidth, cardHeight).build();
            choice.active = entry.supported();
            addDrawableChild(choice);
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

        int step = Math.min(24, (height - 48) / 9);
        int y = 42;
        ButtonWidget scan = ButtonWidget.builder(Text.literal(scanning ? "扫描中…" : "扫描 Steam 壁纸"), button -> rescan())
                .dimensions(controlsX, y, controlsWidth, 20).build();
        scan.active = !scanning;
        addDrawableChild(scan);
        y += step;
        addSlider(controlsX, y, controlsWidth, "壁纸帧率", 30, 120, WallpaperMenuClient.CONFIG.wallpaperFps, v -> {
            WallpaperMenuClient.CONFIG.wallpaperFps = v;
            WallpaperBackground.close();
        });
        y += step;
        int half = (controlsWidth - 4) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("声音：" + (WallpaperMenuClient.CONFIG.audioEnabled ? "开" : "关")), button -> {
            WallpaperMenuClient.CONFIG.audioEnabled = !WallpaperMenuClient.CONFIG.audioEnabled;
            WallpaperBackground.close(); saveConfig(); clearAndInit();
        }).dimensions(controlsX, y, half, 20).build());
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

'''+s[b:]
s=s.replace('java.util.Map<String, Identifier>', 'java.util.Map<String, Preview>')
a=s.index('        List<WallpaperEntry> entries =',s.index('    public void render('))
s=s[:a]+'''        List<WallpaperEntry> entries = WallpaperMenuClient.WALLPAPERS;
        for (int slot = 0; slot < 4 && page * 4 + slot < entries.size(); slot++) {
            WallpaperEntry entry = entries.get(page * 4 + slot);
            int x = gridLeft + slot % 2 * (cardWidth + 6);
            int y = 42 + slot / 2 * (cardHeight + 6);
            Preview preview = previewTextures.get(entry.id());
            context.fill(x, y, x + cardWidth, y + cardHeight, 0xff171717);
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
            context.drawBorder(x, y, cardWidth, cardHeight, selected ? 0xff80d8ff : 0xff777777);
            if (mouseX >= x && mouseX < x + cardWidth && mouseY >= y && mouseY < y + cardHeight) {
                context.drawBorder(x + 1, y + 1, cardWidth - 2, cardHeight - 2, 0xffffffff);
            }
            String name = (selected ? "✓ " : "") + entry.title();
            if (!entry.supported()) name = "[不可用] " + name;
            String visible = textRenderer.trimToWidth(name, cardWidth - 8);
            if (!visible.equals(name)) visible = textRenderer.trimToWidth(name, cardWidth - 8 - textRenderer.getWidth("…")) + "…";
            context.drawTextWithShadow(textRenderer, visible, x + 4, y + cardHeight - 12, 0xffffff);
        }
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, 24, 0xCCCCCC);
        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("未找到壁纸，请点击扫描"), gridLeft + gridWidth / 2, 64, 0xFFCC66);
        }
    }
}
'''
s=s.replace(' 个支持的视频',' 个支持的壁纸')
p.write_text(s,encoding='utf-8')
subprocess.run(['git','add','--',str(p)],check=True)
