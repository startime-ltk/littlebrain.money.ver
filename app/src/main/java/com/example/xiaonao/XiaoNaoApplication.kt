package com.example.xiaonao

import android.app.Application
import com.example.xiaonao.ads.AdGuard

/**
 * 小脑 Application。
 *
 * 按穿山甲官方要求，SDK 的 init 阶段放在 Application#onCreate 中调用，
 * 避免出现 content 为 null 的异常。init 不会获取个人信息；
 * 真正启动广告业务（TTAdSdk.start）要等用户同意隐私政策后才执行，
 * 见 [com.example.xiaonao.ads.AdGuard]。
 */
class XiaoNaoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 第一步：只初始化，不获取个人信息（未配置 APPID 时内部自动跳过）
        AdGuard.initSdk(this)
        // 第二步：历史已同意过的用户，重启后直接启动广告 SDK；
        // 首次启动的新用户在 MainActivity 隐私弹窗点「同意」后由 AdGuard.agreePrivacy 启动
        if (AdGuard.privacyAgreed(this)) {
            AdGuard.startSdk(this)
        }
    }
}
