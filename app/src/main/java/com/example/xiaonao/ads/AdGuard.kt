package com.example.xiaonao.ads

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.bytedance.sdk.openadsdk.AdSlot
import com.bytedance.sdk.openadsdk.TTAdConfig
import com.bytedance.sdk.openadsdk.TTAdConstant
import com.bytedance.sdk.openadsdk.TTAdNative
import com.bytedance.sdk.openadsdk.TTAdSdk
import com.bytedance.sdk.openadsdk.TTCustomController
import com.bytedance.sdk.openadsdk.TTNativeExpressAd

/**
 * 穿山甲广告统一入口，集中管理三件事：
 *
 *  1. 合规节奏：init（Application 启动时，不碰个人信息）→ start（用户同意隐私政策后）
 *  2. Banner 的加载与失败降级（失败即隐藏，不留空白、不塌布局）
 *  3. 会员（买断去广告）状态与隐私同意状态
 *
 * APPID / 广告位 ID 配置在 app/build.gradle 的 BuildConfig 字段中，
 * 未填入时广告功能整体停用，App 其余功能完全不受影响。
 */
object AdGuard {

    private const val TAG = "AdGuard"
    private const val PREFS = "xiaonao_ads"
    private const val KEY_PRIVACY_AGREED = "privacy_agreed"
    private const val KEY_IS_PRO = "is_pro"

    /** SDK 是否已就绪（start 成功后才能请求广告） */
    private var sdkReady = false

    /** SDK 就绪回调监听者，用于通知 Compose 侧刷新 Banner 显示 */
    private val readyListeners = mutableListOf<() -> Unit>()

    // ---------- 隐私同意 ----------

