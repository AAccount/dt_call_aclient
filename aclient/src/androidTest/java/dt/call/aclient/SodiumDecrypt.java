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
public class SodiumDecrypt
{
	@BeforeClass
	public static void initializeSodium() throws Exception
	{
		NaCl.sodium();
		Sodium.sodium_init();
	}

	@Before
	public void setupKeys()
	{
		Vars.serverPublicSodium = new byte[Sodium.crypto_box_publickeybytes()];
		Vars.privateSodium = new byte[Sodium.crypto_box_secretkeybytes()];
		Sodium.crypto_box_keypair(Vars.serverPublicSodium, Vars.privateSodium);

		Vars.sodiumSymmetricKey = new byte[Sodium.crypto_secretbox_keybytes()];
		Sodium.randombytes_buf(Vars.sodiumSymmetricKey, Sodium.crypto_secretbox_keybytes());
	}

	//input[nonce|message length|encrypted]

	@Test
	public void ensureEmptyInputNull()
	{
		byte[] nothing = new byte[0];
		byte[] decrypted = Utils.sodiumSymDecrypt(nothing);
		assertNull(decrypted);
	}
}
