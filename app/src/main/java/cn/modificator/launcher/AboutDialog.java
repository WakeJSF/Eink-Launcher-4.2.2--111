package cn.modificator.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Created by Modificator
 * time: 17/1/7.下午2:40
 * des:create file and achieve model
 */

public class AboutDialog {

  Context context;

  private AboutDialog(Context context) {
    this.context = context;
  }

  public static AboutDialog getInstance(Context context) {
    return new AboutDialog(context);
  }

  private View initLayout() {
    LinearLayout root = new LinearLayout(context);
    int padding = Utils.dp2Px(context, 15);
    root.setPadding(padding, padding, padding, padding);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(0xffffffff);
    TextView appName = new TextView(context);
    appName.setText("E-ink Launcher漂浮版");
    appName.setTextSize(30);
    root.addView(appName);
    View line = new View(context);
    line.setBackgroundColor(0xff000000);
    root.addView(line, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Utils.dp2Px(context, 1)));

    TextView authorInfo = new TextView(context);

    authorInfo.setText("原作者：Modificator\n修改者：漂浮版\n\n本版本基于 Modificator 的原作 E-Ink Launcher，\n以及郑羊羊（zyyme）的「咩E-Ink Launcher」修改版制作。\n感谢两位作者的开发与分享。");
    authorInfo.setTextSize(16);
    authorInfo.setTextColor(0xff000000);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      authorInfo.setLineSpacing(authorInfo.getLineSpacingExtra(), 1.2f);
    }
    LinearLayout.LayoutParams authorInfoLP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    authorInfoLP.topMargin = Utils.dp2Px(context, 10);
    authorInfoLP.bottomMargin = Utils.dp2Px(context, 10);
    root.addView(authorInfo, authorInfoLP);

    line = new View(context);
    line.setBackgroundColor(0xff000000);
    root.addView(line, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Utils.dp2Px(context, 1)));

    TextView thanksView = new TextView(context);
    thanksView.setText("同时感谢 Maciej Haudek 等开源贡献者。");
    thanksView.setTextSize(14);
    thanksView.setPadding(0, Utils.dp2Px(context, 10), 0, Utils.dp2Px(context, 10));
    root.addView(thanksView);

    line = new View(context);
    line.setBackgroundColor(0xff000000);
    root.addView(line, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Utils.dp2Px(context, 1)));

    TextView launcherInfo = new TextView(context);
    launcherInfo.setText("\n版本来源：E-Ink Launcher 0.1.8.6（Modificator）\n源码：github.com/zanjie1999/E-Ink-Launcher\n\n本漂浮版针对 BOYUE T80S / Android 4.2 做了适配：\n两行桌面、页面小点、Dock 栏、墨水屏全刷、\n自定义字体与图标等。\n\n自定义图标：把图标文件按应用包名命名，\n放到 /sdcard/E-Ink Launcher/icon/ 即可。");
    launcherInfo.setTextColor(0xff000000);
    launcherInfo.setTextSize(13);
    root.addView(launcherInfo);

    return root;
  }

  public void show() {
    new AlertDialog.Builder(context)
        .setView(initLayout())
        .setPositiveButton(R.string.dialog_close, null)
        .show();
  }
}
