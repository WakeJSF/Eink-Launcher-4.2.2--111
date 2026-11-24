package cn.modificator.launcher.model;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * 一键全屏刷新（E-Ink GC16 / 全刷）工具。
 *
 * <p>墨水屏硬件刷新接口各厂商实现完全不同，没有统一 Android API。本类按优先级依次尝试：
 * <ol>
 *   <li> Boyue/Boeye 专用（本设备 T80S 已实机确认）：反射 android.view.View.byFullRefresh()。
 *        该方法由厂商注入 framework（framework.odex 内），原厂桌面正是靠它全刷。</li>
 *   <li>广播：发常见国产 E-Ink 全刷广播（墨案/Allwinner 等已验证 android.eink.force.refresh）</li>
 *   <li>反射厂商 SDK：Onyx(Boox) EpdController、Yota EpdManager 等</li>
 *   <li>通用兜底：全屏黑白闪屏覆盖层，借助系统窗口变化触发 EPD 全刷</li>
 * </ol>
 * 全部操作都有异常保护，任何一步失败都不会导致桌面崩溃。兼容 Android 4.2.2（API 17）。
 */
public final class EpdRefresh {

  private static final String TAG = "EpdRefresh";
  private static final Handler MAIN = new Handler(Looper.getMainLooper());

  /** 国产墨水屏常见的强制全刷广播（不同 ROM 支持其一即可） */
  private static final String[] REFRESH_ACTIONS = new String[] {
      "com.by.android.refresh.start",          // Boyue/Boeye 原厂桌面全刷广播（本机确认存在）
      "android.eink.force.refresh",
      "android.intent.action.EPD_FULL_REFRESH",
      "com.yotadevices.intent.action.EPD_REFRESH",
      "eink.action.refresh"
  };

  private EpdRefresh() {
  }

  /**
   * 触发一次全屏刷新。应在 UI 线程调用。
   *
   * @param activity 当前桌面 Activity，用于加载覆盖层与显示提示
   */
  public static void fullRefresh(Activity activity) {
    if (activity == null) return;

    // 第一优先：本设备(Boyue T80S)确认可用的 View.byFullRefresh() 隐藏方法
    boolean boyueOk = tryBoyueViewFullRefresh();
    // 本机 Boyue 硬件接口已确认可用：跳过广播和反射扫描。
    // 5 次同步广播会让 UI 线程阻塞，Dock 全刷点击后延迟明显；硬件刷新本身已足够。
    int sent = boyueOk ? 0 : sendRefreshBroadcasts(activity);
    boolean sdkOk = boyueOk ? false : tryVendorSdk(activity);

    // 若前面没有一条确切成功的硬件刷新路径，再叠加闪屏兜底，确保肉眼可见一次刷新。
    // Boyue 专用方法成功时不叠加，避免全刷过程中被黑层二次干扰。
    if (!boyueOk) {
      showFlashOverlay(activity);
    }

    // 按用户要求：全刷不再弹出 Toast 提示（刷屏本身已是视觉反馈）。
    Log.i(TAG, "fullRefresh: boyue=" + boyueOk + ", broadcasts=" + sent + ", vendorSdk=" + sdkOk);
  }

  /**
   * Boyue/Boeye 专用全刷（本设备 T80S 实机已确认）。
   *
   * <p>反编译原厂桌面 com.boyue.app.byreader.common.utils.EInkRefresh 得知，
   * 全刷通过反射调用 android.view.View 上被厂商注入的隐藏静态方法：
   * <pre>
   *   Class<?> view = Class.forName("android.view.View");
   *   view.getDeclaredMethod("byFullRefresh").invoke(null);        // 静态，无参
   *   view.getDeclaredMethod("setEinkUpdateMode", int.class)...
   * </pre>
   * 该方法本体在 framework.odex 内，编译期 SDK 无此 API，必须运行时反射。
   *
   * @return 是否成功调用到硬件全刷方法
   */
  private static boolean tryBoyueViewFullRefresh() {
    try {
      Class<?> viewCls = Class.forName("android.view.View");

      // 方式一：直接触发全刷（原厂 EInkRefresh.byFullRefresh() 的做法）
      try {
        Method m = viewCls.getDeclaredMethod("byFullRefresh");
        m.setAccessible(true);
        Object result = m.invoke(null);
        Log.i(TAG, "View.byFullRefresh() -> " + result);
        return true;
      } catch (NoSuchMethodException e) {
        Log.d(TAG, "View.byFullRefresh() not present");
      }

      // 方式二：先设为全刷模式，再请求局部刷新触发一次全局重绘
      try {
        Method setMode = viewCls.getDeclaredMethod("setEinkUpdateMode", int.class);
        setMode.setAccessible(true);
        setMode.invoke(null, 1); // 1 = 全刷模式
        Method local = viewCls.getDeclaredMethod("byPartRefresh");
        local.setAccessible(true);
        local.invoke(null);
        Log.i(TAG, "View.setEinkUpdateMode(1)+byPartRefresh() ok");
        return true;
      } catch (NoSuchMethodException e) {
        Log.d(TAG, "View E Ink mode methods not present");
      }
    } catch (Throwable t) {
      Log.w(TAG, "tryBoyueViewFullRefresh failed", t);
    }
    return false;
  }

