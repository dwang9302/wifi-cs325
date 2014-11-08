package wifi;
import java.util.concurrent.ArrayBlockingQueue;
import rf.RF;

public class Receiver implements Runnable
{
	private ArrayBlockingQueue<byte[]> data;
	//private ArrayBlockingQueue<byte[]> acks;
	//private short myAddress; 
	private RF theRF;
	
	public Receiver(RF theRF, ArrayBlockingQueue<byte[]> data)
	{
		this.theRF = theRF;
		this.data = data;
	}
	
	@Override
	public void run() {
		while(true)
		{
			byte[] received = theRF.receive();
			
			//use the packet class to help extract data...
			
			//check to see if the message is for us (Dest Addr)
			
			//if not, ignore
			
			//is yes, check to see the frame type
			
			//ACK : store it into the acks (sender may want to check it )
			
			
			//DATA :
			int sequenceExpected = 0;
			//make and send ack
			//check sequence number,
			//if good: store it to data
			
			sequenceExpected++;
	
		}
		
	}

}
