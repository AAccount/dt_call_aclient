package dt.call.aclient.sodium;
/**
 * Created by Daniel on 01/18/20.
 *
 */
import android.content.Intent;

import com.goterl.lazycode.lazysodium.LazySodium;
import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.interfaces.Box;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.pool.DatagramPacketPool;
import dt.call.aclient.sodium.SodiumUtils;

public class SodiumUDP
{
	private static final String tag = "SodiumUDP";
	private static final int OORANGE_LIMIT = 100;
	private static final int HEADERS = 52;

	//stats
	private final StringBuilder statsBuilder = new StringBuilder();
	private String missingLabel, garbageLabel, txLabel, rxLabel, rxSeqLabel, txSeqLabel, skippedLabel, oorangeLabel;
	private int garbage=0, txData=0, rxData=0, rxSeq=0, txSeq=0, skipped=0, oorange=0; //int r/w atomic

	//udp port constants
	private static final int UDP_RETRIES = 10;
	private static final int UDP_ACK_TIMEOUT = 100; //in milliseconds
	private static final int DSCP_EXPEDITED_FWD = (0x2E << 2);

	//tx rx thread stuff
	private Thread receiveMonitorThread, txthread, rxthread;
	private final LinkedBlockingQueue<DatagramPacket> sendQ = new LinkedBlockingQueue<>();
	private final byte[] txPacketBuffer = new byte[Const.SIZE_MAX_UDP];
	private DatagramPacketPool txPacketPool;
	private final LinkedBlockingQueue<DatagramPacket> receiveQ = new LinkedBlockingQueue<>();
	private final byte[] packetDecrypted = new byte[Const.SIZE_MAX_UDP];
	private DatagramPacketPool rxPacketPool = new DatagramPacketPool();
	private final DecimalFormat decimalFormat = new DecimalFormat("#.###");

	//reconnect udp variables
	private boolean reconnectionAttempted = false;
	private volatile long lastReceivedTimestamp = 0; //volatile makes r/w atomic
	private int reconnectTries = 0;

	//safe stop
	private final Object stopLock = new Object();
	private boolean stopRequested = false;

	//actual sodium and udp guts
	private DatagramSocket mediaUdp;
	private InetAddress callServer;
	private byte[] voiceSymmetricKey;
	private String address;
	private int port;
	private boolean useable = false;

	private final static Object idlock = new Object();
	private static int runningID = 0;
	private final int id;

	public void setVoiceSymmetricKey(byte[] voiceSymmetricKey)
	{
		this.voiceSymmetricKey = voiceSymmetricKey;
	}

	public SodiumUDP(String caddress, int cport)
	{
		address = caddress;
		port = cport;
		missingLabel = Vars.applicationContext.getString(R.string.call_main_stat_mia);
		txLabel = Vars.applicationContext.getString(R.string.call_main_stat_tx);
		rxLabel = Vars.applicationContext.getString(R.string.call_main_stat_rx);
		garbageLabel = Vars.applicationContext.getString(R.string.call_main_stat_garbage);
		rxSeqLabel = Vars.applicationContext.getString(R.string.call_main_stat_rx_seq);
		txSeqLabel = Vars.applicationContext.getString(R.string.call_main_stat_tx_seq);
		skippedLabel = Vars.applicationContext.getString(R.string.call_main_stat_skipped);
		oorangeLabel = Vars.applicationContext.getString(R.string.call_main_stat_oorange);

		synchronized(idlock)
		{
			id = runningID;
			runningID++;
		}
		Utils.logcat(Const.LOGD, tag, "Created UDP " + address +":"+port + " id:"+ id);
	}

	public boolean connect()
	{
		return registerVoiceUDP();
	}

	public void start() //need to wait until state == incall before starting the sodium threads or else everything dies
	{
		txPacketPool = new DatagramPacketPool(callServer, Vars.mediaPort); //need to wait for register to establish the "callServer" variable
		startReceiveMonitorThread();
		startRX();
		startTX();
	}

