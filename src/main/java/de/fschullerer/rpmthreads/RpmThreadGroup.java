package de.fschullerer.rpmthreads;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.engine.TreeCloner;
import org.apache.jmeter.threads.AbstractThreadGroup;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterThread;
import org.apache.jmeter.threads.ListenerNotifier;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.ListedHashTree;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;
import org.apache.log.Priority;

/**
 * ThreadGroup class.
 */
public class RpmThreadGroup extends AbstractThreadGroup {

    /** serialVersionUID */
    private static final long serialVersionUID = 1L;

    /**
     * RPM list
     */
    public static final String RPM_LIST = "ThreadGroup.rpm_list";

    private static final long WAIT_TO_DIE = JMeterUtils.getPropDefault("jmeterengine.threadstop.wait",
            5 * 1000); // 5
    // seconds

    private static final Logger log = LoggingManager.getLoggerForClass();

    /**
     * Is test (still) running?
     */
    private volatile boolean running = false;

    // List of active threads
    private final Map<JMeterThread, Thread> allThreads = new ConcurrentHashMap<>();

    private transient Thread threadStarter;

    /**
     * No-arg constructor.
     */
    public RpmThreadGroup() {
        // Non args constructor.
    }

    /**
     * Starts Threads using ramp up
     */
    class ThreadStarter implements Runnable {

        private final int groupCount;
        private final ListenerNotifier notifier;
        private final ListedHashTree threadGroupTree;
        private final StandardJMeterEngine engine;
        private final JMeterContext context;

        public ThreadStarter(int groupCount, ListenerNotifier notifier, ListedHashTree threadGroupTree,
                             StandardJMeterEngine engine) {
            super();
            this.groupCount = groupCount;
            this.notifier = notifier;
            this.threadGroupTree = threadGroupTree;
            this.engine = engine;
            // Store context from Root Thread to pass it to created threads
            this.context = JMeterContextService.getContext();

        }

        @Override
        public void run() {
            // Copy in ThreadStarter thread context from calling Thread
            JMeterContextService.getContext().setVariables(this.context.getVariables());

            List<Long> waitTimesList = createWaitTimesList(getRPMlist());

            List<Long> absoluteTimeWaitList = new ArrayList<Long>();
            long start = getSystemTimer();
            Long delayBefore = (long) 0;
            int threadNum = 0;
            for (Long delay : waitTimesList) {
                absoluteTimeWaitList.add(start + delay + delayBefore);
                delayBefore = delay + delayBefore;
            }

            for (Long absoluteDelay : absoluteTimeWaitList) {
                while (running && absoluteDelay > getSystemTimer()) {
                    threadsleep(3);
                }
                JMeterThread jmThread = makeThread(groupCount, notifier, threadGroupTree, engine, threadNum, context);

                Thread newThread = new Thread(jmThread, jmThread.getThreadName());
                newThread.setDaemon(false); // ThreadStarter is daemon, but we don't want sampler threads to be so too
                registerStartedThread(jmThread, newThread);
                newThread.start();
                threadNum++;
            }


        }
    }

    @Override
    public void start(int groupCount, ListenerNotifier notifier, ListedHashTree threadGroupTree,
                      StandardJMeterEngine engine) {
        running = true;
        threadStarter = new Thread(new ThreadStarter(groupCount, notifier, threadGroupTree, engine),
                getName() + "-ThreadStarter");
        threadStarter.setDaemon(true);
        threadStarter.start();
    }



    /**
     * Sleep
     *
     * @param time Time in milliseconds.
     */
    public static void threadsleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            log.log(Priority.DEBUG, "Thread sleep was interrupted");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get rpm list.
     *
     * @return the rpm list.
     */
    public String getRPMlist() {
        return this.getPropertyAsString(RPM_LIST);
    }

    private ListedHashTree cloneTree(ListedHashTree tree) {
        TreeCloner cloner = new TreeCloner(true);
        tree.traverse(cloner);
        return cloner.getClonedTree();
    }

    /**
     * Register Thread when it starts
     *
     * @param jMeterThread {@link JMeterThread}
     * @param newThread    Thread
     */
    private void registerStartedThread(JMeterThread jMeterThread, Thread newThread) {
        allThreads.put(jMeterThread, newThread);
    }

