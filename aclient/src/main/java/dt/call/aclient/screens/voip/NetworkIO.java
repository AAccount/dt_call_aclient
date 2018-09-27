package dt.call.aclient.screens.voip;

import android.app.IntentService;
import android.content.Intent;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketException;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

public class NetworkIO extends IntentService
{
	private static final String tag = "VoIP_Network_IO";
	private CallMain parent;

	public NetworkIO()
	{
		super(tag);
	}

	@Override
	protected void onHandleIntent(Intent intent)
	{
		startForeground(Const.STATE_NOTIFICATION_ID, Vars.stateNotification);
		parent = CallMain.current;

		receiveThread();
		transmitThread();
	}

	private void receiveThread()
	{
		Thread networkThread = new Thread(new Runnable()
		{
			private static final String tag = "DecodeNetwork";

			@Override
			public void run()
			{
				while(Vars.state == CallState.INCALL)
				{
					final byte[] inputBuffer = new byte[Const.SIZE_MEDIA];
					final DatagramPacket received = new DatagramPacket(inputBuffer, Const.SIZE_MEDIA);
					try
					{
						Vars.mediaUdp.receive(received);
						parent.receiveQ.put(received);
					}
					catch(InterruptedException e)
					{
						Utils.dumpException(tag, e);
					}
					catch (IOException e)
					{
						//if the socket has problems, shouldn't continue the call.
						Utils.dumpException(tag, e);
						Vars.state = CallState.NONE;
						Utils.killSockets();

						try
						{
							parent.onStopWrapper();
						}
						catch (Exception inner)
						{
							//see encoder thread for why onStop() is called in a try
						}
					}
				}
			}
		});
		networkThread.setName("Media_Decoder_Network");
		networkThread.start();
	}

	private void transmitThread()
	{
		Thread networkThread = new Thread(new Runnable()
		{
			private static final String tag = "EncodeNetwork";

			@Override
			public void run()
			{
				while(Vars.state == CallState.INCALL)
				{
					try
					{
						final DatagramPacket packet = parent.sendQ.take();
						Vars.mediaUdp.send(packet);
					}
					catch (IOException e)
					{
						Utils.dumpException(tag, e);
						Vars.state = CallState.NONE;
						Utils.killSockets();

						try
						{
							parent.onStopWrapper();
						}
						catch (Exception ex)
						{
							//don't know whether encode or decode will call onStop() first. the second one will get a null exception
							//because the main ui thead will be gone after the first onStop() is called. catch the exception
						}					}
					catch (InterruptedException e)
					{
						Utils.dumpException(tag, e);
					}
				}
			}
		});
		networkThread.setName("Media_Encoder_Network");
		networkThread.start();
	}
}
