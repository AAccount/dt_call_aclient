package dt.call.aclient;

import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.libsodium.jni.NaCl;
import org.libsodium.jni.Sodium;

import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class SodiumSymmetricDecrypt
{
	private static final String TEST_MESSAGE = "testing testing 1 2 3 blah blah blah filler content";


	@BeforeClass
	public static void initializeSodium()
	{
		NaCl.sodium();
		Sodium.sodium_init();
	}

	@Before
	public void setupKeys()
	{
		Vars.serverPublicSodium = new byte[Sodium.crypto_box_publickeybytes()];
		byte[] serverPrivateSodium = new byte[Sodium.crypto_box_secretkeybytes()];
		Sodium.crypto_box_keypair(Vars.serverPublicSodium, serverPrivateSodium);

		byte[] selfPublicSodium = new byte[Sodium.crypto_box_publickeybytes()];
		Vars.privateSodium = new byte[Sodium.crypto_box_secretkeybytes()];
		Sodium.crypto_box_keypair(selfPublicSodium, Vars.privateSodium);

		Vars.sodiumSymmetricKey = new byte[Sodium.crypto_secretbox_keybytes()];
		Sodium.randombytes_buf(Vars.sodiumSymmetricKey, Sodium.crypto_secretbox_keybytes());
	}

	//input[nonce|message length|encrypted]

	@Test
	public void ensureEmptyInputNull()
	{
		final byte[] nothing = new byte[0];
		final byte[] decrypted = Utils.sodiumSymDecrypt(nothing);
		assertNull(decrypted);
	}

	@Test
	public void ensureOnlyLengthAndNonceNull()
	{
		final int nonceLength = Sodium.crypto_secretbox_noncebytes();
		final int headerLength = Const.JAVA_MAX_PRECISION_INT + nonceLength;
		final byte[] wholeSetup = Utils.sodiumSymEncrypt(TEST_MESSAGE.getBytes());
		byte[] lengthAndNonce = new byte[headerLength];
		System.arraycopy(wholeSetup, 0, lengthAndNonce, 0, headerLength);

		final byte[] decrypted = Utils.sodiumSymDecrypt(lengthAndNonce);
		assertNull(decrypted);
	}

	@Test
	public void ensureOnlyLengthNonceWithRiggedLengthNull()
	{
		final int nonceLength = Sodium.crypto_secretbox_noncebytes();
		byte[] lengthAndNonce = new byte[Const.JAVA_MAX_PRECISION_INT + nonceLength];
		final byte[] nonce = new byte[nonceLength];
		Sodium.randombytes_buf(nonce, nonceLength);
		System.arraycopy(nonce, 0, lengthAndNonce, 0, nonceLength);

		//length = 100
		lengthAndNonce[nonceLength+(Const.JAVA_MAX_PRECISION_INT-1)] = 100;

		final byte[] decrypted = Utils.sodiumSymDecrypt(lengthAndNonce);
		assertNull(decrypted);
	}

	@Test
	public void ensureOnlyLengthNonceWithNegativeLengthNull()
	{
		final int nonceLength = Sodium.crypto_secretbox_noncebytes();
		byte[] lengthAndNonce = new byte[Const.JAVA_MAX_PRECISION_INT + nonceLength];
		final byte[] nonce = new byte[nonceLength];
		Sodium.randombytes_buf(nonce, nonceLength);
		System.arraycopy(nonce, 0, lengthAndNonce, 0, nonceLength);

		//length = 100
		lengthAndNonce[nonceLength+(Const.JAVA_MAX_PRECISION_INT-1)] = -100;

		final byte[] decrypted = Utils.sodiumSymDecrypt(lengthAndNonce);
		assertNull(decrypted);
	}

	@Test
	public void ensureOnlyLengthNonceWithLargerThanIntLengthNull()
	{
		final int nonceLength = Sodium.crypto_secretbox_noncebytes();
		byte[] lengthAndNonce = new byte[Const.JAVA_MAX_PRECISION_INT + nonceLength];
		final byte[] nonce = new byte[nonceLength];
		Sodium.randombytes_buf(nonce, nonceLength);
		System.arraycopy(nonce, 0, lengthAndNonce, 0, nonceLength);

		for(int i=0; i<Const.JAVA_MAX_PRECISION_INT; i++)
		{
			lengthAndNonce[nonceLength+i] = Byte.MAX_VALUE;
		}

		final byte[] decrypted = Utils.sodiumSymDecrypt(lengthAndNonce);
		assertNull(decrypted);
	}

	@Test
	public void ensureFakeCiphertextNull()
	{
		final int fakeCiphertextLength = 10;
		final int nonceLength = Sodium.crypto_secretbox_noncebytes();
		byte[] setup = new byte[Const.JAVA_MAX_PRECISION_INT + nonceLength + fakeCiphertextLength];
		final byte[] nonce = new byte[nonceLength];
		Sodium.randombytes_buf(nonce, nonceLength);
		System.arraycopy(nonce, 0, setup, 0, nonceLength);

		setup[nonceLength+(Const.JAVA_MAX_PRECISION_INT-1)] = fakeCiphertextLength;

		final byte[] fakeCiphertext = new byte[fakeCiphertextLength];
		Sodium.randombytes(fakeCiphertext, fakeCiphertextLength);
		System.arraycopy(fakeCiphertext, 0, setup, Const.JAVA_MAX_PRECISION_INT + nonceLength, fakeCiphertextLength);

		final byte[] decrypted = Utils.sodiumSymDecrypt(setup);
		assertNull(decrypted);
	}

	@Test
	public void ensureTamperedNonceNull()
	{
		byte[] setup = Utils.sodiumSymEncrypt("testing".getBytes());
		setup[0]++;

		final byte[] decrypted = Utils.sodiumSymDecrypt(setup);
		assertNull(decrypted);
	}

	@Test
	public void ensureTamperedCiphertextNull()
	{
		byte[] setup = Utils.sodiumSymEncrypt("testing".getBytes());
		final int nonceLength = Sodium.crypto_secretbox_noncebytes();
		setup[nonceLength + Const.JAVA_MAX_PRECISION_INT]++;

		final byte[] decrypted = Utils.sodiumSymDecrypt(setup);
		assertNull(decrypted);
	}

	@Test
	public void ensureTrimmedLengthNoCrash()
	{
		byte[] setup = Utils.sodiumSymEncrypt(TEST_MESSAGE.getBytes());
		final int nonceLength = Sodium.crypto_secretbox_noncebytes();
		byte endLengthByte = setup[nonceLength + Const.JAVA_MAX_PRECISION_INT - 1];
		endLengthByte = (byte)((int)endLengthByte / 2);
		setup[nonceLength + Const.JAVA_MAX_PRECISION_INT - 1] = endLengthByte;

		final byte[] decrypted = Utils.sodiumSymDecrypt(setup);
		assertTrue(decrypted.length == endLengthByte);
		//it has no way of knowing what the original length was, the best
		//it can do is just trim the output to whatever length you say
	}

	@Test
	public void ensureSodiumSymmDecryptActuallyWorks()
	{
		final byte[] setup = Utils.sodiumSymEncrypt(TEST_MESSAGE.getBytes());

		final byte[] decrypted = Utils.sodiumSymDecrypt(setup);
		final String message = new String(decrypted);
		assertTrue(message.equals(TEST_MESSAGE));
	}

	@Test
	public void ensureWrongKeyNull()
	{
		byte[] originalSymmetricKey = new byte[Sodium.crypto_secretbox_keybytes()];
		System.arraycopy(Vars.sodiumSymmetricKey, 0, originalSymmetricKey, 0, Sodium.crypto_secretbox_keybytes());
		Vars.sodiumSymmetricKey = new byte[Sodium.crypto_secretbox_keybytes()];
		Sodium.randombytes_buf(Vars.sodiumSymmetricKey, Sodium.crypto_secretbox_keybytes());

		final byte[] wrongSetup = Utils.sodiumSymEncrypt(TEST_MESSAGE.getBytes());

		System.arraycopy(originalSymmetricKey, 0, Vars.sodiumSymmetricKey, 0, Sodium.crypto_secretbox_keybytes());
		final byte[] decrypted = Utils.sodiumSymDecrypt(wrongSetup);
		assertNull(decrypted);
	}

	@Test
	public void ensureCompleteGarbageNull()
	{
		final byte[] nothing = Utils.sodiumSymDecrypt(TEST_MESSAGE.getBytes());
		assertNull(nothing);
	}
}
