package wifi;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;


public class Clock {
	public Clock()
	{
		
	}
	
	public Date getLocalTime()
	{
		Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid"));
		Date currentDate = calendar.getTime();
		return currentDate;
	}

}
