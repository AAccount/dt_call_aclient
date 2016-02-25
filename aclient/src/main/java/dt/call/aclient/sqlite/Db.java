package dt.call.aclient.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

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
	private final String mkhistory = "create table " + tableHistory + " " +
			"(" +
			colTs + " integer primary key, " +
			colWho + " text, " +
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
			cursor.close();
			return true;
		}
		else
		{
			cursor.close();
			return false;
		}
	}

	public void deleteContact(Contact contact)
	{
		appdb.delete(tableContacts, "name=?", new String[]{contact.getName()});
	}

	public void changeNickname(Contact contact)
	{
		ContentValues chNick = new ContentValues();
		chNick.put(colNick, contact.getNickname());
		appdb.update(tableContacts, chNick, "name=?", new String[]{contact.getName()});
	}

	public ArrayList<Contact> getContacts()
	{
		ArrayList<Contact> result = new ArrayList<Contact>();
		Cursor cursor = appdb.rawQuery("select * from contacts", null);
		cursor.moveToFirst();
		while(!cursor.isAfterLast())
		{
			String fromRowName = cursor.getString(cursor.getColumnIndex(colName));
			String fromRowNick = cursor.getString(cursor.getColumnIndex(colNick));
			Contact fromRow = new Contact(fromRowName, fromRowNick);
			result.add(fromRow);
			cursor.moveToNext();
		}
		cursor.close();
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
		ArrayList<History> result = new ArrayList<History>();
		final String query = "select timestamp, who, nickname, call_type\n" +
				"from history left join contacts\n" +
				"on history.who = contacts.name\n" +
				"limit 20";
		Cursor cursor = appdb.rawQuery(query, null);
		cursor.moveToFirst();
		while(!cursor.isAfterLast())
		{
			long ts = cursor.getLong(cursor.getColumnIndex(colTs));
			int type = cursor.getInt(cursor.getColumnIndex(colType));
			String name = cursor.getString(cursor.getColumnIndex(colWho));
			String nickname = cursor.getString(cursor.getColumnIndex(colNick));
			Contact contact = new Contact(name, nickname);
			History history = new History(ts, contact, type);
			result.add(history);
			cursor.moveToNext();
		}
		cursor.close();
		return result;
	}
}
