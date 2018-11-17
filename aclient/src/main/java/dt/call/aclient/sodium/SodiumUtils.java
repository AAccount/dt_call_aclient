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

import dt.call.aclient.pool.ByteBufferPool;
import dt.call.aclient.Const;
import dt.call.aclient.Utils;

public class SodiumUtils
{
	private static final LazySodiumAndroid lazySodium = new LazySodiumAndroid(new SodiumAndroid());
	private static final String tag = "sodium_utils";
	private static final ByteBufferPool encryptionBuffers = new ByteBufferPool(10000);
	private static final ByteBufferPool decryptionBuffers = new ByteBufferPool(10000);
	private static final ByteBufferPool decryptionNonces = new ByteBufferPool(Box.NONCEBYTES);

	public static int asymmetricEncrypt(byte[] message, byte[] receiverPublic, byte[] myPrivate, byte[] output)
	{
		return encrypt(message, message.length,true, receiverPublic, myPrivate, output);
	}

	public static int symmetricEncrypt(byte[] message, int length, byte[] symkey, byte[] output)
	{
		return encrypt(message, length,false, null, symkey, output);
	}

	private static int encrypt(byte[] message, int length, boolean asym, byte[] receiverPublic, byte[] myPrivate, byte[] output)
	{
		if(length > message.length)
		{
			return 0;
		}

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
		byte[] cipherText = encryptionBuffers.getByteBuffer();
		if(asym)
		{
			cipherTextLength = Box.MACBYTES + length;
			libsodiumOK = lazySodium.cryptoBoxEasy(cipherText, message, length, nonce, receiverPublic, myPrivate);
		}
		else
		{
			cipherTextLength = SecretBox.MACBYTES + length;
			libsodiumOK = lazySodium.cryptoSecretBoxEasy(cipherText, message, length, nonce, myPrivate);
		}

		//something went wrong with the encryption
		if(!libsodiumOK)
		{
			Utils.logcat(Const.LOGE, tag, "sodium encryption failed, asym: " + asym + " return code: " + libsodiumOK);
			encryptionBuffers.returnBuffer(cipherText);
			return 0;
		}

		//glue all the information together for sending
		//[nonce|message length|encrypted message]
		final byte[] messageLengthDissasembled = Utils.disassembleInt(length);
		final int setupLength = nonceLength+Const.SIZEOF_INT +cipherTextLength;
		System.arraycopy(nonce, 0, output, 0, nonceLength);
		System.arraycopy(messageLengthDissasembled, 0, output, nonceLength, Const.SIZEOF_INT);
		System.arraycopy(cipherText, 0, output, nonceLength+Const.SIZEOF_INT, cipherTextLength);
		encryptionBuffers.returnBuffer(cipherText);
		return setupLength;
	}

	public static int asymmetricDecrypt(byte[] setup, byte[] senderPublic, byte[] myPrivate, byte[] output)
	{
		return decrypt(setup, setup.length, true, senderPublic, myPrivate, output);
	}

	public static int asymmetricDecrypt(byte[] setup, int setupLength, byte[] senderPublic, byte[] myPrivate, byte[] output)
	{
		return decrypt(setup, setupLength, true, senderPublic, myPrivate, output);
	}

	public static int symmetricDecrypt(byte[] setup, byte[] symkey, byte[] output)
	{
		return decrypt(setup, setup.length, false, null, symkey, output);
	}

	public static int symmetricDecrypt(byte[] setup, int setupLength, byte[] symkey, byte[] output)
	{
		return decrypt(setup, setupLength, false, null, symkey, output);
	}

	private static int decrypt(byte[] setup, int setupLength, boolean asym, byte[] senderPublic, byte[] myPrivate, byte[] output)
	{
		if(setupLength > setup.length)
		{
			return 0;
		}

		//[nonce|message length|encrypted message]
		//both nonce lengths are 24
		final int nonceLength = Box.NONCEBYTES;

		//check if more than just the nonce and message length are there
		if(setupLength <= (nonceLength + Const.SIZEOF_INT))
		{
			return 0;
		}

		//reassemble the nonce
		final byte[] nonce = decryptionNonces.getByteBuffer();
		System.arraycopy(setup, 0, nonce, 0, nonceLength);

		//get the message length and check it
		final byte[] messageLengthDisassembled = new byte[Const.SIZEOF_INT];
		System.arraycopy(setup, nonceLength, messageLengthDisassembled, 0, Const.SIZEOF_INT);
		final int messageLength = Utils.reassembleInt(messageLengthDisassembled);
		final int cipherLength = setupLength - nonceLength - Const.SIZEOF_INT;
		final boolean messageCompressed = messageLength > cipherLength;
		final boolean messageMIA = messageLength < 1;
		if(messageCompressed || messageMIA)
		{
			return 0;
		}

		//get the cipher text
		final byte[] cipherText = decryptionBuffers.getByteBuffer();
		System.arraycopy(setup, nonceLength+Const.SIZEOF_INT, cipherText, 0, cipherLength);
//		final byte[] messageStorage= new byte[cipherLength];//store the message in somewhere it is guaranteed to fit in case messageLength is bogus/malicious

		boolean libsodiumOK = false;
		if(asym)
		{
			libsodiumOK = lazySodium.cryptoBoxOpenEasy(output, cipherText, cipherLength, nonce, senderPublic, myPrivate);
		}
		else
		{
			libsodiumOK = lazySodium.cryptoSecretBoxOpenEasy(output, cipherText, cipherLength, nonce, myPrivate);
		}
		decryptionBuffers.returnBuffer(cipherText);
		decryptionNonces.returnBuffer(nonce);

		if(!libsodiumOK)
		{
			Utils.logcat(Const.LOGE, tag, "sodium decryption failed, asym: " + asym + " return code: " + libsodiumOK);;
			return 0;
		}

		//now that the message has been successfully decrypted, take in on blind faith messageLength was ok
		//	up to the next function to make sure the decryption contents aren't truncated by a malicious messageLength
//		final byte[] message = new byte[messageLength];
//		System.arraycopy(messageStorage, 0, message, 0, messageLength);
		return messageLength;
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
			Utils.applyFiller(dump);
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
		final byte[] result = new byte[keyStringified.length/Const.STRINGIFY_EXPANSION];
		for(int i=0; i<keyStringified.length; i=i+Const.STRINGIFY_EXPANSION)
		{
			int hundreds = (keyStringified[i]-ASCII_OFFSET)*100;
			int tens = (keyStringified[i+1]-ASCII_OFFSET)*10;
			int ones = (keyStringified[i+2]-ASCII_OFFSET);
			int actual = hundreds + tens + ones;
			result[i/Const.STRINGIFY_EXPANSION] = (byte)(actual & Const.UNSIGNED_CHAR_MAX);
			hundreds = tens = ones = 0;
		}
		Utils.applyFiller(keyStringified);
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
