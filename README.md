# Wallpaper Menu (Fabric 1.21.1)

将本机 Wallpaper Engine 壁纸用于 Minecraft 菜单背景。当前为 **0.1.1 实验性预发布版**，主要在 Windows 上开发验证。

## 安装

1. 安装 Minecraft **1.21.1**、Java **21** 与 Fabric Loader **0.16.10**。
2. 将 Release 中的 `wallpaper-menu-0.1.1.jar`、Fabric API **0.116.1+1.21.1**、Mod Menu **11.0.5** 放入游戏 `mods` 目录。
3. 视频/音频播放需要系统 PATH 中可执行的 **FFmpeg**，不随本模组分发。
4. 网页壁纸需要安装 Microsoft Edge 或 Google Chrome。
5. 场景壁纸需要 Windows 和已安装、可运行的 **Wallpaper Engine**；本模组不是独立 scene.pkg 渲染器。
6. 从 Mod Menu 打开 Wallpaper Menu，扫描 Steam 壁纸后选择项目。

### 整合包预设壁纸

整合包可以在游戏 `config/wallpaper-menu/pack.json` 提供：

```json
{
  "defaultWallpaperId": "Workshop项目目录ID"
}
```

壁纸文件不随该声明分发。将玩家已经通过 Steam 订阅/合法取得的壁纸按 ID 与本地扫描结果匹配；只有玩家尚无选择时才应用此默认项，已有个人选择优先。需要用户自行提供文件时，可放入 `config/wallpaper-menu/packs/<id>/`，目录内使用 Wallpaper Engine 的 `project.json` 和原资源结构；模组会一并扫描。不要将第三方壁纸或 Workshop 文件随整合包分发，除非作者或许可证明确授权。

本项目不提供 Wallpaper Engine、Minecraft、FFmpeg 或任何 Workshop 壁纸。请自行合法获取并遵守各自许可。

## 功能

- Steam 默认/自定义库路径发现，扫描 MP4、网页和 scene 项目。
- 每页九张壁纸，预览封面，切换背景。
- 30–120 捕获帧率设置（不保证实际达到目标帧率）、暗化、标题大小与透明度、可选毛玻璃按钮。
- 0–100 音乐响度：0 静音，大于 0 开启并控制增益；旧声音开关配置兼容。
- 壁纸响度大于 0 时压低原版音乐至 0，归零时恢复本次自动静音前记录的值。
- 网页/场景输入转发；场景借助透明、穿透、不进入任务栏的 Wallpaper Engine 宿主窗口抓帧。
- 正常退出释放模组创建的播放资源；不强制结束用户自己的 Wallpaper Engine 主程序。
- Windows 系统媒体会话标题/艺人显示（取决于播放器提供的元数据）。

## 已知限制

- 实验性版本，不承诺所有第三方壁纸、网页 API、场景脚本或多显示器组合兼容。
- 场景静音使用 Wallpaper Engine 全局 mute/unmute，可能影响桌面壁纸声音；场景音量属性是否响应依赖实际项目，尚未完成全部听音验证。
- 视频音画没有共享时钟，长时间同步不保证；网页音量通过媒体元素及 Web Audio 增益适配，特殊网页可能不兼容。
- 分辨率、捕获和编码消耗 CPU/GPU；捕获帧率不等于实际输出帧率。
- 部分标题透明度、页面背景、窗口与任务栏行为仍需更多实机验证。
- JVM 强制终止或系统崩溃不保证执行正常退出钩子。

## 构建

```powershell
.\gradlew.bat test build --no-daemon
.\gradlew.bat runClient --no-daemon
```

构建产物位于 `build/libs`。`tools` 中的显式本机 smoke 工具可能需要 FFmpeg、音频设备、浏览器或指定 Workshop 项目；不要将这些检查通过等同于所有场景验收通过。

## 许可

本项目源码使用 MIT 许可，见 LICENSE。嵌入的 JNA/JNA-platform 属于其各自作者并遵循其上游许可；外部软件与壁纸素材不属于本项目许可范围。
