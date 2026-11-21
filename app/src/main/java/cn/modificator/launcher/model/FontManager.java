package cn.modificator.launcher.model;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 桌面自定义字体管理器。
 *
 * <p>字体文件放置目录（与自定义图标目录同级）：
 * <ul>
 *   <li>Android 4.4+ (KITKAT): {@code Documents/E-Ink Launcher/font/}</li>
 *   <li>更早版本: {@code 外部存储根目录/E-Ink Launcher/font/}</li>
 * </ul>
 *
 * <p>支持 .ttf / .otf 文件，使用 {@link Typeface#createFromFile(String)} 加载
 * （该 API 自 API 1 起可用，兼容 Android 4.2.2）。加载失败时安全降级为系统默认字体。
 */
public class FontManager {

  private static final String TAG = "FontManager";

  /** 字体目录名（位于 "E-Ink Launcher" 下） */
  private static final String FONT_DIR = "E-Ink Launcher" + File.separator + "font";

  /** 支持的字体扩展名 */
  private static final String[] FONT_EXT = {".ttf", ".otf", ".TTF", ".OTF"};

  /** Typeface 缓存：字体文件名 → Typeface */
  private final android.util.LruCache<String, Typeface> cache =
      new android.util.LruCache<String, Typeface>(8);

  /** 记录加载失败的文件名，避免每次都重试 */
  private final java.util.Set<String> failed = new java.util.HashSet<String>();

  /** 获取字体根目录 */
  public static File getFontDirectory() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
      return new File(
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FONT_DIR);
    }
    return new File(Environment.getExternalStorageDirectory(), FONT_DIR);
  }

  /** 确保字体目录存在（首次使用时创建，方便用户放文件） */
  public static boolean ensureFontDirectory() {
    try {
      File dir = getFontDirectory();
      if (!dir.exists()) {
        return dir.mkdirs();
      }
      return true;
    } catch (Exception e) {
      Log.w(TAG, "Failed to create font directory", e);
      return false;
    }
  }

  /**
   * 扫描字体目录，返回按文件名排序的字体文件列表。
   * 目录不存在或无权限时返回空列表。
   */
  public static List<File> listFonts() {
    List<File> result = new ArrayList<File>();
    File dir = getFontDirectory();
    File[] files = dir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File parent, String name) {
        for (String ext : FONT_EXT) {
          if (name.endsWith(ext)) return true;
        }
        return false;
      }
    });
    if (files != null) {
      result.addAll(Arrays.asList(files));
      Collections.sort(result, new Comparator<File>() {
        @Override
        public int compare(File a, File b) {
          return a.getName().compareToIgnoreCase(b.getName());
        }
      });
    }
    return result;
  }

  /**
   * 根据文件名加载 Typeface。
   *
   * @param fileName 字体文件名（如 "myfont.ttf"），为空返回 null
   * @return Typeface 实例；文件不存在或加载失败时返回 null（调用方应降级为系统默认字体）
   */
  public Typeface loadFont(String fileName) {
    if (fileName == null || fileName.length() == 0) return null;

    Typeface cached = cache.get(fileName);
    if (cached != null) return cached;
    if (failed.contains(fileName)) return null;

    File file = new File(getFontDirectory(), fileName);
    if (!file.exists() || !file.isFile()) {
      Log.w(TAG, "Font file not found: " + file.getAbsolutePath());
      failed.add(fileName);
      return null;
    }
    if (failed.contains(fileName)) return null;

    try {
      Typeface tf = Typeface.createFromFile(file.getPath());
      if (tf != null) {
        cache.put(fileName, tf);
        return tf;
      }
    } catch (Exception e) {
      Log.w(TAG, "Failed to load font: " + fileName, e);
    } catch (OutOfMemoryError e) {
      Log.w(TAG, "OOM loading font: " + fileName);
    }
    failed.add(fileName);
    return null;
  }

  /** 字体文件是否发生变更（用于失败后重新尝试，例如用户刚放好文件） */
  public void resetFailures() {
    failed.clear();
  }

  /** 清空缓存（字体文件被替换时调用） */
  public void clearCache() {
    cache.evictAll();
    failed.clear();
  }
}
