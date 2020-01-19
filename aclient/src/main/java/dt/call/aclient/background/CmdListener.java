package dt.call.aclient.background;

import android.app.AlarmManager;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
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
import java.net.SocketException;
import java.util.Arrays;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.Voip.SoundEffects;
import dt.call.aclient.Voip.Voice;
import dt.call.aclient.background.async.OperatorCommand;
import dt.call.aclient.pool.ByteBufferPool;
import dt.call.aclient.screens.CallMain;
import dt.call.aclient.sodium.SodiumUtils;
import dt.call.aclient.sqlite.SQLiteDb;

/**
 * Created by Daniel on 1/19/16.
 * Mostly copied and pasted from jclient
 */
public class CmdListener extends IntentService
{
	//wakelock tag
	private static final String WAKELOCK_INCOMING = "dt.call.aclient:incoming";
	private static final int COMMAND_MAX_SEGMENTS = 5;
	private static final String tag = "CmdListener";

	private static final String SODIUM_PLACEHOLDER = "...";

	private boolean inputValid = true;

	//for deciding when to send the ready command
	private boolean haveVoiceKey = false;
	private boolean preparationsComplete = false;
	private boolean isCallInitiator = false;

	public CmdListener()
	{
		super(tag);
	}

	@Override
	protected void onHandleIntent(Intent workIntent)
	{
		//Post 8.0 needs a "foreground" service otherwise command listener will
		//	be denied to start even though the login was ok.
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
		{
			startForeground(Const.STATE_NOTIFICATION_ID, Vars.stateNotification);
		}

		Utils.logcat(Const.LOGD, tag, "command listener started");
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

			try
			{//the async magic here... it will patiently wait until something comes in

				final String fromServer = Vars.commandSocket.readString(Const.SIZE_COMMAND);
				final String[] respContents = fromServer.split("\\|");
				Utils.logcat(Const.LOGD, tag, censorIncomingCmd(respContents));
				Vars.applicationContext = getApplicationContext();

				//check for properly formatted command
				if(respContents.length > COMMAND_MAX_SEGMENTS)
				{
					Utils.logcat(Const.LOGW, tag, "command has too many segments to be valid");
					continue;
				}

				//verify timestamp
				final long ts = Long.valueOf(respContents[0]);
				if(!Utils.validTS(ts))
				{
					Utils.logcat(Const.LOGW, tag, "Rejecting server response for bad timestamp");
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
					Vars.incomingCallLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, WAKELOCK_INCOMING);
					Vars.incomingCallLock.acquire();

					//build the contacts list if it doesn't already exist
					if(Vars.contactTable == null)
					{
						SQLiteDb.getInstance(getApplicationContext()).populateContacts();
						SQLiteDb.getInstance(getApplicationContext()).populatePublicKeys();
					}

					Vars.state = CallState.INIT;
					isCallInitiator = false;
					haveVoiceKey = false;
					preparationsComplete = false;
					Vars.callWith = involved;

					//launch the incoming call screen
					SoundEffects.getInstance().playRingtone();
					if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
					{
						Utils.setNotification(R.string.state_popup_incoming, R.color.material_light_blue, Utils.GO_A10_INCOMING);
					}
					else
					{
						Utils.setNotification(R.string.state_popup_incoming, R.color.material_light_blue, Utils.GO_CALL);
						final Intent showIncoming = new Intent(getApplicationContext(), CallMain.class);
						showIncoming.putExtra(CallMain.DIALING_MODE, false);
						showIncoming.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); //needed to start activity from background
						startActivity(showIncoming);
					}
					continue;
				}

				//for all commands except "incoming", need to verify that the other person the command says you're in a call with
				//	is really the other person you're in a call with
				if (!involved.equals(Vars.callWith))
				{
					Utils.logcat(Const.LOGW, tag, "Erroneous command involving: " + involved + " instead of: " + Vars.callWith);
					continue;
				}

				if(command.equals("available"))
				{
					Vars.state = CallState.INIT;
					isCallInitiator = true;
					preparationsComplete = false;
					notifyCallStateChange(Const.BROADCAST_CALL_TRY);
					Utils.setNotification(R.string.state_popup_init, R.color.material_light_blue, Utils.GO_CALL);
				}
				else if (command.equals("start"))
				{
					Vars.state = CallState.INCALL;
					notifyCallStateChange(Const.BROADCAST_CALL_START);
					Utils.setNotification(R.string.state_popup_incall, R.color.material_light_blue, Utils.GO_CALL);
				}
				else if (command.equals("end"))
				{
					SoundEffects.getInstance().stopRingtone();
					Vars.state = CallState.NONE;
					Vars.callWith = Const.nobody;
					notifyCallStateChange(Const.BROADCAST_CALL_END);
					Utils.setNotification(R.string.state_popup_idle, R.color.material_green, Utils.GO_HOME);
					Utils.releaseA9CallWakelock();
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
						if(!Arrays.equals(receivedKey, expectedKey))
						{
							//if the server presented a mismatched key stop the process.
							//either you didn't know about the key change or something is very wrong
							Utils.logcat(Const.LOGE, tag,
									"Server sent a MISMATCHED public key for " + Vars.callWith +
											" . It was:\n" + receivedKeyDump +
											"\nBut expected: " + SodiumUtils.SODIUM_PUBLIC_HEADER+Utils.stringify(expectedKey));
							giveUp();
							continue;
						}
					}
					else
					{
						//with nothing else to go on, assume this really is the person's key
						Vars.publicSodiumTable.put(Vars.callWith, receivedKey);
						SQLiteDb.getInstance(getApplicationContext()).insertPublicKey(Vars.callWith, receivedKey);
						expectedKey = receivedKey;
					}

