package dt.call.aclient.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Daniel on 1/21/16.
 */
public class Db extends SQLiteOpenHelper
{
	private static final String dbname = "calldb";
	private static final String tableContacts = "contacts";
	private static final String colName = "name";
	private static final String colNick = "nickname";

	private static final String tableHistory = "history";
	private static final String colTs = "timestamp";
	private static final String colWho = "who";
	private static final String colType = "call_type";
	final String mkhistory = "create table " + tableHistory + " " +
			"(" +
			colTs + " integer primary key, " +
			colWho + "text, " +
			colType + " integer " +
			")";

	private SQLiteDatabase appdb;

	public Db(Context c)
	{
		super(c, dbname, null, 2);
		appdb = getWritableDatabase();
	}
	@Override
	public void onCreate(SQLiteDatabase db)
	{
		final String mkcontacts = "create table " + tableContacts + " " +
				"(" +
				colName +" text primary key," +
				colNick +" text" +
				")";
		db.execSQL(mkcontacts);
		db.execSQL(mkhistory);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
	{
		//no upgrade at this point so nothing to worry about
		if(oldVersion == 1 && newVersion == 2)
		{
			db.execSQL(mkhistory);
		}
	}

	public void insertContact(Contact contact)
	{
		ContentValues newContact = new ContentValues();
		newContact.put(colName, contact.getName());
		newContact.put(colNick, contact.getNickname());
		appdb.insert(tableContacts, null, newContact);
	}

	public boolean contactExists(Contact contact)
	{
		String[] columnToReturn = {colName};
		String selection = colName + "=?";
		String[] selectionArgs = {contact.getName()};
		Cursor cursor = appdb.query(tableContacts, columnToReturn, selection, selectionArgs, null, null, null);
		if(cursor.moveToFirst())
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	public void deleteContact(Contact contact)
	{
		appdb.delete(tableContacts, colName + "=?", new String[]{contact.getName()});
	}

	public void changeNickname(Contact contact)
	{
		ContentValues chNick = new ContentValues();
		chNick.put(colNick, contact.getNickname());
		appdb.update(tableContacts, chNick, colName + "=?", new String[]{contact.getName()});
	}

	public ArrayList<Contact> getContacts()
	{
		ArrayList<Contact> result = new ArrayList<Contact>();
		Cursor cursor = appdb.rawQuery("select * from " + tableContacts, null);
		cursor.moveToFirst();
		while(!cursor.isAfterLast())
		{
			String fromRowName = cursor.getString(cursor.getColumnIndex(colName));
			String fromRowNick = cursor.getString(cursor.getColumnIndex(colNick));
			Contact fromRow = new Contact(fromRowName, fromRowNick);
			result.add(fromRow);
			cursor.moveToNext();
		}
		return result;
	}

	public void insertHistory(History history)
	{
		ContentValues newHistory = new ContentValues();
		newHistory.put(colTs, history.getTimestamp());
		newHistory.put(colWho, history.getWho().getName());
		newHistory.put(colType, history.getType());
		appdb.insert(tableHistory, null, newHistory);
	}

	public ArrayList<History> getRecentHistory()
	{
		//establish a hash table of contact name to its object to be able to print out
		//	the nickname in the history screen
		ArrayList<Contact> contacts = getContacts();
		HashMap<String, Contact> contactHashMap = new HashMap<String, Contact>();
		for(Contact contact : contacts)
		{
			contactHashMap.put(contact.getName(), contact);
		}

		ArrayList<History> result = new ArrayList<History>();
		final String query = "select * from " + tableHistory + " limit 20";
		Cursor cursor = appdb.rawQuery(query, null);
		cursor.moveToFirst();
		while(!cursor.isAfterLast())
		{
			long ts = cursor.getLong(cursor.getColumnIndex(colTs));
			int type = cursor.getInt(cursor.getColumnIndex(colType));
			String name = cursor.getString(cursor.getColumnIndex(colWho));
			Contact corresponding = contactHashMap.get(name);
			if(corresponding == null)
			{
				corresponding = new Contact(name);
			}
			History history = new History(ts, corresponding, type);
			result.add(history);
			cursor.moveToNext();
		}
		return result;
	}
}
