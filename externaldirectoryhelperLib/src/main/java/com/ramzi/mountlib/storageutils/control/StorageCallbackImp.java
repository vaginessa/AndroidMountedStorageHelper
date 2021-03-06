/*** The MIT License (MIT)

 Copyright (c) 2016 Ramesh M Nair

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 ***/

package com.ramzi.mountlib.storageutils.control;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.support.v4.os.EnvironmentCompat;
import android.text.TextUtils;
import android.util.Log;

import com.ramzi.mountlib.storageutils.AsyncResult;
import com.ramzi.mountlib.storageutils.DocumentsContract;
import com.ramzi.mountlib.storageutils.StorageUtils;
import com.ramzi.mountlib.storageutils.StorageVolume;
import com.ramzi.mountlib.storageutils.model.RootInfo;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class StorageCallbackImp implements StorageCallback {

    public static final String ROOT_ID_PRIMARY_EMULATED = "primary";
    public static final String ROOT_ID_SECONDARY = "secondary";
    private ArrayList<RootInfo> mRoots=new ArrayList<>();
    private HashMap<String, RootInfo> mIdToRoot=new HashMap<>();
    private HashMap<String, File> mIdToPath=new HashMap<>();
    private final Object mRootsLock = new Object();

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void forceCheckStorage(final OnFinishChecking mStorageInfo, final Context mContext) {
        new AsyncTask<String, Void, AsyncResult<String>>() {
            @Override
            protected AsyncResult<String> doInBackground(String... strings) {
                loadStorage(mContext);
                return null;
            }

            @Override
            protected void onPostExecute(AsyncResult<String> stringAsyncResult) {
                super.onPostExecute(stringAsyncResult);
                Log.d("post","execteeeeeee");
                mStorageInfo.getStorageInfo(mRoots,mIdToRoot,mIdToPath);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "");
//        loadStorage(mContext);

//        mStorageInfo.getStorageInfo();

    }

    int count = 0;

    public void loadStorage(Context mContext)
    {
        StorageUtils storageUtils = new StorageUtils(mContext);

        for (StorageVolume volume : storageUtils.getStorageMounts()) {
            final File path = volume.getPathFile();
             String rootId="";
             String title="";
            if (volume.isPrimary && volume.isEmulated) {
                rootId = ROOT_ID_PRIMARY_EMULATED;
                title = "Internal Storage";
            } else if (volume.getUuid() != null) {
                rootId = ROOT_ID_SECONDARY + volume.getLabel();
                String label = volume.getLabel();
                title = !TextUtils.isEmpty(label) ? label
                        : "External Storage"
                        + (count > 0 ? " " + count : "");
                count++;
            } else {
                Log.d("TAG", "Missing UUID for " + volume.getPath() + "; skipping");
            }

            if (mIdToPath.containsKey(rootId)) {
                Log.w("TAG", "Duplicate UUID " + rootId + "; skipping");
            }

            try {
                Log.w("TAG", "Duplicate UUID " + rootId + "; skipping");

                mIdToPath.put(rootId, path);
                final RootInfo root = new RootInfo();

                root.rootId = rootId;
                root.flags = DocumentsContract.Root.FLAG_SUPPORTS_CREATE | DocumentsContract.Root.FLAG_SUPPORTS_EDIT | DocumentsContract.Root.FLAG_LOCAL_ONLY | DocumentsContract.Root.FLAG_ADVANCED
                        | DocumentsContract.Root.FLAG_SUPPORTS_SEARCH | DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD;
                root.title = title;
                root.docId = getDocIdForFile(path);
                root.path = path.getPath();
                mRoots.add(root);
                mIdToRoot.put(rootId, root);
                Log.d("TITLE------------",title);
                Log.d("PATH------------",getDocIdForFile(path));
                Log.d("ROOT_ID------------",rootId);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                throw new IllegalStateException(e);
            }


        }

    }


    private String getDocIdForFile(File file) throws FileNotFoundException {
        String path = file.getAbsolutePath();

        // Find the most-specific root path
        Map.Entry<String, File> mostSpecific = null;
        synchronized (mRootsLock) {
            for (Map.Entry<String, File> root : mIdToPath.entrySet()) {
                final String rootPath = root.getValue().getPath();
                if (path.startsWith(rootPath) && (mostSpecific == null
                        || rootPath.length() > mostSpecific.getValue().getPath().length())) {
                    mostSpecific = root;
                }
            }
        }

        if (mostSpecific == null) {
            throw new FileNotFoundException("Failed to find root that contains " + path);
        }

        // Start at first char of path under root
        final String rootPath = mostSpecific.getValue().getPath();
        if (rootPath.equals(path)) {
            path = "";
        } else if (rootPath.endsWith("/")) {
            path = path.substring(rootPath.length());
        } else {
            path = path.substring(rootPath.length() + 1);
        }

        return mostSpecific.getKey() + ':' + path;
    }
}
