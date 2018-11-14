package dt.call.aclient.pool;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;

public class DatagramPacketPool
{
	private ArrayList<DatagramPacket> packets = new ArrayList<DatagramPacket>();
	private int size = 10;
	private InetAddress defaultAddress = null;
	private int defaultPort = 0;

	private static final int BUFFER_SIZE = 2048;

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
			final byte[] packetBuffer = new byte[BUFFER_SIZE];
			DatagramPacket packet = new DatagramPacket(packetBuffer, BUFFER_SIZE);
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
		DatagramPacket packet = packets.get(0);
		packets.remove(0);
		Arrays.fill(packet.getData(), (byte)0);
		return packet;
	}

	public void returnDatagramPacket(DatagramPacket datagramPacket)
	{
		packets.add(datagramPacket);
	}
}