  /** 发送全刷广播，返回成功发送的条数 */
  private static int sendRefreshBroadcasts(Context context) {
    int sent = 0;
    for (String action : REFRESH_ACTIONS) {
      try {
        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        context.sendBroadcast(intent);
        sent++;
      } catch (Throwable t) {
        Log.w(TAG, "broadcast failed: " + action, t);
      }
    }
    return sent;
  }

  /**
   * 反射尝试调用厂商 EPD SDK 的全屏刷新方法。仅当设备上存在对应类时生效。
   *
   * @return 是否至少成功调用了一个厂商接口
   */
  private static boolean tryVendorSdk(Context context) {
    boolean ok = false;

    // Onyx Boox: com.onyx.android.sdk.api.device.epd.EpdController
    try {
      Class<?> cls = Class.forName("com.onyx.android.sdk.api.device.epd.EpdController");
      for (Method m : cls.getDeclaredMethods()) {
        if ("updateScreen".equals(m.getName())) {
          Class<?>[] types = m.getParameterTypes();
          if (types.length == 2 && types[0] == View.class && types[1] == int.class) {
            m.setAccessible(true);
            // 32 = EINK_UPDATE_MODE_FULL (Onyx)
            m.invoke(null, null, 32);
            ok = true;
            break;
          }
        }
      }
    } catch (Throwable t) {
      // 设备不是 Onyx，忽略
    }

    // Yota: com.yotadevices.epd.EpdManager / updateMode(ALL)
    if (!ok) {
      try {
        Class<?> cls = Class.forName("com.yotadevices.epd.EpdManager");
        Object mgr = cls.getMethod("getInstance", Context.class).invoke(null, context);
        if (mgr == null) {
          Constructor<?> c = cls.getDeclaredConstructor(Context.class);
          c.setAccessible(true);
          mgr = c.newInstance(context);
        }
        for (Method m : cls.getMethods()) {
          Class<?>[] types = m.getParameterTypes();
          if ("update".equalsIgnoreCase(m.getName()) && types.length == 1 && types[0] == int.class) {
            m.setAccessible(true);
            m.invoke(mgr, 1); // 1 = full/ALL mode
            ok = true;
            break;
          }
        }
      } catch (Throwable t) {
        // 设备不是 Yota，忽略
      }
    }

    return ok;
  }

  /**
   * 通用兜底：在窗口上叠加一个全屏黑色层，短暂延时后移除。
   * 大面积黑白像素翻转会促使 EPD 驱动执行一次 GC16 全刷，清掉残影。
   */
  private static void showFlashOverlay(final Activity activity) {
    MAIN.post(new Runnable() {
      @Override
      public void run() {
        try {
          // 挂到 content 根节点（FrameLayout），保证覆盖整个应用内容区且层级正确
          final FrameLayout content = (FrameLayout) activity.findViewById(android.R.id.content);
          final View flash = new View(activity);
          flash.setBackgroundColor(Color.BLACK);
          FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
          content.addView(flash, lp);

          MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
              try {
                content.removeView(flash);
              } catch (Throwable t) {
                Log.w(TAG, "remove overlay failed", t);
              }
            }
          }, 180);
        } catch (Throwable t) {
          Log.w(TAG, "flash overlay failed", t);
        }
      }
    });
  }
}
