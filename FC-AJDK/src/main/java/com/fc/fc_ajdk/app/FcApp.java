package com.fc.fc_ajdk.app;

import com.fc.fc_ajdk.config.Settings;
import com.fc.fc_ajdk.config.Configure;
import com.fc.fc_ajdk.core.crypto.Decryptor;

import static com.fc.fc_ajdk.ui.Inputer.askIfYes;

public abstract class FcApp {
    private Settings settings;

    public byte[] requestPriKey(){
        while(true){
            if(Configure.checkPassword(settings.getBr(),settings.getSymkey(),settings.getConfig())){
                byte[] priKey = Decryptor.decryptPrikey(settings.getMyPrikeyCipher(),settings.getSymkey());
                if(priKey!=null)return priKey;
            }
            if(settings.getBr()!=null && !askIfYes(settings.getBr(),"Wrong password. Try again?"))
                return null;
        }
    }

    public static boolean verifyPassword(Settings settings){
        return Configure.checkPassword(settings.getBr(),settings.getSymkey(),settings.getConfig());
    }
    public void close() {
        settings.close();
    }

    public Settings getSettings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }
}
