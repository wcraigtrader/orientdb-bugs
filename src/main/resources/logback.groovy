import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.FileAppender
import org.slf4j.bridge.SLF4JBridgeHandler

// statusListener(OnConsoleStatusListener)

SLF4JBridgeHandler.removeHandlersForRootLogger()
SLF4JBridgeHandler.install()

def PATTERN = '%date{yyyy-MM-dd HH:mm:ss} %-5level %-25thread %logger{22} - %m%n'

// Define console logging
appender('CONSOLE', ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = PATTERN
    }
}

appender('FILE', FileAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = PATTERN
    }
    file = 'performance.log'
    append = true
}

// Enable appenders
root(WARN, ['CONSOLE','FILE'])

// Override log levels
logger('com.akonizo', INFO)
logger('com.orientechnologies', DEBUG)
logger('tinkerpop', DEBUG)
