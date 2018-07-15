package dt.call.aclient;

import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.libsodium.jni.NaCl;
import org.libsodium.jni.Sodium;

import static junit.framework.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class SodiumAsymmetricDecrypt
{
	private static final String TEST_MESSAGE = "asymmetric testing testing 1 2 3 blah blah blah filler content";

	private byte[] otherPublic;
	private byte[] otherPrivate;
	private byte[] selfPublicSodium;
	private byte[] myPrivateKey;

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

		selfPublicSodium = new byte[Sodium.crypto_box_publickeybytes()];
		Vars.privateSodium = new byte[Sodium.crypto_box_secretkeybytes()];
		Sodium.crypto_box_keypair(selfPublicSodium, Vars.privateSodium);
		myPrivateKey = new byte[Sodium.crypto_box_secretkeybytes()];
		System.arraycopy(Vars.privateSodium, 0, myPrivateKey, 0, Sodium.crypto_box_secretkeybytes());

		otherPublic = new byte[Sodium.crypto_box_publickeybytes()];
		otherPrivate = new byte[Sodium.crypto_box_secretkeybytes()];
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
		final int nonceLength = Sodium.crypto_box_noncebytes();
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
		final int nonceLength = Sodium.crypto_box_noncebytes();
		byte[] lengthAndNonce = new byte[Const.JAVA_MAX_PRECISION_INT + nonceLength];
		final byte[] nonce = new byte[nonceLength];
		Sodium.randombytes_buf(nonce, nonceLength);
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
		final int nonceLength = Sodium.crypto_box_noncebytes();
		byte[] lengthAndNonce = new byte[Const.JAVA_MAX_PRECISION_INT + nonceLength];
		final byte[] nonce = new byte[nonceLength];
		Sodium.randombytes_buf(nonce, nonceLength);
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
		final int nonceLength = Sodium.crypto_box_noncebytes();
		byte[] lengthAndNonce = new byte[Const.JAVA_MAX_PRECISION_INT + nonceLength];
		final byte[] nonce = new byte[nonceLength];
		Sodium.randombytes_buf(nonce, nonceLength);
		System.arraycopy(nonce, 0, lengthAndNonce, 0, nonceLength);

		for(int i=0; i<Const.JAVA_MAX_PRECISION_INT; i++)
		{
			lengthAndNonce[nonceLength+i] = Byte.MAX_VALUE;
		}

		setupAsMe();
		final byte[] decrypted = Utils.sodiumAsymDecrypt(lengthAndNonce, otherPublic);
		assertNull(decrypted);
	}

	private void setupAsMe()
	{
		System.arraycopy(myPrivateKey, 0, Vars.privateSodium, 0, Sodium.crypto_box_secretkeybytes());
	}

	private void setupAsOther()
	{
		System.arraycopy(otherPrivate, 0, Vars.privateSodium, 0, Sodium.crypto_box_secretkeybytes());
	}
}
