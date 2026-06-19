package com.fongmi.android.tv;

import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.UpdateDialog;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Github;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

public class Updater implements Download.Callback, UpdateListener {

    private String apkUrl;
    private UpdateDialog dialog;
    private Download download;

    private Updater() {
    }

    public static Updater create() {
        return new Updater();
    }

    public Updater force() {
        Notify.show(R.string.update_check);
        Setting.putUpdate(true);
        return this;
    }

    public void start(FragmentActivity activity) {
        if (!Setting.getUpdate()) return;
        Task.execute(() -> doInBackground(activity));
    }

    private void doInBackground(FragmentActivity activity) {
        try {
            String json = OkHttp.string(Github.API_LATEST_RELEASE);
            JSONObject object = new JSONObject(json);
            String tagName = object.getString("tag_name");
            String body = object.optString("body", "");
            int versionCode = extractVersionCode(tagName);

            if (versionCode <= BuildConfig.VERSION_CODE) return;

            JSONArray assets = object.getJSONArray("assets");
            String apkUrl = null;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                if (asset.getString("name").endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url");
                    break;
                }
            }

            if (apkUrl == null) return;

            this.apkUrl = apkUrl;
            App.post(() -> show(activity, tagName, body));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int extractVersionCode(String tagName) {
        try {
            String numeric = tagName.replaceAll("[^0-9]", "");
            return Integer.parseInt(numeric);
        } catch (Exception e) {
            return 0;
        }
    }

    private void show(FragmentActivity activity, String version, String desc) {
        dismiss();
        dialog = UpdateDialog.create().title(ResUtil.getString(R.string.update_version, version)).desc(desc).listener(this).show(activity);
    }

    @Override
    public void onConfirm(View view) {
        view.setEnabled(false);
        download = Download.create(apkUrl, Path.cache("update.apk"));
        download.start(this);
    }

    @Override
    public void onCancel(View view) {
        Setting.putUpdate(false);
        download = null;
        dismiss();
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void progress(int progress) {
        if (dialog != null) dialog.setProgress(progress);
    }

    @Override
    public void error(String msg) {
        Notify.show(msg);
        dismiss();
    }

    @Override
    public void success(File file) {
        FileUtil.openFile(file);
        dismiss();
    }
}
