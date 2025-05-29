package com.fc.fc_ajdk.handlers;

import com.fc.fc_ajdk.config.Settings;
import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.Encryptor;
import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.fc.fc_ajdk.data.fcData.FcSession;
import com.fc.fc_ajdk.utils.Hex;

import java.util.*;

import com.fc.fc_ajdk.utils.IdNameUtils;

/**
 * FcSessionClient is a client for managing sessions.
 * There are two ways to store sessions: 1) LevelDB for persistence; 2) JedisPool in Redis.
 */
public class SessionHandler extends Handler<FcSession> {
    private static final String ID_SESSION_NAME = "idSessionName";
    private static final String SESSIONS = "sessions";
    private static final String USED_SESSIONS = "usedSessions";
    private static final String USER_ID_SESSION_NAME_MAP = "userIdSessionName";
    
    private final String jedisNameSessionKey;
    private final String jedisUserIdNameKey;
    private final String jedisUsedSessionsKey;

    public SessionHandler(Settings settings) {
        super(settings, HandlerType.SESSION, LocalDB.SortType.UPDATE_ORDER, FcSession.class, true, false);

        this.jedisNameSessionKey = null;
        this.jedisUserIdNameKey = null;
        this.jedisUsedSessionsKey = null;

        // Create maps for LevelDB
        createMap(USER_ID_SESSION_NAME_MAP);
        createMap(USED_SESSIONS);
    }

    public FcSession getSessionByName(String sessionName) {
        if (sessionName == null) return null;
        return localDB.get(sessionName);
    }

    public void putSession(FcSession session) {
        if(session==null)return;
        if(session.getId()==null)
            if(session.getKey()!=null||session.getKeyBytes()!=null)session.makeId();

        String sessionName = session.getId();
        String fid = session.getUserId();

        localDB.put(sessionName, session);
        localDB.putInMap(USER_ID_SESSION_NAME_MAP, fid, sessionName);

        // Add to usedSessions
        List<String> sessionNames = localDB.getFromMap(USED_SESSIONS, fid);
        if (sessionNames == null) {
            sessionNames = new ArrayList<>();
        }
        if (!sessionNames.contains(sessionName)) {
            sessionNames.add(sessionName);
            localDB.putInMap(USED_SESSIONS, fid, sessionNames);
        }
    }

    public FcSession getSessionByUserId(String userId) {
        String sessionName = getNameByUserId(userId);
        return getSessionByName(sessionName);
    }

    public String getNameByUserId(String userId) {
        return localDB.getFromMap(USER_ID_SESSION_NAME_MAP, userId);
    }

    public void putUserIdSessionId(String userId, String sessionName) {
        localDB.putInMap(USER_ID_SESSION_NAME_MAP, userId, sessionName);
    }

    public void removeSession(String sessionName) {
        FcSession session = localDB.get(sessionName);
        if (session != null) {
            String fid = session.getUserId();
            remove(sessionName);
            localDB.removeFromMap(USER_ID_SESSION_NAME_MAP, fid);

            // Remove from usedSessions
            List<String> sessionNames = localDB.getFromMap(USED_SESSIONS, fid);
            if (sessionNames != null) {
                sessionNames.remove(sessionName);
                if (sessionNames.isEmpty()) {
                    localDB.removeFromMap(USED_SESSIONS, fid);
                } else {
                    localDB.putInMap(USED_SESSIONS, fid, sessionNames);
                }
            }
        }
    }

    public void removeSessionByUserId(String userId) {
        String sessionName = getNameByUserId(userId);
        if (sessionName == null) return;
        remove(sessionName);
        localDB.removeFromMap(USER_ID_SESSION_NAME_MAP, userId);
    }

    public void removeFidName(String userId) {
        localDB.removeFromMap(USER_ID_SESSION_NAME_MAP, userId);
    }

    public FcSession addNewSession(String fid, String userPubKey) {
        byte[] sessionKey = IdNameUtils.genNew32BytesKey();
        String sessionName = IdNameUtils.makeKeyName(sessionKey);

        // Set the new session
        FcSession session = new FcSession();
        session.setKeyBytes(sessionKey);
        session.setKey(Hex.toHex(sessionKey));
        session.setUserId(fid);
        session.setId(sessionName);
        session.setPubkey(userPubKey);
        session.setKeyCipher(makeKeyCipher(sessionKey, userPubKey));
        String oldSessionName = localDB.getFromMap(USER_ID_SESSION_NAME_MAP, fid);
        if (oldSessionName != null) {
            remove(oldSessionName);
            localDB.removeFromMap(USER_ID_SESSION_NAME_MAP, fid);
        }
        localDB.putInMap(USER_ID_SESSION_NAME_MAP, fid, sessionName);
        localDB.put(sessionName, session);
        return session;
    }

    private String makeKeyCipher(byte[] sessionKey, String userPubKey) {
        if (sessionKey == null || userPubKey == null) return null;
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = encryptor.encryptByAsyOneWay(sessionKey, Hex.fromHex(userPubKey));
        if (cryptoDataByte == null) return null;
        return cryptoDataByte.toJson();
    }

    public String getJedisNameSessionKey() {
        return jedisNameSessionKey;
    }

    public String getJedisUserIdNameKey() {
        return jedisUserIdNameKey;
    }

    public synchronized void putUsedSessions(String key, List<String> sessionNames) {
        if (key == null || sessionNames == null) return;
        
        // Create an unmodifiable copy of the sessions list for thread safety
        List<String> sessionNamesCopy = Collections.unmodifiableList(new ArrayList<>(sessionNames));
        localDB.putInMap(USED_SESSIONS, key, sessionNamesCopy);
    }

    public List<FcSession> getUsedSessions(String key) {
        if (key == null) return null;
        List<String> sessionNames = localDB.getFromMap(USED_SESSIONS, key);
        if (sessionNames == null) return null;

        // Convert session names to FcSession objects
        List<FcSession> sessions = new ArrayList<>();
        for (String name : sessionNames) {
            FcSession session = getSessionByName(name);
            if (session != null) {
                sessions.add(session);
            }
        }
        return Collections.unmodifiableList(sessions);
    }

    public synchronized void removeUsedSessions(String key) {
        if (key == null) return;
        localDB.removeFromMap(USED_SESSIONS, key);
    }
}
