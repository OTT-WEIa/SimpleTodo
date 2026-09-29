package com.minimal.todo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.view.View;

import java.security.MessageDigest;

/**
 * 基础防线（都不影响正常使用）：
 * · 首次打开本机时弹一次声明弹窗（只在第一次，之后不再弹）；
 * · 顺手做两件基础检查：本应用不该声明任何权限、签名是否是原来那个。
 *   多出权限（广告/木马通常要靠联网权限）或者签名变了（说明包被重新打包过）
 *   就提示一次，让用户知道这个包可能被改过。
 */
final class AppGuard {

    /** 构建这个包时用的证书 SHA-256。二次打包/塞代码会换签名，对不上就说明被改过。 */
    private static final String EXPECTED_CERT =
            "9ca328e8c873189984cd0c4f265c79b2a5d105ce3c282b24acfcc276e3729060";

    private static final String PREF_FILE = "app_guard";
    private static final String KEY_NOTICE_SHOWN = "notice_shown";
    private static final String KEY_TAMPER_WARNED = "tamper_warned";

    private AppGuard() {
    }

    /** 在每个入口 Activity 的 onCreate 里调用（弹过就不再弹）。 */
    static void check(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_NOTICE_SHOWN, false)) {
            prefs.edit().putBoolean(KEY_NOTICE_SHOWN, true).apply();
            showNotice(activity);
            return;
        }
        String problem = integrityProblem(activity);
        if (problem != null && !prefs.getBoolean(KEY_TAMPER_WARNED, false)) {
            prefs.edit().putBoolean(KEY_TAMPER_WARNED, true).apply();
            showTamperWarning(activity, problem);
        }
    }

    /** 首次打开时的声明：正文居中，底部一个「我知道了」。 */
    private static void showNotice(final Activity activity) {
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_first_run, null);
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(content)
                .setCancelable(false)
                .create();
        content.findViewById(R.id.notice_ok).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static void showTamperWarning(Activity activity, String problem) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.guard_title)
                .setMessage(activity.getString(R.string.guard_msg, problem))
                .setPositiveButton(R.string.help_ok, null)
                .show();
    }

    /** 发现问题就返回一句说明，没问题返回 null。 */
    @SuppressWarnings("deprecation")
    private static String integrityProblem(Context context) {
        try {
            PackageManager manager = context.getPackageManager();
            PackageInfo info = manager.getPackageInfo(context.getPackageName(),
                    PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNATURES);

            // 这个应用不需要任何权限；多出来就说明被塞了东西
            String[] permissions = info.requestedPermissions;
            if (permissions != null && permissions.length > 0) {
                StringBuilder builder = new StringBuilder();
                for (String permission : permissions) {
                    if (builder.length() > 0) {
                        builder.append('、');
                    }
                    builder.append(permission);
                }
                return context.getString(R.string.guard_reason_permission, builder.toString());
            }

            Signature[] signatures = info.signatures;
            if (signatures != null && signatures.length > 0) {
                if (!EXPECTED_CERT.equalsIgnoreCase(sha256(signatures[0].toByteArray()))) {
                    return context.getString(R.string.guard_reason_signature);
                }
            }
        } catch (Throwable ignored) {
            // 查不出来就算了，不打扰用户
            return null;
        }
        return null;
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] result = digest.digest(data);
        StringBuilder builder = new StringBuilder(result.length * 2);
        for (byte b : result) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }
}
