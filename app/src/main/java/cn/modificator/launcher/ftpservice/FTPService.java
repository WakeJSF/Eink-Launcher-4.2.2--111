package cn.modificator.launcher.ftpservice;

/**
 * FTP Service - Android 4.2.2 compatible version
 * Note: Apache FTPServer library is not compatible with Android DEX
 * This service provides placeholder functionality
 */

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.core.app.ServiceCompat;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import cn.modificator.launcher.R;

public class FTPService extends Service {

  public static final int DEFAULT_PORT = 4567;
  public static final String PORT_PREFERENCE_KEY = "ftpPort";
  private static final int NOTIFICATION_ID = 2333;
  private static final String NOTIFICATION_CHANNEL_ID = "ftp_service";

  // Service will (global) broadcast when server start/stop
  public static final String ACTION_STARTED = "cn.modificator.launcher.ftpservice.FTPReceiver.FTPSERVER_STARTED";
  public static final String ACTION_STOPPED = "cn.modificator.launcher.ftpservice.FTPReceiver.FTPSERVER_STOPPED";
  public static final String ACTION_FAILEDTOSTART = "cn.modificator.launcher.ftpservice.FTPReceiver.FTPSERVER_FAILEDTOSTART";

  // RequestStartStopReceiver listens for these actions to start/stop this server
  public static final String ACTION_START_FTPSERVER = "cn.modificator.launcher.ftpservice.FTPReceiver.ACTION_START_FTPSERVER";
  public static final String ACTION_STOP_FTPSERVER = "cn.modificator.launcher.ftpservice.FTPReceiver.ACTION_STOP_FTPSERVER";

  public static int getDefaultPortFromPreferences(SharedPreferences preferences) {
    try {
      return preferences.getInt(PORT_PREFERENCE_KEY, DEFAULT_PORT);
    } catch (ClassCastException ex) {
      Log.e("FtpService", "Default port preference is not an int. Resetting to default.");
      changeFTPServerPort(preferences, DEFAULT_PORT);
      return DEFAULT_PORT;
    }
  }

  public static void changeFTPServerPort(SharedPreferences preferences, int port) {
    preferences.edit()
        .putInt(PORT_PREFERENCE_KEY, port)
        .apply();
  }

  private static final String TAG = FTPService.class.getSimpleName();
  private static int port = DEFAULT_PORT;
  private static boolean running = false;

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.i(TAG, "onStartCommand - FTP service not available (Apache FTPServer not compatible with Android)");
    startForegroundCompat();

    // Broadcast that FTP server failed to start
    Intent failedIntent = new Intent(ACTION_FAILEDTOSTART);
    failedIntent.putExtra("error", "Apache FTPServer library not compatible with Android");
    sendBroadcast(failedIntent);

    return START_NOT_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    running = false;
    stopForegroundCompat();
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    super.onTaskRemoved(rootIntent);
    stopSelf();
  }

  public static boolean isRunning() {
    return running;
  }

  private void startForegroundCompat() {
    Notification.Builder builder;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      if (nm != null) {
        NotificationChannel channel = new NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "FTP Service",
            NotificationManager.IMPORTANCE_LOW
        );
        nm.createNotificationChannel(channel);
      }
      builder = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID);
    } else {
      builder = new Notification.Builder(this);
    }

    builder
        .setContentTitle("Eink Launcher")
        .setContentText("FTP服务暂不可用")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setOngoing(true);

    Notification notification = builder.build();
    startForeground(NOTIFICATION_ID, notification);
  }

  private void stopForegroundCompat() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE);
    } else {
      stopForeground(true);
    }
  }

  public static void sleepIgnoreInterupt(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public static boolean isConnectedToLocalNetwork(Context context) {
    try {
      ConnectivityManager cm = (ConnectivityManager) context
          .getSystemService(Context.CONNECTIVITY_SERVICE);
      if (cm != null) {
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
      }
    } catch (Exception e) {
      Log.e(TAG, "Error checking network connectivity", e);
    }
    return false;
  }

  public static boolean isConnectedToWifi(Context context) {
    try {
      WifiManager wm = (WifiManager) context
          .getApplicationContext()
          .getSystemService(Context.WIFI_SERVICE);
      return wm != null && wm.isWifiEnabled();
    } catch (Exception e) {
      return false;
    }
  }

  public static InetAddress getLocalInetAddress(Context context) {
    try {
      WifiManager wm = (WifiManager) context
          .getApplicationContext()
          .getSystemService(Context.WIFI_SERVICE);
      if (wm != null) {
        int ip = wm.getConnectionInfo().getIpAddress();
        return intToInet(ip);
      }
    } catch (Exception e) {
      Log.e(TAG, "Error getting local IP", e);
    }
    return null;
  }

  public static InetAddress intToInet(int value) {
    try {
      return InetAddress.getByAddress(new byte[]{
          (byte) (value & 0xFF),
          (byte) ((value >> 8) & 0xFF),
          (byte) ((value >> 16) & 0xFF),
          (byte) ((value >> 24) & 0xFF)
      });
    } catch (UnknownHostException e) {
      return null;
    }
  }

  public static byte byteOfInt(int value, int which) {
    return (byte) ((value >> (which * 8)) & 0xFF);
  }

  public static int getPort() {
    return port;
  }

  public static boolean isPortAvailable(int port) {
    ServerSocket ss = null;
    DatagramSocket ds = null;
    try {
      ss = new ServerSocket(port);
      ds = new DatagramSocket(port);
      ds.setReuseAddress(true);
      return true;
    } catch (IOException e) {
      return false;
    } finally {
      if (ds != null) {
        ds.close();
      }
      if (ss != null) {
        try {
          ss.close();
        } catch (IOException e) {
          // ignore
        }
      }
    }
  }
}
