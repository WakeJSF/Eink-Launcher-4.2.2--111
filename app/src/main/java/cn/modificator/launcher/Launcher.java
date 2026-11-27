package cn.modificator.launcher;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import cn.modificator.launcher.model.AppDataCenter;
import cn.modificator.launcher.model.HomeEntranceService;
import cn.modificator.launcher.model.FontManager;
import cn.modificator.launcher.model.IconCache;
import cn.modificator.launcher.model.WifiControl;
import cn.modificator.launcher.widgets.AppItemBinder;
import cn.modificator.launcher.widgets.BatteryView;
import cn.modificator.launcher.widgets.EInkLauncherView;
import cn.modificator.launcher.widgets.LauncherAdapter;

/**
 * 主界面 Activity - E-Ink 墨水屏桌面启动器。
 */
public class Launcher extends AppCompatActivity
    implements AppItemBinder.Callback, EInkLauncherView.OnPageChangeListener,
    SettingFragment.OnSettingChangeListener {

  private static final String TAG_SETTINGS_FRAGMENT = "settings_fragment";

  // ---- Views ----
  private EInkLauncherView launcherView;
  private View footerContainer;
  private View footerContent;
  private View batteryContainer;
  private TextView pageStatus;
  private BatteryView batteryProgress;
  private TextView batteryStatus;
  private TextView textClock;
  private View largeClockContainer;
  private TextView largeClockTime;
  private TextView largeClockDate;
  private ImageView settingIcon;
  private View topDivider;
  private LinearLayout pageIndicator;
  private BatteryView topBatteryProgress;
  private static final int DOCK_SIZE = 4;
  private final View[] dockSlotViews = new View[DOCK_SIZE];
  private final ResolveInfo[] dockInfos = new ResolveInfo[DOCK_SIZE];

  // ---- Data ----
  private AppDataCenter dataCenter;
  private Config config;
  private Calendar calendar;
  private boolean isChina = true;
  private IconCache iconCache;
  private FontManager fontManager;
  /** 顶部大时钟各 TextView 的原始字体（用于"系统默认"恢复） */
  private Typeface defaultLargeClockTimeTypeface;
  private Typeface defaultLargeClockDateTypeface;
  private LauncherAdapter adapter;
  private AppItemBinder binder;
  private boolean isSystemApp = false;
  private int focusArea = FOCUS_NONE;
  private int lastGridIndex = 0;
  private boolean confirmLongPressed = false;
  private int handledLauncherKeyCode = KeyEvent.KEYCODE_UNKNOWN;
  private long handledLauncherKeyDownTime = 0L;
  private final Handler clockHandler = new Handler(Looper.getMainLooper());
  private boolean clockTickerRunning = false;
  private boolean batteryStatusRequestedVisible = false;
  private float swipeDownX;
  private float swipeDownY;
  private boolean swipeStartedInContent;
  private int settingIconNormalWidth;
  private int settingIconNormalHeight;
  private int settingIconNormalHorizontalPadding;
  private int settingIconNormalVerticalPadding;
  private int batteryIconNormalWidth;
  private int batteryIconNormalHeight;
  private int batteryContainerNormalLeftMargin;
  private int batteryContainerNormalRightMargin;
  private int pageStatusNormalWidth;
  private float pageStatusNormalTextSize;
  private int footerNormalHeight;

  // ---- 图标排序 ----
  private boolean reorderMode;
  private ResolveInfo reorderFirst;
  private TextView statusHint;
  private final Handler statusHintHandler = new Handler(Looper.getMainLooper());
  private final Runnable hideStatusHintRunnable = new Runnable() {
    @Override
    public void run() {
      if (statusHint != null) statusHint.setVisibility(View.GONE);
    }
  };

  private static final int FOCUS_NONE = 0;
  private static final int FOCUS_GRID = 1;
  private static final int FOCUS_CLOCK = 2;
  private static final int FOCUS_BATTERY = 3;
  private static final int FOCUS_SETTING = 4;
  private static final int FOCUS_LARGE_CLOCK = 5;

  // ---- Receivers ----
  private boolean batteryRegistered;
  private boolean timeRegistered;
  private boolean usbRegistered;
  private boolean settingsChangedRegistered;

  private final BroadcastReceiver settingsChangedReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      // 刷新UI以反映设置变更
      if (config != null) {
        // Config 已经在构造函数中加载，直接读取最新值
        applyGridSize(config.getColNum(), config.getRowNum());
        if (adapter != null) {
          adapter.setFontSize(config.getFontSize());
          adapter.setAppNameLines(config.getAppNameLines());
        }
        applyCustomFont();
        if (launcherView != null) {
          launcherView.setHideDivider(config.isHideDivider());
          launcherView.setBottomAlignIcons(config.isBottomAlignIcons());
        }
        applyStatusBarVisibility();
        if (binder != null) {
          binder.setDelete(config.isShowCustomIcon());
        }
        updateClockRefreshMode();
      }
    }
  };

  private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      updateTimeShow();
    }
  };

  private final Runnable clockTicker = new Runnable() {
    @Override
    public void run() {
      updateTimeShow();
      clockHandler.postDelayed(this, getNextClockTickDelay());
    }
  };

  private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      handleBatteryChanged(intent);
    }
  };

  private final BroadcastReceiver appChangeReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      iconCache.clearAppCache();
      dataCenter.refreshAppList(binder.isDelete());
    }
  };

  private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
        iconCache.markDirty();
        refreshIcons();
      }
    }
  };

  // =========================================================================
  // Lifecycle
  // =========================================================================

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.launcher_activity);

    config = new Config(this);
    applyScreenOrientation(config.getScreenOrientation());

    // 主题切换
    int themeMode = config.getThemeMode();
    setThemeMode(themeMode);

    WifiControl.init(this);
    applyStatusBarVisibility();

    isChina = getResources().getConfiguration().locale.getCountry().equals("CN");

    initViews();
    registerStaticReceivers();
    checkLaunchHomeNotification();
  }

  private void setThemeMode(int themeMode) {
    Log.d("zyyme设置themeMode", String.valueOf(themeMode));
    if (themeMode == 0) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
      applyLauncherBackground(getResources().getColor(R.color.mainBgColor));
    } else if (themeMode == 1) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
      applyLauncherBackground(getResources().getColor(R.color.mainBgColor));
    } else if (themeMode == 2) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
      applyLauncherBackground(getResources().getColor(R.color.mainBgColor));
    } else if (themeMode == 3) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
      applyLauncherBackground(Color.WHITE);
    } else if (themeMode == 4) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
      applyLauncherBackground(Color.BLACK);
    } else if (themeMode == 5) {
      // 亮色配色，但不绘制白色半透明背景，直接显示壁纸。
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
      applySystemBarIconAppearance(true);
      applyLauncherBackground(Color.TRANSPARENT);
    } else if (themeMode == 6) {
      // 暗色配色，但不绘制黑色半透明背景，直接显示壁纸。
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
      applySystemBarIconAppearance(false);
      applyLauncherBackground(Color.TRANSPARENT);
    }
  }

  private void applySystemBarIconAppearance(boolean darkIcons) {
    // Android 4.2.2 (API 17) 不支持状态栏图标颜色调整
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      int appearance = darkIcons
          ? (android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
              | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
          : 0;
      getWindow().getInsetsController().setSystemBarsAppearance(appearance,
          android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
              | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
      return;
    }
    int flags = getWindow().getDecorView().getSystemUiVisibility();
    if (darkIcons) {
      flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
      }
    } else {
      flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
      }
    }
    getWindow().getDecorView().setSystemUiVisibility(flags);
  }

  private void applyLauncherBackground(final int backgroundColor) {
    try {
      findViewById(R.id.launcherBg).post(new Runnable() {
        @Override
        public void run() {
          try {
            findViewById(R.id.launcherBg).setBackgroundColor(backgroundColor);
            int themeMode = config != null ? config.getThemeMode() : -1;
            if (themeMode == 5) {
              applySystemBarIconAppearance(true);
            } else if (themeMode == 6) {
              applySystemBarIconAppearance(false);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
              getWindow().setStatusBarColor(backgroundColor);
              getWindow().setNavigationBarColor(backgroundColor);
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getWindow().setStatusBarContrastEnforced(false);
                getWindow().setNavigationBarContrastEnforced(false);
                getWindow().setNavigationBarDividerColor(Color.TRANSPARENT);
              }
            }
          } catch (Exception e) {
            android.util.Log.e("Launcher", "Failed to apply background", e);
          }
        }
      });
    } catch (Exception e) {
      android.util.Log.e("Launcher", "Failed to post background update", e);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    registerDynamicReceivers();
    int themeMode = config != null ? config.getThemeMode() : -1;
    if (themeMode == 5) {
      applySystemBarIconAppearance(true);
    } else if (themeMode == 6) {
      applySystemBarIconAppearance(false);
    }
    refreshIcons();
    WifiControl.reloadWifiName();
    applyCustomFont();
  }

  @Override
  protected void onPause() {
    super.onPause();
    unregisterDynamicReceivers();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (dataCenter != null) {
      dataCenter.shutdown();
    }
    if (binder != null) {
      binder.shutdown();
    }
    unregisterDynamicReceivers();
    unregisterReceiver(appChangeReceiver);
    WifiControl.release();
  }

  @Override
  public void onTrimMemory(int level) {
    super.onTrimMemory(level);
    if (iconCache != null) {
      iconCache.trimMemory(level);
    }
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent ev) {
    switch (ev.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        clearKeyboardFocus();
        swipeDownX = ev.getX();
        swipeDownY = ev.getY();
        int contentTop = largeClockContainer != null ? largeClockContainer.getTop() : 0;
        int contentBottom = getSwipeContentBottom();
        swipeStartedInContent = swipeDownY >= contentTop && swipeDownY <= contentBottom;
        break;
      case MotionEvent.ACTION_UP:
        float dx = ev.getX() - swipeDownX;
        float dy = ev.getY() - swipeDownY;
        float threshold = Math.min(getWindow().getDecorView().getWidth(),
            getWindow().getDecorView().getHeight()) / 6f;
        float primary = Math.abs(dx) >= Math.abs(dy) ? dx : dy;
        boolean endedInContent = ev.getY() >= (largeClockContainer != null
            ? largeClockContainer.getTop() : 0)
            && ev.getY() <= getSwipeContentBottom();
        if (swipeStartedInContent && endedInContent && Math.abs(primary) > threshold) {
          if (primary > 0) {
            onPagePrev();
          } else {
            onPageNext();
          }
          return true;
        }
        break;
    }
    return super.dispatchTouchEvent(ev);
  }

  private int getSwipeContentBottom() {
    if (launcherView != null && launcherView.getVisibility() == View.VISIBLE) {
      return launcherView.getBottom();
    }
    return largeClockContainer != null ? largeClockContainer.getBottom() : 0;
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    int keyCode = event.getKeyCode();
    Log.d("zyyme dispatchKeyEvent", String.valueOf(keyCode));

    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      if (handleLauncherKeyDown(keyCode, event)) {
        handledLauncherKeyCode = keyCode;
        handledLauncherKeyDownTime = event.getDownTime();
        return true;
      }
    } else if (event.getAction() == KeyEvent.ACTION_UP) {
      boolean wasHandledOnDown =
          handledLauncherKeyCode == keyCode && handledLauncherKeyDownTime == event.getDownTime();
      if (handleLauncherKeyUp(keyCode, event) || wasHandledOnDown) {
        clearHandledLauncherKey();
        return true;
      }
    }

    return super.dispatchKeyEvent(event);
  }

  // =========================================================================
  // View 初始化
  // =========================================================================

  private void initViews() {
    launcherView = findViewById(R.id.mList);
    launcherView.setFocusable(true);
    launcherView.setFocusableInTouchMode(true);
    launcherView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    footerContainer = findViewById(R.id.footerContainer);
    footerContent = findViewById(R.id.footerContent);
    batteryContainer = findViewById(R.id.batteryContainer);
    pageStatus = findViewById(R.id.pageStatus);
    batteryProgress = findViewById(R.id.batteryProgress);
    batteryStatus = findViewById(R.id.batteryStatus);
    textClock = findViewById(R.id.textClock);
    largeClockContainer = findViewById(R.id.largeClockContainer);
    largeClockTime = findViewById(R.id.largeClockTime);
    largeClockDate = findViewById(R.id.largeClockDate);
    topDivider = findViewById(R.id.topDivider);
    pageIndicator = findViewById(R.id.pageIndicator);
    topBatteryProgress = findViewById(R.id.topBatteryProgress);
    statusHint = findViewById(R.id.statusHint);
    // 记录原始字体，供"系统默认"选项恢复（必须在 applyCustomFont 之前）
    defaultLargeClockTimeTypeface = largeClockTime.getTypeface();
    defaultLargeClockDateTypeface = largeClockDate.getTypeface();
    textClock.setBackgroundResource(R.drawable.footer_item_focus);
    textClock.setClickable(true);
    View.OnClickListener clockClickListener = new View.OnClickListener() {
      @Override public void onClick(View v) { clearKeyboardFocus(); openClockActivity(); }
    };
    largeClockTime.setOnClickListener(clockClickListener);
    largeClockDate.setOnClickListener(clockClickListener);

    settingIcon = findViewById(R.id.toSetting);
    settingIconNormalWidth = settingIcon.getLayoutParams().width;
    settingIconNormalHeight = settingIcon.getLayoutParams().height;
    settingIconNormalHorizontalPadding = settingIcon.getPaddingLeft();
    settingIconNormalVerticalPadding = settingIcon.getPaddingTop();
    batteryIconNormalWidth = batteryProgress.getLayoutParams().width;
    batteryIconNormalHeight = batteryProgress.getLayoutParams().height;
    ViewGroup.MarginLayoutParams batteryParams =
        (ViewGroup.MarginLayoutParams) batteryContainer.getLayoutParams();
    batteryContainerNormalLeftMargin = batteryParams.leftMargin;
    batteryContainerNormalRightMargin = batteryParams.rightMargin;
    pageStatusNormalWidth = pageStatus.getLayoutParams().width;
    pageStatusNormalTextSize = pageStatus.getTextSize();
    footerNormalHeight = footerContent.getLayoutParams().height;
    settingIcon.setImageDrawable(
        Utils.tintDrawable(getResources().getDrawable(R.drawable.navibar_icon_settings_highlight),
            ColorStateList.valueOf(getResources().getColor(R.color.textColor))));
    footerContent.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
      @Override
      public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                 int oldLeft, int oldTop, int oldRight, int oldBottom) {
        if (right - left != oldRight - oldLeft) {
          updateTimeShow();
        }
      }
    });

    // 配置 Binder、Adapter、View
    iconCache = new IconCache(this);
    fontManager = new FontManager();
    binder = new AppItemBinder(this);
    binder.setCallback(this);
    binder.setIconCache(iconCache);
    binder.setHideAppPkg(config.getHideApps());
    adapter = new LauncherAdapter();
    adapter.setBinder(binder);
    adapter.setFontSize(config.getFontSize());
    adapter.setAppNameLines(config.getAppNameLines());
    adapter.setAppNameVisible(config.isShowAppName());
    applyCustomFont();
    launcherView.setAdapter(adapter);
    launcherView.setOnPageChangeListener(this);

    // 初始化数据中心
    dataCenter = new AppDataCenter(this);
    dataCenter.setSortMode(config.getSortMode());
    dataCenter.setAppOrder(config.getAppOrder());
    dataCenter.setPageStatus(pageStatus);

    // 一次性配置网格参数，避免多次重建
    applyGridSize(config.getColNum(), config.getRowNum());
    launcherView.post(new Runnable() {
      @Override
      public void run() {
        launcherView.requestFocus();
      }
    });
    dataCenter.setAdapter(adapter);
    dataCenter.setHideApps(config.getHideApps());
    dataCenter.setDockApps(config.getDockApps());
    dataCenter.setOnPageInfoChangedListener(new AppDataCenter.OnPageInfoChangedListener() {
      @Override
      public void onPageInfoChanged(int pageIndex, int pageCount) {
        updatePageIndicator();
      }
    });
    setupDock();

    // 翻页按钮
    findViewById(R.id.lastPage).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showLastPageAndHideSelection();
      }
    });
    findViewById(R.id.nextPage).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showNextPageAndHideSelection();
      }
    });

    // 设置按钮
    findViewById(R.id.toSetting).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        clearKeyboardFocus();
        openSettingsFragment();
      }
    });

    // 管理完成按钮
    findViewById(R.id.deleteFinish).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finishManageMode();
      }
    });
    // 电池点击打开设置
    batteryProgress.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        clearKeyboardFocus();
        openBatterySettings();
      }
    });
    topBatteryProgress.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        clearKeyboardFocus();
        openBatterySettings();
      }
    });
    textClock.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        clearKeyboardFocus();
        openClockActivity();
      }
    });

    // 时间显示
    calendar = Calendar.getInstance();
    updateTimeShow();
    updateLargeClockLayout();

    // 检测系统应用
    try {
      isSystemApp = !isUserApp(getPackageManager().getPackageInfo(getPackageName(), 0));
    } catch (PackageManager.NameNotFoundException e) {
      e.printStackTrace();
    }
  }

  // =========================================================================
  // SettingFragment.OnSettingChangeListener 实现
  // =========================================================================

  /** 广播动作：设置已更改，需要刷新UI */
  public static final String ACTION_SETTINGS_CHANGED = "cn.modificator.launcher.ACTION_SETTINGS_CHANGED";

  @Override
  public void onRowNumChanged(int rowNum) {
    config.setRowNum(rowNum);
    applyGridSize(config.getColNum(), config.getRowNum());
  }

  @Override
  public void onColNumChanged(int colNum) {
    config.setColNum(colNum);
    applyGridSize(config.getColNum(), config.getRowNum());
  }

  @Override
  public void onFontSizeChanged(float size) {
    adapter.setFontSize(size);
  }

  /** 统一入口：分别应用 App 名字体与大时钟字体（两者独立配置） */
  private void applyCustomFont() {
    applyAppFont();
    applyClockFont();
  }

  /** App 名称字体（失败降级为系统默认） */
  private void applyAppFont() {
    if (fontManager == null || config == null) return;
    Typeface typeface = fontManager.loadFont(config.getCustomFont());
    if (adapter != null) {
      adapter.setAppNameTypeface(typeface);
    }
  }

  /** 顶部大时钟字体（大时间 + 年月日星期），失败恢复各自原始字体 */
  private void applyClockFont() {
    if (fontManager == null || config == null) return;
    Typeface typeface = fontManager.loadFont(config.getClockFont());
    Typeface timeFont = typeface != null ? typeface : defaultLargeClockTimeTypeface;
    Typeface dateFont = typeface != null ? typeface : defaultLargeClockDateTypeface;
    if (largeClockTime != null) {
      largeClockTime.setTypeface(timeFont);
    }
    if (largeClockDate != null) {
      largeClockDate.setTypeface(dateFont);
    }
    // 字号需要按新字体重新测量，否则大字体可能溢出
    if (largeClockContainer != null && largeClockTime != null) {
      updateLargeClockTextSize();
    }
  }

  @Override
  public void onFontChanged(String fontName) {
    config.setCustomFont(fontName);
    if (fontManager != null) fontManager.resetFailures();
    applyAppFont();
  }

  @Override
  public void onClockFontChanged(String fontName) {
    config.setClockFont(fontName);
    if (fontManager != null) fontManager.resetFailures();
    applyClockFont();
  }

  @Override
  public void onAppNameLinesChanged(int lines) {
    adapter.setAppNameLines(lines);
  }

  @Override
  public void onHideDividerChanged(boolean hide) {
    launcherView.setHideDivider(hide);
  }

  @Override
  public void onShowStatusBarChanged(boolean show) {
    applyStatusBarVisibility();
    recreate();
  }

  @Override
  public void onShowCustomIconChanged(boolean show) {
    // 设置页持有自己的 Config 实例；必须同步回 Launcher 的实例，
    // 否则 refreshIcons() 仍按旧的开关值扫描，必须重启才生效。
    config.setShowCustomIcon(show);
    iconCache.markDirty();
    iconCache.clearIconCache();
    refreshIcons();
  }

  @Override
  public void onClockShowSecondsChanged(boolean show) {
    config.setClockShowSeconds(show);
    updateClockRefreshMode();
    updateTimeShow();
  }

  @Override
  public void onShowLargeClockChanged(boolean show) {
    config.setShowLargeClock(show);
    dataCenter.setLargeClockEnabled(show);
    applyCurrentPageLayout();
    updateTimeShow();
    launcherView.selectFirstAvailable();
    clearKeyboardFocus();
  }

  @Override
  public void onShowAppNameChanged(boolean show) {
    config.setShowAppName(show);
    if (adapter != null) {
      adapter.setAppNameVisible(show);
    }
  }

  @Override
  public void onBottomAlignIconsChanged(boolean align) {
    config.setBottomAlignIcons(align);
    launcherView.setBottomAlignIcons(align);
    launcherView.selectFirstAvailable();
    clearKeyboardFocus();
  }

  @Override
  public void onEnterManageMode() {
    binder.setDelete(true);
    dataCenter.refreshAppList(true);
    findViewById(R.id.deleteFinish).setVisibility(View.VISIBLE);
  }

  @Override
  public void onSortModeChanged(int mode) {
    // 切换自动排序方式后，旧的手动顺序不再有意义，避免用户以为排序设置失效
    config.setAppOrder("");
    dataCenter.setSortMode(mode);
    dataCenter.setAppOrder("");
  }

  @Override
  public void onThemeModeChanged(int mode) {
    if (mode == config.getThemeMode()) {
      return;
    }
    config.setThemeMode(mode);
    setThemeMode(mode);
    recreate();
  }

  @Override
  public void onScreenOrientationChanged(int mode) {
    config.setScreenOrientation(mode);
    applyScreenOrientation(mode);
  }

  // =========================================================================
  // 布局更新
  // =========================================================================

  private void refreshIcons() {
    if (adapter == null || iconCache == null) return;
    if (iconCache.refreshCustomIcons(
        getExternalCacheDir() != null, config.isShowCustomIcon())) {
      adapter.refreshDisplay();
    }
    // Dock 使用独立的 ImageView，不会随网格刷新，需单独重绑一次图标
    refreshDock();
  }

  // =========================================================================
  // Dock
  // =========================================================================

  private void setupDock() {
    int[] slotIds = { R.id.dockSlot0, R.id.dockSlot1, R.id.dockSlot2, R.id.dockSlot3 };
    for (int i = 0; i < DOCK_SIZE; i++) {
      final int slot = i;
      ViewGroup slotView = findViewById(slotIds[i]);
      if (slotView == null) continue;
      View item = LayoutInflater.from(this).inflate(R.layout.dock_item, slotView, false);
      item.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) { onDockClick(slot); }
      });
      item.setOnLongClickListener(new View.OnLongClickListener() {
        @Override public boolean onLongClick(View v) {
          onDockLongClick(slot);
          return true;
        }
      });
      slotView.removeAllViews();
      slotView.addView(item);
      dockSlotViews[i] = item;
    }
    refreshDock();
  }

  private void refreshDock() {
    if (binder == null || dataCenter == null) return;
    List<String> keys = config.getDockApps();
    Map<String, ResolveInfo> all = new HashMap<>();
    for (ResolveInfo info : dataCenter.getAllAppsForDock()) {
      all.put(AppDataCenter.componentKey(info), info);
    }
    for (int i = 0; i < DOCK_SIZE; i++) {
      View item = dockSlotViews[i];
      if (item == null) continue;
      ImageView icon = (ImageView) item.findViewById(R.id.dockItemIcon);
      TextView label = (TextView) item.findViewById(R.id.dockItemLabel);
      String key = i < keys.size() ? keys.get(i) : "";
      ResolveInfo info = key.length() > 0 ? all.get(key) : null;
      dockInfos[i] = info;
      if (info == null) {
        icon.setImageDrawable(null);
        label.setText("");
        continue;
      }
      binder.bindDockIcon(icon, info, config.isShowCustomIcon());
      label.setText(binder.getDockLabel(info));
    }
  }

  private void onDockClick(int slot) {
    ResolveInfo info = dockInfos[slot];
    if (info == null) return;
    String pkg = info.activityInfo.packageName;
    boolean virtualItem = AppDataCenter.SETTINGS_PACKAGE_NAME.equals(pkg)
        || AppDataCenter.LOCK_PACKAGE_NAME.equals(pkg)
        || AppDataCenter.WIFI_PACKAGE_NAME.equals(pkg)
        || AppDataCenter.CLEAR_PACKAGE_NAME.equals(pkg)
        || AppDataCenter.REFRESH_PACKAGE_NAME.equals(pkg);
    // 虚拟功能项（尤其是全刷）直接响应，跳过焦点/选中态重绘，避免墨水屏上多一次刷新造成延迟
    if (!virtualItem) {
      clearKeyboardFocus();
    }
    onItemClick(info);
  }

  private void onDockLongClick(final int slot) {
    final ResolveInfo info = dockInfos[slot];
    if (info == null || binder == null) return;
    CharSequence label = binder.getDockLabel(info);
    new AlertDialog.Builder(this)
        .setTitle(label)
        .setMessage(R.string.dock_remove)
        .setPositiveButton(R.string.dock_remove, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            removeAppFromDock(AppDataCenter.componentKey(info));
          }
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  public boolean addAppToDock(String componentKey, int slot) {
    if (componentKey == null || slot < 0 || slot > 3) return false;
    if (config.getDockApps().contains(componentKey)) {
      config.removeDockApp(componentKey);
    }
    if (!config.addDockApp(componentKey, slot)) return false;
    refreshDock();
    dataCenter.setDockApps(config.getDockApps());
    Toast.makeText(this, R.string.dock_added, Toast.LENGTH_SHORT).show();
    return true;
  }

  public boolean removeAppFromDock(String componentKey) {
    if (!config.removeDockApp(componentKey)) return false;
    refreshDock();
    dataCenter.setDockApps(config.getDockApps());
    Toast.makeText(this, R.string.dock_removed, Toast.LENGTH_SHORT).show();
    return true;
  }

  @Override
  public boolean addToDock(String componentKey, int slot) {
    return addAppToDock(componentKey, slot);
  }

  @Override
  public boolean removeFromDock(String componentKey) {
    return removeAppFromDock(componentKey);
  }

  public List<ResolveInfo> getDockCandidates() {
    return dataCenter != null ? dataCenter.getAllAppsForDock() : new ArrayList<ResolveInfo>();
  }

  public List<String> getDockSlots() {
    return config.getDockApps();
  }

  private void applyGridSize(int colNum, int rowNum) {
    if (launcherView != null) {
      launcherView.configure(colNum, 2, config.isHideDivider());
    }
    if (dataCenter != null) {
      dataCenter.setGridSize(colNum, 2);
      dataCenter.setLargeClockEnabled(true);
    }
    if (dataCenter != null) {
      applyCurrentPageLayout();
    }
    if (calendar != null) {
      updateTimeShow();
    }
  }

  private void applyCurrentPageLayout() {
    int cols = config.getColNum();
    // 新版桌面图标区固定两行，多余应用进入后续页面
    launcherView.configure(cols, 2, config.isHideDivider());
    launcherView.setBottomAlignIcons(false);
    launcherView.setVisibility(View.VISIBLE);

    ViewGroup.LayoutParams clockParams = largeClockContainer.getLayoutParams();
    clockParams.height = 0;
    clockParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
    ((LinearLayout.LayoutParams) clockParams).weight = 3f;
    largeClockContainer.setVisibility(View.VISIBLE);

    ViewGroup.LayoutParams gridParams = launcherView.getLayoutParams();
    ((LinearLayout.LayoutParams) gridParams).weight = 4f;
    launcherView.setLayoutParams(gridParams);

    updatePageIndicator();
    updateTopDividerWidth();
    updateFooterFocus();
  }

  /** 用实心/空心圆点显示桌面页数，替代原来的页数文字 */
  private void updatePageIndicator() {
    if (pageIndicator == null || dataCenter == null) return;
    int count = dataCenter.getPageCount();
    int current = dataCenter.getPageIndex();
    if (count < 1) count = 1;
    if (pageIndicator.getChildCount() != count) {
      pageIndicator.removeAllViews();
      for (int i = 0; i < count; i++) {
        TextView dot = new TextView(this);
        dot.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22f);
        dot.setTextColor(getResources().getColor(R.color.textColor));
        dot.setPadding(9, 0, 9, 0);
        pageIndicator.addView(dot);
      }
    }
    for (int i = 0; i < count; i++) {
      TextView dot = (TextView) pageIndicator.getChildAt(i);
      dot.setText(i == current ? "●" : "○");
    }
  }

  /** 上分隔线宽度跟随年月日星期文字宽度 */
  private void updateTopDividerWidth() {
    if (topDivider == null || largeClockDate == null) return;
    largeClockDate.post(new Runnable() {
      @Override
      public void run() {
        CharSequence text = largeClockDate.getText();
        if (text == null || text.length() == 0) return;
        int width = (int) Math.ceil(largeClockDate.getPaint().measureText(text.toString()));
        if (width <= 0) return;
        ViewGroup.LayoutParams params = topDivider.getLayoutParams();
        if (params.width != width) {
          params.width = width;
          topDivider.setLayoutParams(params);
        }
      }
    });
  }

  private void updateLargeClockLayout() {
    if (launcherView == null || config == null || dataCenter == null) return;
    applyCurrentPageLayout();
    updateLargeClockTextSize();
  }

  private void updateLargeClockTextSize() {
    if (largeClockContainer == null || largeClockTime == null || largeClockDate == null) return;
    largeClockContainer.post(new Runnable() {
      @Override
      public void run() {
        int timeHeight = largeClockTime.getHeight();
        int timeWidth = largeClockTime.getWidth();
        CharSequence timeText = largeClockTime.getText();
        if (timeHeight <= 0 || timeWidth <= 0 || timeText == null || timeText.length() == 0) return;

        float heightLimitedSize = timeHeight * 0.78f;
        float currentSize = largeClockTime.getTextSize();
        float measuredWidth = largeClockTime.getPaint().measureText(timeText + ":", 0, timeText.length() + 1);
        float widthLimitedSize = measuredWidth > 0
            ? currentSize * timeWidth / measuredWidth : heightLimitedSize;
        float timeTextSizePx = Math.min(heightLimitedSize, widthLimitedSize);
        largeClockTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, timeTextSizePx);
        largeClockDate.setTextSize(TypedValue.COMPLEX_UNIT_PX, timeTextSizePx * 0.18f);
      }
    });
  }

  private void applyScreenOrientation(int mode) {
    setRequestedOrientation(getRequestedOrientationForMode(mode));
  }

  private int getRequestedOrientationForMode(int mode) {
    switch (mode) {
      case 1:
        return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
      case 2:
        return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
      case 3:
        return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
      case 4:
        return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
      case 0:
      default:
        return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    }
  }

  // =========================================================================
  // AppItemBinder.Callback 实现
  // =========================================================================

  @Override
  public void onItemClick(ResolveInfo info) {
    String pkgName = info.activityInfo.packageName;

    if (reorderMode) {
      handleReorderClick(info);
      return;
    }

    if (AppDataCenter.SETTINGS_PACKAGE_NAME.equals(pkgName)) {
      clearKeyboardFocus();
      openSettingsFragment();
    } else if (AppDataCenter.LOCK_PACKAGE_NAME.equals(pkgName)) {
      lockScreen();
    } else if (AppDataCenter.WIFI_PACKAGE_NAME.equals(pkgName)) {
      WifiControl.onClickWifiItem();
    } else if (AppDataCenter.CLEAR_PACKAGE_NAME.equals(pkgName)) {
      cleanMemory();
    } else if (AppDataCenter.REFRESH_PACKAGE_NAME.equals(pkgName)) {
      cn.modificator.launcher.model.EpdRefresh.fullRefresh(this);
    } else {
      ComponentName comp = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
      Intent intent = new Intent(Intent.ACTION_MAIN);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
      intent.addCategory(Intent.CATEGORY_LAUNCHER);
      intent.setComponent(comp);
      startActivity(intent);
    }
  }

  @Override
  public void onItemLongClick(View anchor, ResolveInfo info) {
    if (reorderMode) {
      cancelReorder();
      return;
    }
    String packageName = info.activityInfo.packageName;

    if (AppDataCenter.SETTINGS_PACKAGE_NAME.equals(packageName)) {
      showVirtualItemMenu(info, getString(R.string.dialog_open_settings));
    } else if (AppDataCenter.LOCK_PACKAGE_NAME.equals(packageName)) {
      showPowerMenu(info);
    } else if (AppDataCenter.WIFI_PACKAGE_NAME.equals(packageName)) {
      showVirtualItemMenu(info, getString(R.string.dialog_wifi_settings));
    } else if (AppDataCenter.CLEAR_PACKAGE_NAME.equals(packageName)
        || AppDataCenter.REFRESH_PACKAGE_NAME.equals(packageName)) {
      startReorder(info);
    } else {
      showAppInfoDialog(info, packageName);
    }
  }

  /** 虚拟项长按菜单：保留原有入口，同时提供“调整位置” */
  private void showVirtualItemMenu(final ResolveInfo info, final String primaryAction) {
    CharSequence[] actions = {primaryAction, getString(R.string.dialog_reorder)};
    new AlertDialog.Builder(this)
        .setTitle(AppDataCenter.getSafeLabel(info, this, getPackageManager()))
        .setItems(actions, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (which == 0) {
              String pkg = info.activityInfo.packageName;
              if (AppDataCenter.SETTINGS_PACKAGE_NAME.equals(pkg)) {
                openSettingsFragment();
              } else if (AppDataCenter.WIFI_PACKAGE_NAME.equals(pkg)) {
                WifiControl.onLongClickWifiItem();
              }
            } else {
              startReorder(info);
            }
          }
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  @Override
  public void onItemDeleteClick(ResolveInfo info) {
    Intent deleteIntent = new Intent(Intent.ACTION_DELETE,
        Uri.parse("package:" + info.activityInfo.packageName));
    startActivity(deleteIntent);
  }

  @Override
  public void onItemHideToggle(String packageName, boolean hidden) {
    // 管理模式下的隐藏切换仅更新 UI 状态，"完成" 按钮处理持久化
  }

  // =========================================================================
  // EInkLauncherView.OnPageChangeListener 实现
  // =========================================================================

  @Override
  public void onPageNext() {
    showNextPageAndHideSelection();
    // showNextPageAndHideSelection 内部已调用 applyCurrentPageLayout 完成布局与重新绑定，
    // 这里只需刷新大时钟字号，避免再次全量 configure/bindAll 造成翻页卡顿。
    updateLargeClockTextSize();
    updateTimeShow();
  }

  @Override
  public void onPagePrev() {
    showLastPageAndHideSelection();
    updateLargeClockTextSize();
    updateTimeShow();
  }

  private void showPowerMenu(final ResolveInfo info) {
    if (!isSystemApp) {
      startReorder(info);
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle(R.string.power_title)
        .setItems(R.array.power_menu, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (which == 0) {
              Intent intent = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
              intent.putExtra("android.intent.extra.KEY_CONFIRM", false);
              intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              startActivity(intent);
            } else if (which == 1) {
              PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
              pm.reboot("重启");
            } else {
              startReorder(info);
            }
          }
        })
        .setPositiveButton(R.string.dialog_cancel, null)
        .show();
  }

  private void showAppInfoDialog(final ResolveInfo info, final String packageName) {
    final String key = AppDataCenter.componentKey(info);
    final boolean inDock = config.getDockApps().contains(key);
    CharSequence[] actions = {
        getString(R.string.dialog_reorder),
        getString(R.string.dialog_app_info),
        inDock ? getString(R.string.dialog_remove_from_dock) : getString(R.string.dialog_add_to_dock),
        getString(R.string.dialog_copy_package_name, packageName),
        getString(R.string.dialog_hide),
        getString(R.string.dialog_uninstall)
    };
    new AlertDialog.Builder(this)
        .setIcon(iconCache.getIcon(packageName, info, getPackageManager()))
        .setTitle(iconCache.getLabel(packageName, info, getPackageManager()))
        .setItems(actions, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (which == 0) {
              startReorder(info);
            } else if (which == 1) {
              Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                  Uri.parse("package:" + packageName));
              startActivity(intent);
            } else if (which == 2) {
              if (inDock) {
                removeAppFromDock(key);
              } else {
                showDockSlotChooser(info);
              }
            } else if (which == 3) {
              ClipboardManager clipboard = (ClipboardManager)
                  getSystemService(Context.CLIPBOARD_SERVICE);
              if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("packageName", packageName));
                Toast.makeText(Launcher.this, R.string.package_name_copied,
                    Toast.LENGTH_SHORT).show();
              }
            } else if (which == 4) {
              Set<String> hideApps = binder.getHideAppPkg();
              if (!hideApps.add(packageName)) {
                hideApps.remove(packageName);
              }
              config.setHideApps(new HashSet<>(hideApps));
              dataCenter.setHideApps(config.getHideApps());
            } else if (which == 5) {
              Intent deleteIntent = new Intent(Intent.ACTION_DELETE,
                  Uri.parse("package:" + packageName));
              startActivity(deleteIntent);
            }
          }
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  // =========================================================================
  // 图标排序（长按 → 调整位置 → 点击另一个图标交换）
  // =========================================================================

  private void startReorder(ResolveInfo info) {
    reorderFirst = info;
    reorderMode = true;
    CharSequence label = AppDataCenter.getSafeLabel(info, this, getPackageManager());
    showStatusHint(getString(R.string.reorder_first, label), true);
  }

  private void handleReorderClick(ResolveInfo info) {
    if (reorderFirst == null) {
      cancelReorder();
      return;
    }
    String keyA = AppDataCenter.componentKey(reorderFirst);
    String keyB = AppDataCenter.componentKey(info);
    if (keyA.equals(keyB)) {
      cancelReorder();
      return;
    }
    CharSequence labelA = AppDataCenter.getSafeLabel(reorderFirst, this, getPackageManager());
    CharSequence labelB = AppDataCenter.getSafeLabel(info, this, getPackageManager());
    boolean swapped = dataCenter.swapApps(keyA, keyB);
    if (swapped) {
      config.setAppOrder(dataCenter.getComponentKeys());
      showStatusHint(getString(R.string.reorder_done, labelA, labelB), false);
    } else {
      showStatusHint(getString(R.string.reorder_cancelled), false);
    }
    reorderFirst = null;
    reorderMode = false;
  }

  private void cancelReorder() {
    reorderFirst = null;
    reorderMode = false;
    showStatusHint(getString(R.string.reorder_cancelled), false);
  }

  private void showStatusHint(CharSequence text, boolean persistent) {
    if (statusHint == null) return;
    statusHintHandler.removeCallbacks(hideStatusHintRunnable);
    statusHint.setText(text);
    statusHint.setVisibility(View.VISIBLE);
    if (!persistent) {
      statusHintHandler.postDelayed(hideStatusHintRunnable, 2500);
    }
  }

  /** 长按桌面 App 后直接选择 Dock 槽位，无需进入设置页 */
  private void showDockSlotChooser(final ResolveInfo info) {
    final String key = AppDataCenter.componentKey(info);
    List<String> slots = config.getDockApps();
    Map<String, String> names = new HashMap<String, String>();
    for (ResolveInfo candidate : dataCenter.getAllAppsForDock()) {
      CharSequence label = AppDataCenter.getSafeLabel(candidate, this, getPackageManager());
      names.put(AppDataCenter.componentKey(candidate),
          label != null && label.length() > 0 ? label.toString() : "");
    }
    String[] items = new String[4];
    for (int i = 0; i < 4; i++) {
      String occupied = i < slots.size() ? slots.get(i) : "";
      if (occupied == null || occupied.length() == 0) {
        items[i] = (i + 1) + ". " + getString(R.string.dock_empty_slot);
      } else {
        String name = names.get(occupied);
        items[i] = (i + 1) + ". " + (name != null && name.length() > 0 ? name : occupied);
      }
    }
    CharSequence label = AppDataCenter.getSafeLabel(info, this, getPackageManager());
    new AlertDialog.Builder(this)
        .setTitle(getString(R.string.dock_add_to) + ": " + (label != null ? label : key))
        .setItems(items, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (!addAppToDock(key, which)) {
              Toast.makeText(Launcher.this, R.string.dock_slot_full, Toast.LENGTH_SHORT).show();
            }
          }
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  // =========================================================================
  // 时间显示
  // =========================================================================

  private void updateTimeShow() {
    if (textClock == null || calendar == null) return;

    boolean is24Hour = DateFormat.is24HourFormat(this);
    boolean showSeconds = config != null && config.isClockShowSeconds();
    calendar.setTimeInMillis(System.currentTimeMillis());
    Locale locale = Locale.getDefault();

    String dateText = new SimpleDateFormat("yyyy-MM-dd", locale).format(calendar.getTime());
    String shortDateText = new SimpleDateFormat("MM-dd", locale).format(calendar.getTime());
    StringBuilder timeFormat = new StringBuilder(is24Hour ? "H:mm" : "h:mm");
    if (showSeconds) {
      timeFormat.append(":ss");
    }
    String plainTimeText = new SimpleDateFormat(
        timeFormat.toString(), locale).format(calendar.getTime());
    String timeText = plainTimeText;
    if (!is24Hour && isChina) {
      timeText = Utils.getAMPMCNString(
          calendar.get(Calendar.HOUR), calendar.get(Calendar.AM_PM)) + timeText;
    }
    if (!is24Hour && !isChina) {
      timeText += new SimpleDateFormat(" a", locale).format(calendar.getTime());
    }
    String weekdayText = new SimpleDateFormat("EEEE", locale).format(calendar.getTime());

    largeClockContainer.setVisibility(View.VISIBLE);
    largeClockTime.setText(plainTimeText);
    largeClockDate.setText(new SimpleDateFormat("yyyy年M月d日 E", locale).format(calendar.getTime()));
    updateLargeClockTextSize();
    updateTopDividerWidth();
    updateFooterLayout(dateText, shortDateText, plainTimeText, timeText, weekdayText);
    textClock.setVisibility(View.GONE);
  }

  private void updateFooterLayout(String dateText, String shortDateText,
                                  String plainTimeText, String timeText,
                                  String weekdayText) {
    textClock.setText(plainTimeText);
    textClock.setVisibility(View.VISIBLE);
    int availableWidth = footerContent.getWidth()
        - footerContent.getPaddingLeft() - footerContent.getPaddingRight();
    if (availableWidth <= 0) return;

    int plainTimeWidth = measureClockWidth(plainTimeText);

    int normalControlsWidth = settingIconNormalWidth + batteryIconNormalWidth
        + batteryContainerNormalLeftMargin + batteryContainerNormalRightMargin
        + pageStatusNormalWidth;
    float controlsScale = normalControlsWidth == 0 ? 1f
        : Math.min(1f, Math.max(0f,
            (availableWidth - plainTimeWidth) / (float) normalControlsWidth));
    int controlsWidth = updateFooterControls(controlsScale);

    String dateAndTime = dateText + " " + timeText;
    String shortDateAndTime = shortDateText + " " + plainTimeText;
    String fullClock = dateAndTime + " " + weekdayText;
    int dateAndTimeWidth = measureClockWidth(dateAndTime);
    int shortDateAndTimeWidth = measureClockWidth(shortDateAndTime);
    int fullClockWidth = measureClockWidth(fullClock);
    boolean showDate = controlsWidth + dateAndTimeWidth <= availableWidth;
    boolean showShortDate = !showDate
        && controlsWidth + shortDateAndTimeWidth <= availableWidth;
    boolean showWeekday = showDate
        && controlsWidth + fullClockWidth <= availableWidth;

    String visibleClockText = plainTimeText;
    if (showWeekday) {
      visibleClockText = fullClock;
    } else if (showDate) {
      visibleClockText = dateAndTime;
    } else if (showShortDate) {
      visibleClockText = shortDateAndTime;
    }

    int batteryIconWidth = batteryProgress.getLayoutParams().width;
    int batteryStatusWidth = (int) Math.ceil(
        batteryStatus.getPaint().measureText(batteryStatus.getText().toString()));
    int batteryStatusExtraWidth = Math.max(0, batteryStatusWidth - batteryIconWidth);
    boolean showBatteryStatus = batteryStatusRequestedVisible
        && controlsScale >= 1f
        && !useCompactFooterHeight()
        && showWeekday
        && controlsWidth + fullClockWidth + batteryStatusExtraWidth <= availableWidth;

    textClock.setText(visibleClockText);
    batteryContainer.setVisibility(View.VISIBLE);
    batteryStatus.setVisibility(showBatteryStatus ? View.VISIBLE : View.GONE);
  }

  private int measureClockWidth(String text) {
    return (int) Math.ceil(textClock.getPaint().measureText(text))
        + textClock.getCompoundPaddingLeft() + textClock.getCompoundPaddingRight();
  }

  private int updateFooterControls(float scale) {
    boolean compactHeight = useCompactFooterHeight();
    int footerHeight = compactHeight
        ? footerNormalHeight * 2 / 3
        : footerNormalHeight;
    int settingWidth = scaledSize(settingIconNormalWidth, scale);
    int batteryWidth = scaledSize(batteryIconNormalWidth, scale);
    int batteryHeight = scaledSize(batteryIconNormalHeight, scale);
    int batteryLeftMargin = scaledSize(batteryContainerNormalLeftMargin, scale);
    int batteryRightMargin = scaledSize(batteryContainerNormalRightMargin, scale);
    int pageWidth = scaledSize(pageStatusNormalWidth, scale);

    ViewGroup.LayoutParams params = settingIcon.getLayoutParams();
    int settingHeight = compactHeight ? footerHeight : settingIconNormalHeight;
    if (params.width != settingWidth || params.height != settingHeight) {
      params.width = settingWidth;
      params.height = settingHeight;
      settingIcon.setLayoutParams(params);
    }
    int horizontalPadding = scaledSize(settingIconNormalHorizontalPadding, scale);
    int verticalPadding = compactHeight
        ? settingIconNormalVerticalPadding / 3
        : settingIconNormalVerticalPadding;
    if (settingIcon.getPaddingLeft() != horizontalPadding
        || settingIcon.getPaddingRight() != horizontalPadding
        || settingIcon.getPaddingTop() != verticalPadding
        || settingIcon.getPaddingBottom() != verticalPadding) {
      settingIcon.setPadding(horizontalPadding, verticalPadding,
          horizontalPadding, verticalPadding);
    }

    params = batteryProgress.getLayoutParams();
    if (params.width != batteryWidth || params.height != batteryHeight) {
      params.width = batteryWidth;
      params.height = batteryHeight;
      batteryProgress.setLayoutParams(params);
    }
    ViewGroup.MarginLayoutParams batteryParams =
        (ViewGroup.MarginLayoutParams) batteryContainer.getLayoutParams();
    if (batteryParams.leftMargin != batteryLeftMargin
        || batteryParams.rightMargin != batteryRightMargin) {
      batteryParams.leftMargin = batteryLeftMargin;
      batteryParams.rightMargin = batteryRightMargin;
      batteryContainer.setLayoutParams(batteryParams);
    }

    params = pageStatus.getLayoutParams();
    if (params.width != pageWidth) {
      params.width = pageWidth;
      pageStatus.setLayoutParams(params);
    }
    float pageTextSize = Math.max(1f, pageStatusNormalTextSize * scale);
    if (pageStatus.getTextSize() != pageTextSize) {
      pageStatus.setTextSize(TypedValue.COMPLEX_UNIT_PX, pageTextSize);
    }

    setViewHeight(footerContainer, footerHeight);
    setViewHeight(footerContent, footerHeight);
    return settingWidth + batteryWidth + batteryLeftMargin + batteryRightMargin + pageWidth;
  }

  private int scaledSize(int normalSize, float scale) {
    if (normalSize <= 0) return 0;
    return Math.max(1, (int) Math.floor(normalSize * scale));
  }

  private boolean useCompactFooterHeight() {
    return launcherView != null && launcherView.getRowNum() < 3;
  }

  private void setViewHeight(View view, int height) {
    ViewGroup.LayoutParams params = view.getLayoutParams();
    if (params.height != height) {
      params.height = height;
      view.setLayoutParams(params);
    }
  }

  // =========================================================================
  // 电池信息
  // =========================================================================

  private void handleBatteryChanged(Intent intent) {
    int rawLevel = intent.getIntExtra("level", -1);
    int scale = intent.getIntExtra("scale", -1);
    int status = intent.getIntExtra("status", -1);
    int health = intent.getIntExtra("health", -1);

    int level = (rawLevel >= 0 && scale > 0) ? (rawLevel * 100) / scale : -1;
    batteryProgress.setProgress(level);
    topBatteryProgress.setProgress(level);
    batteryStatusRequestedVisible = true;

    if (BatteryManager.BATTERY_HEALTH_OVERHEAT == health) {
      batteryStatus.setText(R.string.battery_heat);
      updateTimeShow();
      return;
    }

    switch (status) {
      case BatteryManager.BATTERY_STATUS_UNKNOWN:
        batteryStatus.setText(R.string.battery_unknown);
        break;
      case BatteryManager.BATTERY_STATUS_CHARGING:
        batteryStatus.setText(R.string.battery_charging);
        break;
      case BatteryManager.BATTERY_STATUS_DISCHARGING:
      case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
        if (level < 15) {
          batteryStatus.setText(R.string.battery_low);
        } else {
          batteryStatusRequestedVisible = false;
        }
        break;
      case BatteryManager.BATTERY_STATUS_FULL:
        batteryStatus.setText(R.string.battery_full);
        break;
      default:
        batteryStatus.setText(R.string.battery_wtf);
        break;
    }
    // 右上角电量只显示 BatteryView 圆圈内的百分比，不再额外显示一行文字
    updateTimeShow();
  }

  // =========================================================================
  // 广播注册/注销
  // =========================================================================

  /** 注册生命周期不变的静态广播 */
  private void registerStaticReceivers() {
    // 应用安装/卸载广播
    IntentFilter appChangeFilter = new IntentFilter();
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
    appChangeFilter.addDataScheme("package");
    registerCompatReceiver(appChangeReceiver, appChangeFilter);
  }

  /** 注册跟随 onResume/onPause 的动态广播 */
  private void registerDynamicReceivers() {
    if (!batteryRegistered) {
      registerCompatReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
      batteryRegistered = true;
    }
    if (!settingsChangedRegistered) {
      IntentFilter settingsFilter = new IntentFilter(ACTION_SETTINGS_CHANGED);
      registerCompatReceiver(settingsChangedReceiver, settingsFilter);
      settingsChangedRegistered = true;
    }
    updateClockRefreshMode();
    if (!usbRegistered) {
      registerUsbReceiver();
    }
  }

  private void unregisterDynamicReceivers() {
    if (batteryRegistered) {
      unregisterReceiver(batteryReceiver);
      batteryRegistered = false;
    }
    if (settingsChangedRegistered) {
      unregisterReceiver(settingsChangedReceiver);
      settingsChangedRegistered = false;
    }
    if (timeRegistered) {
      unregisterReceiver(timeReceiver);
      timeRegistered = false;
    }
    stopClockTicker();
    if (usbRegistered) {
      unregisterReceiver(usbReceiver);
      usbRegistered = false;
    }
  }

  private void registerUsbReceiver() {
    IntentFilter usbFilter = new IntentFilter();
    usbFilter.addAction(Intent.ACTION_UMS_DISCONNECTED);
    usbFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
    usbFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
    usbFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
    usbFilter.addDataScheme("file");
    registerCompatReceiver(usbReceiver, usbFilter);
    usbRegistered = true;
  }

  private void registerCompatReceiver(BroadcastReceiver receiver, IntentFilter filter) {
    Utils.registerReceiverCompat(this, receiver, filter);
  }

  private void updateClockRefreshMode() {
    updateTimeShow();
    if (config != null && config.isClockShowSeconds()) {
      if (timeRegistered) {
        unregisterReceiver(timeReceiver);
        timeRegistered = false;
      }
      startClockTicker();
    } else {
      stopClockTicker();
      if (!timeRegistered) {
        registerCompatReceiver(timeReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
        timeRegistered = true;
      }
    }
  }

  private void startClockTicker() {
    if (clockTickerRunning) return;
    clockTickerRunning = true;
    clockHandler.removeCallbacks(clockTicker);
    clockHandler.postDelayed(clockTicker, getNextClockTickDelay());
  }

  private void stopClockTicker() {
    clockTickerRunning = false;
    clockHandler.removeCallbacks(clockTicker);
  }

  private long getNextClockTickDelay() {
    return 1000 - System.currentTimeMillis() % 1000;
  }

  // =========================================================================
  // 按键处理
  // =========================================================================

  private boolean handleLauncherKeyDown(int keyCode, KeyEvent event) {
    boolean hasFragment = isSettingsFragmentShown()
        || getSupportFragmentManager().getBackStackEntryCount() > 0;
    if (hasFragment) {
      if (keyCode == KeyEvent.KEYCODE_BACK) {
        onBackPressed();
        return true;
      }
      return false;
    }

    if (keyCode == KeyEvent.KEYCODE_BACK) {
      if (finishManageModeIfNeeded()) {
        return true;
      }
      showFirstPageAndKeepFocusState();
      return true;
    } else if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
      showLastPageAndSelectFirst();
      return true;
    } else if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
      showNextPageAndSelectFirst();
      return true;
    } else if (isConfirmKey(keyCode)) {
      if (focusArea == FOCUS_NONE) {
        confirmLongPressed = false;
        return true;
      }
      if (event.getRepeatCount() > 0) {
        if (!confirmLongPressed) {
          confirmLongPressed = true;
          performFocusedLongClick();
        }
        return true;
      }
      event.startTracking();
      return true;
    } else if (isDirectionKey(keyCode)) {
      moveSelectionByKey(keyCode);
      return true;
    }
    return false;
  }

  private boolean handleLauncherKeyUp(int keyCode, KeyEvent event) {
    if (isSettingsFragmentShown()
        || getSupportFragmentManager().getBackStackEntryCount() != 0
        || !isLauncherNavigationKey(keyCode)) {
      return false;
    }
    if (isConfirmKey(keyCode)) {
      if (focusArea != FOCUS_NONE && !confirmLongPressed && !event.isCanceled()) {
        performFocusedClick();
      }
      confirmLongPressed = false;
      return true;
    }
    return true;
  }

  private boolean finishManageModeIfNeeded() {
    if (!binder.isDelete()) {
      return false;
    }
    finishManageMode();
    return true;
  }

  private void finishManageMode() {
    Set<String> hideApps = new HashSet<>(binder.getHideAppPkg());
    config.setHideApps(hideApps);
    dataCenter.setHideApps(config.getHideApps());
    binder.setDelete(false);
    findViewById(R.id.deleteFinish).setVisibility(View.GONE);
  }

  private void clearHandledLauncherKey() {
    handledLauncherKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    handledLauncherKeyDownTime = 0L;
  }

  private boolean isLauncherNavigationKey(int keyCode) {
    return keyCode == KeyEvent.KEYCODE_BACK
        || keyCode == KeyEvent.KEYCODE_PAGE_UP
        || keyCode == KeyEvent.KEYCODE_PAGE_DOWN
        || isConfirmKey(keyCode)
        || isDirectionKey(keyCode);
  }

  private boolean isConfirmKey(int keyCode) {
    return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
        || keyCode == KeyEvent.KEYCODE_ENTER
        || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
  }

  private boolean isDirectionKey(int keyCode) {
    return keyCode == KeyEvent.KEYCODE_DPAD_LEFT
        || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
        || keyCode == KeyEvent.KEYCODE_DPAD_UP
        || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
  }

  private void moveSelectionByKey(int keyCode) {
    if (focusArea == FOCUS_NONE) {
      focusGrid(0);
      return;
    }

    if (focusArea == FOCUS_LARGE_CLOCK) {
      if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) focusGrid(0);
      return;
    }
    if (focusArea == FOCUS_CLOCK || focusArea == FOCUS_BATTERY || focusArea == FOCUS_SETTING) {
      moveFooterFocus(keyCode);
      return;
    }

    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && isOnTopRow()) {
      if (config.isShowLargeClock() && dataCenter.getPageIndex() == 0) {
        focusLargeClock();
      } else {
        expandNotifications();
      }
      return;
    }

    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && isOnBottomRow()) {
      lastGridIndex = launcherView.getSelectedIndex();
      if (config.isShowLargeClock()) focusFooter(FOCUS_SETTING);
      else focusFooter(FOCUS_CLOCK);
      return;
    }

    if (launcherView.moveSelection(keyCode)) return;

    int selectedIndex = launcherView.getSelectedIndex();
    if (selectedIndex < 0) return;

    int colNum = launcherView.getColNum();
    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && selectedIndex % colNum == 0) {
      if (dataCenter.showLastPage()) {
        applyCurrentPageLayout();
        launcherView.setSelectedIndex(launcherView.getCrossPageTargetIndex(selectedIndex, false));
        focusGrid(launcherView.getSelectedIndex());
      }
    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && selectedIndex % colNum == colNum - 1) {
      if (dataCenter.showNextPage()) {
        applyCurrentPageLayout();
        launcherView.setSelectedIndex(launcherView.getCrossPageTargetIndex(selectedIndex, true));
        focusGrid(launcherView.getSelectedIndex());
      }
    }
  }

  private void moveFooterFocus(int keyCode) {
    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
      focusGrid(lastGridIndex);
    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && focusArea == FOCUS_CLOCK) {
      focusFooter(FOCUS_BATTERY);
    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && focusArea == FOCUS_BATTERY) {
      focusFooter(config.isShowLargeClock() ? FOCUS_SETTING : FOCUS_CLOCK);
    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && focusArea == FOCUS_BATTERY) {
      focusFooter(FOCUS_SETTING);
    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && focusArea == FOCUS_SETTING) {
      focusFooter(config.isShowLargeClock() ? FOCUS_SETTING : FOCUS_BATTERY);
    }
  }

  private boolean isOnTopRow() {
    int selectedIndex = launcherView.getSelectedIndex();
    return selectedIndex >= 0 && selectedIndex < launcherView.getColNum();
  }

  private boolean isOnBottomRow() {
    int selectedIndex = launcherView.getSelectedIndex();
    if (selectedIndex < 0) return false;
    int colNum = launcherView.getColNum();
    return selectedIndex + colNum >= launcherView.getDisplayedItemCount();
  }

  private void performFocusedClick() {
    if (focusArea == FOCUS_NONE) {
      return;
    }
    if (focusArea == FOCUS_GRID) {
      launcherView.performSelectedItemClick();
    } else if (focusArea == FOCUS_CLOCK) {
      textClock.performClick();
    } else if (focusArea == FOCUS_BATTERY) {
      batteryProgress.performClick();
    } else if (focusArea == FOCUS_SETTING) {
      settingIcon.performClick();
    } else if (focusArea == FOCUS_LARGE_CLOCK) {
      largeClockTime.performClick();
    }
  }

  private boolean performFocusedLongClick() {
    if (focusArea == FOCUS_NONE) {
      return false;
    }
    if (focusArea == FOCUS_GRID) {
      return launcherView.performSelectedItemLongClick();
    }
    return false;
  }

  private void focusGrid(int index) {
    focusArea = FOCUS_GRID;
    launcherView.setSelectedIndex(index);
    launcherView.showSelection();
    updateFooterFocus();
  }

  private void focusFooter(int area) {
    focusArea = area;
    launcherView.hideSelection();
    updateFooterFocus();
  }

  private void focusLargeClock() {
    focusArea = FOCUS_LARGE_CLOCK;
    launcherView.hideSelection();
    largeClockTime.setSelected(true);
    largeClockDate.setSelected(false);
  }

  private void clearKeyboardFocus() {
    focusArea = FOCUS_NONE;
    confirmLongPressed = false;
    launcherView.hideSelection();
    updateFooterFocus();
  }

  private void updateFooterFocus() {
    if (textClock != null) {
      textClock.setSelected(focusArea == FOCUS_CLOCK);
    }
    if (largeClockTime != null) {
      largeClockTime.setSelected(focusArea == FOCUS_LARGE_CLOCK);
      largeClockDate.setSelected(focusArea == FOCUS_LARGE_CLOCK);
    }
    if (batteryProgress != null) {
      batteryProgress.setSelected(focusArea == FOCUS_BATTERY);
    }
    if (settingIcon != null) {
      settingIcon.setSelected(focusArea == FOCUS_SETTING);
    }
  }

  /** 显示设置页面（Fragment 覆盖层） */
  private void openSettingsFragment() {
    View container = findViewById(R.id.settingContainer);
    if (container == null) {
      return;
    }
    container.setVisibility(View.VISIBLE);
    androidx.fragment.app.FragmentManager fm = getSupportFragmentManager();
    if (fm.findFragmentByTag(TAG_SETTINGS_FRAGMENT) == null) {
      fm.beginTransaction()
          .add(R.id.settingContainer, new SettingFragment(), TAG_SETTINGS_FRAGMENT)
          .commitNow();
    }
    focusArea = FOCUS_SETTING;
    updateFooterFocus();
  }

  /** 关闭设置页面 */
  private void closeSettingsFragment() {
    androidx.fragment.app.FragmentManager fm = getSupportFragmentManager();
    androidx.fragment.app.Fragment fragment = fm.findFragmentByTag(TAG_SETTINGS_FRAGMENT);
    if (fragment != null) {
      fm.beginTransaction().remove(fragment).commitNow();
    }
    View container = findViewById(R.id.settingContainer);
    if (container != null) {
      container.setVisibility(View.GONE);
    }
    clearKeyboardFocus();
  }

  /** 设置页面是否正在显示（基于容器可见性，避免异步 commit 时序问题） */
  private boolean isSettingsFragmentShown() {
    View container = findViewById(R.id.settingContainer);
    return container != null && container.getVisibility() == View.VISIBLE;
  }

  private void openClockActivity() {
    startActivity(new Intent(this, ClockActivity.class));
  }

  private void openBatterySettings() {
    Intent intent = new Intent();
    intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$PowerUsageSummaryActivity"));
    try {
      startActivity(intent);
    } catch (Exception e) {
      Toast.makeText(Launcher.this, "无法打开电池设置", Toast.LENGTH_SHORT).show();
      e.printStackTrace();
    }
  }

  private void expandNotifications() {
    try {
      Object service = getSystemService("statusbar");
      if (service == null) return;
      String methodName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
          ? "expandNotificationsPanel"
          : "expand";
      Method method = service.getClass().getMethod(methodName);
      method.invoke(service);
    } catch (Exception ignored) {
    }
  }

  private void showNextPageAndSelectFirst() {
    if (dataCenter.showNextPage()) {
      applyCurrentPageLayout();
      focusGrid(0);
    }
  }

  private void showLastPageAndSelectFirst() {
    if (dataCenter.showLastPage()) {
      applyCurrentPageLayout();
      focusGrid(0);
    }
  }

  private void showFirstPageAndKeepFocusState() {
    boolean hadKeyboardFocus = focusArea != FOCUS_NONE;
    dataCenter.showFirstPage();
    if (hadKeyboardFocus) {
      focusGrid(0);
    } else {
      launcherView.selectFirstAvailable();
      clearKeyboardFocus();
    }
  }

  private void showNextPageAndHideSelection() {
    if (dataCenter.showNextPage()) {
      applyCurrentPageLayout();
      launcherView.selectFirstAvailable();
      clearKeyboardFocus();
    }
  }

  private void showLastPageAndHideSelection() {
    if (dataCenter.showLastPage()) {
      applyCurrentPageLayout();
      launcherView.selectFirstAvailable();
      clearKeyboardFocus();
    }
  }

  @Override
  public void onBackPressed() {
    if (reorderMode) {
      cancelReorder();
      return;
    }
    // 优先关闭设置页面
    if (isSettingsFragmentShown()) {
      closeSettingsFragment();
      return;
    }
    // 保留原有的 Fragment 回退栈处理
    if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
      super.onBackPressed();
      config.setFontSize(config.getFontSize());
    }
  }

  // =========================================================================
  // 锁屏
  // =========================================================================

  /**
   * Launcher 是 singleInstance，直接启动 ADD_DEVICE_ADMIN 会被系统以 NEW_TASK 拒绝。
   * 统一交给透明代理页处理：已激活则直接锁屏，未激活则先激活再锁屏。
   */
  public void lockScreen() {
    try {
      Intent intent = new Intent(this, DeviceAdminActivator.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);
    } catch (Exception e) {
      showStatusHint(getString(R.string.lock_admin_failed), false);
    }
  }

  // =========================================================================
  // 内存清理
  // =========================================================================

  /**
   * 旧实现直接执行 libfillRam.so，在 Android 8.1 上会把前台 Launcher 一起杀掉，
   * 导致桌面重启并弹出主屏幕选择器。改为系统标准 API：只清理后台进程，
   * 显式排除自身，避免自杀；同时用可用内存差值给出结果提示。
   */
  private void cleanMemory() {
    showStatusHint(getString(R.string.clean_start), false);
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          final ActivityManager am =
              (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
          if (am == null) return;
          ActivityManager.MemoryInfo before = new ActivityManager.MemoryInfo();
          am.getMemoryInfo(before);

          Set<String> candidates = new HashSet<String>();
          String self = getPackageName();
          List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
          if (processes != null) {
            for (ActivityManager.RunningAppProcessInfo process : processes) {
              if (process == null || process.processName == null) continue;
              if (process.processName.equals(self)
                  || process.processName.startsWith(self + ":")) continue;
              if (process.importance < ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) {
                continue;
              }
              String pkg = process.processName.split(":")[0];
              if (pkg.length() > 0 && !pkg.equals(self)) candidates.add(pkg);
            }
          }
          for (String pkg : candidates) {
            try {
              am.killBackgroundProcesses(pkg);
            } catch (Throwable ignored) {
              // 单个包清理失败不影响其他应用
            }
          }

          ActivityManager.MemoryInfo after = new ActivityManager.MemoryInfo();
          am.getMemoryInfo(after);
          long freed = Math.max(0L, after.availMem - before.availMem);
          final String freedText = String.format(Locale.getDefault(), "%.1f MB",
              freed / 1024f / 1024f);
          statusHintHandler.post(new Runnable() {
            @Override
            public void run() {
              showStatusHint(getString(R.string.clean_done, freedText), false);
            }
          });
        } catch (Throwable e) {
          Log.e("MemoryClean", "clean failed", e);
          statusHintHandler.post(new Runnable() {
            @Override
            public void run() {
              showStatusHint(getString(R.string.clean_error, e.getMessage()), false);
            }
          });
        }
      }
    }, "memory-clean").start();
  }

  // =========================================================================
  // 状态栏/系统应用判断/通知栏
  // =========================================================================

  public void applyStatusBarVisibility() {
    int flags = WindowManager.LayoutParams.FLAG_FULLSCREEN;
    if (config.isShowStatusBar()) {
      getWindow().clearFlags(flags);
    } else {
      getWindow().setFlags(flags, flags);
    }
  }

  public boolean isUserApp(PackageInfo pInfo) {
    return (pInfo.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0;
  }

  private void checkLaunchHomeNotification() {
    if (!TextUtils.equals(Build.DEVICE, "virgo-perf1")) return;
    Intent service = new Intent(this, HomeEntranceService.class);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      startForegroundService(service);
    } else {
      startService(service);
    }
  }
}
