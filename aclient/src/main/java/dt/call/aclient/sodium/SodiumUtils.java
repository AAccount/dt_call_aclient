package dt.call.aclient.sodium;

//Created October 18, 2018

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.interfaces.Box;
import com.goterl.lazycode.lazysodium.interfaces.SecretBox;

import java.io.InputStream;
import java.util.Arrays;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;

public class SodiumUtils
{
	private static final LazySodiumAndroid lazySodium = new LazySodiumAndroid(new SodiumAndroid());
	private static final String tag = "sodium_utils";

	public static byte[] asymmetricEncrypt(byte[] message, byte[] receiverPublic, byte[] myPrivate)
	{
		return encrypt(message, true, receiverPublic, myPrivate);
	}

	public static byte[] symmetricEncrypt(byte[] message, byte[] symkey)
	{
		return encrypt(message, false, null, symkey);
	}

	private static byte[] encrypt(byte[] message, boolean asym, byte[] receiverPublic, byte[] myPrivate)
	{
		//setup nonce (like a password salt)
		int nonceLength = 0;
		if(asym)
		{
			nonceLength = Box.NONCEBYTES;
		}
		else
		{
			nonceLength = SecretBox.NONCEBYTES;
		}
		byte[] nonce = lazySodium.randomBytesBuf(nonceLength);

		//setup cipher text
		int cipherTextLength = 0;
		boolean libsodiumOK = false;
		byte[]cipherText;
		if(asym)
		{
			cipherTextLength = Box.MACBYTES + message.length;
			cipherText = new byte[cipherTextLength];
			libsodiumOK = lazySodium.cryptoBoxEasy(cipherText, message, message.length, nonce, receiverPublic, myPrivate);
		}
		else
		{
			cipherTextLength = SecretBox.MACBYTES + message.length;
			cipherText = new byte[cipherTextLength];
			libsodiumOK = lazySodium.cryptoSecretBoxEasy(cipherText, message, message.length, nonce, myPrivate);
		}

		//something went wrong with the encryption
		if(!libsodiumOK)
		{
			Utils.logcat(Const.LOGE, tag, "sodium encryption failed, asym: " + asym + " return code: " + libsodiumOK);;
			return null;
		}

		//glue all the information together for sending
		//[nonce|message length|encrypted message]
		final byte[] messageLengthDissasembled = Utils.disassembleInt(message.length, Const.JAVA_MAX_PRECISION_INT);
		final byte[] finalSetup = new byte[nonceLength+Const.JAVA_MAX_PRECISION_INT+cipherTextLength];
		System.arraycopy(nonce, 0, finalSetup, 0, nonceLength);
		System.arraycopy(messageLengthDissasembled, 0, finalSetup, nonceLength, Const.JAVA_MAX_PRECISION_INT);
		System.arraycopy(cipherText, 0, finalSetup, nonceLength+Const.JAVA_MAX_PRECISION_INT, cipherTextLength);
		return finalSetup;
	}

	public static byte[] asymmetricDecrypt(byte[] setup, byte[] senderPublic, byte[] myPrivate)
	{
		return decrypt(setup, true, senderPublic, myPrivate);
	}

	public static byte[] symmetricDecrypt(byte[] setup, byte[] symkey)
	{
		return decrypt(setup, false, null, symkey);
	}

