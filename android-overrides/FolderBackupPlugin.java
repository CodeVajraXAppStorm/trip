package com.tripsplit.hisaab;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import androidx.activity.result.ActivityResult;
import androidx.documentfile.provider.DocumentFile;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

@CapacitorPlugin(name = "FolderBackup")
public class FolderBackupPlugin extends Plugin {

    private SharedPreferences sp() {
        return getContext().getSharedPreferences("fb", Context.MODE_PRIVATE);
    }

    private DocumentFile root() {
        String u = sp().getString("uri", null);
        if (u == null) return null;
        Uri uri = Uri.parse(u);
        boolean ok = false;
        for (UriPermission p : getContext().getContentResolver().getPersistedUriPermissions()) {
            if (p.getUri().equals(uri) && p.isWritePermission()) {
                ok = true;
                break;
            }
        }
        if (!ok) return null;
        DocumentFile d = DocumentFile.fromTreeUri(getContext(), uri);
        return (d != null && d.exists()) ? d : null;
    }

    @PluginMethod
    public void pickFolder(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        );
        startActivityForResult(call, i, "onPick");
    }

    @ActivityCallback
    private void onPick(PluginCall call, ActivityResult r) {
        if (call == null) return;
        if (r.getResultCode() != Activity.RESULT_OK || r.getData() == null || r.getData().getData() == null) {
            call.reject("cancelled");
            return;
        }
        Uri uri = r.getData().getData();
        getContext().getContentResolver().takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        );
        sp().edit().putString("uri", uri.toString()).apply();
        DocumentFile d = DocumentFile.fromTreeUri(getContext(), uri);
        JSObject o = new JSObject();
        o.put("uri", uri.toString());
        o.put("name", (d != null && d.getName() != null) ? d.getName() : "Folder");
        call.resolve(o);
    }

    @PluginMethod
    public void getFolder(PluginCall call) {
        DocumentFile d = root();
        JSObject o = new JSObject();
        if (d != null) {
            o.put("uri", d.getUri().toString());
            o.put("name", d.getName() != null ? d.getName() : "Folder");
        }
        call.resolve(o);
    }

    @PluginMethod
    public void writeFile(PluginCall call) {
        try {
            DocumentFile d = root();
            if (d == null) {
                call.reject("no folder");
                return;
            }
            String name = call.getString("name");
            String content = call.getString("content");
            DocumentFile f = d.findFile(name);
            if (f == null) f = d.createFile("application/json", name);
            if (f == null) {
                call.reject("create failed");
                return;
            }
            OutputStream os = getContext().getContentResolver().openOutputStream(f.getUri(), "wt");
            os.write(content.getBytes(StandardCharsets.UTF_8));
            os.close();
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void readAll(PluginCall call) {
        try {
            DocumentFile d = root();
            if (d == null) {
                call.reject("no folder");
                return;
            }
            JSArray arr = new JSArray();
            for (DocumentFile f : d.listFiles()) {
                String n = f.getName();
                if (n == null || !n.startsWith("trip-") || !n.endsWith(".json")) continue;
                InputStream is = getContext().getContentResolver().openInputStream(f.getUri());
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] b = new byte[4096];
                int k;
                while ((k = is.read(b)) > 0) bo.write(b, 0, k);
                is.close();
                JSObject o = new JSObject();
                o.put("name", n);
                o.put("content", bo.toString("UTF-8"));
                arr.put(o);
            }
            JSObject res = new JSObject();
            res.put("files", arr);
            call.resolve(res);
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void deleteFile(PluginCall call) {
        try {
            DocumentFile d = root();
            if (d != null) {
                DocumentFile f = d.findFile(call.getString("name"));
                if (f != null) f.delete();
            }
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }
          }