	public void close()
	{
		Utils.logcat(Const.LOGD, tag, "Closing UDP  " + address +":"+port + " id:"+ id);
		useable = false;

		//overwrite the voice sodium symmetric key memory contents
		SodiumUtils.applyFiller(voiceSymmetricKey);
		if(mediaUdp != null && !mediaUdp.isClosed())
		{
			mediaUdp.close();
			mediaUdp = null;
		}

		if(receiveMonitorThread != null)
		{
			receiveMonitorThread.interrupt();
		}
		receiveMonitorThread = null;

		if(txthread != null)
		{
			txthread.interrupt();
		}
		txthread = null;

		if(rxthread != null)
		{
			rxthread.interrupt();
		}
		rxthread = null;

		SodiumUtils.applyFiller(txPacketBuffer);
		SodiumUtils.applyFiller(packetDecrypted);
	}

	private void startTX()
	{
		txthread = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Utils.logcat(Const.LOGD, tag, "Sodium UDP TX thread started id:"+ id);
				while(Vars.state == CallState.INCALL)
				{
					DatagramPacket packet = null;
					try
					{
						packet = sendQ.take();
						mediaUdp.send(packet);
					}
					catch(IOException e) //this will happen at the end of a call, no need to reconnect.
					{
						Utils.dumpException(tag, e);
						if(!reconnectUDP())
						{
							stopOnError();
							return;
						}
					}
					catch(InterruptedException e)
					{
						break;
					}
					finally
					{
						txPacketPool.returnDatagramPacket(packet);
					}
				}
				Utils.logcat(Const.LOGD, tag, "Sodium UDP TX thread stopped id:"+ id);
			}
		});
		txthread.setName("SodiumUDP_TX@"+ id);
		txthread.start();
	}

	private void startRX()
	{
		rxthread = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Utils.logcat(Const.LOGD, tag, "Sodium UDP RX thread started id:"+ id);
				while(Vars.state == CallState.INCALL)
				{
					DatagramPacket received;
					try
					{
						received = rxPacketPool.getDatagramPacket();
						mediaUdp.receive(received);
						lastReceivedTimestamp = System.nanoTime();
						receiveQ.put(received);
					}
					catch(SocketTimeoutException e)
					{//to prevent this thread from hanging forever, there is now a udp read timeout during calls
						Utils.dumpException(tag, e);
					}
					catch(InterruptedException | NullPointerException e)
					{
						//can get a null pointer if the connection dies, media decoder dies, but this network thread is still alive
						break;
					}
					catch(IOException e) //this will happen at the end of a call, no need to reconnect.
					{
						Utils.dumpException(tag, e);
						if(!reconnectUDP())
						{
							stopOnError();
							break;
						}
					}
				}
				Utils.logcat(Const.LOGD, tag, "Sodium UDP RX thread stopped id:"+ id);
			}
		});
		rxthread.setName("SodiumUDP_RX@"+ id);
		rxthread.start();
	}

	public void write(byte[] dataOut, int length)
	{
		if(!useable)
		{
			Utils.logcat(Const.LOGE, tag, "udp write isn't useable id:"+ id);
			return;
		}
		Arrays.fill(txPacketBuffer, (byte)0);
		final byte[] txSeqDisassembled = Utils.disassembleInt(txSeq);
		System.arraycopy(txSeqDisassembled, 0, txPacketBuffer, 0, Const.SIZEOF_INT);
		txSeq++;
		System.arraycopy(dataOut, 0 , txPacketBuffer, Const.SIZEOF_INT, length);

		final DatagramPacket packet = txPacketPool.getDatagramPacket();
		final byte[] packetBufferEncrypted = packet.getData();
		final int packetBufferEncryptedLength = SodiumUtils.symmetricEncrypt(txPacketBuffer, Const.SIZEOF_INT+length, voiceSymmetricKey, packetBufferEncrypted);
		if(packetBufferEncryptedLength == 0)
		{
			Utils.logcat(Const.LOGE, tag, "voice symmetric encryption failed id:"+ id);
		}
		else
		{
			packet.setLength(packetBufferEncryptedLength);
			try
			{
				sendQ.put(packet);
			}
			catch(InterruptedException e)
			{
			}
			txData = txData + packetBufferEncryptedLength + HEADERS;
		}
	}

	public int read(byte[] dataIn)
	{
		if(!useable)
		{
			Utils.logcat(Const.LOGE, tag, "udp read isn't useable id:"+ id);
			return 0;
		}

		try
		{
			//read encrypted opus
			Arrays.fill(packetDecrypted, (byte) 0);
			final DatagramPacket received = receiveQ.take();

			//decrypt
			rxData = rxData + received.getLength() + HEADERS;
			final int packetDecLength = SodiumUtils.symmetricDecrypt(received.getData(), received.getLength(), voiceSymmetricKey, packetDecrypted); //contents [seq#|opus chunk]
			rxPacketPool.returnDatagramPacket(received);
			if(packetDecLength == 0)//contents [seq#|opus chunk]
			{
				Utils.logcat(Const.LOGD, tag, "Invalid decryption id:"+ id);
				garbage++;
				return 0;
			}

			final byte[] sequenceBytes = new byte[Const.SIZEOF_INT];
			System.arraycopy(packetDecrypted, 0, sequenceBytes, 0, Const.SIZEOF_INT);
			final int sequence = Utils.reassembleInt(sequenceBytes);
			if(sequence <= rxSeq)
			{
				skipped++;
				return 0;
			}

			//out of range receive sequences have happened before. still unexplained. log it as a stat
			if(Math.abs(sequence - rxSeq) > OORANGE_LIMIT)
			{
				oorange++;
				return 0;
			}
			else
			{
				rxSeq = sequence;
			}

			//extract the opus chunk
			Arrays.fill(dataIn, (byte)0);
			final int encodedLength = packetDecLength - Const.SIZEOF_INT;
			System.arraycopy(packetDecrypted, Const.SIZEOF_INT, dataIn, 0, encodedLength);
			return encodedLength;
		}
		catch(InterruptedException e)
		{
			return 0;
		}
	}

	private void startReceiveMonitorThread()
	{
		receiveMonitorThread = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Utils.logcat(Const.LOGD, tag, "Sodium UDP receive monitor start id:"+ id);
				while(Vars.state == CallState.INCALL)
				{
					final long A_SECOND = 1000000000L; //usual delay between receives is ~60.2milliseconds

					final long now = System.nanoTime();
					final long btw = now - lastReceivedTimestamp;
					if((lastReceivedTimestamp > 0) && (btw > A_SECOND) && (mediaUdp != null))
					{
						Utils.logcat(Const.LOGD, tag, "delay since last received more than 1s: " + now+ " " + lastReceivedTimestamp +  " id:"+ id);
						useable = false;
						mediaUdp.close();
					}

					try
					{
						Thread.sleep(1000);
					}
					catch(InterruptedException e)
					{
						break;
					}
				}
				Utils.logcat(Const.LOGD, tag, "Sodium UDP receive monitor stop id:"+ id);
			}
		});
		receiveMonitorThread.setName("SodiumUDP_RX_Monitor@"+ id);
		receiveMonitorThread.start();
	}

	public String stats()
	{
		final String rxDisp=formatInternetMeteric(rxData), txDisp=formatInternetMeteric(txData);
		final int missing = txSeq-rxSeq;
		statsBuilder.setLength(0);
		statsBuilder
				.append(missingLabel).append(": ").append(missing > 0 ? missing : 0).append(" ").append(garbageLabel).append(": ").append(garbage).append("\n")
				.append(rxLabel).append(":").append(rxDisp).append(" ").append(txLabel).append(":").append(txDisp).append("\n")
				.append(rxSeqLabel).append(":").append(rxSeq).append(" ").append(txSeqLabel).append(":").append(txSeq).append("\n")
				.append(skippedLabel).append(":").append(skipped).append(" ").append(oorangeLabel).append(": ").append(oorange);
		return statsBuilder.toString();
	}

	private String formatInternetMeteric(int n)
	{
		final int mega = 1000000;
		final int kilo = 1000;

		if(n > mega)
		{
			return decimalFormat.format((float)n / (float)mega) + "M";
		}
		else if (n > kilo)
		{
			return (n/kilo) + "K";
		}
		else
		{
			return Integer.toString(n);
		}
	}

	private void stopOnError()
	{
		synchronized(stopLock)
		{
			if(!stopRequested)
			{
				stopRequested = true;
				useable = false;
				final Intent callEnd = new Intent(Const.BROADCAST_CALL);
				callEnd.putExtra(Const.BROADCAST_CALL_RESP, Const.BROADCAST_CALL_END);
				Vars.applicationContext.sendBroadcast(callEnd);
			}
		}
	}

	private synchronized boolean reconnectUDP()
	{
		if(Vars.state == CallState.INCALL)
		{
			final int MAX_UDP_RECONNECTS = 10;
			if(reconnectTries > MAX_UDP_RECONNECTS)
			{
				return false;
			}

			if(reconnectionAttempted)
			{
				reconnectionAttempted = false;
				return true;
			}
			else
			{
				sendQ.clear();
				receiveQ.clear();

				reconnectTries++;
				boolean reconnected = registerVoiceUDP();
				reconnectionAttempted = true;
				return reconnected;
			}
		}
		return false;
	}

	private boolean registerVoiceUDP()
	{
		final LazySodium lazySodium = new LazySodiumAndroid(new SodiumAndroid());

		//setup the udp socket BEFORE using it
		try
		{
			callServer = InetAddress.getByName(Vars.serverAddress);
			mediaUdp = new DatagramSocket();
			mediaUdp.setTrafficClass(DSCP_EXPEDITED_FWD);
		}
		catch (Exception e)
		{
			Utils.dumpException(tag, e);
			useable = false;
			return false;
		}

		int retries = UDP_RETRIES;
		while(retries > 0)
		{
			final String registration = String.valueOf(Utils.currentTimeSeconds()) + "|" + Vars.sessionKey;
			final byte[] sodiumSealedRegistration = new byte[Box.SEALBYTES + registration.length()];
			lazySodium.cryptoBoxSeal(sodiumSealedRegistration, registration.getBytes(), registration.length(), Vars.serverPublicSodium);

			//send the registration
			final DatagramPacket registrationPacket = new DatagramPacket(sodiumSealedRegistration, sodiumSealedRegistration.length, callServer, Vars.mediaPort);
			try
			{
				mediaUdp.setSoTimeout(UDP_ACK_TIMEOUT);
				mediaUdp.send(registrationPacket);
			}
			catch (IOException e)
			{
				//couldn't send, nothing more you can do, try again
				retries--;
				continue;
			}

			//wait for media port registration ack
			final byte[] ackBuffer = new byte[Const.SIZE_MAX_UDP];
			final DatagramPacket ack = new DatagramPacket(ackBuffer, Const.SIZE_MAX_UDP);
			try
			{
				mediaUdp.receive(ack);
			}
			catch (IOException t)
			{
				//not much you can do if it took too long
				retries--;
				continue; //no response to parse
			}

			//decrypt ack
			final byte[] decAck = new byte[Const.SIZE_COMMAND];
			final int decAckLength = SodiumUtils.symmetricDecrypt(ack.getData(), ack.getLength(), Vars.commandSocket.getTcpKey(), decAck);
			if(decAckLength == 0)
			{
				return false;
			}
			final String ackString = new String(decAck, 0, decAckLength);

			//verify ack timestamp
			long ackts = 0;
			try
			{
				ackts = Long.valueOf(ackString);
			}
			catch(NumberFormatException n)
			{
				Utils.dumpException(tag, n);
			}

			if(Utils.validTS(ackts))
			{
				try
				{
					final int A_SECOND = 1000;
					mediaUdp.setSoTimeout(A_SECOND);
				}
				catch (SocketException e)
				{
					Utils.dumpException(tag, e);
				}
				useable = true;
				return true; //udp media port established, no need to retry
			}
			retries--;
		}
		useable = false;
		return false;
	}

	public String toString()
	{
		String sendingTo = "Sending to server @ " + address + ":" + port + " " + useable;
		String sendingFrom = "Sending from local: " + mediaUdp.getLocalAddress().toString() + ":" + mediaUdp.getLocalPort();
		return sendingTo+"\n"+sendingFrom;
	}
}
