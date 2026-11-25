package cn.modificator.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import androidx.fragment.app.Fragment;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import cn.modificator.launcher.model.AppDataCenter;
import cn.modificator.launcher.model.AppSortComparator;
import cn.modificator.launcher.model.FontManager;
import cn.modificator.launcher.model.WifiControl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 设置页面 Fragment。
 */
public class SettingFragment extends Fragment implements View.OnClickListener {

  private static final String TAG = "SettingFragment";
  private static final int REQUEST_PICK_WALLPAPER = 10003;
  private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
  private static final ExecutorService WALLPAPER_EXECUTOR =
      Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
          return new Thread(runnable, "wallpaper-setter");
        }
      });

  /** 设置变更回调接口：宿主 Activity 应实现此接口以响应设置变更。 */
  public interface OnSettingChangeListener {
    void onRowNumChanged(int rowNum);
    void onColNumChanged(int colNum);
    void onFontSizeChanged(float size);
    void onFontChanged(String fontName);
    void onClockFontChanged(String fontName);
    void onAppNameLinesChanged(int lines);
    void onHideDividerChanged(boolean hide);
    void onShowStatusBarChanged(boolean show);
    void onShowCustomIconChanged(boolean show);
    void onClockShowSecondsChanged(boolean show);
    void onShowLargeClockChanged(boolean show);
    void onShowAppNameChanged(boolean show);
    void onBottomAlignIconsChanged(boolean align);
    void onSortModeChanged(int mode);
    void onThemeModeChanged(int mode);
    void onScreenOrientationChanged(int mode);
    void onEnterManageMode();
    List<ResolveInfo> getDockCandidates();
    List<String> getDockSlots();
    boolean addToDock(String componentKey, int slot);
    boolean removeFromDock(String componentKey);
  }

  private OnSettingChangeListener listener;

  private Spinner colNumSpinner;
  private Spinner rowNumSpinner;
  private Spinner appNameLinesSpinner;
  private Spinner sortModeSpinner;
  private Spinner themeModeSpinner;
  private Spinner screenOrientationSpinner;
  private SeekBar fontControl;
  private View rootView;
  private TextView hideDivider;
  private TextView showStatusBar;
  private TextView showCustomIcon;
  private TextView clockShowSeconds;
  private TextView showLargeClock;
  private TextView showAppName;
  private TextView bottomAlignIcons;
  private TextView startAtBoot;
  private Config config;
  private View changeFontSize;
  private View customFont;
  private TextView customFontLabel;
  private View clockFont;
  private TextView clockFontLabel;
  private View deleteApp;
  private View dockManager;
  private View helpAbout;
  private View openDeviceManager;
  private View setWallpaper;
  private View showWifiName;

  @SuppressWarnings("deprecation")
  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    if (context instanceof OnSettingChangeListener) {
      listener = (OnSettingChangeListener) context;
    } else {
      throw new ClassCastException(context.toString() + " must implement OnSettingChangeListener");
    }
  }

  // 保留旧版本兼容性
  @SuppressWarnings("deprecation")
  @Override
  public void onAttach(android.app.Activity activity) {
    super.onAttach(activity);
    if (activity instanceof OnSettingChangeListener) {
      listener = (OnSettingChangeListener) activity;
    } else {
      throw new ClassCastException(activity.toString() + " must implement OnSettingChangeListener");
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.activity_setting, null);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    rootView = getView();
    config = new Config(getActivity());
    initViews();
    initSpinners();
    initFontControl();
  }

  // =========================================================================
  // 初始化
  // =========================================================================

  /**
   * 把原来右侧竖排的设置项平铺为上半屏三列。
   * 只重排视图、不改动任何 ID，原有点击逻辑无需调整。
   */
  private void tileSettingsMenu() {
    LinearLayout menu = (LinearLayout) rootView.findViewById(R.id.menuList);
    if (menu == null) return;
    List<View> items = new ArrayList<View>();
    for (int i = 0; i < menu.getChildCount(); i++) {
      View child = menu.getChildAt(i);
      int id = child.getId();
      if (id == R.id.toBack1) {
        child.setVisibility(View.GONE);
      } else if (id == View.NO_ID && !(child instanceof ViewGroup)) {
        // 原竖排分隔线不再复用，分列后统一重建
        child.setVisibility(View.GONE);
      } else {
        items.add(child);
      }
    }
    if (items.isEmpty()) return;
    menu.removeAllViews();
    menu.setOrientation(LinearLayout.HORIZONTAL);
    final int columns = 3;
    for (int c = 0; c < columns; c++) {
      LinearLayout column = new LinearLayout(getActivity());
      column.setOrientation(LinearLayout.VERTICAL);
      column.setLayoutParams(new LinearLayout.LayoutParams(
          0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      for (int i = c; i < items.size(); i += columns) {
        View item = items.get(i);
        int height = item.getLayoutParams() != null
            ? item.getLayoutParams().height : ViewGroup.LayoutParams.WRAP_CONTENT;
        column.addView(item, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, height));
        // 每个选项之间加一条分隔线，末尾不留多余线条
        if (i + columns < items.size()) {
          column.addView(newDivider(), new LinearLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT, Utils.dp2Px(getActivity(), 1)));
        }
      }
      menu.addView(column);
    }
  }

  private View newDivider() {
    View divider = new View(getActivity());
    divider.setBackgroundColor(0xff000000);
    return divider;
  }

  private void initViews() {
    tileSettingsMenu();
    View toBack = rootView.findViewById(R.id.toBack);
    View btnHideFontControl = rootView.findViewById(R.id.btnHideFontControl);

    toBack.setOnClickListener(this);
    rootView.findViewById(R.id.rootView).setOnClickListener(this);
    btnHideFontControl.setOnClickListener(this);

    showStatusBar = rootView.findViewById(R.id.showStatusBar);
    showCustomIcon = rootView.findViewById(R.id.showCustomIcon);
    clockShowSeconds = rootView.findViewById(R.id.clockShowSeconds);
    showLargeClock = rootView.findViewById(R.id.showLargeClock);
    showAppName = rootView.findViewById(R.id.showAppName);
    bottomAlignIcons = rootView.findViewById(R.id.bottomAlignIcons);
    startAtBoot = rootView.findViewById(R.id.startAtBoot);
    showWifiName = rootView.findViewById(R.id.showWifiName);
    hideDivider = rootView.findViewById(R.id.hideDivider);
    fontControl = rootView.findViewById(R.id.font_control);
    colNumSpinner = rootView.findViewById(R.id.col_num_spinner);
    rowNumSpinner = rootView.findViewById(R.id.row_num_spinner);
    appNameLinesSpinner = rootView.findViewById(R.id.appNameLine);
    sortModeSpinner = rootView.findViewById(R.id.sortModeSpinner);
    themeModeSpinner = rootView.findViewById(R.id.theme_mode_spinner);
    screenOrientationSpinner = rootView.findViewById(R.id.screen_orientation_spinner);
    changeFontSize = rootView.findViewById(R.id.changeFontSize);
    customFont = rootView.findViewById(R.id.customFont);
    customFontLabel = rootView.findViewById(R.id.customFontLabel);
    clockFont = rootView.findViewById(R.id.clockFont);
    clockFontLabel = rootView.findViewById(R.id.clockFontLabel);
    deleteApp = rootView.findViewById(R.id.deleteApp);
    dockManager = rootView.findViewById(R.id.dockManager);
    helpAbout = rootView.findViewById(R.id.helpAbout);
    openDeviceManager = rootView.findViewById(R.id.openDeviceManager);
    setWallpaper = rootView.findViewById(R.id.setWallpaper);

    showStatusBar.setOnClickListener(this);
    hideDivider.setOnClickListener(this);
    showCustomIcon.setOnClickListener(this);
    clockShowSeconds.setOnClickListener(this);
    showLargeClock.setOnClickListener(this);
    showAppName.setOnClickListener(this);
    bottomAlignIcons.setOnClickListener(this);
    startAtBoot.setOnClickListener(this);
    showWifiName.setOnClickListener(this);
    changeFontSize.setOnClickListener(this);
    customFont.setOnClickListener(this);
    clockFont.setOnClickListener(this);
    deleteApp.setOnClickListener(this);
    dockManager.setOnClickListener(this);
    helpAbout.setOnClickListener(this);
    openDeviceManager.setOnClickListener(this);
    setWallpaper.setOnClickListener(this);

    initDpadFocus(toBack, btnHideFontControl);

    // 初始化 UI 状态
    showStatusBar.getPaint().setStrikeThruText(!config.isShowStatusBar());
    hideDivider.getPaint().setStrikeThruText(config.isHideDivider());
    hideDivider.setText(config.isHideDivider() ? "显示分隔线" : "隐藏分隔线");
    showCustomIcon.getPaint().setStrikeThruText(!config.isShowCustomIcon());
    updateClockShowSecondsState();
    showLargeClock.getPaint().setStrikeThruText(!config.isShowLargeClock());
    showAppName.getPaint().setStrikeThruText(!config.isShowAppName());
    bottomAlignIcons.getPaint().setStrikeThruText(!config.isBottomAlignIcons());
    updateStartAtBootState();
    fontControl.setProgress((int) ((config.getFontSize() - 10) * 10));
    updateCustomFontLabel();
    updateClockFontLabel();
  }

  /** 更新"App 名字体"项的副标题：显示当前字体名或"系统默认" */
  private void updateCustomFontLabel() {
    if (customFontLabel == null) return;
    String name = config.getCustomFont();
    customFontLabel.setText(name == null || name.length() == 0
        ? getString(R.string.font_system_default) : name);
  }

  /** 更新"大时钟字体"项的副标题：显示当前字体名或"系统默认" */
  private void updateClockFontLabel() {
    if (clockFontLabel == null) return;
    String name = config.getClockFont();
    clockFontLabel.setText(name == null || name.length() == 0
        ? getString(R.string.font_system_default) : name);
  }

  private void initDpadFocus(View toBack, View btnHideFontControl) {
    View[] menuItems = new View[] {
        colNumSpinner,
        rowNumSpinner,
        appNameLinesSpinner,
        sortModeSpinner,
        screenOrientationSpinner,
        hideDivider,
        showStatusBar,
        showWifiName,
        showCustomIcon,
        clockShowSeconds,
        showLargeClock,
        showAppName,
        bottomAlignIcons,
        startAtBoot,
        changeFontSize,
        customFont,
        clockFont,
        deleteApp,
        dockManager,
        themeModeSpinner,
        setWallpaper,
        openDeviceManager,
        helpAbout
    };

    for (View item : menuItems) {
      makeFocusable(item);
    }
    makeFocusable(toBack);
    makeFocusable(btnHideFontControl);
    makeFocusable(fontControl);

    colNumSpinner.requestFocus();
  }

  private void makeFocusable(View view) {
    view.setFocusable(true);
    view.setFocusableInTouchMode(false);
    if (!(view instanceof Spinner) && !(view instanceof SeekBar)) {
      view.setBackgroundResource(R.drawable.setting_item_focus);
    }
  }

  private void initSpinners() {
    rowNumSpinner.setSelection(config.getRowNum() - 1, false);
    rowNumSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int rowNum = position + 1;
        config.setRowNum(rowNum);
        listener.onRowNumChanged(rowNum);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    colNumSpinner.setSelection(config.getColNum() - 1, false);
    colNumSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int colNum = position + 1;
        config.setColNum(colNum);
        listener.onColNumChanged(colNum);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    appNameLinesSpinner.setSelection(getAppLineSpinnerSelectPosition(), false);
    appNameLinesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int lines = (position == 3) ? Integer.MAX_VALUE : position;
        config.setAppNameLines(lines);
        listener.onAppNameLinesChanged(lines);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    sortModeSpinner.setSelection(config.getSortMode(), false);
    sortModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (AppSortComparator.modeNeedsUsageStats(position)
            && !AppSortComparator.hasUsageStatsPermission(getActivity())) {
          Toast.makeText(getActivity(), R.string.sort_need_usage_permission, Toast.LENGTH_LONG).show();
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
          }
          sortModeSpinner.setSelection(config.getSortMode(), false);
          return;
        }
        config.setSortMode(position);
        listener.onSortModeChanged(position);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    screenOrientationSpinner.setSelection(config.getScreenOrientation(), false);
    screenOrientationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (position == config.getScreenOrientation()) {
          return;
        }
        config.setScreenOrientation(position);
        listener.onScreenOrientationChanged(position);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    themeModeSpinner.setSelection(config.getThemeMode(), false);
    themeModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (position == config.getThemeMode()) {
          return;
        }
        config.setThemeMode(position);
        listener.onThemeModeChanged(position);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });
  }

  private void initFontControl() {
    fontControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
          float newSize = 10 + progress / 10f;
          config.setFontSize(newSize);
          listener.onFontSizeChanged(newSize);
        }
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {
      }
    });
  }

  private int getAppLineSpinnerSelectPosition() {
    int lines = config.getAppNameLines();
    return (lines <= 2) ? lines : 3;
  }

  // =========================================================================
  // 点击处理
  // =========================================================================

  @Override
  public void onClick(View v) {
    int id = v.getId();
    if (id == R.id.toBack || id == R.id.rootView) {
      closeSettings();
    } else if (id == R.id.deleteApp) {
      handleDeleteApp();
    } else if (id == R.id.dockManager) {
      showDockManager();
    } else if (id == R.id.showStatusBar) {
      handleToggleStatusBar();
    } else if (id == R.id.helpAbout) {
      AboutDialog.getInstance(getActivity()).show();
    } else if (id == R.id.btnHideFontControl) {
      rootView.findViewById(R.id.menuList).setVisibility(View.VISIBLE);
      rootView.findViewById(R.id.font_control_p).setVisibility(View.GONE);
      changeFontSize.requestFocus();
    } else if (id == R.id.changeFontSize) {
      rootView.findViewById(R.id.menuList).setVisibility(View.GONE);
      rootView.findViewById(R.id.font_control_p).setVisibility(View.VISIBLE);
      fontControl.requestFocus();
    } else if (id == R.id.customFont) {
      handleCustomFont();
    } else if (id == R.id.clockFont) {
      handleClockFont();
    } else if (id == R.id.hideDivider) {
      handleToggleDivider();
    } else if (id == R.id.showWifiName) {
      handleShowWifiName();
    } else if (id == R.id.showCustomIcon) {
      handleToggleCustomIcon();
    } else if (id == R.id.clockShowSeconds) {
      handleToggleClockShowSeconds();
    } else if (id == R.id.showLargeClock) {
      handleToggleLargeClock();
    } else if (id == R.id.showAppName) {
      handleToggleShowAppName();
    } else if (id == R.id.bottomAlignIcons) {
      handleToggleBottomAlignIcons();
    } else if (id == R.id.startAtBoot) {
      handleToggleStartAtBoot();
    } else if (id == R.id.setWallpaper) {
      handleSetWallpaper();
    } else if (id == R.id.openDeviceManager) {
      startActivity(new Intent().setComponent(
          new ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings")));
    }
  }

  private void handleDeleteApp() {
    listener.onEnterManageMode();
    closeSettings();
  }

  /** 从设置页打开应用列表，点击或长按应用即可放入 Dock */
  private void showDockManager() {
    final Activity activity = getActivity();
    if (activity == null || listener == null) return;
    final List<ResolveInfo> apps = listener.getDockCandidates();
    final List<String> labels = new ArrayList<String>();
    PackageManager pm = activity.getPackageManager();
    for (ResolveInfo info : apps) {
      CharSequence label = AppDataCenter.getSafeLabel(info, activity, pm);
      labels.add(label != null && label.length() > 0
          ? label.toString() : (info.activityInfo != null ? info.activityInfo.packageName : ""));
    }
    ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity,
        android.R.layout.simple_list_item_1, labels);
    AlertDialog dialog = new AlertDialog.Builder(activity)
        .setTitle(R.string.dock_manager_title)
        .setAdapter(adapter, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (which >= 0 && which < apps.size()) showDockSlotDialog(apps.get(which));
          }
        })
        .setNegativeButton(android.R.string.cancel, null)
        .create();
    dialog.getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
      @Override
      public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (position >= 0 && position < apps.size()) showDockSlotDialog(apps.get(position));
        return true;
      }
    });
    dialog.show();
  }

  /** 选择目标槽位；已在 Dock 中的应用也可以移出 */
  private void showDockSlotDialog(final ResolveInfo info) {
    final Activity activity = getActivity();
    if (activity == null || listener == null) return;
    final String key = AppDataCenter.componentKey(info);
    final List<String> slots = listener.getDockSlots();
    List<ResolveInfo> candidates = listener.getDockCandidates();
    Map<String, String> names = new HashMap<String, String>();
    PackageManager pm = activity.getPackageManager();
    for (ResolveInfo candidate : candidates) {
      CharSequence label = AppDataCenter.getSafeLabel(candidate, activity, pm);
      names.put(AppDataCenter.componentKey(candidate),
          label != null && label.length() > 0
              ? label.toString()
              : (candidate.activityInfo != null ? candidate.activityInfo.packageName : ""));
    }
    final boolean canRemove = slots.contains(key);
    String[] items = new String[canRemove ? 5 : 4];
    for (int i = 0; i < 4; i++) {
      String occupied = i < slots.size() ? slots.get(i) : "";
      if (occupied == null || occupied.length() == 0) {
        items[i] = (i + 1) + ". " + getString(R.string.dock_empty_slot);
      } else {
        String name = names.get(occupied);
        items[i] = (i + 1) + ". " + (name != null ? name : occupied);
      }
    }
    if (canRemove) items[4] = getString(R.string.dock_remove);
    String chosen = names.get(key);
    new AlertDialog.Builder(activity)
        .setTitle(getString(R.string.dock_add_to) + ": "
            + (chosen != null ? chosen : info.activityInfo.packageName))
        .setItems(items, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (which >= 0 && which < 4) {
              if (!listener.addToDock(key, which)) {
                Toast.makeText(activity, R.string.dock_slot_full, Toast.LENGTH_SHORT).show();
              }
            } else if (canRemove && which == 4) {
              listener.removeFromDock(key);
            }
          }
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void handleToggleStatusBar() {
    boolean newValue = !config.isShowStatusBar();
    config.setShowStatusBar(newValue);
    listener.onShowStatusBarChanged(newValue);
    closeSettings();
  }

  private void handleToggleDivider() {
    boolean newValue = !config.isHideDivider();
    config.setHideDivider(newValue);
    hideDivider.setText(newValue ? "显示分隔线" : "隐藏分隔线");
    listener.onHideDividerChanged(newValue);
    closeSettings();
  }

  /** App 名字体设置入口 */
  private void handleCustomFont() {
    showFontDialog(false);
  }

  /** 大时钟字体设置入口 */
  private void handleClockFont() {
    showFontDialog(true);
  }

  /**
   * 弹出字体选择对话框。列出 font 目录下的 .ttf/.otf 文件，加一个"系统默认"选项。
   * 选中后写入对应的 Config 并通过 listener 立即应用。
   *
   * @param forClock true=大时钟字体，false=App 名字体
   */
  private void showFontDialog(final boolean forClock) {
    final Activity activity = getActivity();
    if (activity == null) return;

    FontManager.ensureFontDirectory();
    final List<File> fonts = FontManager.listFonts();
    final String current = forClock ? config.getClockFont() : config.getCustomFont();

    final String[] labels = new String[fonts.size() + 1];
    labels[0] = getString(R.string.font_system_default);
    int checked = 0;
    for (int i = 0; i < fonts.size(); i++) {
      labels[i + 1] = fonts.get(i).getName();
      if (fonts.get(i).getName().equals(current)) {
        checked = i + 1;
      }
    }

    new AlertDialog.Builder(activity)
        .setTitle(forClock ? R.string.setting_clock_font : R.string.setting_custom_font)
        .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            String chosen = (which == 0) ? "" : fonts.get(which - 1).getName();
            if (forClock) {
              config.setClockFont(chosen);
              updateClockFontLabel();
              if (listener != null) {
                listener.onClockFontChanged(chosen);
              }
            } else {
              config.setCustomFont(chosen);
              updateCustomFontLabel();
              if (listener != null) {
                listener.onFontChanged(chosen);
              }
            }
            dialog.dismiss();
            Toast.makeText(activity,
                which == 0 ? getString(R.string.font_applied_default)
                           : getString(R.string.font_applied) + chosen,
                Toast.LENGTH_SHORT).show();
          }
        })
        .setNegativeButton(android.R.string.cancel, null)
        .setNeutralButton(R.string.font_open_folder, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Toast.makeText(activity, FontManager.getFontDirectory().getAbsolutePath(),
                Toast.LENGTH_LONG).show();
          }
        })
        .show();
  }

  private void handleShowWifiName() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 10002);
    }
  }

  private void handleToggleCustomIcon() {
    Utils.checkStoragePermission(getActivity(), new Runnable() {
      @Override
      public void run() {
        boolean newValue = !config.isShowCustomIcon();
        config.setShowCustomIcon(newValue);
        listener.onShowCustomIconChanged(newValue);
        closeSettings();
      }
    });
  }

  private void handleToggleClockShowSeconds() {
    boolean newValue = !config.isClockShowSeconds();
    config.setClockShowSeconds(newValue);
    updateClockShowSecondsState();
    listener.onClockShowSecondsChanged(newValue);
  }

  private void updateClockShowSecondsState() {
    clockShowSeconds.getPaint().setStrikeThruText(!config.isClockShowSeconds());
  }

  private void handleToggleLargeClock() {
    boolean show = !config.isShowLargeClock();
    config.setShowLargeClock(show);
    showLargeClock.getPaint().setStrikeThruText(!show);
    OnSettingChangeListener settingListener = listener;
    if (settingListener != null) {
      settingListener.onShowLargeClockChanged(show);
    }
    closeSettings();
  }

  private void handleToggleShowAppName() {
    boolean show = !config.isShowAppName();
    config.setShowAppName(show);
    showAppName.getPaint().setStrikeThruText(!show);
    OnSettingChangeListener settingListener = listener;
    if (settingListener != null) {
      settingListener.onShowAppNameChanged(show);
    }
  }

  /** 通过宿主 Activity 关闭设置页面 */
  private void closeSettings() {
    Activity activity = getActivity();
    if (activity != null) {
      activity.onBackPressed();
    }
  }

  private void handleToggleBottomAlignIcons() {
    boolean align = !config.isBottomAlignIcons();
    config.setBottomAlignIcons(align);
    bottomAlignIcons.getPaint().setStrikeThruText(!align);
    listener.onBottomAlignIconsChanged(align);
  }

  private void handleToggleStartAtBoot() {
    config.setStartAtBoot(!config.isStartAtBoot());
    updateStartAtBootState();
  }

  private void updateStartAtBootState() {
    startAtBoot.getPaint().setStrikeThruText(!config.isStartAtBoot());
  }

  private void handleSetWallpaper() {
    Activity activity = getActivity();
    if (activity == null) return;

    WallpaperManager wallpaperManager = WallpaperManager.getInstance(activity.getApplicationContext());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !wallpaperManager.isWallpaperSupported()) {
      Toast.makeText(activity, R.string.wallpaper_not_supported, Toast.LENGTH_SHORT).show();
      return;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !wallpaperManager.isSetWallpaperAllowed()) {
      Toast.makeText(activity, R.string.wallpaper_not_allowed, Toast.LENGTH_SHORT).show();
      return;
    }

    Intent pickerIntent = new Intent(
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
            ? Intent.ACTION_OPEN_DOCUMENT
            : Intent.ACTION_GET_CONTENT);
    pickerIntent.addCategory(Intent.CATEGORY_OPENABLE);
    pickerIntent.setType("image/*");
    pickerIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

    try {
      startActivityForResult(pickerIntent, REQUEST_PICK_WALLPAPER);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(activity, R.string.wallpaper_picker_unavailable, Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode != REQUEST_PICK_WALLPAPER || resultCode != Activity.RESULT_OK) return;

    Uri uri = data != null ? data.getData() : null;
    if (uri == null) {
      Activity activity = getActivity();
      if (activity != null) {
        Toast.makeText(activity, R.string.wallpaper_read_failed, Toast.LENGTH_SHORT).show();
      }
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      showWallpaperTargetDialog(uri);
    } else {
      applyWallpaper(uri, WallpaperManager.FLAG_SYSTEM);
    }
  }

  private void showWallpaperTargetDialog(final Uri uri) {
    Activity activity = getActivity();
    if (activity == null) return;

    CharSequence[] targets = new CharSequence[] {
        getString(R.string.wallpaper_target_home),
        getString(R.string.wallpaper_target_home_and_lock)
    };
    new AlertDialog.Builder(activity)
        .setTitle(R.string.wallpaper_target_title)
        .setItems(targets, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            int flags = which == 0
                ? WallpaperManager.FLAG_SYSTEM
                : WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK;
            applyWallpaper(uri, flags);
          }
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  private void applyWallpaper(final Uri uri, final int flags) {
    Activity activity = getActivity();
    if (activity == null) return;
    final Context appContext = activity.getApplicationContext();

    WALLPAPER_EXECUTOR.execute(new Runnable() {
      @Override
      public void run() {
        InputStream inputStream;
        try {
          inputStream = appContext.getContentResolver().openInputStream(uri);
        } catch (IOException | SecurityException e) {
          Log.e(TAG, "Unable to open selected wallpaper", e);
          postWallpaperToast(appContext, R.string.wallpaper_read_failed);
          return;
        }

        if (inputStream == null) {
          postWallpaperToast(appContext, R.string.wallpaper_read_failed);
          return;
        }

        try (InputStream stream = inputStream) {
          WallpaperManager wallpaperManager = WallpaperManager.getInstance(appContext);
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wallpaperManager.setStream(stream, null, true, flags);
          } else {
            wallpaperManager.setStream(stream);
          }
          postWallpaperToast(appContext, R.string.wallpaper_setting_success);
        } catch (IOException | SecurityException | IllegalArgumentException e) {
          Log.e(TAG, "Unable to set wallpaper", e);
          postWallpaperToast(appContext, R.string.wallpaper_setting_failed);
        }
      }
    });
  }

  private static void postWallpaperToast(final Context context, final int messageResId) {
    MAIN_HANDLER.post(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(context, messageResId, Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == 10002) {
      WifiControl.reloadWifiName();
      closeSettings();
    }
  }

  // =========================================================================
  // 生命周期
  // =========================================================================

}
