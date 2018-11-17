package dt.call.aclient;

import android.support.test.runner.AndroidJUnit4;

import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.interfaces.Box;
import com.goterl.lazycode.lazysodium.utils.KeyPair;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import dt.call.aclient.sodium.SodiumUtils;

import static junit.framework.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class SodiumAsymmetricDecrypt
{
	private static final String TEST_MESSAGE = "testing testing 1 2 3";
	private static final int BUFFER_SIZE = 4096;

	private byte[] otherPublic;
	private byte[] otherPrivate;
	private byte[] myPublicSodium;
	private byte[] myPrivateKey;
	private LazySodiumAndroid lazySodium= new LazySodiumAndroid(new SodiumAndroid());

	private byte[] encryptionBuffer = new byte[BUFFER_SIZE];
	private byte[] decryptionBuffer = new byte[BUFFER_SIZE];

	@Before
	public void setupKeys()
	{
		try
		{
			final KeyPair serverKeys = lazySodium.cryptoBoxKeypair();
			Vars.serverPublicSodium = serverKeys.getPublicKey().getAsBytes();

			final KeyPair myKeys = lazySodium.cryptoBoxKeypair();
			myPublicSodium = myKeys.getPublicKey().getAsBytes();
			myPrivateKey = myKeys.getSecretKey().getAsBytes();

			final KeyPair otherKeys = lazySodium.cryptoBoxKeypair();
			otherPublic = otherKeys.getPublicKey().getAsBytes();
			otherPrivate = otherKeys.getSecretKey().getAsBytes();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	@Before
	public void clearBuffers()
	{
		Arrays.fill(encryptionBuffer, (byte)0);
		Arrays.fill(decryptionBuffer, (byte)0);
	}

	@Test
	public void ensureEmptyInputNothing()
	{
		final byte[] nothing = new byte[0];
		final int decryptedLength = SodiumUtils.asymmetricDecrypt(nothing, otherPublic, myPrivateKey, decryptionBuffer);
		Assert.assertTrue(decryptedLength == 0);
	}

	@Test
	public void ensureOnlyLengthAndNonceNothing()
	{
		final int nonceLength = Box.NONCEBYTES;
		final int headerLength = Const.SIZEOF_INT + nonceLength;
		final int wholeSetupLength = SodiumUtils.asymmetricEncrypt(TEST_MESSAGE.getBytes(), myPublicSodium, otherPrivate, encryptionBuffer);

		final int decryptedLength = SodiumUtils.asymmetricDecrypt(encryptionBuffer, headerLength, otherPublic, myPrivateKey, decryptionBuffer);
		Assert.assertTrue(decryptedLength == 0);
	}

	@Test
	public void ensureOnlyLengthNonceWithRiggedLengthNothing()
	{
		final int nonceLength = Box.NONCEBYTES;
		byte[] lengthAndNonce = new byte[Const.SIZEOF_INT + nonceLength];
		final byte[] nonce = lazySodium.randomBytesBuf( nonceLength);
		System.arraycopy(nonce, 0, lengthAndNonce, 0, nonceLength);

		lengthAndNonce[nonceLength+(Const.SIZEOF_INT -1)] = 100;

		final int decryptedLength = SodiumUtils.asymmetricDecrypt(lengthAndNonce, otherPublic, myPrivateKey, decryptionBuffer);
		Assert.assertTrue(decryptedLength == 0);
	}

	@Test
	public void ensureOnlyLengthNonceWithLargerThanSetupLengthNothing()
	{
		final int nonceLength = Box.NONCEBYTES;
		byte[] lengthAndNonce = new byte[Const.SIZEOF_INT + nonceLength];
		final byte[] nonce = lazySodium.randomBytesBuf( nonceLength);
		System.arraycopy(nonce, 0, lengthAndNonce, 0, nonceLength);

		for(int i = 0; i<Const.SIZEOF_INT; i++)
		{
			lengthAndNonce[nonceLength+i] = Byte.MAX_VALUE;
		}

		final int decryptedLength = SodiumUtils.asymmetricDecrypt(lengthAndNonce, otherPublic, myPrivateKey, decryptionBuffer);
		Assert.assertTrue(decryptedLength == 0);
	}

	@Test
	public void ensureFakeCiphertextNothing()
	{
		final int fakeCiphertextLength = 10;
		final int nonceLength = Box.NONCEBYTES;
		final byte[] nonce = lazySodium.randomBytesBuf( nonceLength);
		System.arraycopy(nonce, 0, encryptionBuffer, 0, nonceLength);

		encryptionBuffer[nonceLength+(Const.SIZEOF_INT -1)] = fakeCiphertextLength;

		final byte[] fakeCiphertext = lazySodium.randomBytesBuf(fakeCiphertextLength);
		System.arraycopy(fakeCiphertext, 0, encryptionBuffer, Const.SIZEOF_INT + nonceLength, fakeCiphertextLength);

		final int setupLength = nonceLength + Const.SIZEOF_INT + fakeCiphertextLength;
		final int decryptedLength = SodiumUtils.asymmetricDecrypt(encryptionBuffer, setupLength, otherPublic, myPrivateKey, decryptionBuffer);
		Assert.assertTrue(decryptedLength == 0);
	}

	@Test
	public void ensureTamperedNonceNothing()
	{
		final int encryptionLength = SodiumUtils.asymmetricEncrypt(TEST_MESSAGE.getBytes(), myPublicSodium, otherPrivate, encryptionBuffer);
		encryptionBuffer[0]++;

		final int decryptedLength = SodiumUtils.asymmetricDecrypt(encryptionBuffer, encryptionLength, otherPublic, myPrivateKey, decryptionBuffer);
		Assert.assertTrue(decryptedLength == 0);
	}

	@Test
	public void ensureTamperedCiphertextNothing()
	{
		final int encryptionLength = SodiumUtils.asymmetricEncrypt(TEST_MESSAGE.getBytes(), myPublicSodium, otherPrivate, encryptionBuffer);
		final int nonceLength = Box.NONCEBYTES;
		encryptionBuffer[nonceLength + Const.SIZEOF_INT]++;

		final int decryptedLength = SodiumUtils.asymmetricDecrypt(encryptionBuffer, encryptionLength, otherPublic, myPrivateKey, decryptionBuffer);
		Assert.assertTrue(decryptedLength == 0);
	}

	@Test
	public void ensureSodiumAsymmDecryptActuallyWorks()
	{
		final int encryptionLength = SodiumUtils.asymmetricEncrypt(TEST_MESSAGE.getBytes(), myPublicSodium, otherPrivate, encryptionBuffer);
		final int decryptionLength = SodiumUtils.asymmetricDecrypt(encryptionBuffer, encryptionLength, otherPublic, myPrivateKey, decryptionBuffer);
		final String message = new String(decryptionBuffer, 0, decryptionLength);
		Assert.assertEquals(message, TEST_MESSAGE);
	}

	@Test
	public void ensureCompleteGarbageNothing()
	{
		final int encryptionLength = SodiumUtils.asymmetricDecrypt(TEST_MESSAGE.getBytes(), otherPublic, myPrivateKey, encryptionBuffer);
		Assert.assertTrue(encryptionLength == 0);
	}

	@Test
	public void ensureWrongSenderNothing()
	{
		try
		{
			final KeyPair mysteryKeys = lazySodium.cryptoBoxKeypair();
			final byte[] mysteryPrivate = mysteryKeys.getSecretKey().getAsBytes();
			final int encryptionLength = SodiumUtils.asymmetricEncrypt(TEST_MESSAGE.getBytes(), myPublicSodium, mysteryPrivate, encryptionBuffer);

			final int decryptionLength = SodiumUtils.asymmetricDecrypt(encryptionBuffer, encryptionLength, otherPublic, myPrivateKey, decryptionBuffer);
			Assert.assertTrue(decryptionLength == 0);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void ensureNotAddressedForMeNothing()
	{
		try
		{
			final KeyPair mysteryKeys = lazySodium.cryptoBoxKeypair();
			final byte[] mysteryPrivate = mysteryKeys.getSecretKey().getAsBytes();
			final int encryptionLength = SodiumUtils.asymmetricEncrypt(TEST_MESSAGE.getBytes(), otherPublic, mysteryPrivate, encryptionBuffer);

			final int decryptionLength = SodiumUtils.asymmetricDecrypt(encryptionBuffer, encryptionLength, otherPublic, myPrivateKey, decryptionBuffer);
			Assert.assertTrue(decryptionLength == 0);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}
}
