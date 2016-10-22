package org.netcrusher.core.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class LoggingFilter implements TransformFilter {

    private final Logger logger;

    private final LoggingFilterLevel level;

    private final String[] hex;

    public LoggingFilter(String loggerSuffix, LoggingFilterLevel level) {
        String loggerName = this.getClass().getCanonicalName();
        if (loggerSuffix != null) {
            loggerName = loggerName + "." + loggerSuffix;
        }

        this.hex = new String[256];
        for (int i = 0; i < 256; i++) {
            this.hex[i] = String.format("%02x", i);
        }

        this.logger = LoggerFactory.getLogger(loggerName);
        this.level = level;
    }

    @Override
    public void transform(InetSocketAddress clientAddress, ByteBuffer bb) {
        if (isLogEnabled()) {
            int size = bb.remaining();
            if (size > 0) {
                StringBuilder sb = new StringBuilder(size * 2);

                if (bb.hasArray()) {
                    final byte[] bytes = bb.array();

                    final int offset = bb.arrayOffset() + bb.position();
                    final int limit = bb.arrayOffset() + bb.limit();

                    for (int i = offset; i < limit; i++) {
                        int b = 0x0000_0000_0000_00FF & (int) bytes[i];
                        sb.append(hex[b]);
                    }
                } else {
                    for (int i = bb.position(); i < bb.limit(); i++) {
                        int b = 0x0000_0000_0000_00FF & (int) bb.get(i);
                        sb.append(hex[b]);
                    }
                }

                log(clientAddress, size, sb);
            } else {
                log(clientAddress, size, "");
            }
        }
    }

    private boolean isLogEnabled() {
        switch (level) {
            case TRACE:
                return logger.isTraceEnabled();
            case DEBUG:
                return logger.isDebugEnabled();
            case INFO:
                return logger.isInfoEnabled();
            case WARN:
                return logger.isWarnEnabled();
            case ERROR:
                return logger.isErrorEnabled();
            default:
                return false;
        }
    }

    private void log(InetSocketAddress clientAddress, int size, CharSequence data) {
        Object[] params = { clientAddress, size, data };

        switch (level) {
            case TRACE:
                logger.trace("<{}> ({}): {}", params);
                break;
            case DEBUG:
                logger.debug("<{}> ({}): {}", params);
                break;
            case INFO:
                logger.info("<{}> ({}): {}", params);
                break;
            case WARN:
                logger.warn("<{}> ({}): {}", params);
                break;
            case ERROR:
                logger.error("<{}> ({}): {}", params);
                break;
        }
    }
}
