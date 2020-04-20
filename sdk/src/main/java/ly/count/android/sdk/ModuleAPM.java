package ly.count.android.sdk;

import android.app.Activity;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class ModuleAPM extends ModuleBase {

    Apm apmInterface = null;

    Map<String, Long> codeTraces;

    Map<String, Long> networkTraces;

    //used to determine app start time
    boolean hasFirstOnResumeHappened = false;

    long firstOnResumeTimeMs = -1;// used for determining app start duration
    long lastScreenSwitchTime = -1;// timestamp of when the app last changed from foreground to background

    int activitiesOpen = -1;

    ModuleAPM(Countly cly, CountlyConfig config) {
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleAPM] Initialising");
        }

        codeTraces = new HashMap<>();
        networkTraces = new HashMap<>();

        activitiesOpen = 0;

        apmInterface = new Apm();
    }

    void startTraceInternal(String traceKey) {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleAPM] Calling 'startTraceInternal' with key:[" + traceKey + "]");
        }

        if (traceKey == null || traceKey.isEmpty()) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleAPM] Provided a invalid trace key");
            }
            return;
        }

        Long currentTimestamp = UtilsTime.currentTimestampMs();
        codeTraces.put(traceKey, currentTimestamp);
    }

    void endTraceInternal(String traceKey) {
        //end time counting as fast as possible
        Long currentTimestamp = UtilsTime.currentTimestampMs();

        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleAPM] Calling 'endTraceInternal' with key:[" + traceKey + "]");
        }

        if (traceKey == null || traceKey.isEmpty()) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleAPM] Provided a invalid trace key");
            }
            return;
        }

        if(codeTraces.containsKey(traceKey)) {
            Long startTimestamp = codeTraces.remove(traceKey);

            if(startTimestamp == null){
                if (_cly.isLoggingEnabled()) {
                    Log.e(Countly.TAG, "[ModuleAPM] endTraceInternal, retrieved 'startTimestamp' is null");
                }
            } else {
                Long durationMs = currentTimestamp - startTimestamp;

                _cly.connectionQueue_.sendAPMCustomTrace(traceKey, durationMs, startTimestamp, currentTimestamp);
            }
        } else {
            if (_cly.isLoggingEnabled()) {
                Log.w(Countly.TAG, "[ModuleAPM] endTraceInternal, trying to end trace which was not started");
            }
        }
    }

    /**
     * Begin the tracking of a network request
     * @param networkTraceKey key that identifies the network trace
     * @param uniqueId this is important in cases where multiple requests in parallel are done
     *                 for the same trace. This helps to distinguish them
     */
    void startNetworkRequestInternal(String networkTraceKey, String uniqueId) {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleAPM] Calling 'startNetworkRequestInternal' with key:[" + networkTraceKey + "]");
        }

        if (networkTraceKey == null || networkTraceKey.isEmpty()) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleAPM] Provided a invalid trace key");
            }
            return;
        }

        if (uniqueId == null || uniqueId.isEmpty()) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleAPM] Provided a invalid uniqueId");
            }
            return;
        }

        String internalTraceKey = networkTraceKey + "|" + uniqueId;
        Long currentTimestamp = UtilsTime.currentTimestampMs();
        networkTraces.put(internalTraceKey, currentTimestamp);
    }

    /**
     * Mark that a network request has ended
     * @param networkTraceKey key that identifies the network trace
     * @param uniqueId this is important in cases where multiple requests in parallel are done
     *                 for the same trace. This helps to distinguish them.
     * @param responseCode returned response code
     * @param requestPayloadSize sent request payload size in bytes
     * @param responsePayloadSize received response payload size in bytes
     */
    void endNetworkRequestInternal(String networkTraceKey, String uniqueId, int responseCode, int requestPayloadSize, int responsePayloadSize) {
        //end time counting as fast as possible
        Long currentTimestamp = UtilsTime.currentTimestampMs();

        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleAPM] Calling 'endNetworkRequestInternal' with key:[" + networkTraceKey + "]");
        }

        if (networkTraceKey == null || networkTraceKey.isEmpty()) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleAPM] Provided a invalid trace key");
            }
            return;
        }

        if (uniqueId == null || uniqueId.isEmpty()) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleAPM] Provided a invalid uniqueId");
            }
            return;
        }

        if(!(responseCode >= 100 && responseCode < 600)) {
            if (_cly.isLoggingEnabled()) {
                Log.w(Countly.TAG, "[ModuleAPM] Invalid response code was provided");
            }
            responseCode = -1;
        }

        if(requestPayloadSize < 0) {
            if (_cly.isLoggingEnabled()) {
                Log.w(Countly.TAG, "[ModuleAPM] Invalid request payload size was provided");
            }
        }

        if(responsePayloadSize < 0) {
            if (_cly.isLoggingEnabled()) {
                Log.w(Countly.TAG, "[ModuleAPM] Invalid response payload size was provided");
            }
        }

        String internalTraceKey = networkTraceKey + "|" + uniqueId;

        if(networkTraces.containsKey(internalTraceKey)) {
            Long startTimestamp = networkTraces.remove(internalTraceKey);

            if(startTimestamp == null){
                if (_cly.isLoggingEnabled()) {
                    Log.e(Countly.TAG, "[ModuleAPM] endNetworkRequestInternal, retrieved 'startTimestamp' is null");
                }
            } else {
                Long responseTimeMs = currentTimestamp - startTimestamp;

                _cly.connectionQueue_.sendAPMNetworkTrace(networkTraceKey, responseTimeMs, responseCode, requestPayloadSize, responsePayloadSize, startTimestamp, currentTimestamp);
            }
        } else {
            if (_cly.isLoggingEnabled()) {
                Log.w(Countly.TAG, "[ModuleAPM] endNetworkRequestInternal, trying to end trace which was not started");
            }
        }
    }

    void recordAppStart() {
        if(_cly.config_.recordAppStartTime) {
            if(Countly.applicationStart == -1){
                if (_cly.isLoggingEnabled()) {
                    Log.w(Countly.TAG, "[ModuleAPM] Application onCreate start time is not recorded. Don't forget to call 'applicationOnCreate'");
                    return;
                }
            }

            long durationMs = firstOnResumeTimeMs - Countly.applicationStart;

            _cly.connectionQueue_.sendAPMAppStart(durationMs, Countly.applicationStart, firstOnResumeTimeMs);
        }
    }

    void calculateAppRunningTimes(int previousCount, int newCount) {
        boolean goingToBackground = (previousCount == 1 && newCount == 0);
        boolean goingToForeground = (previousCount == 0 && newCount == 1);

        if(goingToBackground || goingToForeground) {
            long currentTimeMs = UtilsTime.currentTimestampMs();

            if(lastScreenSwitchTime != -1) {
                // if it was '-1' then it just started, todo might be a issue with halt where it is only reset on first screen change
                long durationMs = currentTimeMs - lastScreenSwitchTime;

                if (goingToForeground) {
                    // coming from a background mode to the foreground
                    _cly.connectionQueue_.sendAPMScreenTime(false, durationMs, lastScreenSwitchTime, currentTimeMs);
                } else if (goingToBackground) {
                    // going form the foreground to the background
                    _cly.connectionQueue_.sendAPMScreenTime(true, durationMs, lastScreenSwitchTime, currentTimeMs);
                }
            }

            lastScreenSwitchTime = currentTimeMs;
        } else {
            // changing screens normally
        }
    }

    @Override
    public void halt() {
        codeTraces = null;
        networkTraces = null;
    }

    /**
     * used for background / foreground time recording
     * @param activity
     */
    @Override
    public void callbackOnActivityResumed(Activity activity) {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[Apm] Calling 'callbackOnActivityResumed', [" + activitiesOpen + "] -> [" + (activitiesOpen + 1) + "]");
        }

        calculateAppRunningTimes(activitiesOpen, activitiesOpen + 1);
        activitiesOpen++;

        if(!hasFirstOnResumeHappened) {
            hasFirstOnResumeHappened = true;

            firstOnResumeTimeMs = UtilsTime.currentTimestampMs();

            recordAppStart();
        }
    }

    /**
     * used for background / foreground time recording
     * @param activity
     */
    @Override
    void callbackOnActivityStopped(Activity activity) {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[Apm] Calling 'callbackOnActivityStopped', [" + activitiesOpen + "] -> [" + (activitiesOpen - 1) + "]");
        }

        calculateAppRunningTimes(activitiesOpen, activitiesOpen - 1);
        activitiesOpen--;
    }

    public class Apm {
        /**
         * Start a trace of a action you want to track
         * @param traceKey key by which this action is identified
         */
        public void startTrace(String traceKey) {
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Apm] Calling 'startTrace' with key:[" + traceKey + "]");
            }

            startTraceInternal(traceKey);
        }

        /**
         * End a trace of a action you want to track
         * @param traceKey key by which this action is identified
         */
        public void endTrace(String traceKey) {
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Apm] Calling 'endTrace' with key:[" + traceKey + "]");
            }

            endTraceInternal(traceKey);
        }

        /**
         * Begin the tracking of a network request
         * @param networkTraceKey key that identifies the network trace
         * @param uniqueId this is important in cases where multiple requests in parallel are done
         *                 for the same trace. This helps to distinguish them.
         */
        public void startNetworkRequest(String networkTraceKey, String uniqueId) {
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Apm] Calling 'startNetworkRequest' with key:[" + networkTraceKey + "], uniqueID:[" + uniqueId + "]");
            }

            startNetworkRequestInternal(networkTraceKey, uniqueId);
        }

        /**
         * Mark that a network request has ended
         * @param networkTraceKey key that identifies the network trace
         * @param uniqueId this is important in cases where multiple requests in parallel are done
         *                 for the same trace. This helps to distinguish them.
         * @param responseCode returned response code
         * @param requestPayloadSize sent request payload size in bytes
         * @param responsePayloadSize received response payload size in bytes
         */
        public void endNetworkRequest(String networkTraceKey, String uniqueId, int responseCode, int requestPayloadSize, int responsePayloadSize) {
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Apm] Calling 'endNetworkRequest' with key:[" + networkTraceKey + "], uniqueID:[" + uniqueId + "]");
            }

            endNetworkRequestInternal(networkTraceKey, uniqueId, responseCode, requestPayloadSize, responsePayloadSize);
        }
    }
}
