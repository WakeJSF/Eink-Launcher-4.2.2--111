package cn.modificator.launcher;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import cn.modificator.launcher.model.AdminReceiver;

/**
 * 设备管理器激活代理页。
 *
 * Launcher 是 singleInstance，直接由它启动 ADD_DEVICE_ADMIN 会被系统判为 NEW_TASK 并拒绝：
 * "Cannot start ADD_DEVICE_ADMIN as a new task"。这里用一个无界面的透明 Activity
 * 作为中转，从普通任务中启动激活页，并在激活成功后立即锁屏。
 */
public class DeviceAdminActivator extends Activity {

  private static final int REQUEST_ENABLE_ADMIN = 2001;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
    ComponentName admin = new ComponentName(this, AdminReceiver.class);
    if (dpm != null && dpm.isAdminActive(admin)) {
      try {
        dpm.lockNow();
      } catch (Exception ignored) {
      }
      finish();
      return;
    }

    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
        getString(R.string.lock_admin_explanation));
    try {
      startActivityForResult(intent, REQUEST_ENABLE_ADMIN);
    } catch (Exception e) {
      Toast.makeText(this, R.string.lock_admin_failed, Toast.LENGTH_SHORT).show();
      finish();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode != REQUEST_ENABLE_ADMIN) return;

    DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
    ComponentName admin = new ComponentName(this, AdminReceiver.class);
    if (dpm != null && dpm.isAdminActive(admin)) {
      try {
        dpm.lockNow();
      } catch (Exception ignored) {
      }
    } else {
      Toast.makeText(this, R.string.lock_admin_failed, Toast.LENGTH_SHORT).show();
    }
    finish();
  }
}
