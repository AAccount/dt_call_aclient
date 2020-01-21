package dt.call.aclient;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.goterl.lazycode.lazysodium.interfaces.SecretBox;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Random;

import dt.call.aclient.Voip.SodiumUDP;
import dt.call.aclient.codec.Opus;

@RunWith(AndroidJUnit4.class)
public class SodiumUDPTest
{
	private final String tag = "Sodium UDP Test";
	private final int port = 1959;
	private final int BUFFER_SIZE = 1000;

	private Random r = new Random();
	private Thread fakeDTOperator;
	private boolean testing = true;

	@Before
	public void startFakeDTOperator()
	{
		testing = true;
		fakeDTOperator = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					DatagramSocket serverSocket = new DatagramSocket(port);
					byte[] receiveData = new byte[BUFFER_SIZE];
					byte[] sendData = new byte[BUFFER_SIZE];
					DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
					DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length);

					while(testing)
					{
						byte[] packetBuffer = receivePacket.getData();
						Arrays.fill(packetBuffer, (byte)0);
						serverSocket.receive(receivePacket);

						byte[] outgoing = sendPacket.getData();
						Arrays.fill(outgoing, (byte)0);
						System.arraycopy(receivePacket.getData(), 0, sendPacket.getData(), 0, BUFFER_SIZE);
						InetAddress IPAddress = receivePacket.getAddress();
						int port = receivePacket.getPort();
						sendPacket.setAddress(IPAddress);
						sendPacket.setPort(port);
						serverSocket.send(sendPacket);
					}
				}
				catch(IOException i)
				{
					Log.e(tag, i.getMessage());
				}
			}
		});
		fakeDTOperator.start();
	}

	@After
	public void stopFakeDTOperator()
	{
		testing = false;
		fakeDTOperator.interrupt();
	}

	@Test
	public void simulateCall()
	{
		Vars.state = CallState.INCALL;
		final int SIMULATED_PACKETS = 100000;
		SodiumUDP sodiumUDP = new SodiumUDP("localhost", port);
		boolean connected = sodiumUDP.connect();
		sodiumUDP.start();
		Assert.assertTrue(connected);
		byte[] voiceSymmetricKey = new byte[SecretBox.KEYBYTES];
		r.nextBytes(voiceSymmetricKey);
		sodiumUDP.setVoiceSymmetricKey(voiceSymmetricKey);

		final byte[] dataOut = new byte[BUFFER_SIZE];
		final byte[] dataIn = new byte[BUFFER_SIZE];
		for(int i=0; i<SIMULATED_PACKETS; i++)
		{
			Arrays.fill(dataOut, (byte) 0);
			r.nextBytes(dataOut);
			sodiumUDP.write(dataOut, BUFFER_SIZE);
			int read = sodiumUDP.read(dataIn);
			Assert.assertEquals(BUFFER_SIZE, read);
			Assert.assertArrayEquals(dataOut, dataIn);
		}
	}
}