					//first person gets to choose the key for the call
					if(isCallInitiator)
					{
						//choose sodium key
						byte[] voiceSymmetricKey = lazySodium.randomBytesBuf(SecretBox.KEYBYTES);
						haveVoiceKey = true; //person who makes the call gets to choose the key
						Voice.getInstance().setVoiceKey(voiceSymmetricKey);

						//have sodium encrypt its key
						final byte[] sodiumAsymEncrypted = new byte[Const.SIZE_COMMAND];
						final int sodiumAsymEncryptedLength	= SodiumUtils.asymmetricEncrypt(voiceSymmetricKey, expectedKey, Vars.selfPrivateSodium, sodiumAsymEncrypted);
						final String finalEncryptedString = Utils.stringify(sodiumAsymEncrypted, sodiumAsymEncryptedLength);

						//send the sodium key
						final String passthrough = Utils.currentTimeSeconds() + "|passthrough|" + involved + "|" + finalEncryptedString + "|" + Vars.sessionKey;
						Utils.logcat(Const.LOGD, tag,"passthrough of sodium key " + passthrough.replace(finalEncryptedString, SODIUM_PLACEHOLDER));
						try
						{
							Vars.commandSocket.write(passthrough);
						}
						catch (Exception e)
						{
							Utils.dumpException(tag, e);
							new OperatorCommand().doInForeground(OperatorCommand.END); //can't send voice key, nothing left to continue
						}
					}

					//try to register media port
					final boolean registeredUDP = Voice.getInstance().connect();

					if(registeredUDP)
					{
						preparationsComplete = true;
						sendReady();
					}
					else
					{
						giveUp();
						Utils.logcat(Const.LOGE, tag, "call preparations cannot complete");
					}
				}
				else if(command.equals("direct"))
				{
					//decrypt the sodium symmetric key
					final String setupString = respContents[2];
					final byte[] setup = Utils.destringify(setupString);
					final byte[] callWithKey = Vars.publicSodiumTable.get(involved);
					final byte[] voiceKeyDecrypted = new byte[Const.SIZE_COMMAND];
					final int voiceKeyDecryptedLength = SodiumUtils.asymmetricDecrypt(setup, callWithKey, Vars.selfPrivateSodium, voiceKeyDecrypted);
					final byte[] voiceSymmetricKey = new byte[voiceKeyDecryptedLength];
					System.arraycopy(voiceKeyDecrypted, 0, voiceSymmetricKey, 0, voiceKeyDecryptedLength);

					if(voiceKeyDecryptedLength < 1)
					{
						Voice.getInstance().setVoiceKey(voiceSymmetricKey);
						haveVoiceKey = true;
						sendReady();
					}
					else
					{
						Utils.logcat(Const.LOGE, tag, "Passthrough of sodium symmetric key failed");
						giveUp();
					}
				}
			}
			catch(NumberFormatException n)
			{
				Utils.dumpException(tag, n);
			}
			catch(Exception e)
			{
				Utils.killSockets();
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
		final AlarmManager manager = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
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
		deadBroadcast.setClass(getApplicationContext(), BackgroundManager.class);
		sendBroadcast(deadBroadcast);
	}

	private String censorIncomingCmd(String[] parsed)
	{
		if(parsed.length < 1)
		{
			return "";
		}

		try
		{
			if(parsed[1].equals("direct"))
			{//timestamp|direct|(encrypted aes key)|other_person
				return parsed[0] + "|" + parsed[1] + "|"+SODIUM_PLACEHOLDER+"|" + parsed[3];
			}
			else
			{
				String result = "";
				for(int i=0; i<parsed.length; i++)
				{
					result = result + parsed[i] + "|";
				}
				result = result.substring(0, result.length()-1);
				return result;
			}
		}
		catch(Exception e)
		{
			Utils.dumpException(tag, e);
			return "(censoring error)";
		}
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
	 * really are ready and will only send the command if you have the sodium symmetric key and media port is established.
	 */
	private void sendReady()
	{
		Utils.logcat(Const.LOGD, tag, "key, prep " + haveVoiceKey + ","+preparationsComplete);
		if(haveVoiceKey && preparationsComplete)
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
		new OperatorCommand().doInForeground(OperatorCommand.END);
		notifyCallStateChange(Const.BROADCAST_CALL_END);
	}
}