    fun privacyAgreed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PRIVACY_AGREED, false)

    /** 用户在弹窗点「同意并继续」：记录状态并立即启动广告 SDK */
    fun agreePrivacy(context: Context) {
        prefs(context).edit().putBoolean(KEY_PRIVACY_AGREED, true).apply()
        startSdk(context)
    }

    // ---------- 会员（买断去广告） ----------

    fun isPro(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IS_PRO, false)

    /** 激活码校验成功后调用（激活码功能二期接入，此处先留好开关） */
    fun setPro(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_IS_PRO, value).apply()
    }

    // ---------- SDK 生命周期 ----------

    /**
     * 初始化 SDK（只 init 不 start，不获取个人信息）。
     * 由 XiaoNaoApplication#onCreate 调用。APPID 未配置时静默跳过。
     */
    fun initSdk(context: Context) {
        if (!appIdConfigured()) {
            Log.w(TAG, "未配置 PANGLE_APP_ID，广告功能停用（配置入口：app/build.gradle）")
            return
        }
        val config = TTAdConfig.Builder()
            .appId(appId())
            .appName("小脑")
            .titleBarTheme(TTAdConstant.TITLE_BAR_THEME_DARK)
            .allowShowNotify(true)
            .debug(com.example.xiaonao.BuildConfig.DEBUG) // 测试阶段开日志，release 自动关
            .directDownloadNetworkType(TTAdConstant.NETWORK_STATE_WIFI)
            .supportMultiProcess(false)
            .customController(object : TTCustomController() {
                // 隐私从紧配置：不主动使用地理位置、不采集应用安装列表，
                // 与隐私政策页的声明保持一致
                override fun isCanUseLocation(): Boolean = false
                override fun alist(): Boolean = false
            })
            .build()
        TTAdSdk.init(context.applicationContext, config)
    }

    /**
     * 启动 SDK（start 之后才能请求广告）。
     * 必须发生在用户同意隐私政策之后；已同意过（含历史启动）也会执行。
     */
    fun startSdk(context: Context) {
        if (!privacyAgreed(context) || !appIdConfigured()) return
        if (sdkReady) return
        TTAdSdk.start(object : TTAdSdk.Callback {
            override fun success() {
                sdkReady = true
                Log.i(TAG, "穿山甲 SDK 启动成功")
                readyListeners.forEach { it() }
                readyListeners.clear()
            }

            override fun fail(code: Int, msg: String?) {
                Log.w(TAG, "穿山甲 SDK 启动失败: code=$code msg=$msg")
            }
        })
    }

    fun isSdkReady(): Boolean = sdkReady

    /** 注册 SDK 就绪回调；已就绪则立即触发。返回取消注册的函数。 */
    fun onSdkReady(listener: () -> Unit): () -> Unit {
        if (sdkReady) {
            listener()
            return {}
        }
        readyListeners.add(listener)
        return { readyListeners.remove(listener) }
    }

    // ---------- Banner ----------

    /**
     * 加载 Banner 广告到容器。任何一步不满足（未就绪 / 未配置 / 加载失败）
     * 都会隐藏容器，保证界面不留空白、不塌布局。
     *
     * @param container Banner 容器（FrameLayout）
     */
    fun loadBanner(container: ViewGroup) {
        if (!sdkReady || isPro(container.context)) {
            container.visibility = View.GONE
            return
        }
        if (bannerCodeId().startsWith("YOUR_")) {
            Log.w(TAG, "未配置 PANGLE_BANNER_CODE_ID，Banner 不展示")
            container.visibility = View.GONE
            return
        }

        val adNative: TTAdNative = TTAdSdk.getAdManager().createAdNative(container.context)
        val adSlot = AdSlot.Builder()
            .setCodeId(bannerCodeId())
            // Banner 素材期望尺寸（宽 x 高，单位 dp）；640x320 为官方 demo 常用值
            .setImageAcceptedSize(640, 320)
            .build()

        adNative.loadBannerExpressAd(adSlot, object : TTAdNative.BannerExpressAdListener {
            override fun onBannerExpressAdLoad(ads: MutableList<TTNativeExpressAd>?) {
                val ad = ads?.firstOrNull()
                if (ad == null) {
                    container.visibility = View.GONE
                    return
                }
                // render 是异步的，渲染完成通过 setExpressRenderListener 回调；
                // 这里在 render 前把容器 visibility 置为 VISIBLE，
                // 若后续渲染失败再由 onRenderFail 隐藏。
                ad.setExpressRenderListener(object : TTNativeExpressAd.ExpressRenderListener {
                    override fun onRenderSuccess(view: android.view.View?, ad: TTNativeExpressAd?) {
                        if (view == null) {
                            container.visibility = View.GONE
                            return
                        }
                        container.removeAllViews()
                        if (view.parent == null) {
                            container.addView(view)
                        }
                        container.visibility = View.VISIBLE
                    }

                    override fun onRenderFail(view: android.view.View?, code: Int, msg: String?) {
                        Log.w(TAG, "Banner 渲染失败: code=$code msg=$msg")
                        container.visibility = View.GONE
                    }
                })
                ad.render()
            }

            override fun onError(code: Int, message: String?) {
                Log.w(TAG, "Banner 加载失败: code=$code message=$message")
                container.visibility = View.GONE
            }
        })
    }

    /** 释放 Banner 广告，避免内存泄漏（Compose 视图销毁时调用） */
    fun destroyBanner(container: ViewGroup) {
        container.removeAllViews()
        container.visibility = View.GONE
    }

    // ---------- 内部工具 ----------

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun appId(): String = com.example.xiaonao.BuildConfig.PANGLE_APP_ID

    private fun bannerCodeId(): String = com.example.xiaonao.BuildConfig.PANGLE_BANNER_CODE_ID

    private fun appIdConfigured(): Boolean =
        appId().isNotBlank() && !appId().startsWith("YOUR_")

    /** 测试用：把容器强制为 FrameLayout 的辅助（保留给未来扩展） */
    internal fun wrapInFrame(context: Context): FrameLayout = FrameLayout(context)
}
