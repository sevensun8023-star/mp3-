# MP3播放器

比亚迪 DiLink 车机本地音乐播放器，支持 **卡拉 OK 逐字变色**、**两行歌词**、**地图/桌面悬浮显示**。

## 功能

- 扫描 SD 卡 / 车机 `Music` 目录中的 mp3 等音频
- 自动匹配同名 `.lrc` 歌词文件
- 应用内 + 全局悬浮窗双行歌词
- 卡拉 OK 效果：已唱字变色，未唱字半透明
- 可调字体大小、高亮颜色、悬浮位置（上/中/下）

## 音乐与歌词放置

```
/sdcard/Music/
  ├── 歌手A/
  │   ├── 歌曲1.mp3
  │   └── 歌曲1.lrc
  └── 歌曲2.mp3
  └── 歌曲2.lrc
```

**LRC 格式支持：**

- 普通行级：`[01:02.34]歌词内容`
- 字级（更准确卡拉 OK）：`[01:02.00]你[01:02.30]好[01:02.60]啊`
- 仅有行级时间时，会自动按字数均分逐字时间

## 编译 APK

1. 用 **Android Studio** 打开本项目目录 `mp3-player`
2. 等待 Gradle 同步完成
3. 菜单 **Build → Build Bundle(s) / APK(s) → Build APK(s)**
4. APK 路径：`app/build/outputs/apk/debug/app-debug.apk`

## 安装到车机

### U 盘安装

1. 复制 `app-debug.apk` 到 U 盘
2. 车机文件管理器打开 U 盘，点击安装
3. 允许安装未知来源（如系统提示）

### ADB 安装

```bash
adb connect <车机IP>   # 若无线调试
adb install -r app-debug.apk
```

## 车机必设权限（DiLink 4.0）

1. **存储权限**：首次打开 App 允许读取音频
2. **显示在其他应用上层**（悬浮歌词关键）
   - 设置 → 应用 → MP3播放器 → 权限
   - 或 App 内「歌词设置 → 去开启悬浮窗」
3. **极速模式 → 白名单**：加入 MP3播放器，防后台被杀
4. **禁止自启动**：确认没有误关本应用

## 使用步骤

1. 打开 **MP3播放器** → 点击 **扫描音乐**
2. 点击歌曲开始播放
3. 开启 **悬浮歌词：开**
4. 切到 **桌面** 或 **地图**，应能看到两行悬浮歌词
5. **歌词设置** 里可调字体、颜色、位置

## 项目结构

```
app/src/main/java/com/car/mp3player/
  MainActivity.kt          # 主界面、列表、播放控制
  SettingsActivity.kt      # 歌词样式设置
  MusicPlaybackService.kt  # 后台播放 + 歌词同步
  LyricsOverlayService.kt  # 全局悬浮歌词
  lrc/LrcParser.kt         # LRC 解析与逐字时间
  ui/KaraokeLyricView.kt   # 两行卡拉 OK 绘制
```

## 后续可扩展

- 开机自启动
- 方向盘 / 蓝牙媒体键
- 歌词在线下载
- 仪表屏歌词（需额外适配）
