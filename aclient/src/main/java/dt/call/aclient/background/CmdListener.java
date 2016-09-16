package dt.call.aclient.background;

import android.app.IntentService;
import android.content.Intent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.cert.CertificateException;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.screens.CallIncoming;
import dt.call.aclient.sqlite.Contact;

/**
 * Created by Daniel on 1/19/16.
 * Mostly copied and pasted from jclient
 */
public class CmdListener extends IntentService
{
	private static final String tag = "CmdListener";

	//copied over from jclient
	private boolean inputValid = false; //causes the thread to stop whether for technical or paranoia
	private BufferedReader txtin;

	public CmdListener()
	{
		super(tag);
		try
		{
			txtin = new BufferedReader(new InputStreamReader(Vars.commandSocket.getInputStream()));
			inputValid = true;
		}
		catch (Exception e)
		{
			Utils.dumpException(tag, e);
			notifyDead();
		}
	}

	@Override
	protected void onHandleIntent(Intent workIntent)
	{
		//	don't want this to catch the login resposne
		Utils.logcat(Const.LOGD, tag, "command listener INTENT SERVICE started");

		String logd = ""; //accumulate all the diagnostic message together to prevent multiple entries of diagnostics in log ui just for cmd listener
		while(inputValid)
		{
			//responses from the server command connection will always be in text format
			//timestamp|ring|notavailable|tried_to_call
			//timestamp|ring|available|tried_to_call
			//timestamp|ring|incoming|trying_to_call
			//timestamp|ring|busy|tried_to_call
			//timestamp|ring|timeout|trying to call you
			//timestamp|lookup|who|exists
			//timestamp|resp|login|sessionid
			//timestamp|call|start|with
			//timestamp|call|reject|by
			//timestamp|call|end|by
			//timestamp|call|drop|sessionid

			try
			{//the async magic here... it will patiently wait until something comes in

				String fromServer = txtin.readLine();
				String[] respContents = fromServer.split("\\|");
				logd = logd +  "Server response raw: " + fromServer + "\n";

				//check for properly formatted command
				if(respContents.length != 4)
				{
					Utils.logcat(Const.LOGW, tag, "invalid server response");
					continue;
				}

				//verify timestamp
				long ts = Long.valueOf(respContents[0]);
				if(!Utils.validTS(ts))
				{
					Utils.logcat(Const.LOGW, tag, "Rejecting server response for bad timestamp");
					continue;
				}

				//look at what the server is telling the call simulator to do
				String serverCommand = respContents[1];
				if(serverCommand.equals("ring"))
				{
					String subCommand = respContents[2];
					String involved = respContents[3];
					if(subCommand.equals("notavailable"))
					{
						if(involved.equals(Vars.callWith.getName()))
						{
							logd = logd +   Vars.callWith + " isn't online to talk with right now\n";
							Vars.state = CallState.NONE;
							Vars.callWith = Const.nobody;
							notifyCanInit(false);
							Utils.setNotification(R.string.state_popup_idle, R.color.material_green, Vars.go2HomePending);
						}
						else
						{
							Utils.logcat(Const.LOGW, tag, "Erroneous user n/a for call from: " + involved + " instead of: " + Vars.callWith);
						}
					}
					else if(subCommand.equals("available"))
					{
						if(involved.equals(Vars.callWith.getName()))
						{
							logd = logd +  Vars.callWith + " is online. Ringing him/her now\n";
							Vars.state = CallState.INIT;
							notifyCanInit(true);
							Utils.setNotification(R.string.state_popup_init, R.color.material_light_blue, Vars.go2CallMainPending);
							//if the person is online, the server will ring him
						}
						else
						{
							Utils.logcat(Const.LOGW, tag, "Erroneous user available from: " + involved + " instead of: " + Vars.callWith);
						}
					}
					else if(subCommand.equals("incoming"))
					{
						logd = logd +  "Incoming call from: " + involved + "\n";
						Vars.state = CallState.INIT;
						Contact contact = new Contact(involved, Vars.contactTable.get(involved));
						Vars.callWith = contact;

						Utils.setNotification(R.string.state_popup_incoming, R.color.material_light_blue, Vars.go2CallIncomingPending);
						Intent showIncoming = new Intent(getApplicationContext(), CallIncoming.class);
						showIncoming.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); //needed to start activity from background
						startActivity(showIncoming);
					}
					else if(subCommand.equals("busy"))
					{
						logd = logd +  involved + " is already in a call\n";
						Vars.state = CallState.NONE;
						Vars.callWith = Const.nobody;
						notifyCanInit(false);
						Utils.setNotification(R.string.state_popup_idle, R.color.material_green, Vars.go2HomePending);
					}
					else if(subCommand.equals("timeout"))
					{
						if(involved.equals(Vars.callWith.getName()))
						{
							logd = logd +  "60seconds is up to answer a call from " + Vars.callWith + "\n";
							Vars.state = CallState.NONE;
							Vars.callWith = Const.nobody;
							notifyCallStateChange(Const.BROADCAST_CALL_END);
							Utils.setNotification(R.string.state_popup_idle, R.color.material_green, Vars.go2HomePending);
						}
						else
						{
							Utils.logcat(Const.LOGW, tag, "Erroneous timeout from: " + involved + " instead of: " + Vars.callWith);
						}
					}
					else
					{
						Utils.logcat(Const.LOGW, tag, "Unknown server RING command: " + fromServer);
					}
				}
				else if (serverCommand.equals("call"))
				{
					String subCommand = respContents[2];
					String involved = respContents[3];
					if(subCommand.equals("start"))
					{
						if(involved.equals(Vars.callWith.getName()))
						{
							logd = logd +  Vars.callWith + " picked up. Start talking.\n";
							Vars.state = CallState.INCALL;
							notifyCallStateChange(Const.BROADCAST_CALL_START);
							Utils.setNotification(R.string.state_popup_incall, R.color.material_light_blue, Vars.go2CallMainPending);
						}
						else
						{
							Utils.logcat(Const.LOGW, tag, "Erroneous start call with: " + involved + " instead of: " + Vars.callWith);
						}
					}
					else if(subCommand.equals("reject") || subCommand.equals("end"))
					{
						if(involved.equals(Vars.callWith.getName()))
						{
							logd = logd +  Vars.callWith + " is ending the call.\n";
							//don't change the call state and call with. those will be managed by the screens
							Vars.state = CallState.NONE;
							Vars.callWith = Const.nobody;
							notifyCallStateChange(Const.BROADCAST_CALL_END);
							Utils.setNotification(R.string.state_popup_idle, R.color.material_green, Vars.go2HomePending);

							if(subCommand.equals("end"))
							{//need for force stop the media read thread if it's an end
								//there is no way to kill the thread but to stop the socket to cause an exception
								//	restart after the exception
								Vars.mediaSocket.close();
								try
								{
									Vars.mediaSocket = Utils.mkSocket(Vars.serverAddress, Vars.mediaPort, Vars.expectedCertDump);
									String associateMedia = Const.JBYTE + Utils.generateServerTimestamp() + "|" + Vars.sessionid;
									Vars.mediaSocket.getOutputStream().write(associateMedia.getBytes());
								}
								catch (CertificateException c)
								{
									Utils.logcat(Const.LOGE, tag, "Tring to reestablish media port but somehow the certificate is wrong");
									inputValid = false;
								}
							}
						}
						else
						{
							Utils.logcat(Const.LOGW, tag, "Erroneous call rejected/end with: " + involved + " instead of " + Vars.callWith);
						}
					}
					else if(subCommand.equals("drop"))
					{
						long servSession = Long.valueOf(respContents[3]);
						if(servSession == Vars.sessionid)
						{
							logd = logd +  "Call with " + Vars.callWith + " was dropped\n";
							Vars.state = CallState.NONE;
							Vars.callWith = Const.nobody;
							notifyCallStateChange(Const.BROADCAST_CALL_END);
							Utils.setNotification(R.string.state_popup_idle, R.color.material_green, Vars.go2HomePending);

							//there is no way to kill the thread but to stop the socket to cause an exception
							//	restart after the exception
							Vars.mediaSocket.close();
							try
							{
								Vars.mediaSocket = Utils.mkSocket(Vars.serverAddress, Vars.mediaPort, Vars.expectedCertDump);
								String associateMedia = Const.JBYTE + Utils.generateServerTimestamp() + "|" + Vars.sessionid;
								Vars.mediaSocket.getOutputStream().write(associateMedia.getBytes());
							}
							catch (CertificateException c)
							{
								Utils.logcat(Const.LOGE, tag, "trying to reassociate media after dropped call but suddenly the server's cert is invalid");
								inputValid = false;
							}
						}
					}
					else
					{
						Utils.logcat(Const.LOGW, tag, "Erroneous call command: " + fromServer);
					}
				}
				else if(serverCommand.equals("lookup"))
				{
					String who = respContents[2];
					String status = respContents[3];
					logd = logd + "Lookup of: " + who + " --> " + status + "\n";

					Intent lookupStatus = new Intent(Const.BROADCAST_HOME);
					lookupStatus.putExtra(Const.BROADCAST_HOME_TYPE, Const.BROADCAST_HOME_TYPE_LOOKUP);
					lookupStatus.putExtra(Const.BROADCAST_HOME_LOOKUP_NAME, who);
					lookupStatus.putExtra(Const.BROADCAST_HOME_LOOKUP_RESULT, status);
					sendBroadcast(lookupStatus);
				}
				else if(serverCommand.equals("resp"))
				{//currently only being used for invalid command
					Utils.logcat(Const.LOGW, tag, "command was invalid");
				}
				else
				{
					Utils.logcat(Const.LOGW, tag, "Unknown command/response: " + fromServer);
				}

				Utils.logcat(Const.LOGD, tag, logd);
			}
			catch (IOException e)
			{
				Utils.logcat(Const.LOGE, tag, "Command socket closed...");
				Utils.dumpException(tag, e);
				inputValid = false;
			}
			catch(NumberFormatException n)
			{
				Utils.logcat(Const.LOGE, tag, "string --> # error: ");
			}
			catch(NullPointerException n)
			{
				Utils.logcat(Const.LOGE, tag, "Command socket null pointer exception");
				inputValid = false;
			}
		}
		notifyDead();
	}

	/**
	 * Broadcasts to UserHome whether or not you can start the call
	 * @param canInit whether to star the call or not
	 */
	private void notifyCanInit(boolean canInit)
	{
		Utils.logcat(Const.LOGD, tag, "broadcasting type_init intent to home with status: " + canInit);
		Intent initStatus = new Intent(Const.BROADCAST_HOME);
		initStatus.putExtra(Const.BROADCAST_HOME_TYPE, Const.BROADCAST_HOME_TYPE_INIT);
		initStatus.putExtra(Const.BROADCAST_HOME_INIT_CANINIT, canInit);
		sendBroadcast(initStatus);
	}

	/**
	 * Broadcasts to CallInit and CallMain about call state changes
	 * @param change Either Const.BROADCAST_CALL_END (end call) or Const.BROADCAST_CALL_START (start call)
	 */
	private void notifyCallStateChange(String change)
	{
		Intent stateChange = new Intent(Const.BROADCAST_CALL);
		if(change.equals(Const.BROADCAST_CALL_END))
		{
			Utils.logcat(Const.LOGD, tag, "broadcasting call end");
			stateChange.putExtra(Const.BROADCAST_CALL_RESP, Const.BROADCAST_CALL_END);
		}
		else if (change.equals(Const.BROADCAST_CALL_START))
		{
			Utils.logcat(Const.LOGD, tag, "broadcasting call start");
			stateChange.putExtra(Const.BROADCAST_CALL_RESP, Const.BROADCAST_CALL_START);
		}
		else
		{
			//an invalid call response to broadcast was given
			return;
		}
		sendBroadcast(stateChange);
	}

	private void notifyDead()
	{
		//only 1 case where you don't want to restart the command listener: quitting the app.
		//the utils.quit function disables BackgroundManager first before killing the sockets
		//that way when this dies, nobody will answer the command listener dead broadcast

		Utils.logcat(Const.LOGE, tag, "broadcasting dead command listner");
		try
		{
			Intent deadBroadcast = new Intent(Const.BROADCAST_BK_CMDDEAD);
			sendBroadcast(deadBroadcast);
		}
		catch (Exception e)
		{
			Utils.logcat(Const.LOGE, tag, "couldn't broadcast dead command listener... leftover broadacast from java socket stupidities?");
			Utils.dumpException(tag, e);
		}
	}
}
