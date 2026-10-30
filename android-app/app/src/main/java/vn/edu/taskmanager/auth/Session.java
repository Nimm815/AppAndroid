package vn.edu.taskmanager.auth;
import android.content.Context;
/** Legacy storage selection only; retains existing data without requiring login. */
public final class Session {
    /** Retain an existing verified session without showing any authentication UI. */
    public static String token(Context c){
        String accountToken = AccountSession.token(c);
        if (!accountToken.isEmpty()) return accountToken;
        // Once this installation has used the account flow, logout must not fall
        // back to a token left by an older app version.
        if (AccountSession.authenticationRequired(c)) return "";
        String value=c.getSharedPreferences("auth_session",0).getString("token","");
        if(value.isEmpty())return "";
        try {
            java.security.KeyStore store=java.security.KeyStore.getInstance("AndroidKeyStore");store.load(null);
            String[] parts=value.split(":");
            javax.crypto.Cipher cipher=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE,store.getKey("taskmind_session",null),
                new javax.crypto.spec.GCMParameterSpec(128,android.util.Base64.decode(parts[0],android.util.Base64.NO_WRAP)));
            return new String(cipher.doFinal(android.util.Base64.decode(parts[1],android.util.Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
        }catch(Exception e){return "";}
    }
    public static int user(Context c){return AccountSession.userId(c);}
    public static String storage(Context c,String name){
        int active=user(c);
        int last=c.getSharedPreferences("taskmind_account_session",0).getInt("lastUserId",0);
        int owner=active>0?active:last;
        int legacy=c.getSharedPreferences("auth_session",0).getInt("legacy_owner",0);
        return owner==0||owner==legacy?name:name+"_user_"+owner;
    }
}
