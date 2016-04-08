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
import dt.call.aclient.sqlite.Db;
import dt.call.aclient.sqlite.History;

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

	//new to aclient!!!
	private Db db;

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
		//TODO: look into why media socket dies after making a call, ending it, then turning off the screen
		//	don't want this to catch the login resposne
		Utils.logcat(Const.LOGD, tag, "command listener INTENT SERVICE started");
		db = new Db(getApplicationContext());

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
				Utils.logcat(Const.LOGD, tag, "Server response raw: " + fromServer);

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
							Utils.logcat(Const.LOGD, tag, Vars.callWith + " isn't online to talk with right now");
							Vars.state = CallState.NONE;
							Vars.callWith = Const.nobody;
							notifyCanInit(false);
							Utils.updateNotification(getString(R.string.state_popup_idle), Vars.go2HomePending);
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
							Utils.logcat(Const.LOGD, tag, Vars.callWith + " is online. Ringing him/her now");
							Vars.state = CallState.INIT;
							notifyCanInit(true);
							Utils.updateNotification(getString(R.string.state_popup_init), Vars.go2CallMainPending);
							//if the person is online, the server will ring him
						}
						else
						{
							Utils.logcat(Const.LOGW, tag, "Erroneous user available from: " + involved + " instead of: " + Vars.callWith);
						}
					}
					else if(subCommand.equals("incoming"))
					{
						Utils.logcat(Const.LOGD, tag, "Incoming call from: " + involved);
						Vars.state = CallState.INIT;
						Contact contact = new Contact(involved, Vars.contactTable.get(involved));
						History history = new History(Utils.getLocalTimestamp(), contact, Const.incoming);
						db.insertHistory(history);
						Vars.callWith = contact;

						Utils.updateNotification(getString(R.string.state_popup_incoming), null);
						Intent showIncoming = new Intent(getApplicationContext(), CallIncoming.class);
						showIncoming.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); //needed to start activity from background
						startActivity(showIncoming);
					}
					else if(subCommand.equals("busy"))
					{
						Utils.logcat(Const.LOGD, tag, involved + " is already in a call");
						Vars.state = CallState.NONE;
						Vars.callWith = Const.nobody;
						notifyCanInit(false);
						Utils.updateNotification(getString(R.string.state_popup_idle), Vars.go2HomePending);
					}
					else if(subCommand.equals("timeout"))
					{
						if(involved.equals(Vars.callWith.getName()))
						{
							Utils.logcat(Const.LOGD, tag, "60seconds is up to answer a call from " + Vars.callWith);
							Vars.state = CallState.NONE;
							Vars.callWith = Const.nobody;
							notifyStateChange(Const.BROADCAST_CALL_END);
							Utils.updateNotification(getString(R.string.state_popup_idle), Vars.go2HomePending);
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
							Utils.logcat(Const.LOGD, tag, Vars.callWith + " picked up. Start talking.");
							Vars.state = CallState.INCALL;
							notifyStateChange(Const.BROADCAST_CALL_START);
							Utils.updateNotification(getString(R.string.state_popup_incall), Vars.go2CallMainPending);
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
							Utils.logcat(Const.LOGD, tag, Vars.callWith + " is ending the call.");
							//don't change the call state and call with. those will be managed by the screens
							Vars.state = CallState.NONE;
							Vars.callWith = Const.nobody;
							notifyStateChange(Const.BROADCAST_CALL_END);
							Utils.updateNotification(getString(R.string.state_popup_idle), Vars.go2HomePending);

							if(subCommand.equals("end"))
							{//need for force stop the media read thread if it's an end
								//there is no way to kill the thread but to stop the socket to cause an exception
								//	restart after the exception
								Vars.mediaSocket.close();
								try
								{
									Vars.mediaSocket = Utils.mkSocket(Vars.serverAddress, Vars.mediaPort, Vars.expectedCertDump);
									String associateMedia = Utils.generateServerTimestamp() + "|" + Vars.sessionid;
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
							Utils.logcat(Const.LOGD, tag, "Call with " + Vars.callWith + " was dropped");
							Vars.state = CallState.NONE;
							Vars.callWith = Const.nobody;
							notifyStateChange(Const.BROADCAST_CALL_END);
							Utils.updateNotification(getString(R.string.state_popup_idle), Vars.go2HomePending);

							//there is no way to kill the thread but to stop the socket to cause an exception
							//	restart after the exception
							Vars.mediaSocket.close();
							try
							{
								Vars.mediaSocket = Utils.mkSocket(Vars.serverAddress, Vars.mediaPort, Vars.expectedCertDump);
								String associateMedia = Utils.generateServerTimestamp() + "|" + Vars.sessionid;
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
						Utils.logcat(Const.LOGD, tag, "Erroneous call command: " + fromServer);
					}
				}
				else if(serverCommand.equals("lookup"))
				{
					String who = respContents[2];
					String status = respContents[3];
					Utils.logcat(Const.LOGD, tag, "Lookup of: " + who + " --> " + status);

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

			}
			catch (IOException e)
			{
				Utils.logcat(Const.LOGE, tag, "Command socket closed...");
				inputValid = false;
			}
			catch(NumberFormatException n)
			{
				Utils.logcat(Const.LOGE, tag, "string --> # error: ");
			}
			catch(NullPointerException n)
			{
				Utils.logcat(Const.LOGE, tag, "Command socket terminated from the server");
				inputValid = false;
			}
		}
		notifyDead();
	}

	/**
	 * Boradcasts to UserHome whether or not you can start the call
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
	private void notifyStateChange(String change)
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
		Utils.logcat(Const.LOGE, tag, "broadcasting dead command listner");
		if(Vars.dontRestart)
		{
			Utils.logcat(Const.LOGD, tag, "not restart command listener because dontRestart == true");
			Vars.dontRestart = false;
			return;
		}
		Vars.cmdListenerRunning = false;

		Intent deadBroadcast = new Intent(Const.BROADCAST_BK_CMDDEAD);
		sendBroadcast(deadBroadcast);
	}
}
