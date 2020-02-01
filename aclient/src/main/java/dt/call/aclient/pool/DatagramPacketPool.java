package dt.call.aclient.pool;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.LinkedList;

public class DatagramPacketPool
{
	private LinkedList<DatagramPacket> packets = new LinkedList<DatagramPacket>();
	private int size = 10;
	private InetAddress defaultAddress = null;
	private int defaultPort = 0;
	private final Object bufferLock = new Object();
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
			packets.push(packet);
		}
		size = size*2; //always double the packet pool when it runs out
	}

	public DatagramPacket getDatagramPacket()
	{
		DatagramPacket packet;
		synchronized(bufferLock)
		{
			if(packets.isEmpty())
			{
				generatePackets();
			}
			packet = packets.pop();
			Arrays.fill(packet.getData(), (byte) 0);
		}
		return packet;
	}

	public void returnDatagramPacket(DatagramPacket datagramPacket)
	{
		synchronized(bufferLock)
		{
			if(datagramPacket != null)
			{
				packets.push(datagramPacket);
			}
		}
	}
}
