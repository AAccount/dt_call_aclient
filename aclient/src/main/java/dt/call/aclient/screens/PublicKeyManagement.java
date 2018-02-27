package dt.call.aclient.screens;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import java.util.HashSet;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Vars;
import dt.call.aclient.sqlite.DBLog;

public class PublicKeyManagement extends AppCompatActivity implements View.OnClickListener
{
	private LinearLayout mainLayout;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_public_key_management);
		mainLayout = (LinearLayout)findViewById(R.id.public_key_mgmt_layout);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		HashSet<String> contactHasPublicKey = new HashSet<String>();

		//create a button for every user with a known public key
		for(String user : Vars.publicKeyTable.keySet())
		{
			Button userButton = new Button(this);
			userButton.setTag(user);
			String nickname = Vars.contactTable.get(user);

			//if the user has a contact nick name, put that
			if(nickname == null)
			{
				//no nick name? just use the account name
				nickname = user;
			}
			else
			{
				contactHasPublicKey.add(user);
			}
			userButton.setAllCaps(false);
			userButton.setText(nickname);
			userButton.setOnClickListener(this);
			userButton.setTextColor(getResources().getColor(R.color.material_green));
			mainLayout.addView(userButton);
		}

		//for contacts that don't have a known public key, put them in the list to encourage you to get it
		for(String contact : Vars.contactTable.keySet())
		{
			if(!contactHasPublicKey.contains(contact))
			{
				Button contactButton = new Button(this);
				contactButton.setAllCaps(false);
				contactButton.setTag(contact);
				contactButton.setText(Vars.contactTable.get(contact));
				contactButton.setOnClickListener(this);
				contactButton.setTextColor(getResources().getColor(R.color.material_red));
				mainLayout.addView(contactButton);
			}
		}
	}

	@Override
	public void onClick(View v)
	{
		//somehow not a button that was clicked? don't do anything
		if(v.getClass() != Button.class)
		{
			return;
		}

		//somehow that button doesn't have a string tag? can't do anything
		if(((Button)v).getTag().getClass() != String.class)
		{
			return;
		}

		Intent viewPublicKey = new Intent(PublicKeyManagement.this, PublicKeyDetails.class);
		viewPublicKey.putExtra(Const.EXTRA_UNAME, (String)v.getTag());
		startActivity(viewPublicKey);
	}
}
