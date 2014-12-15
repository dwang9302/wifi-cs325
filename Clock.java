package wifi;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Create a clock to store time for synchronization
 * @author dmaguddayao
 *
 */

public class Clock implements Runnable{
	
	private long time;
	private Status stat;
	private short beaconInterval;
	private Packet helper;
	private short MAC;
	
	private long startTime; //testing how long it was for each loop
	private long endTime; //stores the last one
	private long timeToGetThere;
	private ArrayBlockingQueue<byte[]> beacon;
	
	public Clock(Status sta, short bI, short ourMAC, ArrayBlockingQueue<byte[]> beac)//, long roundTrip)
	{
		startTime = System.currentTimeMillis(); //set this time so we can change the time later.  Doesn't care if time itself has changed
		time = startTime;
		stat = sta;
		beaconInterval = bI;
		helper = new Packet();
		MAC = ourMAC;
		timeToGetThere = 200;
		beacon = beac;
		
	}
	
	public Date getLocalTime()
	{
		Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid"));
		Date currentDate = calendar.getTime();
		return currentDate;
	}
	
	public void changeTime(long newTime)
	{
		time = newTime;
	}
	
	public long getTime()
	{
		return time;
	}
	
	public void changeInterval(short inter)
	{
		beaconInterval = inter;
	}
	public void run()
	{
		while(true)
		{
			try
			{
				Thread.sleep(0, 10); //really small wait time.  Make it so that we don't end up in an endless loop
			}
			catch(InterruptedException e)
			{
				stat.changeStat(Status.UNSPECIFIED_ERROR);
			}
			endTime = System.currentTimeMillis();
        	time = time + (endTime - startTime);
        	if (beaconInterval != -1)
        	{
        		if (beaconInterval == 0)
        		{
        			beacon.clear();
        			beacon.add(helper.createBeacon(time+timeToGetThere, MAC));
        		}
        		else if(time % (beaconInterval*1000) == 0)
        		{
        			beacon.clear();
        			beacon.add(helper.createBeacon(time+timeToGetThere, MAC));
        			//theRF.transmit(helper.createBeacon(time+timeToGetThere, MAC));
        		}
        	}
        	startTime = endTime;
		}
	
	}

}
