package wifi;

/**
* Holds the status of the Linklayer
*
*/
public class Status
{
	private int status;

	//Status code
	public static final int SUCCESS =1;	//Initial value if 802_init is successful
	public static final int UNSPECIFIED_ERROR =2; //	General error code
	public static final int RF_INIT_FAILED =3;	//Attempt to initialize RF layer failed
	public static final int TX_DELIVERED =4;	//Last transmission was acknowledged
	public static final int TX_FAILED =5;	//Last transmission was abandoned after unsuccessful delivery attempts
	public static final int BAD_BUF_SIZE =6;//Buffer size was negative
	public static final int BAD_ADDRESS	=7; //Pointer to a buffer or address was NULL
	public static final int BAD_MAC_ADDRESS =8;	//Illegal MAC address was specified
	public static final int ILLEGAL_ARGUMENT =9;	//One or more arguments are invalid
	public static final int INSUFFICIENT_BUFFER_SPACE =10;	//Outgoing transmission rejected due to insufficient buffer space

	/**
	 * Initialize status with value SUCCESS
	 */
	public Status()
	{
		status = SUCCESS; //initial status
	}

	/**
	 * change the status
	 * @param num
	 */
	public void changeStat(int num)
	{
		status = num;
	}

	/**
	 * return the status
	 * @return status
	 */
	public int getStat()
	{
		return status;
	}
}