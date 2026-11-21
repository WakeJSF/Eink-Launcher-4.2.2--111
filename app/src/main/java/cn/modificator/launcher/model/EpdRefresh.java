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
import android.widget.Toast;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import cn.modificator.launcher.R;

/**
 * 一键全屏刷新（E-Ink GC16 / 全刷）工具。
 *
 * <p>墨水屏硬件刷新接口各厂商实现完全不同，没有统一 Android API。本类按优先级依次尝试：
 * <ol>
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

    int sent = sendRefreshBroadcasts(activity);
    boolean sdkOk = tryVendorSdk(activity);

    // 广播/SDK 是否真正生效无法可靠判断，统一叠加闪屏兜底，确保肉眼可见一次全刷。
    showFlashOverlay(activity);

    String msg = activity.getString(R.string.epd_refresh_done);
    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
    Log.i(TAG, "fullRefresh: broadcasts=" + sent + ", vendorSdk=" + sdkOk);
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
