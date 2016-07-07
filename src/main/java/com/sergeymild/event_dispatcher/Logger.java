package com.sergeymild.event_dispatcher;

/**
 * Created by sergeyMild on 07/07/16.
 */
class Logger {
    static boolean isLoggerEnabled = false;

    public static void log(String message) {
        if (!isLoggerEnabled) return;
        System.out.println(String.format("EventDispatcher: %s", message));
    }
}
