package com.musabgpt.m5;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypted, crash-resistant editor drafts, independent of Termux availability.
 * One AES-GCM record per workspace file in app-private SharedPreferences.
 * Never touches the project file until the user explicitly presses Save.
 */
public final class DraftVault {
    private static final String ALIAS="m5.studio.draft.v1";
    private static final String PREFIX="draft_";
    private static final int MAX_DOCUMENT_BYTES=40_000;
    private static final int MAX_DRAFTS=12;
    private final SharedPreferences store;

    public static final class Draft {
        public final String path,contents,savedHash;
        public final long timestamp;
        Draft(String path,String contents,String savedHash,long timestamp){
            this.path=path;this.contents=contents;this.savedHash=savedHash;this.timestamp=timestamp;
        }
    }
    public DraftVault(Context context) {
        store=context.getSharedPreferences("m5-studio-encrypted-drafts",Context.MODE_PRIVATE);
    }
    private SecretKey secret() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if(!ks.containsAlias(ALIAS)){
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
            generator.generateKey();
        }
        SecretKey result=(SecretKey)ks.getKey(ALIAS,null);
        if(result==null)throw new IllegalStateException("Editor encryption key unavailable");
        return result;
    }
    private String id(String path) throws Exception {
        if(path==null||path.isEmpty()||path.length()>500||path.contains("\0"))
            throw new IllegalArgumentException("Invalid draft path");
        byte[] digest=MessageDigest.getInstance("SHA-256")
            .digest(path.getBytes(StandardCharsets.UTF_8));
        StringBuilder key=new StringBuilder(PREFIX);
        for(byte b:digest)key.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return key.toString();
    }
    public void put(String path,String contents,String savedHash) throws Exception {
        if(contents==null||contents.getBytes(StandardCharsets.UTF_8).length>MAX_DOCUMENT_BYTES)
            throw new IllegalArgumentException("Editor draft exceeds 40 KB limit");
        String key=id(path);
        if(!store.contains(key)&&count()>=MAX_DRAFTS)
            throw new IllegalStateException("12 unsaved drafts retained. Save or discard an existing draft.");
        JSONObject record=new JSONObject()
            .put("path",path).put("text",contents)
            .put("hash",savedHash==null?"":savedHash)
            .put("time",System.currentTimeMillis());
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,secret());
        String token=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"
            +Base64.encodeToString(cipher.doFinal(
                record.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        // commit() is synchronous: do not claim protection until persisted.
        if(!store.edit().putString(key,token).commit())
            throw new IllegalStateException("Android failed to persist encrypted draft");
    }
    public Draft get(String path) throws Exception {
        String raw=store.getString(id(path),"");
        if(raw.isEmpty())return null;
        String[] encoded=raw.split(":",-1);
        if(encoded.length!=2)throw new IllegalStateException("Corrupt draft metadata");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,secret(),
            new GCMParameterSpec(128,Base64.decode(encoded[0],Base64.DEFAULT)));
        JSONObject obj=new JSONObject(new String(cipher.doFinal(
            Base64.decode(encoded[1],Base64.DEFAULT)),StandardCharsets.UTF_8));
        if(!path.equals(obj.optString("path","")))
            throw new IllegalStateException("Encrypted draft path mismatch");
        String contents=obj.getString("text");
        if(contents.getBytes(StandardCharsets.UTF_8).length>MAX_DOCUMENT_BYTES)
            throw new IllegalStateException("Oversize stored draft");
        return new Draft(path,contents,obj.optString("hash",""),obj.optLong("time",0));
    }
    public void remove(String path) throws Exception {
        if(!store.edit().remove(id(path)).commit())
            throw new IllegalStateException("Could not discard stored draft");
    }
    public int count() {
        int total=0;
        for(String key:store.getAll().keySet())if(key.startsWith(PREFIX))total++;
        return total;
    }
    public ArrayList<String> paths() throws Exception {
        ArrayList<String> paths=new ArrayList<>();
        // A draft path is decrypted solely within the local app.
        for(Map.Entry<String,?> item:store.getAll().entrySet()){
            if(!item.getKey().startsWith(PREFIX))continue;
            String value=String.valueOf(item.getValue());
            String[] parts=value.split(":",-1);
            if(parts.length!=2)continue;
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,secret(),
                new GCMParameterSpec(128,Base64.decode(parts[0],Base64.DEFAULT)));
            JSONObject json=new JSONObject(new String(cipher.doFinal(
                Base64.decode(parts[1],Base64.DEFAULT)),StandardCharsets.UTF_8));
            String path=json.optString("path","");
            if(!path.isEmpty())paths.add(path);
        }
        java.util.Collections.sort(paths);
        return paths;
    }
}
