# Wallpaper Menu：早期运行与验收记录

> 以下为视频后端初期的历史记录，功能范围和分辨率描述已过时。当前安装、功能及限制以 [README.md](README.md) 为准。

## 当前运行要求

- Minecraft **1.21.1**、Java **21**、Fabric Loader **0.16.10**。
- Fabric API **0.116.1+1.21.1**、Mod Menu **11.0.5**。
- 当前视频后端调用系统 PATH 中的 `ffmpeg`。JAR **不包含 FFmpeg**；缺失时记录警告并保留原版背景。
- 只播放扫描到且被用户选中的 MP4。网页与场景项目不渲染。

## 开发命令（PowerShell，项目根目录）

```powershell
.\gradlew.bat test build --no-daemon
.\gradlew.bat runClient --no-daemon
```

解码与资源释放检查（需要 FFmpeg；不是 Gradle 单元测试）：

```powershell
javac -d build/decoder-smoke src/main/java/dev/wallpapermenu/wallpaper/VideoDecoder.java tools/VideoDecoderSmoke.java
java -cp build/decoder-smoke VideoDecoderSmoke
javac -d build/decoder-smoke src/main/java/dev/wallpapermenu/wallpaper/VideoDecoder.java tools/VideoDecoderFailureSmoke.java
java -cp build/decoder-smoke VideoDecoderFailureSmoke
javac -d build/audio-smoke src/main/java/dev/wallpapermenu/wallpaper/WallpaperAudio.java tools/WallpaperAudioSmoke.java tools/WallpaperAudioDeviceSmoke.java
java -cp build/audio-smoke WallpaperAudioSmoke
java -cp build/audio-smoke WallpaperAudioDeviceSmoke
```

最后一个检查使用带全零采样音轨的视频，向真实音频设备写入静音 PCM，不播放测试音。无音频设备时允许该检查失败，但不能把失败称为播放验收通过。

## 游戏内使用

1. 从 Mod Menu 打开 Wallpaper Menu 设置。
2. 自动扫描通过 Steam 安装候选路径、Windows Steam 注册表和 `libraryfolders.vdf` 定位 Workshop 与本地项目；无需输入路径。
3. 翻页选择支持的视频；关闭设置返回标题页后播放。
4. 可设置暗化和声音，或点击“恢复原版背景”。默认声音关闭。
5. 标题页、多人列表、世界选择、世界生成/进度及地形加载背景有接入；正常游戏和暂停菜单不替换。

## 按钮毛玻璃

- 设置页提供“按钮毛玻璃”开关，默认关闭；“按钮透明度”滑块范围为 0–100%，数值越大底板越透明，文字不随此滑块变透明。
- 作用于使用原版 PressableWidget 绘制的普通按钮，不强制改写滑块、图标按钮及其他模组自绘控件。
- 使用局部低分辨率采样产生柔化底板，无额外依赖。保留原有文字、焦点、禁用与点击行为。
- 已启用的资源包只要提供普通按钮的任一纹理（正常/高亮/禁用）或对应 `.png.mcmeta`，普通按钮即全部交回原版资源包绘制；仅安装但未启用的包不参与判断。资源重载后重新判断。

## 已有证据与未验收边界

- 构建和现有配置/扫描单元测试通过。
- 短视频跨结尾循环、动态图像帧、解码进程及文件释放 smoke 通过。
- 无音轨降级、立即关闭竞争、带静音音轨的 SourceDataLine 写入检查通过。
- 开发客户端扫描到本机壁纸，并输出 `Wallpaper video first frame uploaded`。
- **尚无最终视觉验收**：桌面前台被其他游戏占用，未验证所有页面、分页操作、切换后画面与色彩。
- **音视频无共享时钟**：两个 FFmpeg 进程分别解码，不能承诺长时间音画同步；音频目前不受 Minecraft 音量滑块控制。
- 固定解码为 960×540、30fps 并中心裁剪，非原分辨率播放；尚无性能预算验收。
- 首帧上传日志只证明纹理上传路径执行，不等于最终画面正确。
- 游戏启动成功不等于加载页面实际触发；需要手动进入世界确认进度文字和视频背景可同时显示。
