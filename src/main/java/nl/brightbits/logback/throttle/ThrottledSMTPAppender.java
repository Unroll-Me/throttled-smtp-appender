package nl.brightbits.logback.throttle;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.net.SMTPAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Can be used to prevent against flooding a mail server with log messages. <br>
 * Basically the source location of a message is tracked. When X messages have
 * been logged from a location within Y seconds (configurable) other messages
 * from that location will be muzzled during the remaining time of that period
 * (Y).<br>
 * <br>
 * 
 * <b>Scenario</b> <br>
 * You configure Logback to send error messages via mail so you are immediately
 * informed when an error occurs. <br>
 * During development this works fine. But in production the load on your
 * application is much higher (hopefully :-)). <br>
 * So when an unexpected problem occurs, lets say it generates an error for
 * every page view, a lot of error messages will be send. <br>
 * This will put stress on your mail server while you probably only need a
 * couple of messages to be aware of the problem. <br>
 * <br>
 * 
 * <b>Usage</b> <br>
 * Configure this appender in your logback.xml file: <br>
 * <br>
 * <code>
 * &lt;?xml&nbsp;version=&quot;1.0&quot;&nbsp;encoding=&quot;UTF-8&quot;?&gt;
 * <br>&lt;configuration&nbsp;debug=&quot;false&quot;&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&lt;!--&nbsp;Writes&nbsp;Logback&nbsp;notifications&nbsp;(like&nbsp;mail&nbsp;errors)&nbsp;to&nbsp;the&nbsp;console&nbsp;--&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&lt;statusListener&nbsp;class=&quot;ch.qos.logback.core.status.OnConsoleStatusListener&quot;&nbsp;/&gt;
 * <br>
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&lt;!--&nbsp;Instead&nbsp;of&nbsp;class=&quot;ch.qos.logback.classic.net.SMTPAppender&quot;&gt;&nbsp;--&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&lt;appender&nbsp;name=&quot;EMAIL&quot;&nbsp;class=&quot;nl.brightbits.logback.throttle.ThrottledSMTPAppender&quot;&gt;
 * <br>
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;!--&nbsp;For&nbsp;example&nbsp;you&nbsp;can&nbsp;configure&nbsp;that&nbsp;only&nbsp;error&nbsp;messages&nbsp;are&nbsp;mailed&nbsp;--&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;filter&nbsp;class=&quot;ch.qos.logback.classic.filter.LevelFilter&quot;&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;level&gt;ERROR&lt;/level&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;onMatch&gt;ACCEPT&lt;/onMatch&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;onMismatch&gt;DENY&lt;/onMismatch&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/filter&gt;
 * <br>
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;!--&nbsp;How&nbsp;to&nbsp;connect&nbsp;to&nbsp;your&nbsp;mail&nbsp;server&nbsp;--&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;SMTPHost&gt;localhost&lt;/SMTPHost&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;Username&gt;test&lt;/Username&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;Password&gt;*******&lt;/Password&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;To&gt;developers.app.xyz@test.no&lt;/To&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;From&gt;app.xyz@test.no&lt;/From&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;!--&nbsp;If&nbsp;there&nbsp;are&nbsp;muzzled&nbsp;messages&nbsp;on&nbsp;which&nbsp;level&nbsp;should&nbsp;these&nbsp;be&nbsp;logged?&nbsp;--&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;!--&nbsp;Defaults&nbsp;to&nbsp;ERROR&nbsp;when&nbsp;omitted&nbsp;--&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;logMuzzledMessagesOnLevel&gt;ERROR&lt;/logMuzzledMessagesOnLevel&gt;
 * <br>
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;!--&nbsp;Define&nbsp;the&nbsp;duration&nbsp;in&nbsp;which&nbsp;'maxMessagesPerTimeWindow'&nbsp;are&nbsp;allowed&nbsp;to&nbsp;be&nbsp;mailed&nbsp;--&gt;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;!--&nbsp;Defaults&nbsp;to&nbsp;60&nbsp;when&nbsp;omitted&nbsp;--&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;timeWindowDurationSeconds&gt;10&lt;/timeWindowDurationSeconds&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;!--&nbsp;How&nbsp;many&nbsp;messages&nbsp;(per&nbsp;location)&nbsp;may&nbsp;be&nbsp;mailed&nbsp;within&nbsp;the&nbsp;'timeWindowDurationSeconds'&nbsp;--&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;!--&nbsp;Defaults&nbsp;to&nbsp;10&nbsp;when&nbsp;omitted&nbsp;--&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;maxMessagesPerTimeWindow&gt;20&lt;/maxMessagesPerTimeWindow&gt;
 * <br>
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;layout&nbsp;class=&quot;ch.qos.logback.classic.PatternLayout&quot;&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;Pattern&gt;%date{ISO8601}&nbsp;%-5level&nbsp;[%thread]&nbsp;%class.%method\(%file:%line\)&nbsp;-&nbsp;%msg&nbsp;%n&lt;/Pattern&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/layout&gt;
 * <br>
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;cyclicBufferTracker&nbsp;class=&quot;ch.qos.logback.core.spi.CyclicBufferTrackerImpl&quot;&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;!--&nbsp;Send&nbsp;just&nbsp;one&nbsp;log&nbsp;entry&nbsp;per&nbsp;email&nbsp;--&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;bufferSize&gt;1&lt;/bufferSize&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/cyclicBufferTracker&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&lt;/appender&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&lt;root&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;level&nbsp;value=&quot;WARN&quot;&nbsp;/&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;appender-ref&nbsp;ref=&quot;EMAIL&quot;&nbsp;/&gt;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&lt;/root&gt;
 * <br>&lt;/configuration&gt;
 * </code> <br>
 * <br>
 * 
 * <b>Example</b> <br>
 * Messages allowed to be mailed will not be touched and mailed like normal. <br>
 * Muzzled messages will be logged like: <code>
 * Muzzled 10 messages from nl.brightbits.logback.throttle.ThrottledSMTPAppenderTest.testThrottling(ThrottledSMTPAppenderTest.java:43) 
 * </code>
 * 
 * @author Ricardo Lindooren
 * 
 */
