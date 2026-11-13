package com.waymark.files;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Base64;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sync through a folder the person picks (src/state/sync.svelte.ts): one Waymark
 * sync file that Syncthing, Drive, Nextcloud or any other app keeps the
 * same across phones. Waymark reads and writes only that folder, through the
 * permission the system picker grants, so it needs no storage permission.
 */
@CapacitorPlugin(name = "FolderSync")
public class FolderSyncPlugin extends Plugin {

    static final int MAX_FILE = 64 * 1024 * 1024;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override
    protected void handleOnDestroy() {
        worker.shutdown();
    }

    /** Opens the system folder picker and keeps access to the folder chosen. */
    @PluginMethod
    public void pick(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );
        startActivityForResult(call, intent, "picked");
    }

    @ActivityCallback
    private void picked(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Intent data = result.getData();
        if (result.getResultCode() != Activity.RESULT_OK || data == null || data.getData() == null) {
            call.resolve(new JSObject());
            return;
        }
        Uri tree = data.getData();
        try {
            getContext().getContentResolver().takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
        } catch (SecurityException e) {
            call.reject("No lasting access to that folder");
            return;
        }
        worker.execute(() -> {
            JSObject out = new JSObject();
            out.put("uri", tree.toString());
            out.put("name", displayName(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))));
            call.resolve(out);
        });
    }

    /** Gives the folder back, when the person stops syncing. */
    @PluginMethod
    public void release(PluginCall call) {
        String uri = call.getString("uri");
        try {
            if (uri != null) getContext().getContentResolver().releasePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
        } catch (SecurityException ignored) {
            // Already gone.
        }
        call.resolve();
    }

    /**
     * The file's bytes (base64) and when it last changed, or nothing when it isn't there yet.
     * With `since`, a file unchanged since then comes back without its bytes.
     */
    @PluginMethod
    public void read(PluginCall call) {
        String uri = call.getString("uri");
        String file = call.getString("file");
        long since = call.getLong("since", -1L);
        if (uri == null || file == null) {
            call.reject("uri and file are needed");
            return;
        }
        worker.execute(() -> {
            try {
                Uri tree = Uri.parse(uri);
                Found found = find(tree, file);
                JSObject out = new JSObject();
                if (found == null) {
                    call.resolve(out);
                    return;
                }
                out.put("modified", found.modified);
                if (found.modified != 0 && found.modified == since) {
                    call.resolve(out);
                    return;
                }
                ContentResolver resolver = getContext().getContentResolver();
                try (InputStream in = resolver.openInputStream(found.uri)) {
                    if (in == null) throw new java.io.IOException("Could not open the sync file");
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        bytes.write(buffer, 0, n);
                        if (bytes.size() > MAX_FILE) throw new java.io.IOException("The sync file is too large");
                    }
                    out.put("data", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP));
                }
                call.resolve(out);
            } catch (Exception | OutOfMemoryError e) {
                call.reject(e.getMessage() == null ? "Could not read the sync folder" : e.getMessage());
            }
        });
    }

    /** Writes the file, creating it the first time, and says when it changed. */
    @PluginMethod
    public void write(PluginCall call) {
        String uri = call.getString("uri");
        String file = call.getString("file");
        String data = call.getString("data");
        if (uri == null || file == null || data == null) {
            call.reject("uri, file and data are needed");
            return;
        }
        worker.execute(() -> {
            try {
                Uri tree = Uri.parse(uri);
                ContentResolver resolver = getContext().getContentResolver();
                Found found = find(tree, file);
                Uri target = found != null
                    ? found.uri
                    : DocumentsContract.createDocument(
                        resolver,
                        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)),
                        "application/json",
                        file
                    );
                if (target == null) throw new java.io.IOException("Could not create the sync file");
                try (OutputStream out = resolver.openOutputStream(target, "wt")) {
                    if (out == null) throw new java.io.IOException("Could not write the sync file");
                    out.write(Base64.decode(data, Base64.NO_WRAP));
                }
                Found written = find(tree, file);
                JSObject result = new JSObject();
                result.put("modified", written == null ? 0 : written.modified);
                call.resolve(result);
            } catch (Exception | OutOfMemoryError e) {
                call.reject(e.getMessage() == null ? "Could not write to the sync folder" : e.getMessage());
            }
        });
    }

    private static final class Found {
        final Uri uri;
        final long modified;

        Found(Uri uri, long modified) {
            this.uri = uri;
            this.modified = modified;
        }
    }

    /** The named file directly inside the folder, or null. */
    private Found find(Uri tree, String name) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        String[] columns = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        };
        try (Cursor cursor = getContext().getContentResolver().query(children, columns, null, null, null)) {
            if (cursor == null) return null;
            while (cursor.moveToNext()) {
                if (!name.equals(cursor.getString(1))) continue;
                long modified = cursor.isNull(2) ? 0 : cursor.getLong(2);
                return new Found(DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)), modified);
            }
        }
        return null;
    }

    private String displayName(Uri document) {
        try (Cursor cursor = getContext().getContentResolver().query(
            document, new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME }, null, null, null
        )) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0);
        } catch (RuntimeException ignored) {
            // Shown without a name.
        }
        return "";
    }
}
