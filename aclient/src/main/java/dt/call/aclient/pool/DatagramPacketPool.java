package dt.call.aclient.pool;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.ArrayList;

public class DatagramPacketPool
{
	private ArrayList<DatagramPacket> packets = new ArrayList<DatagramPacket>();
	private int size = 10;
	private InetAddress defaultAddress = null;
	private int defaultPort = 0;
	private final int placeholderSize = 1;
	private byte[] placeholder = new byte[placeholderSize];

	public DatagramPacketPool()
	{
		generatePackets();
	}

	public DatagramPacketPool(InetAddress cdefaultaddr, int cdefaultport)
	{
		defaultAddress = cdefaultaddr;
		defaultPort = cdefaultport;
		generatePackets();
	}

	private void generatePackets()
	{
		for(int i=0; i<size; i++)
		{
			DatagramPacket packet = new DatagramPacket(placeholder, placeholderSize);
			if(defaultAddress != null)
			{
				packet.setAddress(defaultAddress);
				packet.setPort(defaultPort);
			}
			packets.add(packet);
		}
		size = size*2; //always double the buffer pool when it runs out
	}

	public DatagramPacket getDatagramPacket()
	{
		if(packets.isEmpty())
		{
			generatePackets();
		}
		DatagramPacket buffer = packets.get(0);
		packets.remove(0);
		return buffer;
	}

	public void returnDatagramPacket(DatagramPacket datagramPacket)
	{
		packets.add(datagramPacket);
	}
}
