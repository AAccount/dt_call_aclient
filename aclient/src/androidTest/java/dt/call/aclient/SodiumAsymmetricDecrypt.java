package dt.call.aclient;

import android.support.test.runner.AndroidJUnit4;

import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.interfaces.Box;
import com.goterl.lazycode.lazysodium.utils.KeyPair;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class SodiumAsymmetricDecrypt
{
	private static final String TEST_MESSAGE = "testing testing 1 2 3";

	private byte[] otherPublic;
	private byte[] otherPrivate;
	private byte[] selfPublicSodium;
	private byte[] myPrivateKey;
	private LazySodiumAndroid lazySodium= new LazySodiumAndroid(new SodiumAndroid());

	@Before
	public void setupKeys()
	{
		try
		{
			KeyPair serverKeys = lazySodium.cryptoBoxKeypair();
			Vars.serverPublicSodium = serverKeys.getPublicKey().getAsBytes();

			myPrivateKey = new byte[Box.SECRETKEYBYTES];
			KeyPair myKeys = lazySodium.cryptoBoxKeypair();
			selfPublicSodium = myKeys.getPublicKey().getAsBytes();
			Vars.privateSodium = myKeys.getSecretKey().getAsBytes();
			System.arraycopy(Vars.privateSodium, 0, myPrivateKey, 0, Box.SECRETKEYBYTES);

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
		final byte[] decrypted = Utils.sodiumAsymDecrypt(nothing, otherPublic);
		assertNull(decrypted);
	}

	@Test
	public void ensureOnlyLengthAndNonceNull()
	{
		final int nonceLength = Box.NONCEBYTES;
		final int headerLength = Const.JAVA_MAX_PRECISION_INT + nonceLength;
		setupAsOther();
		final byte[] wholeSetup = Utils.sodiumAsymEncrypt(TEST_MESSAGE.getBytes(), selfPublicSodium);
		byte[] lengthAndNonce = new byte[headerLength];
		System.arraycopy(wholeSetup, 0, lengthAndNonce, 0, headerLength);

		setupAsMe();
		final byte[] decrypted = Utils.sodiumAsymDecrypt(lengthAndNonce, otherPublic);
		assertNull(decrypted);
	}

	@Test
	public void ensureOnlyLengthNonceWithRiggedLengthNull()
	{
		final int nonceLength = Box.NONCEBYTES;
		byte[] lengthAndNonce = new byte[Const.JAVA_MAX_PRECISION_INT + nonceLength];
		final byte[] nonce = lazySodium.randomBytesBuf( nonceLength);
		System.arraycopy(nonce, 0, lengthAndNonce, 0, nonceLength);

		//length = 100
		lengthAndNonce[nonceLength+(Const.JAVA_MAX_PRECISION_INT-1)] = 100;

		setupAsMe();
		final byte[] decrypted = Utils.sodiumAsymDecrypt(lengthAndNonce, otherPublic);
		assertNull(decrypted);
	}


	@Test
	public void ensureOnlyLengthNonceWithNegativeLengthNull()
	{
		final int nonceLength = Box.NONCEBYTES;
		byte[] lengthAndNonce = new byte[Const.JAVA_MAX_PRECISION_INT + nonceLength];
		final byte[] nonce = lazySodium.randomBytesBuf( nonceLength);
		System.arraycopy(nonce, 0, lengthAndNonce, 0, nonceLength);

		//length = 100
		lengthAndNonce[nonceLength+(Const.JAVA_MAX_PRECISION_INT-1)] = -100;

		setupAsMe();
		final byte[] decrypted = Utils.sodiumAsymDecrypt(lengthAndNonce, otherPublic);
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

		setupAsMe();
		final byte[] decrypted = Utils.sodiumAsymDecrypt(lengthAndNonce, otherPublic);
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

		final byte[] decrypted = Utils.sodiumAsymDecrypt(setup, otherPublic);
		assertNull(decrypted);
	}

	@Test
	public void ensureTamperedNonceNull()
	{
		setupAsOther();
		byte[] setup = Utils.sodiumAsymEncrypt(TEST_MESSAGE.getBytes(), selfPublicSodium);
		setup[0]++;

		setupAsMe();
		final byte[] decrypted = Utils.sodiumAsymDecrypt(setup, otherPublic);
		assertNull(decrypted);
	}

	@Test
	public void ensureTamperedCiphertextNull()
	{
		setupAsOther();
		byte[] setup = Utils.sodiumAsymEncrypt(TEST_MESSAGE.getBytes(), selfPublicSodium);
		final int nonceLength = Box.NONCEBYTES;
		setup[nonceLength + Const.JAVA_MAX_PRECISION_INT]++;

		setupAsMe();
		final byte[] decrypted = Utils.sodiumAsymDecrypt(setup, otherPublic);
		assertNull(decrypted);
	}

	@Test
	public void ensureTrimmedLengthNoCrash()
	{
		setupAsOther();
		byte[] setup = Utils.sodiumAsymEncrypt(TEST_MESSAGE.getBytes(), selfPublicSodium);
		final int nonceLength = Box.NONCEBYTES;
		byte endLengthByte = setup[nonceLength + Const.JAVA_MAX_PRECISION_INT - 1];
		endLengthByte = (byte)((int)endLengthByte / 2);
		setup[nonceLength + Const.JAVA_MAX_PRECISION_INT - 1] = endLengthByte;

		setupAsMe();
		final byte[] decrypted = Utils.sodiumAsymDecrypt(setup, otherPublic);
		assertTrue(decrypted.length == endLengthByte);
		//it has no way of knowing what the original length was, the best
		//it can do is just trim the output to whatever length you say
	}

	@Test
	public void ensureSodiumAsymmDecryptActuallyWorks()
	{
		setupAsOther();
		final byte[] setup = Utils.sodiumAsymEncrypt(TEST_MESSAGE.getBytes(), selfPublicSodium);

		setupAsMe();
		final byte[] decrypted = Utils.sodiumAsymDecrypt(setup, otherPublic);
		final String message = new String(decrypted);
		assertTrue(message.equals(TEST_MESSAGE));
	}

	@Test
	public void ensureCompleteGarbageNull()
	{
		final byte[] nothing = Utils.sodiumAsymDecrypt(TEST_MESSAGE.getBytes(), otherPublic);
		assertNull(nothing);
	}

	@Test
	public void ensureWrongSignerNull()
	{
		try
		{
			KeyPair mysteryKeys = lazySodium.cryptoBoxKeypair();
			byte[] mysteryPrivate = mysteryKeys.getSecretKey().getAsBytes();
			System.arraycopy(mysteryPrivate, 0, Vars.privateSodium, 0, Box.SECRETKEYBYTES);
			final byte[] setup = Utils.sodiumAsymEncrypt(TEST_MESSAGE.getBytes(), selfPublicSodium);

			setupAsMe();
			final byte[] decrypted = Utils.sodiumAsymDecrypt(setup, otherPublic);
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
			System.arraycopy(mysteryPrivate, 0, Vars.privateSodium, 0, Box.SECRETKEYBYTES);
			final byte[] setup = Utils.sodiumAsymEncrypt(TEST_MESSAGE.getBytes(), otherPublic);

			setupAsMe();
			final byte[] decrypted = Utils.sodiumAsymDecrypt(setup, otherPublic);
			assertTrue(decrypted == null);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}

	private void setupAsMe()
	{
		System.arraycopy(myPrivateKey, 0, Vars.privateSodium, 0, Box.SECRETKEYBYTES);
	}

	private void setupAsOther()
	{
		System.arraycopy(otherPrivate, 0, Vars.privateSodium, 0, Box.SECRETKEYBYTES);
	}
}
