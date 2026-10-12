package com.musabgpt.m5;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;

/** Encrypt optional free-tier API keys at rest with Android Keystore AES-GCM. */
public final class SecureKeys {
    private static final String ALIAS="m5.native.freeprovider.aesgcm.v1";
    private final SharedPreferences store;
    public SecureKeys(Context context) {
        store=context.getSharedPreferences("m5-keys-encrypted",Context.MODE_PRIVATE);
    }
    private SecretKey secret() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if(!ks.containsAlias(ALIAS)){
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            KeyGenParameterSpec spec=new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build();
            gen.init(spec);
            gen.generateKey();
        }
        SecretKey key=(SecretKey)ks.getKey(ALIAS,null);
        if(key==null)throw new IllegalStateException("Android Keystore key missing");
        return key;
    }
    public void put(int family,String value) throws Exception {
        if(family<1||family>6||value==null||value.length()>500)
            throw new IllegalArgumentException("Invalid API key");
        if(value.isEmpty()){
            store.edit().remove("token_"+family).apply();
            return;
        }
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,secret());
        byte[] iv=cipher.getIV();
        byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        store.edit().putString("token_"+family,
            Base64.encodeToString(iv,Base64.NO_WRAP)+":"+
            Base64.encodeToString(encrypted,Base64.NO_WRAP)).apply();
    }
    public String get(int family) throws Exception {
        String encoded=store.getString("token_"+family,"");
        if(encoded.isEmpty())return "";
        String[] pieces=encoded.split(":",-1);
        if(pieces.length!=2)throw new IllegalStateException("Invalid encrypted API key data");
        byte[] iv=Base64.decode(pieces[0],Base64.DEFAULT);
        byte[] ciphertext=Base64.decode(pieces[1],Base64.DEFAULT);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,secret(),new GCMParameterSpec(128,iv));
        return new String(cipher.doFinal(ciphertext),StandardCharsets.UTF_8);
    }
    public boolean has(int family){return !store.getString("token_"+family,"").isEmpty();}
}
