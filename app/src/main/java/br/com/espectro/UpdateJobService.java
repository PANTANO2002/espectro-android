package br.com.espectro;

import android.app.job.*;

/** The OS chooses the actual execution time; no exact-alarm or always-on service. */
public final class UpdateJobService extends JobService {
    private JobParameters active;
    @Override public boolean onStartJob(JobParameters params) {
        if (!GithubUpdater.automatic(this)) return false;
        active = params;
        GithubUpdater.check(this, false, message -> new android.os.Handler(getMainLooper()).post(() -> {
            if (active == params) { active = null; jobFinished(params, false); }
        }));
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) { active = null; return true; }
}
