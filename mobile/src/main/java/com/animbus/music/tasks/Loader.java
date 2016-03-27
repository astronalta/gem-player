/*
 * Copyright 2016 Substance Mobile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.animbus.music.tasks;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.WorkerThread;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public abstract class Loader<Return> {
    protected Context context;

    public Loader(Context context, Object... params) {
        this.context = context.getApplicationContext();
        this.runParams = params;
        mObserver = getObserver();
    }

    protected abstract ContentObserver getObserver();

    protected abstract Uri getUri();

    protected abstract List<Return> doLoad(Object... params);

    ///////////////////////////////////////////////////////////////////////////
    // Underlying AsyncTask
    ///////////////////////////////////////////////////////////////////////////

    private LoadTask mTask;

    class LoadTask extends AsyncTask<Object, Return, List<Return>> {
        private boolean isExecuting = false;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            unregisterMediaStoreListener();
        }

        @Override
        protected List<Return> doInBackground(Object... params) {
            isExecuting = true;
            try {
                return doLoad(params);
            } finally {
                isExecuting = false;
            }
        }

        @SafeVarargs
        @WorkerThread
        public final void oneLoaded(Return... progress) {
            publishProgress(progress);
        }

        @SafeVarargs
        @Override
        protected final void onProgressUpdate(Return... values) {
            super.onProgressUpdate(values);
            for (Return val : values) mVerifyListener.onOneLoaded(val);
        }

        @Override
        protected void onPostExecute(List<Return> result) {
            super.onPostExecute(result);
            mVerifyListener.onCompleted(result);
            registerMediaStoreListener();
            if (mUpdateQueued) update(result);
        }

        public boolean isExecuting() {
            return isExecuting;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Run
    ///////////////////////////////////////////////////////////////////////////

    protected Object[] runParams;

    public void run() {
        if (mTask.getStatus() == AsyncTask.Status.FINISHED) mTask = new LoadTask();
        mTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, runParams);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Update
    ///////////////////////////////////////////////////////////////////////////

    //Currently loaded data. When calling update() this will be set, then emptied
    private List<Return> currentData = new ArrayList<>();
    private TaskListener<Return> mVerifyListener = new TaskListener<Return>() {
        @Override
        public void onOneLoaded(Return item) {
            if (!currentData.contains(item)) for (TaskListener<Return> listener : mListeners) listener.onOneLoaded(item);
        }

        @Override
        public void onCompleted(List<Return> result) {
            if (currentData != result) for (TaskListener<Return> listener : mListeners) listener.onCompleted(result);
            currentData.clear();
        }
    };
    private boolean mUpdateQueued = false;
    protected final ContentObserver mObserver;

    public void update(final List<Return> currentData) {
        if (mTask != null && !mTask.isExecuting() && currentData != null) {
            mUpdateQueued = false;
            this.currentData = currentData;
            run();
        } else {
            Log.e(getClass().getSimpleName(), "Update: FAILED");
            mUpdateQueued = true;
        }
    }

    public void registerMediaStoreListener() {
        getContext().getContentResolver().registerContentObserver(getUri(), true, mObserver);
    }

    public void unregisterMediaStoreListener() {
        getContext().getContentResolver().unregisterContentObserver(mObserver);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////

    /**
     * The listener for {@link Loader} events
     * @param <Return> What type of variable should be passed to the listener. When extending {@link Loader}, you will specify what this should be
     */
    public interface TaskListener<Return> {
        void onOneLoaded(Return item);

        void onCompleted(List<Return> result);
    }

    protected List<TaskListener<Return>> mListeners = new ArrayList<>();

    public void addListener(TaskListener<Return> listener) {
        mListeners.add(listener);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Misc.
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Wrapper method around {@link AsyncTask#publishProgress(Object[])}
     * @param progress What to pass to the AsyncTask
     */
    @WorkerThread
    public void notifyOneLoaded(Return progress){
        mTask.oneLoaded(progress);
    }

    /**
     * @return Application congress taken from provided congress
     */
    public Context getContext() {
        return context;
    }
}
