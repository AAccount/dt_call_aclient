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

		//create a button for every user with a known public key and all contacts
		HashSet<String> userSet = new HashSet<String>();
		userSet.addAll(Vars.publicSodiumTable.keySet());
		userSet.addAll(Vars.contactTable.keySet()); //hash set won't add duplicates

		for(String user : userSet)
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
			userButton.setAllCaps(false);
			userButton.setText(nickname);
			userButton.setOnClickListener(this);

			if(Vars.publicSodiumTable.containsKey(user))
			{
				userButton.setTextColor(getResources().getColor(R.color.material_green));
			}
			else
			{
				userButton.setTextColor(getResources().getColor(R.color.material_red));
			}
			mainLayout.addView(userButton);
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
