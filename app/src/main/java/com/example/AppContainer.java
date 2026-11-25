package com.example;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.data.local.AppDatabase;
import com.example.data.repository.SpendingRepository;
import com.example.ml.FakeMirageRepository;
import com.example.ml.MirageOcrEngine;
import com.example.ml.MirageRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Clean Service Locator wiring offline Room persistence and the on-device Mirage OCR engine.
 */
public final class AppContainer {

    @NonNull
    public final ExecutorService ioExecutor;
    @NonNull
    public final AppDatabase database;
    @NonNull
    public final SpendingRepository spendingRepository;
    @NonNull
    public final MirageRepository mirageRepository;

    public AppContainer(@NonNull Context appContext) {
        this.ioExecutor = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "ledgerflow-io");
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });
        this.database = AppDatabase.getInstance(appContext, ioExecutor);
        this.spendingRepository = new SpendingRepository(database.spendingDao(), ioExecutor);
        FakeMirageRepository fallback = new FakeMirageRepository();
        this.mirageRepository = new MirageOcrEngine(appContext, fallback);
    }

    public void onLowMemoryOrTrim() {
        mirageRepository.releaseResources();
    }
}
