package dev.wallpapermenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/** Missing assets never block playing; Steam owns subscription and download. */
public final class WorkshopWallpaperScreen extends Screen {
    private final Screen parent;

    public WorkshopWallpaperScreen(Screen parent) {
        super(Text.literal("整合包壁纸尚未安装"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2;
        ButtonWidget steam = ButtonWidget.builder(Text.literal("打开 Steam 订阅页面"), button -> {
            String url = WallpaperMenuClient.CONFIG.workshopUrl();
            if (!url.isEmpty()) Util.getOperatingSystem().open(java.net.URI.create(url));
        }).dimensions(x, y, 200, 20).build();
        steam.active = !WallpaperMenuClient.CONFIG.workshopUrl().isEmpty();
        addDrawableChild(steam);
        addDrawableChild(ButtonWidget.builder(Text.literal("重试自动下载"), button -> WallpaperMenuClient.requestWorkshopDownload())
                .dimensions(x, y + 24, 200, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回菜单，后台继续检测"), button -> close())
                .dimensions(x, y + 48, 200, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("暂不使用此预设"), button -> {
            WallpaperMenuClient.CONFIG.packDefaultDisabled = true;
            WallpaperMenuClient.CONFIG.selectedWallpaperId = "";
            try {
                WallpaperMenuClient.CONFIG.save(net.fabricmc.loader.api.FabricLoader.getInstance()
                        .getConfigDir().resolve("wallpaper-menu.json"));
            } catch (java.io.IOException exception) {
                WallpaperMenuClient.LOGGER.warn("Could not save wallpaper opt-out", exception);
            }
            close();
        }).dimensions(x, y + 72, 200, 20).build());
    }

    @Override
    public void tick() {
        if (!WallpaperMenuClient.CONFIG.needsWorkshop(WallpaperMenuClient.WALLPAPERS)) close();
    }

    @Override
    public void close() { client.setScreen(parent); }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 58, 0xffffff);
        String name = WallpaperMenuClient.CONFIG.packWallpaperTitle;
        if (name == null || name.isBlank()) name = WallpaperMenuClient.CONFIG.workshopItemId;
        context.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(name, width - 24),
                width / 2, height / 2 - 42, 0xffffff);
        context.drawCenteredTextWithShadow(textRenderer, "需 Steam 登录并拥有 Wallpaper Engine；已自动请求客户端下载",
                width / 2, height / 2 - 28, 0xcccccc);
        context.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(WallpaperMenuClient.workshopDownloadStatus, width - 24),
                width / 2, height / 2 - 16, 0xcccccc);
    }
}
