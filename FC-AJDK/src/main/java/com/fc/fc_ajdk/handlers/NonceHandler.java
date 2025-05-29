package com.fc.fc_ajdk.handlers;
import static com.fc.fc_ajdk.constants.FieldNames.WINDOW_TIME;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.fc.fc_ajdk.config.Settings;
import com.fc.fc_ajdk.data.fcData.FcEntity;

public class NonceHandler extends Handler<FcEntity>{
    private final Map<Integer, Long> nonceMap;
    private final Long windowTime;
    private final ReadWriteLock lock;
    private static final Random random = new Random();

    public NonceHandler(Settings settings) {
        super(HandlerType.NONCE);
        this.windowTime = ((Number) settings.getSettingMap().get(WINDOW_TIME)).longValue();
        this.nonceMap = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
    }

    public static  boolean isBadTime(long userTime, long windowTime){
        if(windowTime==0)return false;
        long currentTime = System.currentTimeMillis();
        return Math.abs(currentTime - userTime) > windowTime;
    }

    public void putNonce(Integer nonce) {
        if (nonce == null) return;
        lock.writeLock().lock();
        try {
            nonceMap.put(nonce, System.currentTimeMillis());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeTimeOutNonce() {
        long currentTime = System.currentTimeMillis();
        lock.writeLock().lock();
        try {
            nonceMap.entrySet().removeIf(entry -> 
                currentTime - entry.getValue() > windowTime);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isBadNonce(Integer nonce) {
        if (nonce == null) return true;
        
        lock.readLock().lock();
        try {
            Long birthTime = nonceMap.get(nonce);
            if (birthTime == null) return false;

            long currentTime = System.currentTimeMillis();
            if (currentTime - birthTime > windowTime) {
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    nonceMap.remove(nonce);
                    return false;
                } finally {
                    lock.writeLock().unlock();
                    lock.readLock().lock();
                }
            }
            return true;
        } finally {
            lock.readLock().unlock();
        }
    }

    public static Integer newNonce() {
        return random.nextInt();
    }
}