public class ThrottledSMTPAppender extends SMTPAppender {

	private static class LocationData {
		public final Semaphore semaphore;
		public final AtomicInteger messagesMuzzled;

		public LocationData(int semaphoreCount, int initialMessagesMuzzled) {
			semaphore = new Semaphore(semaphoreCount);
			messagesMuzzled = new AtomicInteger(initialMessagesMuzzled);
		}
	}

	/*
	 * the write lock is used for removing entries, and the read lock is used
	 * for inserting and updating entries. A lock-free threadsafe pattern is
	 * used to insert. Essentially, mutating the value including inserting a new
	 * one counts as a "read" and changing the value counts as a "write"
	 */

	ReadWriteLock locationDataLock = new ReentrantReadWriteLock();

	ConcurrentMap<String, LocationData> locationData = new ConcurrentHashMap<>();

	private long timeWindowDurationSeconds = 60;
	private int maxMessagesPerTimeWindow = 10;
	private Level logMuzzledMessagesOnLevel = Level.ERROR;
	private String replenisherThreadName = "ThrottledSMTPAppender-Replenisher";
	
	@Override
	public void start() {
		super.start();

		ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {

			@Override
			public Thread newThread(Runnable task) {

				Thread thread = new Thread(replenisherThreadName);
				thread.setDaemon(true);
				return thread;
			}
		});

