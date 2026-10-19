# 变现改造说明（v1.25）

> 本文件记录「小脑盈利计划」的代码改造。运营层面的完整方案见《小脑盈利执行手册》（HTML）。
> 原则：原仓库 little-brian 自此冻结，所有变现改造只在本仓库进行。

## 一、v1.25 已完成（Step 1~2：SDK 接入 + 合规 + Banner）

| 模块 | 文件 | 说明 |
|---|---|---|
| Application | `XiaoNaoApplication.kt` | 在 onCreate 中调用 `AdGuard.initSdk`（只 init 不 start，不碰个人信息） |
| 广告封装 | `ads/AdGuard.kt` | 合规节奏（init→隐私同意→start）、Banner 加载与失败降级、pro 会员状态预留 |
| Banner 组件 | `ads/BannerAd.kt` | Compose `AndroidView` 包装；pro 用户不占位、SDK 未就绪不渲染、加载失败自动隐藏 |
| 隐私弹窗 | `ui/PrivacyConsentDialog.kt` | 首次启动弹出；同意才启动广告 SDK，拒绝则广告停用但功能可用 |
| 清单配置 | `AndroidManifest.xml` | `tools:overrideLibrary` 放行 minSdk 24 冲突、TT_PANGOLIN 权限、TTFileProvider |
| 构建配置 | `settings.gradle` / `app/build.gradle` | 穿山甲 Maven 仓库 + SDK 依赖 + BuildConfig 广告位字段 |
| 字符串 | `strings.xml` | 隐私政策 URL 与弹窗文案 |
| 混淆规则 | `proguard-rules.pro` | 穿山甲 keep 规则（R8 当前关闭，为重开预留） |

## 二、配置方法（拿到穿山甲 APPID 后）

编辑 `app/build.gradle`，两处占位符替换为真实值：

```groovy
buildConfigField "String", "PANGLE_APP_ID", '"5404308"'          // 5 开头的 7 位数字
buildConfigField "String", "PANGLE_BANNER_CODE_ID", '"9451137576"' // Banner 代码位 ID
```

未配置时广告功能自动停用（日志有 warning），App 其余功能完全正常。

## 三、本地测试步骤

1. 环境：JDK 21 + Gradle 8.9 + Android Studio（compileSdk 34）
2. **必须用真机测试**：穿山甲 SDK 只带 arm 架构 so，x86/x86_64 模拟器加载不到广告
3. 首次启动应弹出隐私政策 → 点「同意并继续」→ 主列表底部出现 Banner
4. 不点同意直接杀掉进程重进，应再次弹窗；Banner 不出现
5. 卸载重装可重置同意状态（清数据亦可：设置 → 应用 → 小脑 → 清除数据）

## 四、尚未完成（后续 Step）

- **Step 3 激励视频**：统计页导出（每日前 2 次免费）+ 批量导入 20 条上限解锁
- **Step 8 会员激活码**：Room 加 license 表 + 设置页输入入口 + pro 状态联动
- **Step 4 隐私政策网页**：`startime-ltk.github.io/privacy.html` 需上线（弹窗里已引用该 URL）
- **Step 6 酷安上架**：6 张截图（桌面小组件实拍第一张）

## 五、已知注意点

1. `minSdk 21` 机型（Android 5.0/6.0）上穿山甲 SDK 不可用，广告自动不展示，其余功能不受影响
2. R8 保持关闭（v1.23 闪退教训），将来重开前先跑一遍核心路径回归测试
3. 上架 Google Play 的包需关闭 App 内自建 OTA 更新（与 Play 更新机制冲突）
4. 穿山甲 SDK 版本以接入中心披露为准，升级前必须本地完整测试
