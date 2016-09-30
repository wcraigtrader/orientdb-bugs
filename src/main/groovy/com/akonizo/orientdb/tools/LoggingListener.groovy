package com.akonizo.orientdb.tools

import com.orientechnologies.orient.core.command.OCommandOutputListener
import groovy.util.logging.Slf4j

@Slf4j
class LoggingListener implements OCommandOutputListener {
    def buffer = new StringBuilder()

    @Override
    void onMessage(String message) {
        if (message.startsWith('\n- ')) {
            buffer.append(message.substring(3))
        } else {
            buffer.append(message)
            log.debug buffer.toString()
            buffer.setLength(0)
        }
    }
}
