package dt.call.aclient.background;

import android.app.AlarmManager;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import com.goterl.lazycode.lazysodium.LazySodium;
import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.interfaces.Box;
import com.goterl.lazycode.lazysodium.interfaces.SecretBox;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.async.CommandEndAsync;
import dt.call.aclient.screens.CallIncoming;
import dt.call.aclient.sodium.SodiumUtils;
import dt.call.aclient.sqlite.SQLiteDb;

/**
 * Created by Daniel on 1/19/16.
 * Mostly copied and pasted from jclient
 */
public class CmdListener extends IntentService
{
	private static final String tag = "CmdListener";

	//copied over from jclient
	private boolean inputValid = true; //causes the thread to stop whether for technical or paranoia

	//for deciding when to send the ready command
	private boolean haveAesKey = false;
	private boolean preparationsComplete = false;
	private boolean isCallInitiator = false;

	public CmdListener()
	{
		super(tag);
	}

	@Override
	protected void onHandleIntent(Intent workIntent)
	{
		Utils.logcat(Const.LOGD, tag, "command listener INTENT SERVICE started");
		final LazySodium lazySodium = new LazySodiumAndroid(new SodiumAndroid());

		while(inputValid)
		{
			//responses from the server command connection will always be in text format
			//timestamp|available|other_person
			//timestamp|incoming|trying_to_call
			//timestamp|start|other_person
			//timestamp|end|other_person
			//timestamp|prepare|public key|other_person
			//timestamp|direct|(encrypted aes key)|other_person
			//timestamp|invalid

			String logd = ""; //accumulate all the diagnostic message together to prevent multiple entries of diagnostics in log ui just for cmd listener
			try
			{//the async magic here... it will patiently wait until something comes in

				final String fromServer = Vars.commandSocket.readString(Const.SIZE_COMMAND);
				final String[] respContents = fromServer.split("\\|");
				logd = "Server response raw: " + fromServer + "\n";

				//check for properly formatted command
				if(respContents.length > Const.COMMAND_MAX_SEGMENTS)
				{
					Utils.logcat(Const.LOGW, tag, logd+"command has too many segments to be valid");
					continue;
				}

				//verify timestamp
				final long ts = Long.valueOf(respContents[0]);
				if(!Utils.validTS(ts))
				{
					Utils.logcat(Const.LOGW, tag, logd+"Rejecting server response for bad timestamp");
					continue;
				}

				//look at what the server is telling the call simulator to do
				final String command = respContents[1];
				final String involved = respContents[respContents.length-1];

				//"incoming" has no "other person you're in a call with" to verify because incoming defines who that other person is
				if (command.equals("incoming"))
				{
					//wake up the cell phone
					final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
					Vars.incomingCallLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, Const.WAKELOCK_INCOMING);
					Vars.incomingCallLock.acquire();

					//build the contacts list if it doesn't already exist
					if(Vars.contactTable == null)
					{
						SQLiteDb.getInstance(getApplication()).populateContacts();
						SQLiteDb.getInstance(getApplication()).populatePublicKeys();
					}

					Vars.state = CallState.INIT;
					isCallInitiator = false;
					haveAesKey = false;
					Vars.callWith = involved;

					//launch the incoming call screen
					Utils.setNotification(R.string.state_popup_incoming, R.color.material_light_blue, Vars.go2CallIncomingPending);
					final Intent showIncoming = new Intent(getApplicationContext(), CallIncoming.class);
					showIncoming.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); //needed to start activity from background
					startActivity(showIncoming);
					continue;
				}

				//for all commands except "incoming", need to verify that the other person the command says you're in a call with
				//	is really the other person you're in a call with
				if (!involved.equals(Vars.callWith))
				{
					Utils.logcat(Const.LOGW, tag, logd+"Erroneous command involving: " + involved + " instead of: " + Vars.callWith);
					continue;
				}

				if(command.equals("available"))
				{
					Vars.state = CallState.INIT;
					isCallInitiator = true;
					haveAesKey = true; //person who makes the call gets to choose the key
					notifyCallStateChange(Const.BROADCAST_CALL_TRY);
					Utils.setNotification(R.string.state_popup_init, R.color.material_light_blue, Vars.go2CallMainPending);
				}
				else if (command.equals("start"))
				{
					Vars.state = CallState.INCALL;
					notifyCallStateChange(Const.BROADCAST_CALL_START);
					Utils.setNotification(R.string.state_popup_incall, R.color.material_light_blue, Vars.go2CallMainPending);
				}
				else if (command.equals("end"))
				{
					Vars.state = CallState.NONE;
					Vars.callWith = Const.nobody;
					notifyCallStateChange(Const.BROADCAST_CALL_END);
					Utils.setNotification(R.string.state_popup_idle, R.color.material_green, Vars.go2HomePending);
				}
				else if(command.equals("prepare"))
				{
					//prepare the server's presentation of the person's public key for use
					final String receivedKeyDump = respContents[2];
					final byte[] receivedKey = SodiumUtils.interpretKey(receivedKeyDump.getBytes(), false);
					byte[] expectedKey = Vars.publicSodiumTable.get(Vars.callWith);

					//if this person's public key is known, sanity check the server to make sure it sent the right one
					if(Vars.publicSodiumTable.containsKey(Vars.callWith))
					{
						if(Arrays.equals(receivedKey, expectedKey))
						{
							//if the server presented a mismatched key stop the process.
							//either you didn't know about the key change or something is very wrong
							Utils.logcat(Const.LOGE, tag, "Server sent a MISMATCHED public key for " + Vars.callWith + " . It was:\n" + receivedKeyDump + "\nBut expected: " + Utils.stringify(expectedKey));
							giveUp();
							continue;
						}
					}
					else
					{
						//with nothing else to go on, assume this really is the person's key
//						byte[] receivedUserKey = Utils.interpretSodiumPublicKey(receivedKeyDump);
						Vars.publicSodiumTable.put(Vars.callWith, receivedKey);
//						Vars.publicSodiumDumps.put(Vars.callWith, receivedKeyDump);
						SQLiteDb.getInstance(getApplication()).insertPublicKey(Vars.callWith, receivedKey);
						expectedKey = receivedKey;
					}

					//first person gets to choose the key for the call
					if(isCallInitiator)
					{
						//choose sodium key
						Vars.voiceSymmetricKey = lazySodium.randomBytesBuf(SecretBox.KEYBYTES);

						//have sodium encrypt its key
						final byte[] sodiumAsymEncrypted = SodiumUtils.asymmetricEncrypt(Vars.voiceSymmetricKey, expectedKey, Vars.privateSodium);
						final String finalEncryptedString = Utils.stringify(sodiumAsymEncrypted);

						//send the sodium key
						final String passthrough = Utils.currentTimeSeconds() + "|passthrough|" + involved + "|" + finalEncryptedString + "|" + Vars.sessionKey;
						logd = logd + "passthrough of sodium key " + passthrough.replace(finalEncryptedString, Const.SODIUM_PLACEHOLDER) + "\n";
						try
						{
							Vars.commandSocket.write(passthrough);
						}
						catch (Exception e)
						{
							Utils.dumpException(tag, e);
							new CommandEndAsync().doInForeground(); //can't send aes key, nothing left to continue
						}
					}

					//setup the udp socket BEFORE using it
					Vars.callServer = InetAddress.getByName(Vars.serverAddress);
					Vars.mediaUdp = new DatagramSocket();
					Vars.mediaUdp.setTrafficClass(Const.DSCP_EXPEDITED_FWD);
					Vars.mediaUdp.setSoTimeout(Const.UDP_ACK_TIMEOUT);

					//try to register media port
					int retries = Const.UDP_RETRIES;
					boolean gotAck = false;
					while(!gotAck && retries > 0)
					{
						final String registration = String.valueOf(Utils.currentTimeSeconds()) + "|" + Vars.sessionKey;
						final byte[] sodiumSealedRegistration = new byte[Box.SEALBYTES + registration.length()];
						lazySodium.cryptoBoxSeal(sodiumSealedRegistration, registration.getBytes(), registration.length(), Vars.serverPublicSodium);

						//send the registration
						final DatagramPacket registrationPacket = new DatagramPacket(sodiumSealedRegistration, sodiumSealedRegistration.length, Vars.callServer, Vars.mediaPort);
						Vars.mediaUdp.send(registrationPacket);

						//wait for media port registration ack
						final byte[] ackBuffer = new byte[Const.SIZE_MEDIA];
						final DatagramPacket ack = new DatagramPacket(ackBuffer, Const.SIZE_MEDIA);
						try
						{
							Vars.mediaUdp.receive(ack);
						}
						catch (SocketTimeoutException t)
						{
							//not much you can do if it took too long
							retries--;
							continue; //no response to parse
						}

						//extract ack response
						final byte[] ackEncBytes = new byte[ack.getLength()];
						System.arraycopy(ack.getData(), 0, ackEncBytes, 0, ack.getLength());

						//decrypt ack
						final byte[] decAck = SodiumUtils.symmetricDecrypt(ackEncBytes, Vars.commandSocket.getTcpKey());
						if(decAck == null)
						{
							gotAck = false;
							break;
						}
						final String ackString = new String(decAck, "UTF-8");

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
							gotAck = true;
							break; //udp media port established, no need to retry
						}
						retries--;
					}

					if(gotAck)
					{
						Vars.mediaUdp.setSoTimeout(0);
						preparationsComplete = true;
						sendReady();
					}
					else
					{
						logd = logd + "call preparations cannot complete\n";
						giveUp();
						continue;
					}
				}
				else if(command.equals("direct"))
				{
					//decrypt the sodium symmetric key
					final String setupString = respContents[2];
					final byte[] setup = Utils.destringify(setupString);
					final byte[] callWithKey = Vars.publicSodiumTable.get(involved);
					Vars.voiceSymmetricKey = SodiumUtils.asymmetricDecrypt(setup, callWithKey, Vars.privateSodium);

					if(Vars.voiceSymmetricKey != null)
					{
						haveAesKey = true;
						sendReady();
					}
					else
					{
						Utils.logcat(Const.LOGE, tag, "Passthrough of sodium symmetric key failed");
						giveUp();
						continue;
					}

					logd = "Server response raw: " + fromServer.replace(setupString, Const.SODIUM_PLACEHOLDER) + "\n";
				}
				else if(command.equals("invalid"))
				{
					logd = logd + "android app sent an invalid command??!!\n";
				}
				else
				{
					logd = logd + "Unknown command\n";
				}

