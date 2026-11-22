package cn.modificator.launcher.model;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.util.Map;

import cn.modificator.launcher.R;
import cn.modificator.launcher.Utils;
import cn.modificator.launcher.widgets.ObserverFontTextView;
import cn.modificator.launcher.widgets.RatioImageView;

/**
 * WiFi 状态管理及 UI 绑定。
 * 通过 {@link #init(Context)} 初始化单例，{@link #bind(View, Map)} 绑定视图。
 */
public class WifiControl {

  private static final String TAG = "WifiControl";
  private static final String WIFI_ON_RES_NAME = "E-ink_Launcher.WifiOn";
  private static final String WIFI_OFF_RES_NAME = "E-ink_Launcher.WifiOff";

  private ObserverFontTextView appName;
  private RatioImageView appImage;
  private final WifiStateReceiver wifiStateReceiver;
  private final WifiManager wifiManager;
  private final Context appContext;
  private boolean receiverRegistered;

  private int showNameRes;
  private int showIconRes;
  private String connectWifiName;   // 纯 SSID，不含换行
  private Map<String, File> iconReplaceMap;

  // 自定义 WiFi 图标的安全解码缓存（避免状态切换时反复解码大图导致 OOM）
  private Drawable cachedOnDrawable;
  private Drawable cachedOffDrawable;
  private String cachedOnKey;
  private String cachedOffKey;

  private static WifiControl instance;

  public static void init(Context context) {
    release();
    instance = new WifiControl(context.getApplicationContext());
    reloadWifiName();
  }

  private WifiControl(Context context) {
    appContext = context;
    wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);

    applyWifiState(wifiManager.getWifiState());

