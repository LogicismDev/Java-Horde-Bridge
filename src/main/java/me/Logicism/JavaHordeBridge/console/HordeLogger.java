package me.Logicism.JavaHordeBridge.console;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HordeLogger {

    private static Logger logger;

    public HordeLogger(Class<?> clazz) {
        logger = LogManager.getLogger(clazz);

        LoggingOutputStream.redirectSysOutAndSysErr(logger);
    }

    public void trace(String msg) {
        logger.trace(msg);
    }

    public void debug(String msg) {
        logger.debug(msg);
    }

    public void info(String msg) {
        logger.info(msg);
    }

    public void warn(String msg) {
        logger.warn(msg);
    }

    public void error(String msg) {
        logger.error(msg);
    }
}
