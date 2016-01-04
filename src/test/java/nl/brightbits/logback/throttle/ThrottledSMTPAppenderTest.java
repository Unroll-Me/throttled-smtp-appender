package nl.brightbits.logback.throttle;

import java.util.Iterator;
import java.util.regex.Pattern;

import junit.framework.TestCase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dumbster.smtp.SimpleSmtpServer;
import com.dumbster.smtp.SmtpMessage;

/**
 * Basic tests
 * 
 * @author Ricardo Lindooren
 *
 */
public class ThrottledSMTPAppenderTest extends TestCase
{
    private static final Logger logger = LoggerFactory.getLogger(ThrottledSMTPAppenderTest.class);
    
    private SimpleSmtpServer smtpServer;
    
    /**
     * In logback-test.xml the following values are configured:
     * logMuzzledMessagesOnLevel = ERROR
     * timeWindowDurationSeconds = 10
     * maxMessagesPerTimeWindow = 20
     * 
     * So if we send more than 20 messages from a location within 10 seconds throttling should become effective
     * 
     */
    public void testThrottling()
    {
        
    }
    
    @Override
    protected void setUp() throws Exception
    {
        //super.setUp();
        //smtpServer = SimpleSmtpServer.start(4929); /* random */
    }
    
    @Override
    protected void tearDown() throws Exception
    {
        //super.tearDown();
        //smtpServer.stop();
    }
}