				Utils.logcat(Const.LOGD, tag, logd);
			}
			catch (IOException e)
			{
				Utils.killSockets();
				Utils.logcat(Const.LOGE, tag, logd+"Command socket closed...");
				Utils.dumpException(tag, e);
				inputValid = false;
			}
			catch(NumberFormatException n)
			{
				Utils.logcat(Const.LOGE, tag, logd+"string --> # error: ");
				Utils.dumpException(tag, n);
			}
			catch(NullPointerException n)
			{
				Utils.killSockets();
				Utils.logcat(Const.LOGE, tag, logd+"Command socket null pointer exception");
				Utils.dumpException(tag, n);
				inputValid = false;
			}
			catch(Exception e)
			{
				Utils.killSockets();
				Utils.logcat(Const.LOGE, tag, logd+"Other exception");
				Utils.dumpException(tag, e);
				inputValid = false;
			}
		}

		//only 1 case where you don't want to restart the command listener: quitting the app.
		//the utils.quit function disables BackgroundManager first before killing the sockets
		//that way when this dies, nobody will answer the command listener dead broadcast
		Utils.logcat(Const.LOGE, tag, "broadcasting dead command listner");

		//cleanup the pending intents now that the sockets are unsable. also must do asap to prevent
		//timing problems where socket close and pending intent happen at the same time.
		final AlarmManager manager = (AlarmManager) Vars.applicationContext.getSystemService(Context.ALARM_SERVICE);
		try
		{
			manager.cancel(Vars.pendingHeartbeat);
			manager.cancel(Vars.pendingHeartbeat2ndary);
			manager.cancel(Vars.pendingRetries);
			manager.cancel(Vars.pendingHeartbeat2ndary);
		}
		catch(NullPointerException n)
		{
			//can happen on quit if quit cancels the pendings first. nothing you can do.
			//	just part of the normal shutdown procedure. no reason to panic
		}
		final Intent deadBroadcast = new Intent(Const.BROADCAST_RELOGIN);
		deadBroadcast.setClass(Vars.applicationContext, BackgroundManager.class);
		sendBroadcast(deadBroadcast);
	}

	/**
	 * Broadcasts to CallInit and CallMain about call state changes
	 * @param change Either Const.BROADCAST_CALL_END (end call) or Const.BROADCAST_CALL_START (start call) or Const.BROADCAST_CALL_TRY (ok to try and call the other person)
	 */
	private void notifyCallStateChange(String change)
	{
		final Intent stateChange = new Intent(Const.BROADCAST_CALL);
		if((change.equals(Const.BROADCAST_CALL_END)) || (change.equals(Const.BROADCAST_CALL_START)) ||  (change.equals(Const.BROADCAST_CALL_TRY)))
		{
			Utils.logcat(Const.LOGD, tag, "broadcasting: " + change);
			stateChange.putExtra(Const.BROADCAST_CALL_RESP, change);
		}
		else
		{
			//an invalid call response to broadcast was given
			Utils.logcat(Const.LOGD, tag, "ignoring invalid broadcast of: " + change);
			return;
		}
		sendBroadcast(stateChange);
	}

	/**
	 * Try and tell the server you are ready to make a call. This function checks to make sure you
	 * really are ready and will only send the command if you have the aes key and media port is established.
	 */
	private void sendReady()
	{
		Utils.logcat(Const.LOGD, tag, "key, prep " + haveAesKey + ","+preparationsComplete);
		if(haveAesKey && preparationsComplete)
		{
			final String ready = Utils.currentTimeSeconds() + "|ready|" + Vars.callWith + "|" + Vars.sessionKey;
			Utils.logcat(Const.LOGD, tag, ready);
			try
			{
				Vars.commandSocket.write(ready);
			}
			catch(Exception e)
			{
				Utils.dumpException(tag, e);
				giveUp();
			}
		}
	}

	/**
	 * Send the call end to the server and to an android broadcast of call end.
	 * Usually when it is not possible to setup the call.
	 */
	private void giveUp()
	{
		new CommandEndAsync().doInForeground();
		notifyCallStateChange(Const.BROADCAST_CALL_END);
	}
}