    wifiStateReceiver = new WifiStateReceiver();
    IntentFilter filter = new IntentFilter();
    filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
    filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
    Utils.registerReceiverCompat(appContext, wifiStateReceiver, filter);
    receiverRegistered = true;
  }

  public static void release() {
    if (instance == null) return;
    instance.appImage = null;
    instance.appName = null;
    instance.cachedOnDrawable = null;
    instance.cachedOffDrawable = null;
    instance.cachedOnKey = null;
    instance.cachedOffKey = null;
    if (instance.receiverRegistered) {
      try {
        instance.appContext.unregisterReceiver(instance.wifiStateReceiver);
      } catch (IllegalArgumentException ignored) {
      }
      instance.receiverRegistered = false;
    }
    instance = null;
  }

  public static void bind(View view, Map<String, File> iconReplaceMap) {
    if (view == null) {
      instance.appImage = null;
      instance.appName = null;
      return;
    }
    instance.iconReplaceMap = iconReplaceMap;
    instance.appName = view.findViewById(R.id.appName);
    instance.appImage = view.findViewById(R.id.appImage);
    instance.updateStatus();
  }

  public static void reloadWifiName() {
    if (instance == null || instance.wifiManager == null) return;
    try {
      String ssid = instance.wifiManager.getConnectionInfo().getSSID();
      if (ssid == null) return;
      ssid = ssid.replace("\"", "");
      if (TextUtils.isEmpty(ssid) || "<unknown ssid>".equalsIgnoreCase(ssid)) return;
      instance.connectWifiName = ssid;
      instance.showNameRes = R.string.wifi_ssid_only;
      instance.updateStatus();
    } catch (SecurityException ignored) {
      // Android 8+ requires location permission for SSID access.
    }
  }

  private void updateStatus() {
    if (appName == null) return;

    // 连接状态直接显示 SSID 名字（单行）；其余状态显示固定文案（无 %s 占位符）。
    if (showNameRes == R.string.wifi_ssid_only) {
      // 避免对 "%s" getString 时缺参数抛异常
      appName.setText(TextUtils.isEmpty(connectWifiName)
          ? appContext.getString(R.string.wifi_status_on) : connectWifiName);
    } else {
      appName.setText(appContext.getString(showNameRes));
    }

    if (appImage == null) return;
    String fileName = showIconRes == R.drawable.wifi_on ? WIFI_ON_RES_NAME : WIFI_OFF_RES_NAME;
    File replaceFile = iconReplaceMap != null ? iconReplaceMap.get(fileName) : null;
    if (replaceFile != null) {
      setCustomIconSafely(appImage, replaceFile, fileName);
    } else {
      appImage.setImageResource(showIconRes);
    }
  }

  /**
   * 安全加载自定义 WiFi 图标。原实现用 ImageView.setImageURI() 直接解码原始大图
   * （用户常放 2048x2048 PNG），开关 WiFi 时状态广播反复触发解码，极易 OOM 崩溃。
   * 这里改为 inJustDecodeBounds 测尺寸 + 采样缩放 + RGB_565 + 结果缓存，
   * 与 AppItemBinder 中普通 App 图标的安全加载策略一致。
   */
  private void setCustomIconSafely(RatioImageView view, File file, String cacheKeyBase) {
    String cacheKey = cacheKeyBase + "#" + file.lastModified();
    Drawable cached = WIFI_ON_RES_NAME.equals(cacheKeyBase) ? cachedOnDrawable : cachedOffDrawable;
    String cachedKey = WIFI_ON_RES_NAME.equals(cacheKeyBase) ? cachedOnKey : cachedOffKey;
    if (cached != null && cacheKey.equals(cachedKey)) {
      view.setImageDrawable(cached);
      return;
    }

    Bitmap bitmap = null;
    try {
      BitmapFactory.Options bounds = new BitmapFactory.Options();
      bounds.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);

      int target = 128;  // WiFi 图标显示尺寸很小，128 足够，显著省内存
      int sample = 1;
      while (bounds.outWidth / sample > target * 2 || bounds.outHeight / sample > target * 2) {
        sample <<= 1;
        if (sample >= 16) break;
      }

      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inSampleSize = sample;
      options.inPreferredConfig = Bitmap.Config.RGB_565;
      options.inDither = true;
      bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    } catch (OutOfMemoryError e) {
      Log.w(TAG, "OOM decoding custom wifi icon: " + file.getName());
    } catch (Throwable t) {
      Log.w(TAG, "Failed to decode custom wifi icon: " + file.getName(), t);
    }

    if (bitmap != null) {
      Drawable drawable = new BitmapDrawable(appContext.getResources(), bitmap);
      if (WIFI_ON_RES_NAME.equals(cacheKeyBase)) {
        cachedOnDrawable = drawable;
        cachedOnKey = cacheKey;
      } else {
        cachedOffDrawable = drawable;
        cachedOffKey = cacheKey;
      }
      view.setImageDrawable(drawable);
    } else {
      // 解码失败降级为内置图标，绝不再用 setImageURI（OOM 根源）
      view.setImageResource(showIconRes);
    }
  }

  public static void onClickWifiItem() {
    try {
      int state = instance.wifiManager.getWifiState();
      boolean isEnabled = (state == WifiManager.WIFI_STATE_ENABLING || state == WifiManager.WIFI_STATE_ENABLED);
      instance.wifiManager.setWifiEnabled(!isEnabled);
    } catch (Exception e) {
      instance.openWifiSettingsFallback();
    }
  }

  public static void onLongClickWifiItem() {
    Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    instance.appContext.startActivity(intent);
  }

  private void openWifiSettingsFallback() {
    if (tryStartSettings(Settings.ACTION_WIFI_SETTINGS)) return;
    if (tryStartSettings(Settings.ACTION_SETTINGS)) return;
    Toast.makeText(appContext, "打开Wi-Fi设置失败", Toast.LENGTH_SHORT).show();
  }

  private boolean tryStartSettings(String action) {
    Intent intent = new Intent(action);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      appContext.startActivity(intent);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private void applyWifiState(int wifiState) {
    switch (wifiState) {
      case WifiManager.WIFI_STATE_DISABLED:
        showNameRes = R.string.wifi_status_off;
        showIconRes = R.drawable.wifi_off;
        break;
      case WifiManager.WIFI_STATE_DISABLING:
        showNameRes = R.string.wifi_status_closing;
        showIconRes = R.drawable.wifi_on;
        break;
      case WifiManager.WIFI_STATE_ENABLING:
        showNameRes = R.string.wifi_status_opening;
        showIconRes = R.drawable.wifi_off;
        break;
      case WifiManager.WIFI_STATE_ENABLED:
        showNameRes = R.string.wifi_status_on;
        showIconRes = R.drawable.wifi_on;
        break;
    }
  }

  private class WifiStateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();

      if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
        int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
        applyWifiState(wifiState);
      }

      if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
        NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
        if (networkInfo != null) {
          handleNetworkStateChanged(networkInfo);
        }
      }

      updateStatus();
    }
  }

  private void handleNetworkStateChanged(NetworkInfo networkInfo) {
    switch (networkInfo.getState()) {
      case CONNECTED:
        Log.d(TAG, "CONNECTED");
        String wifiName = "";
        if (networkInfo.getExtraInfo() != null) {
          wifiName = networkInfo.getExtraInfo().replace("\"", "");
        }
        if (wifiName.isEmpty()) {
          try {
            String ssid = wifiManager.getConnectionInfo().getSSID();
            if (ssid != null) wifiName = ssid.replace("\"", "");
          } catch (Throwable ignored) {
          }
        }
        // 连接时直接显示 SSID 名字（单行），不再拼"已连接"和换行
        if (!TextUtils.isEmpty(wifiName)) {
          showNameRes = R.string.wifi_ssid_only;
          connectWifiName = wifiName;
        } else {
          showNameRes = R.string.wifi_status_on;
        }
        break;
      case CONNECTING:
        Log.d(TAG, "CONNECTING");
        showNameRes = R.string.wifi_status_connecting;
        break;
      case DISCONNECTED:
        Log.d(TAG, "DISCONNECTED");
        showNameRes = R.string.wifi_status_disconnected;
        break;
      case DISCONNECTING:
        Log.d(TAG, "DISCONNECTING");
        showNameRes = R.string.wifi_status_disconnecting;
        break;
      default:
        break;
    }
  }
}