	private static byte[] decrypt(byte[] setup, boolean asym, byte[] senderPublic, byte[] myPrivate)
	{
		//[nonce|message length|encrypted message]
		int nonceLength = 0;
		if(asym)
		{
			nonceLength = Box.NONCEBYTES;
		}
		else
		{
			nonceLength = SecretBox.NONCEBYTES;
		}

		//check if the nonce and message length are there
		if(setup.length < (nonceLength + Const.JAVA_MAX_PRECISION_INT))
		{
			return null;
		}

		//reassemble the nonce
		final byte[] nonce = new byte[nonceLength];
		System.arraycopy(setup, 0, nonce, 0, nonceLength);

		//get the message length and check it
		final byte[] messageLengthDisassembled = new byte[Const.JAVA_MAX_PRECISION_INT];
		System.arraycopy(setup, nonceLength, messageLengthDisassembled, 0, Const.JAVA_MAX_PRECISION_INT);
		final int messageLength = Utils.reassembleInt(messageLengthDisassembled);
		final int cipherLength = setup.length - nonceLength - Const.JAVA_MAX_PRECISION_INT;
		final boolean messageCompressed = messageLength > cipherLength;
		final boolean messageMIA = messageLength < 1;
		if(messageCompressed || messageMIA)
		{
			return null;
		}

		//get the cipher text
		final byte[] cipherText = new byte[cipherLength];
		System.arraycopy(setup, nonceLength+Const.JAVA_MAX_PRECISION_INT, cipherText, 0, cipherLength);
		final byte[] messageStorage= new byte[cipherLength];//store the message in somewhere it is guaranteed to fit in case messageLength is bogus/malicious

		boolean libsodiumOK = false;
		if(asym)
		{
			libsodiumOK = lazySodium.cryptoBoxOpenEasy(messageStorage, cipherText, cipherLength, nonce, senderPublic, myPrivate);
		}
		else
		{
			libsodiumOK = lazySodium.cryptoSecretBoxOpenEasy(messageStorage, cipherText, cipherLength, nonce, myPrivate);
		}

		if(!libsodiumOK)
		{
			Utils.logcat(Const.LOGE, tag, "sodium decryption failed, asym: " + asym + " return code: " + libsodiumOK);;
			return null;
		}

		//now that the message has been successfully decrypted, take in on blind faith messageLength was ok
		//	up to the next function to make sure the decryption contents aren't truncated by a malicious messageLength
		final byte[] message = new byte[messageLength];
		System.arraycopy(messageStorage, 0, message, 0, messageLength);
		return message;
	}

	public static byte[] interpretKey(byte[] dump, boolean isPrivate)
	{
		//dump the file contents
		final int headerLength = isPrivate ? Const.SODIUM_PRIVATE_HEADER.length() : Const.SODIUM_PUBLIC_HEADER.length();
		final int expectedLength = headerLength + Box.PUBLICKEYBYTES*Const.STRINGIFY_EXPANSION;
		if(dump == null || dump.length != expectedLength)
		{
			Utils.applyFiller(dump);
			return null;
		}

		//see if the file has the correct header
		final byte[] dumpHeader = new byte[headerLength];
		System.arraycopy(dump, 0, dumpHeader, 0, dumpHeader.length);
		final byte[] headerBytes = isPrivate ? Const.SODIUM_PRIVATE_HEADER.getBytes() : Const.SODIUM_PUBLIC_HEADER.getBytes();
		if(!Arrays.equals(dumpHeader, headerBytes))
		{
			return null;
		}


		final byte[] keyStringified = new byte[Box.PUBLICKEYBYTES*Const.STRINGIFY_EXPANSION];
		System.arraycopy(dump, headerLength, keyStringified, 0, keyStringified.length);
		Utils.applyFiller(dump);

		//check if the stringified key length makes sense
		if((keyStringified.length % Const.STRINGIFY_EXPANSION) != 0)
		{
			Utils.applyFiller(keyStringified);
			return null;
		}

		//turn the stringified binary into actual binary
		final int ASCII_OFFSET = 48;
		byte[] result = new byte[keyStringified.length/Const.STRINGIFY_EXPANSION];
		for(int i=0; i<keyStringified.length; i=i+Const.STRINGIFY_EXPANSION)
		{
			int hundreds = (keyStringified[i]-ASCII_OFFSET)*100;
			int tens = (keyStringified[i+1]-ASCII_OFFSET)*10;
			int ones = (keyStringified[i+2]-ASCII_OFFSET);
			int actual = hundreds + tens + ones;
			result[i/Const.STRINGIFY_EXPANSION] = (byte)(actual & Const.UNSIGNED_CHAR_MAX);
			hundreds = tens = ones = 0;
		}
		return result;
	}

	public static byte[] readKeyFileBytes(Uri uri, Context context)
	{
		try
		{
			//read the public key and convert to a string
			final ContentResolver resolver = context.getContentResolver();
			final InputStream inputStream = resolver.openInputStream(uri);
			final int longerHeader = Math.max(Const.SODIUM_PRIVATE_HEADER.length(), Const.SODIUM_PUBLIC_HEADER.length());
			final int maximumRead = longerHeader + Box.PUBLICKEYBYTES*Const.STRINGIFY_EXPANSION;
			final byte[] fileBytes = new byte[maximumRead];
			final int amountRead = inputStream.read(fileBytes);
			inputStream.close();

			final byte[] result = new byte[amountRead];
			System.arraycopy(fileBytes, 0, result, 0, amountRead);
			Utils.applyFiller(fileBytes);

			return result;
		}
		catch(Exception e)
		{
			Utils.dumpException(tag, e);
			return null;
		}
	}
}
