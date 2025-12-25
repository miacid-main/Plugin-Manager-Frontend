package com.chatcleaner.utils;

import com.chatcleaner.ChatCleaner;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class ConsoleLogger extends Handler {

    private final ChatCleaner plugin;

    public ConsoleLogger(ChatCleaner plugin) {
        this.plugin = plugin;
    }

    @Override
    public void publish(LogRecord record) {
        if (plugin.getServiceLink() != null) {
            String message = "[" + record.getLevel() + "] " + record.getMessage();
            plugin.getServiceLink().logConsole(message);
        }
    }

    @Override
    public void flush() {}

    @Override
    public void close() throws SecurityException {}
}