		pool.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {

				synchronized (locationDataLock.writeLock()) {
					replenishTokens();
					logMuzzledMessages();
					locationData.clear();
				}
			}
		}, timeWindowDurationSeconds, timeWindowDurationSeconds, TimeUnit.SECONDS);
	}

	@Override
	protected void append(ILoggingEvent eventObject) {
		final String locationKey = getCallerLocation(eventObject);

		synchronized (locationDataLock.readLock()) {

			// standard ConcurrentMap pattern to avoid extra object creation
			// yes, this is happening inside a synchronization block because
			// this pattern permits two reads to happen concurrently, as reads
			// are responsible for insertions

			LocationData data = locationData.get(locationKey);

			// It is enforced by read-write lock the value of
			// locationData.get(locationKey) will not change after this "if"
			// block

			if (data == null) {
				locationData.putIfAbsent(locationKey, new LocationData(maxMessagesPerTimeWindow, 0));
				data = locationData.get(locationKey);
			}

			if (data.semaphore.tryAcquire()) {
				super.append(eventObject);
			}
			else {
				data.messagesMuzzled.incrementAndGet();
			}
		}
	}

	/**
	 * class . method ( file : linenr )
	 * 
	 * @param event
	 * 
	 * @return e.g.:
	 *         <code>nl.brightbits.logback.throttle.ThrottledSMTPAppenderTest.testThrottling(ThrottledSMTPAppenderTest.java:38)</code>
	 */
	private String getCallerLocation(ILoggingEvent event) {
		StackTraceElement[] cda = event.getCallerData();

		if (cda != null && cda.length > 0) {
			StackTraceElement ste = cda[0];
			return ste.getClassName() + "." + ste.getMethodName() + "(" + ste.getFileName() + ":" + ste.getLineNumber()
					+ ")";
		}

		return "?";
	}

	public void setLogMuzzledMessagesOnLevel(String level) {
		this.logMuzzledMessagesOnLevel = Level.toLevel(level);
	}

	public void setTimeWindowDurationSeconds(long timeWindowDurationSeconds) {
		this.timeWindowDurationSeconds = timeWindowDurationSeconds;
	}

	public void setMaxMessagesPerTimeWindow(int maxMessagesPerTimeWindow) {
		this.maxMessagesPerTimeWindow = maxMessagesPerTimeWindow;
	}

	/**
	 * This method is necessary to include as the thread name introduces global
	 * state with a small but theoretical chance of conflicting with another
	 * thread in the user's code
	 * 
	 * @param replenisherThreadName
	 */
	public void setReplenisherThreadName(String replenisherThreadName) {
		this.replenisherThreadName = replenisherThreadName;
	}

	/**
	 * Threadsafe if 1) run from single thread, and 2) only code location from
	 * which release is called
	 */

	private void replenishTokens() {
		for (LocationData data : locationData.values()) {
			int usedPermits = maxMessagesPerTimeWindow - data.semaphore.availablePermits();
			data.semaphore.release(usedPermits);
		}
	}

	/**
	 * Logs a message for every location that has muzzled messages. It is good
	 * design to log from this process as it happens on own threadpool offline
	 * from any other logging that may happen in this application
	 */
	private void logMuzzledMessages() {

		Iterator<Map.Entry<String, LocationData>> it = locationData.entrySet().iterator();

		while (it.hasNext()) {

			Map.Entry<String, LocationData> entry = it.next();

			String location = entry.getKey();
			LocationData data = entry.getValue();

			int nrMuzzled = data.messagesMuzzled.getAndSet(0);

			final String message = "Muzzled " + nrMuzzled + " message" + (nrMuzzled > 1 ? "s" : "") + " from "
					+ location;

			// Log how many messages have been muzzled for the current
			// location
			switch (logMuzzledMessagesOnLevel.levelInt) {
				case Level.TRACE_INT:
					Log.logger.trace(message);
					break;
				case Level.DEBUG_INT:
					Log.logger.debug(message);
					break;
				case Level.INFO_INT:
					Log.logger.info(message);
					break;
				case Level.WARN_INT:
					Log.logger.warn(message);
					break;
				case Level.ERROR_INT:
					Log.logger.error(message);
					break;
				default:
					Log.logger.error("Unable to muzzle messages due to unexpected Level enum: ",
							logMuzzledMessagesOnLevel);
			}
		}
	}

	/**
	 * Used to instantiate a logger outside the Logback configuration process
	 */
	private static class Log {
		final static Logger logger = LoggerFactory.getLogger(Log.class);
	}
}