    private JMeterThread makeThread(int groupCount, ListenerNotifier notifier, ListedHashTree threadGroupTree,
                                    StandardJMeterEngine engine, int i, JMeterContext context) {
        boolean onErrorStopTest = getOnErrorStopTest();
        boolean onErrorStopTestNow = getOnErrorStopTestNow();
        boolean onErrorStopThread = getOnErrorStopThread();
        boolean onErrorStartNextLoop = getOnErrorStartNextLoop();
        String groupName = getName();
        final JMeterThread jmeterThread = new JMeterThread(cloneTree(threadGroupTree), this, notifier);
        jmeterThread.setThreadNum(i);
        jmeterThread.setThreadGroup(this);
        jmeterThread.setInitialContext(context);
        final String threadName = groupName + " " + (groupCount) + "-" + (i + 1);
        jmeterThread.setThreadName(threadName);
        jmeterThread.setEngine(engine);
        jmeterThread.setOnErrorStopTest(onErrorStopTest);
        jmeterThread.setOnErrorStopTestNow(onErrorStopTestNow);
        jmeterThread.setOnErrorStopThread(onErrorStopThread);
        jmeterThread.setOnErrorStartNextLoop(onErrorStartNextLoop);
        return jmeterThread;
    }

    /**
     * Stop thread called threadName:
     * <ol>
     * <li>stop JMeter thread</li>
     * <li>interrupt JMeter thread</li>
     * <li>interrupt underlying thread</li>
     * </ol>
     *
     * @param threadName String thread name
     * @param now        boolean for stop
     * @return true if thread stopped
     */
    @Override
    public boolean stopThread(String threadName, boolean now) {
        for (Entry<JMeterThread, Thread> entry : allThreads.entrySet()) {
            JMeterThread thrd = entry.getKey();
            if (thrd.getThreadName().equals(threadName)) {
                stopThread(thrd, entry.getValue(), now);
                return true;
            }
        }
        return false;
    }

    /**
     * @param thrd      JMeterThread
     * @param t         Thread
     * @param interrupt Interrup thread or not
     */
    private void stopThread(JMeterThread thrd, Thread t, boolean interrupt) {
        thrd.stop();
        thrd.interrupt(); // interrupt sampler if possible
        if (t != null && interrupt) {
            t.interrupt(); // also interrupt JVM thread
        }
    }

    /**
     * Called by JMeterThread when it finishes
     */
    @Override
    public void threadFinished(JMeterThread thread) {
        log.debug("Ending thread " + thread.getThreadName());
        allThreads.remove(thread);
    }

    /**
     * For each thread, invoke:
     * <ul>
     * <li>{@link JMeterThread#stop()} - set stop flag</li>
     * <li>{@link JMeterThread#interrupt()} - interrupt sampler</li>
     * <li>{@link Thread#interrupt()} - interrupt JVM thread</li>
     * </ul>
     */
    @Override
    public void tellThreadsToStop() {
        running = false;
        try {
            threadStarter.interrupt();
        } catch (Exception e) {
            log.warn("Exception occured interrupting ThreadStarter");
        }
        for (Entry<JMeterThread, Thread> entry : allThreads.entrySet()) {
            stopThread(entry.getKey(), entry.getValue(), true);
        }
    }

    /**
     * For each thread, invoke:
     * <ul>
     * <li>{@link JMeterThread#stop()} - set stop flag</li>
     * </ul>
     */
    @Override
    public void stop() {
        running = false;
        try {
            threadStarter.interrupt();
        } catch (Exception e) {
            log.warn("Exception occured interrupting ThreadStarter");
        }
        for (JMeterThread item : allThreads.keySet()) {
            item.stop();
        }
    }

    /**
     * @return number of active threads
     */
    @Override
    public int numberOfActiveThreads() {
        return allThreads.size();
    }

    /**
     * @return boolean true if all threads stopped
     */
    @Override
    public boolean verifyThreadsStopped() {
        boolean stoppedAll = true;
        stoppedAll = verifyThreadStopped(threadStarter);
        for (Thread t : allThreads.values()) {
            stoppedAll = stoppedAll && verifyThreadStopped(t);
        }
        return stoppedAll;
    }

    /**
     * Verify thread stopped and return true if stopped successfully.
     *
     * @param thread Thread to verify.
     * @return TRUE if thread is stopped.
     */
    private boolean verifyThreadStopped(Thread thread) {
        boolean stopped = true;
        if (thread != null && thread.isAlive()) {
            try {
                thread.join(WAIT_TO_DIE);
            } catch (InterruptedException e) {
                log.log(Priority.DEBUG, "verifyThreadStopped was interrupted");
                Thread.currentThread().interrupt();
            }
            if (thread.isAlive()) {
                stopped = false;
                log.warn("Thread won't exit: " + thread.getName());
            }
        }
        return stopped;
    }

    /**
     * Wait for all Group Threads to stop.
     */
    @Override
    public void waitThreadsStopped() {
        waitThreadStopped(threadStarter);
        for (Thread thread : allThreads.values()) {
            waitThreadStopped(thread);
        }
    }

