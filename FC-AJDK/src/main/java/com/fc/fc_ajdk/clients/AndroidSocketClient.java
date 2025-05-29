package com.fc.fc_ajdk.clients;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.fc.fc_ajdk.data.fcData.TalkUnit;
import com.fc.fc_ajdk.utils.TcpUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import timber.log.Timber;

/**
 * Android-compatible socket client implementation to replace Netty
 */
public class AndroidSocketClient {
    private static final String TAG = "AndroidSocketClient";
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final int RECONNECT_DELAY_MS = 2000;
    
    private final String host;
    private final int port;
    private final TalkClient talkClient;
    private final Handler mainHandler;
    private final ExecutorService executorService;
    
    private Socket socket;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    
    public AndroidSocketClient(String apiUrl, TalkClient talkClient) throws IOException {
        URL url = new URL(apiUrl);
        this.host = url.getHost();
        this.port = url.getPort() > 0 ? url.getPort() : 80;
        this.talkClient = talkClient;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executorService = Executors.newSingleThreadExecutor();
    }
    
    public void connect() {
        if (running.get()) {
            Timber.d("Client is already running");
            return;
        }
        
        running.set(true);
        executorService.execute(this::connectAndListen);
    }
    
    private void connectAndListen() {
        try {
            connectToServer();
            listenForMessages();
        } catch (Exception e) {
            Timber.e(e, "Error in connectAndListen");
            handleConnectionError();
        }
    }
    
    private void connectToServer() throws IOException {
        Timber.d("Connecting to %s:%d", host, port);
        socket = new Socket(host, port);
        inputStream = new DataInputStream(socket.getInputStream());
        outputStream = new DataOutputStream(socket.getOutputStream());
        Timber.d("Connected to server");
    }
    
    private void listenForMessages() {
        while (running.get()) {
            try {
                byte[] messageBytes = TcpUtils.readBytes(inputStream);
                if (messageBytes == null) {
                    // Connection closed by server
                    Timber.d("Connection closed by server");
                    break;
                }
                
                // Process the message
                processMessage(messageBytes);
            } catch (IOException e) {
                Timber.e(e, "Error reading from socket");
                break;
            }
        }
        
        // If we exit the loop and we're still supposed to be running, try to reconnect
        if (running.get() && !reconnecting.get()) {
            handleConnectionError();
        }
    }
    
    private void processMessage(byte[] messageBytes) {
        // Add the message to the received queue for processing
        TalkUnit talkUnit = TalkUnit.fromBytes(messageBytes);
        if (talkUnit != null) {
            talkClient.getReceivedQueue().offer(talkUnit);
        }
    }
    
    public boolean sendBytes(byte[] bytes) {
        if (!running.get() || socket == null || !socket.isConnected()) {
            Timber.e("Cannot send bytes: client not running or socket not connected");
            return false;
        }
        
        try {
            return TcpUtils.writeBytes(outputStream, bytes);
        } catch (Exception e) {
            Timber.e(e, "Error sending bytes");
            return false;
        }
    }
    
    private void handleConnectionError() {
        if (reconnecting.get()) {
            return;
        }
        
        reconnecting.set(true);
        executorService.execute(this::tryReconnect);
    }
    
    private void tryReconnect() {
        for (int attempt = 1; attempt <= MAX_RECONNECT_ATTEMPTS; attempt++) {
            try {
                Timber.d("Attempting to reconnect... (Attempt %d/%d)", attempt, MAX_RECONNECT_ATTEMPTS);
                
                // Close existing socket if any
                closeSocket();
                
                // Create new connection
                connectToServer();
                
                // If we get here, connection was successful
                Timber.d("Successfully reconnected to server");
                reconnecting.set(false);
                return;
            } catch (Exception e) {
                Timber.e(e, "Reconnection attempt %d failed", attempt);
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        // If we get here, all reconnection attempts failed
        Timber.e("Failed to reconnect after %d attempts", MAX_RECONNECT_ATTEMPTS);
        stop();
    }
    
    private void closeSocket() {
        try {
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            if (socket != null) {
                socket.close();
                socket = null;
            }
        } catch (IOException e) {
            Timber.e(e, "Error closing socket");
        }
    }
    
    public void stop() {
        running.set(false);
        reconnecting.set(false);
        closeSocket();
        executorService.shutdown();
    }
    
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
} 