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
        long startTimeMs = System.currentTimeMillis();
        
        // Log 30 error messages for location 1 (10 more than allowed)
        // and 10 error messages for location 2
        for (int i=1; i<=30; i++)
        {
            logger.error("Message " + i + " for location 1");
            if (i<=10)
            {
                logger.error("Message " + i + " for location 2");
            }
        }
        
        long duration = System.currentTimeMillis() - startTimeMs;
        assertTrue("Messages should have been sent within 10 seconds (the configured timewindow)", duration < 10*1000);
        
        // Wait 10 seconds to make sure the time window ends and information about muzzled messages is logged as well
        // Next to that wait another 5 seconds to give Logback a chance to send the emails
        System.out.println("\n" + this.getClass().getSimpleName() + ": waiting 15 seconds to complete timewindow and sending emails");
        try
        {
            Thread.sleep(10*1000 + 5*1000);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        
        assertEquals("Exactly 31 messages should have been mailed (20 plus 1 muzzled message for location 1 and 10 for location 2)", 31, smtpServer.getReceivedEmailSize());

        Pattern subjectPattern = Pattern.compile("^n.b.l.t.ThrottledSMTPAppenderTest - Message [0-9]{1,2} for location (1|2)$");
        
        @SuppressWarnings("unchecked")
        Iterator<SmtpMessage> i = smtpServer.getReceivedEmail();
        int messageNr = 0;
        while (i.hasNext())
        {
            messageNr++;
            SmtpMessage message = i.next();
            
            final String subject = message.getHeaderValue("Subject");
            
            logger.trace(messageNr + " - " + subject);
            
            if (messageNr <= 30)
            {
                assertTrue( subjectPattern.matcher(subject).matches() );
            }
            else
            {
                assertTrue("Message 31 should contain the 'muzzled info'", message.getBody().contains("Muzzled 10 messages from nl.brightbits.logback.throttle.ThrottledSMTPAppenderTest.testThrottling") );
            }
        }
    }
    
    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        smtpServer = SimpleSmtpServer.start();
    }
    
    @Override
    protected void tearDown() throws Exception
    {
        super.tearDown();
        smtpServer.stop();
    }
}
