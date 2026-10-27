package com.gathin.tvkeys;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Setting the jobs, from a computer (the receiver needs DUMP, which adb's shell has and apps can't get); the answer
 * comes back in am's "data=" line:
 * <pre>
 * adb shell am broadcast -n com.gathin.tvkeys/.Setup -a com.gathin.tvkeys.LEARN    # next minute: presses are logged, not acted on
 * adb shell am broadcast -n com.gathin.tvkeys/.Setup -a com.gathin.tvkeys.SET --ei code 1234 --es job app:com.fancode.tv
 * adb shell am broadcast -n com.gathin.tvkeys/.Setup -a com.gathin.tvkeys.SET --ei code 1234     # no job: back to normal
 * adb shell am broadcast -n com.gathin.tvkeys/.Setup -a com.gathin.tvkeys.LIST     # the jobs, and what LEARN saw
 * </pre>
 */
public class Setup extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent i) {
        String a = String.valueOf(i.getAction());
        String out;
        if (a.endsWith(".LEARN")) {
            KeyService.learn();
            out = "learning for 60 s: press the buttons, then LIST";
        } else if (a.endsWith(".SET")) {
            int code = i.getIntExtra("code", -1);
            String job = i.getStringExtra("job");
            if (code < 0 || !Jobs.learnable(code)) {
                out = "not changed: code " + code + " can't be given a job";
            } else if (job == null) {
                KeyService.prefs(c).edit().remove("k" + code).apply();
                out = code + ": back to normal";
            } else if (!Jobs.valid(job)) {
                out = "not changed: job must be app:<package>, input:<input id> or none";
            } else {
                KeyService.prefs(c).edit().putString("k" + code, job).apply();
                out = code + " → " + job;
            }
            KeyService.load(c);
        } else if (a.endsWith(".LIST")) {
            out = "jobs " + KeyService.prefs(c).getAll() + "; learned [" + KeyService.learned() + "]";
        } else {
            out = "unknown action " + a;
        }
        Log.i(KeyService.TAG, out);
        if (isOrderedBroadcast()) setResultData(out);
    }
}
