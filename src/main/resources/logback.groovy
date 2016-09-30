import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.FileAppender
import org.slf4j.bridge.SLF4JBridgeHandler

// statusListener(OnConsoleStatusListener)

SLF4JBridgeHandler.removeHandlersForRootLogger()
SLF4JBridgeHandler.install()

def DATED_PATTERN = '%date{yyyy-MM-dd HH:mm:ss} %-5level %-27thread %logger{22} - %m%n'
def BRIEF_PATTERN = '%m%n'

// Define console logging
appender('CONSOLE', ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = DATED_PATTERN
    }
}

appender('FILE', FileAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = DATED_PATTERN
    }
    file = 'console.log'
    append = false
}

appender('TRANSACTION', FileAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = BRIEF_PATTERN
    }
    file = 'transaction.log'
    append = false
}

// Enable appenders
root(WARN, ['CONSOLE','FILE'])

// Standalone log for transaction logging
logger('TRANSACTION', DEBUG, ['TRANSACTION'], false)

// Override log levels
logger('com.akonizo', DEBUG)
logger('com.orientechnologies', DEBUG)
logger('tinkerpop', DEBUG)
