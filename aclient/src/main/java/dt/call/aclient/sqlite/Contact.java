package dt.call.aclient.sqlite;

/**
 * Created by Daniel on 1/21/16.
 */
public class Contact
{
	private String name, nickname;

	public Contact (String cname)
	{
		name = cname;
		nickname = "";
	}

	public Contact(String cname, String cnick)
	{
		name = cname;
		nickname = cnick;
	}

	public String getName()
	{
		return name;
	}

	public String getNickname()
	{
		return nickname;
	}

	public void setNickname(String nickname)
	{
		this.nickname = nickname;
	}

	public String toString()
	{
		return nickname + " (" + name + ")";
	}

	public boolean equals(Object comparison)
	{
		if(!(comparison instanceof Contact))
		{
			return false;
		}

		//only the user name in the server db matters for if it's the same person or not
		//	nick name is arbitrary
		Contact casted = (Contact)comparison;
		if(name.equals(casted.getName()))
		{
			return true;
		}
		else
		{
			return false;
		}
	}
}
