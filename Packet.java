package wifi;

/**
 * A Helper Class for shifting bits and bytes around to read or write a packet
 * 
 * @author Dennis Maguddayao 
 * @version 11.8.1
 */
public class Packet
{
    private byte firstByte;//used so we can change the bits
    private byte secondByte; //used so we can change the bits
    
    private int sequencePosition; //keeps track of the position the sequence is in
    
    private byte[] packet;
    
    private byte[] tempData;
    private int packetLength;
    private int firstBit;
    private int secondBit;
    private int thirdBit;
    /**
     * Constructor for objects of class Packet
     */
    public Packet()
    {
        // holds no variables
    }

    /**
     * Creates a packet to be sent out.
     * 
     * @param  y   a sample parameter for a method
     * @param  messageType A message type to figure out what type of Data is being sent
     * @param  retransmit Flips the fourth bit if true (this was a retransmission)
     * @param  mAddr the Source Address of who sent made the packet
     * @param  destAddr where the packet is to be sent to
     * @return     the sum of x and y 
     */
    public byte[] createMessage(String messageType, boolean restransmit, short mAddr, short destAddr, byte[] data, int dataLength, short sequenceNumber)
    {
        //grab the packet length to append to the buffer
        packetLength = 10 + dataLength;
        packet = new byte[packetLength]; //creates the packet to place bytes of data in
        if (messageType == "Data")  //not entirely sure, but I'm following the pattern that your right shift to read and left shift to write
        {
            firstByte &= ~(1 << 7); //left shifts to set bit
            firstByte &= ~(1 << 6);
            firstByte &= ~(1 << 5);
        }
        
        else if (messageType == "ACK")
        {
           firstByte &= ~(1 << 7);
           firstByte &= ~(1 << 6);
           firstByte |= (1 << 5);
        }
        
        
        if (restransmit == true)
        {
            firstByte |= (1 << 4);
        }
        else
        {
            firstByte &= ~(1 << 4);
        }
        //figure out the sequence Number
        //start from the end of sequenceNumber and keep going
        
        sequencePosition = sequenceNumber % 4096; //rolls over if we need to reset the sequenceNumber
        secondByte = (byte)(sequenceNumber & 0xff); //This works, I tested it
        for(int i = 8; i < 12; i++) //starts from last bit of the first byte
        {
            if (getNthBitFromRight(sequenceNumber,i) == 1) //uses helper method to find out the bit.  Starts from the right most bit
            {
                firstByte |= (1 << i-8 ); //sets the bits from the left
            }
            else
            {
                firstByte &= ~(1 << i-8);
            }
        }
        packet[0] = firstByte;
        packet[1] = secondByte;
        
        //translate destAddr to be destination addr
        packet[2] = (byte)((destAddr >> 8) & 0xff); //right shifts so we get the first byte (remove the last 8 bits so these will be the place)
        packet[3] = (byte)(destAddr & 0xff); //this works because it grabs the last 8 bits of the packet (which is the last byte)
        
        //translate mAddr to be sourceAddr
        packet[4] = (byte)((mAddr >> 8) & 0xff);
        packet[5] = (byte)(mAddr & 0xff);
        
        //now take the data byte array that we have receieve and add it to the array.  We have the length of the data
        //as seen in the spec int send(short dest, byte[] data, int len)
        if (dataLength != 0)
        {
            for (int i = 0; i <data.length ;i++)//2038; i++)
            {
                packet[6+i] = data[i];
            }
        }
        //figure out how to make extra packets for those with higher than lengh 2038
        
        //we're not implementing a real checksum yet, so we'll just use 1s for CRC
        packet[packetLength-4] = 1;
        packet[packetLength-3] = 1;
        packet[packetLength-2] = 1;
        packet[packetLength-1] = 1;
        
        return packet;
    }
    
    /**
     * Creates an ACK to be sent
     */
    public byte[] createACK(short mAddr, short destAddr, short sequenceNo)
    {
        return createMessage("ACK", false, mAddr, destAddr, null, 0, sequenceNo);
    }
    
    
    /**
     * There is probably a better way to do this check, but this will do for now
     */
    public String checkMessageType(byte[] pack)
    {
        firstBit = getNthBitFromRight(pack[0], 7);
        secondBit = getNthBitFromRight(pack[0], 6);
        thirdBit = getNthBitFromRight(pack[0], 5);
        //System.out.print(firstBit);
        //System.out.print(secondBit);
        //System.out.print(thirdBit);
        if (firstBit == 0)
        {
            if(secondBit ==0)
            {
                if(thirdBit == 0)
                {
                    return "Data";
                }
                else
                {
                    return "ACK";
                }
            }
            else
            {
                return "Beacon";
            }
        }
        else
        {
            if (secondBit == 0)
            {
                if (thirdBit == 0)
                {
                    return "CTS";
                }
                else
                {
                    return "RTS";
                }
            }
            else 
            {
                return "Error:  Not possible sequence";
            }
        }
    }
    
    /**
     * This is used to check if the data you received is actually for your MAC address
     */
    public short checkDest (byte[] pack)
    {
        return (short)(((pack[2] & 0xFFL) << 8) | //places the short in the correct order
                                ((pack[3] & 0xFFL) << 0));
    }
    
    /**
     * Helps grab the source of a packet so we can send an ACK back
     */
    public short checkSource (byte[] pack)
    {
        return (short)(((pack[4] & 0xFFL) << 8) | //places the short in the correct order
                                ((pack[5] & 0xFFL) << 0));
    }
    
    /**
     * Helps grab the data to put into the Transmission class
     */
    public byte[] checkData (byte[] pack)
    {
        tempData = new byte[pack.length - 10];
        for (int i = 6; i < pack.length -4; i++)
        {
            tempData[i-6] = pack[i];
        }
        return tempData;
    }
    
    /**
     * Helper method for Grabbing bits.  Starts from the right most bit
     * From www.coderanch.com/t/464835/java/java/short-bit
     */
    private int getNthBitFromRight (int input, int n)
    {
        return (input & (1<<n)) == 0? 0 : 1; //shifts left from the right most bit and compares that bit
    }
}
