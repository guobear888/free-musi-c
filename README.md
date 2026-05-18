# 免费音乐播放器 Android APP

功能：
- 自动搜索 Internet Archive 上的免费音频资源
- 搜索后自动播放第一首
- 播放完自动下一首
- 支持上一首、下一首、暂停/继续
- 显示来源和授权提示

## 生成 APK 方法

1. 打开 GitHub，新建一个空仓库。
2. 上传本项目所有文件，注意 `.github/workflows/build-apk.yml` 必须保留。
3. 上传完成后，点顶部 `Actions`。
4. 选择 `Build Android APK`。
5. 点 `Run workflow`。
6. 等它运行完成后，进入运行记录，在 `Artifacts` 下载 `免费音乐播放器-debug-apk`。
7. 解压后得到 `app-debug.apk`，下载安装到安卓手机即可。

## 说明

这个APP只搜索合法免费资源，不做盗版音乐搜索。搜索源为 Internet Archive。不同音频资源的授权可能不同，APP内会显示来源和授权字段，正式分发前请再次核对授权。
