package no.sb1.troxy.util;

import no.sb1.troxy.record.v3.Recording;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Periodically collects statistics from Troxy and writes it to a file.
 */
public class StatisticsCollector {
    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(StatisticsCollector.class);
    /**
     * The only instance of the statistics collector.
     */
    private final StatisticsCollectorThread statisticsCollectorThread;
    /**
     * How long each sleep interval should be in milliseconds.
     * This is used to improve accuracy in case the clock is adjusted.
     * Changing timezones will not affect the statistics (it may on Windows as that modifies the system clock).
     * SLEEP_INTERVAL should not be more than the max frequency of gathering statistics, which most likely is 1 gathering per minute (60000ms).
     */
    private static final int SLEEP_INTERVAL = 30000;
    /**
     * The interval between collecting statistics, in minutes.
     */
    private int statisticsInterval;

    private final String statisticsDirectory;

    public StatisticsCollector(final int statisticsInterval, final String statisticsDirectory, final Cache cache) {
        this.statisticsInterval = statisticsInterval;
        this.statisticsDirectory = statisticsDirectory;
        this.statisticsCollectorThread = new StatisticsCollectorThread(cache);
    }

    /**
     * Get the interval between each statistics file.
     *
     * @return The interval between each statistics file.
     */
    public int getStatisticsInterval() {
        return statisticsInterval;
    }

    /**
     * Set the interval between each statistics file.
     *
     * @param statisticsInterval The interval between each statistics file.
     */
    public void setStatisticsInterval(int statisticsInterval) {
        this.statisticsInterval = statisticsInterval;
    }

    /**
     * Get the path to the statistics directory.
     *
     * @return The path to the statistics directory.
     */
    public File getStatisticsDirectory() {
        return new File(statisticsDirectory);
    }

    /**
     * Start the statistics collector.
     */
    public void startThread() {
        statisticsCollectorThread.start();
    }

    /**
     * Stop the statistics collector.
     */
    public void stopThread() {
        statisticsCollectorThread.stop();
    }

    /**
     * The thread collecting and writing statistics.
     */
    private final class StatisticsCollectorThread implements Runnable {
        /**
         * Whether the statistics thread is running.
         * Volatile prevents caching the value within a thread,
         * we want operations on this boolean to be atomic.
         */
        private volatile boolean active;
        /**
         * The thread object.
         */
        private Thread thread;
        /**
         * Lock object.
         */
        private Lock lock = new ReentrantLock();
        /**
         * Lock condition, used for sleeping thread.
         */
        private Condition condition = lock.newCondition();


        private final Cache cache;

        private StatisticsCollectorThread(Cache cache) {
            this.cache = cache;
        }

        /**
         * Start the thread.
         */
        public void start() {
            lock.lock();
            try {
                if (thread != null && thread.isAlive())
                    return;
                thread = new Thread(this);
                active = true;
                thread.start();
            } finally {
                lock.unlock();
            }
        }

        /**
         * Stop the thread.
         */
        public void stop() {
            long startTime = System.currentTimeMillis();
            lock.lock();
            try {
                if (thread == null || !thread.isAlive())
                    return;
                log.info("Stopping statistics thread");
                active = false;
                condition.signal();
            } finally {
                lock.unlock();
            }
            /* join threads */
            try {
                thread.join(30000);
            } catch (InterruptedException e) {
                System.out.println(new Date() + ": Unable to stop statistics thread gracefully after " + (System.currentTimeMillis() - startTime) + "ms, giving up");
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            List<StatisticsData> previousStatistics = new ArrayList<>();
            while (active) {
                lock.lock();
                try {
                    boolean sleep = true;
                    while (sleep && active) {
                        /* if statisticsInterval is set to 0 or less, then never write statistics */
                        long sleepTime = statisticsInterval <= 0 ? SLEEP_INTERVAL : statisticsInterval * 60000 - System.currentTimeMillis() % (statisticsInterval * 60000);
                        if (sleepTime >= SLEEP_INTERVAL)
                            sleepTime = SLEEP_INTERVAL;
                        else
                            sleep = false;
                        condition.await(sleepTime, TimeUnit.MILLISECONDS);
                    }
                    /* collect statistics */
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm");
                    File statisticsFile = new File(getStatisticsDirectory(), "troxy_stat_" + dateFormat.format(new Date()) + "_" + statisticsInterval + "_min_interval.log");
                    List<StatisticsData> currentStatistics = new ArrayList<>();
                    boolean statisticsChanged = false;
                    int index = 0;
                    for (Recording recording : cache.getRecordings()) {
                        StatisticsData sd = new StatisticsData();
                        sd.recording = recording.getFilename();
                        sd.responseCounterTotal = recording.getResponseCounterTotal();
                        sd.responseCounterCurrent = recording.getAndResetResponseCounterCurrent();
                        currentStatistics.add(sd);
                        if (index < previousStatistics.size()) {
                            StatisticsData prevSd = previousStatistics.get(index++);
                            if (sd.responseCounterTotal != prevSd.responseCounterTotal || sd.responseCounterCurrent != prevSd.responseCounterCurrent || !sd.recording.equals(prevSd.recording))
                                statisticsChanged = true;
                        }
                    }
                    previousStatistics = currentStatistics;
                    if (statisticsChanged || index != currentStatistics.size()) {
                        // statistics changed, write new file
                        log.info("Writing statistics to {}", statisticsFile);
                        try (FileWriter fileWriter = new FileWriter(statisticsFile)) {
                            fileWriter.write("# Filename, Total response count since loaded, Response count since last statistics" + System.lineSeparator());
                            for (StatisticsData sd : currentStatistics)
                                fileWriter.write(sd.recording + ", " + sd.responseCounterTotal + ", " + sd.responseCounterCurrent + System.lineSeparator());
                        } catch (IOException e) {
                            log.warn("Unable to write statistics to file", e);
                        }
                    } else {
                        log.info("Not writing statistics, no activity last interval", statisticsFile);
                    }
                } catch (Exception e) {
                    log.warn("Statistics thread received unexpected exception", e);
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    private static class StatisticsData {
        String recording;
        int responseCounterTotal;
        int responseCounterCurrent;
    }
}