    /**
     * Wait for thread to stop.
     *
     * @param thread Thread
     */
    private void waitThreadStopped(Thread thread) {
        if (thread != null) {
            while (thread.isAlive()) {
                try {
                    thread.join(WAIT_TO_DIE);
                } catch (InterruptedException e) {
                    log.log(Priority.DEBUG, "waitThreadStopped was interrupted");
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Get the current value of the most precise available system timer, in milliseconds.
     * @return System timer in milliseconds.
     */
    public static long getSystemTimer() {
        return System.nanoTime() / 1000000;
    }

    /**
     * Create a list with waiting times for the timer thread to control the test
     * threads.
     *
     * @param rpmDurationList A list of Requests Per Minute and durations in the fowwloing
     *                        syntax:
     *                        startRPM1,endRPM1,duration1;startRPM2,endRPM2,duration2;
     *                        startRPM3,endRPM3,duration3; ...
     * @return A list of waiting times in milliseconds.
     */
    private static List<Long> createWaitTimesList(String rpmDurationList) {
        List<Long> waitTimesList = new ArrayList<>();
        String[] rpmDurationAll = rpmDurationList.split(";");
        String[] oneRPMTriple;
        for (String rpm : rpmDurationAll) {
            oneRPMTriple = rpm.split("-");
            if (oneRPMTriple.length < 3) {
                throw new RuntimeException("CF026");
            }
            double startRPM = 0;
            double endRPM = 0;
            double duration = 0;
            try {
                startRPM = Double.valueOf(oneRPMTriple[0].trim());
                endRPM = Double.valueOf(oneRPMTriple[1].trim());
                duration = Double.valueOf(oneRPMTriple[2].trim());
                if (startRPM < 0 || endRPM < 0 || duration < 0) {
                    throw new RuntimeException(
                            "Configuration error in setting performance test values: One or more values are negative: StartRPM: "
                                    + startRPM + " endRPM: " + endRPM + " duration: "
                                    + duration);
                }
            } catch (NumberFormatException e) {
                throw new RuntimeException(
                        "Configuration error in setting performance test values: One or more values are not a number: "
                                + oneRPMTriple[0] + " " + oneRPMTriple[1] + " " + oneRPMTriple[2]);
            }
            waitTimesList.addAll(calculateWaitTimes(startRPM, endRPM, duration));
        }
        return waitTimesList;
    }

    /**
     * Calculate all waiting times between 2 requests in a period.
     *
     * @param startRPM The amount of requests per minute that should be fired at the
     *                 start of this period.
     * @param endRPM   The amount of requests per minute that should be fired at the
     *                 end of this period.
     * @param duration The duration (in minutes) of this requests per minute period.
     * @return A list of waiting times in milliseconds.
     */
    private static List<Long> calculateWaitTimes(double startRPM, double endRPM, double duration) {
        List<Long> waitTimesList = new ArrayList<>();
        double total = 0;
        if (startRPM < endRPM) {
            // currentRPM is variable over the time
            double currentRPM = startRPM;
            // calculate tangent of the tangle
            double tana = (endRPM - startRPM) / duration;
            while (currentRPM <= endRPM) {
                // calculate a specific wait time until the next request should
                // be send
                double waitTime = Math.sqrt((2 / tana) + ((currentRPM / tana) * (currentRPM / tana)))
                                          - currentRPM / tana;
                // calculate next RPM
                currentRPM = currentRPM + waitTime * tana;
                // all wait time <= duration
                total = total + waitTime;
                if (total < duration) {
                    // add wait time in milliseconds to list
                    waitTimesList.add(Math.round(waitTime * 60 * 1000));
                }
            }
        } else if (startRPM > endRPM) {
            // currentRPM is variable over the time
            double currentRPM = startRPM;
            // calculate tangent of the tangle
            double tana = (startRPM - endRPM) / duration;
            while (currentRPM >= endRPM && total < duration) {
                // calculate a specific wait time until the next request should
                // be send
                double waitTime = -Math.sqrt((-2 / tana) + ((currentRPM / tana) * (currentRPM / tana)))
                                          + currentRPM / tana;
                // calculate next RPM
                currentRPM = currentRPM - waitTime * tana;
                // all wait time <= duration
                total = total + waitTime;
                if (Double.isNaN(waitTime) || total > duration) {
                    break;
                } else {
                    // add wait time in milliseconds to list
                    waitTimesList.add(Math.round(waitTime * 60 * 1000));
                }
            }
        } else {
            // startRPM == endRPM
            double waitTime = 1 / startRPM;
            long requestCount = Math.round(duration / waitTime);
            for (int count = 1; count <= requestCount; count++) {
                waitTimesList.add(Math.round(waitTime * 60 * 1000));
            }
        }
        return waitTimesList;
    }

    @Override
    /**
     * Add a new JMeterThread to this ThreadGroup for engine
     */
    public JMeterThread addNewThread(int delay, StandardJMeterEngine engine) {
        return null;
    }
}
