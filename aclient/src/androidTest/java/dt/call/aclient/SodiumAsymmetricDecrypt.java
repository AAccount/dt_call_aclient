package dt.call.aclient;

import android.support.test.runner.AndroidJUnit4;

import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.interfaces.Box;
import com.goterl.lazycode.lazysodium.utils.KeyPair;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import dt.call.aclient.sodium.SodiumUtils;

import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class SodiumAsymmetricDecrypt
{
	private static final String TEST_MESSAGE = "testing testing 1 2 3";

	private byte[] otherPublic;
	private byte[] otherPrivate;
	private byte[] myPublicSodium;
	private byte[] myPrivateKey;
	private LazySodiumAndroid lazySodium= new LazySodiumAndroid(new SodiumAndroid());

	@Before
	public void setupKeys()
	{
		try
		{
			KeyPair serverKeys = lazySodium.cryptoBoxKeypair();
			Vars.serverPublicSodium = serverKeys.getPublicKey().getAsBytes();

			KeyPair myKeys = lazySodium.cryptoBoxKeypair();
			myPublicSodium = myKeys.getPublicKey().getAsBytes();
			myPrivateKey = myKeys.getSecretKey().getAsBytes();

			KeyPair otherKeys = lazySodium.cryptoBoxKeypair();
			otherPublic = otherKeys.getPublicKey().getAsBytes();
			otherPrivate = otherKeys.getSecretKey().getAsBytes();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void ensureEmptyInputNull()
	{
		final byte[] nothing = new byte[0];
		final byte[] decrypted = SodiumUtils.asymmetricDecrypt(nothing, otherPublic, myPrivateKey);
		assertNull(decrypted);
	}

	@Test
	public void ensureOnlyLengthAndNonceNull()
	{
		final int nonceLength = Box.NONCEBYTES;
		final int headerLength = Const.JAVA_MAX_PRECISION_INT + nonceLength;
		final byte[] wholeSetup = SodiumUtils.asymmetricEncrypt(TEST_MESSAGE.getBytes(), myPublicSodium, otherPrivate);
		byte[] lengthAndNonce = new byte[headerLength];
		System.arraycopy(wholeSetup, 0, lengthAndNonce, 0, headerLength);

		final byte[] decrypted = SodiumUtils.asymmetricDecrypt(lengthAndNonce, otherPublic, myPrivateKey);
		assertNull(decrypted);
	}

	@Test
	public void ensureOnlyLengthNonceWithRiggedLengthNull()
	{
		final int nonceLength = Box.NONCEBYTES;
		byte[] lengthAndNonce = new byte[Const.JAVA_MAX_PRECISION_INT + nonceLength];
		final byte[] nonce = lazySodium.randomBytesBuf( nonceLength);
		System.arraycopy(nonce, 0, lengthAndNonce, 0, nonceLength);

		lengthAndNonce[nonceLength+(Const.JAVA_MAX_PRECISION_INT-1)] = 100;

		final byte[] decrypted = SodiumUtils.asymmetricDecrypt(lengthAndNonce, otherPublic, myPrivateKey);
		assertNull(decrypted);
	}


	@Test
	public void ensureOnlyLengthNonceWithNegativeLengthNull()
	{
		final int nonceLength = Box.NONCEBYTES;
		byte[] lengthAndNonce = new byte[Const.JAVA_MAX_PRECISION_INT + nonceLength];
		final byte[] nonce = lazySodium.randomBytesBuf( nonceLength);
		System.arraycopy(nonce, 0, lengthAndNonce, 0, nonceLength);

		lengthAndNonce[nonceLength+(Const.JAVA_MAX_PRECISION_INT-1)] = -100;

		final byte[] decrypted = SodiumUtils.asymmetricDecrypt(lengthAndNonce, otherPublic, myPrivateKey);
		assertNull(decrypted);
	}

	@Test
	public void ensureOnlyLengthNonceWithLargerThanIntLengthNull()
	{
		final int nonceLength = Box.NONCEBYTES;
		byte[] lengthAndNonce = new byte[Const.JAVA_MAX_PRECISION_INT + nonceLength];
		final byte[] nonce = lazySodium.randomBytesBuf( nonceLength);
		System.arraycopy(nonce, 0, lengthAndNonce, 0, nonceLength);

		for(int i=0; i<Const.JAVA_MAX_PRECISION_INT; i++)
		{
			lengthAndNonce[nonceLength+i] = Byte.MAX_VALUE;
		}

		final byte[] decrypted = SodiumUtils.asymmetricDecrypt(lengthAndNonce, otherPublic, myPrivateKey);
		assertNull(decrypted);
	}

	@Test
	public void ensureFakeCiphertextNull()
	{
		final int fakeCiphertextLength = 10;
		final int nonceLength = Box.NONCEBYTES;
		byte[] setup = new byte[Const.JAVA_MAX_PRECISION_INT + nonceLength + fakeCiphertextLength];
		final byte[] nonce = lazySodium.randomBytesBuf( nonceLength);
		System.arraycopy(nonce, 0, setup, 0, nonceLength);

		setup[nonceLength+(Const.JAVA_MAX_PRECISION_INT-1)] = fakeCiphertextLength;

		final byte[] fakeCiphertext = lazySodium.randomBytesBuf(fakeCiphertextLength);
		System.arraycopy(fakeCiphertext, 0, setup, Const.JAVA_MAX_PRECISION_INT + nonceLength, fakeCiphertextLength);

		final byte[] decrypted = SodiumUtils.asymmetricDecrypt(setup, otherPublic, myPrivateKey);
		assertNull(decrypted);
	}

	@Test
	public void ensureTamperedNonceNull()
	{
		byte[] setup = SodiumUtils.asymmetricEncrypt(TEST_MESSAGE.getBytes(), myPublicSodium, otherPrivate);
		setup[0]++;

		final byte[] decrypted = SodiumUtils.asymmetricDecrypt(setup, otherPublic, myPrivateKey);
		assertNull(decrypted);
	}

	@Test
	public void ensureTamperedCiphertextNull()
	{
		byte[] setup = SodiumUtils.asymmetricEncrypt(TEST_MESSAGE.getBytes(), myPublicSodium, otherPrivate);
		final int nonceLength = Box.NONCEBYTES;
		setup[nonceLength + Const.JAVA_MAX_PRECISION_INT]++;

		final byte[] decrypted = SodiumUtils.asymmetricDecrypt(setup, otherPublic, myPrivateKey);
		assertNull(decrypted);
	}

	@Test
	public void ensureTrimmedLengthNoCrash()
	{
		byte[] setup = SodiumUtils.asymmetricEncrypt(TEST_MESSAGE.getBytes(), myPublicSodium, otherPrivate);
		final int nonceLength = Box.NONCEBYTES;
		byte endLengthByte = setup[nonceLength + Const.JAVA_MAX_PRECISION_INT - 1];
		endLengthByte = (byte)((int)endLengthByte / 2);
		setup[nonceLength + Const.JAVA_MAX_PRECISION_INT - 1] = endLengthByte;

		final byte[] decrypted = SodiumUtils.asymmetricDecrypt(setup, otherPublic, myPrivateKey);
		assertTrue(decrypted.length == endLengthByte);
		//it has no way of knowing what the original length was, the best
		//it can do is just trim the output to whatever length you say
	}

	@Test
	public void ensureSodiumAsymmDecryptActuallyWorks()
	{
		final byte[] setup = SodiumUtils.asymmetricEncrypt(TEST_MESSAGE.getBytes(), myPublicSodium, otherPrivate);

		final byte[] decrypted = SodiumUtils.asymmetricDecrypt(setup, otherPublic, myPrivateKey);
		final String message = new String(decrypted);
		assertTrue(message.equals(TEST_MESSAGE));
	}

	@Test
	public void ensureCompleteGarbageNull()
	{
		final byte[] nothing = SodiumUtils.asymmetricDecrypt(TEST_MESSAGE.getBytes(), otherPublic, myPrivateKey);
		assertNull(nothing);
	}

	@Test
	public void ensureWrongSignerNull()
	{
		try
		{
			KeyPair mysteryKeys = lazySodium.cryptoBoxKeypair();
			byte[] mysteryPrivate = mysteryKeys.getSecretKey().getAsBytes();
			final byte[] setup = SodiumUtils.asymmetricEncrypt(TEST_MESSAGE.getBytes(), myPublicSodium, mysteryPrivate);

			final byte[] decrypted = SodiumUtils.asymmetricDecrypt(setup, otherPublic, myPrivateKey);
			assertTrue(decrypted == null);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void ensureNotAddressedForMeNull()
	{
		try
		{
			KeyPair mysteryKeys = lazySodium.cryptoBoxKeypair();
			byte[] mysteryPrivate = mysteryKeys.getSecretKey().getAsBytes();
			final byte[] setup = SodiumUtils.asymmetricEncrypt(TEST_MESSAGE.getBytes(), otherPublic, mysteryPrivate);

			final byte[] decrypted = SodiumUtils.asymmetricDecrypt(setup, otherPublic, myPrivateKey);
			assertTrue(decrypted == null);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}
}
