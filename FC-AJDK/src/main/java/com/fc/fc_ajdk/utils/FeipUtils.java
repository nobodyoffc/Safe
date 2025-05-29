package com.fc.fc_ajdk.utils;

import com.fc.fc_ajdk.data.fchData.OpReturn;
import com.fc.fc_ajdk.data.feipData.CidOpData;
import com.fc.fc_ajdk.data.feipData.Feip;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.fc.fc_ajdk.data.feipData.MasterOpData;
import com.fc.fc_ajdk.utils.TimberLogger;

import static com.fc.fc_ajdk.constants.Constants.FEIP;
import static com.fc.fc_ajdk.constants.Constants.Master;
import static com.fc.fc_ajdk.constants.FieldNames.CID;
import static com.fc.fc_ajdk.constants.OpNames.REGISTER;
import static com.fc.fc_ajdk.constants.OpNames.UNREGISTER;

public class FeipUtils {

    public static Feip parseFeip(OpReturn opre) {

        if(opre.getOpReturn()==null)return null;

        Feip feip = null;
        try {
            String json = JsonUtils.strToJson(opre.getOpReturn());
            feip = new Gson().fromJson(json, Feip.class);
        }catch(JsonSyntaxException e) {
            TimberLogger.d("Bad json on {}. ", opre.getId());
        }
        return  feip;
    }

    public static boolean isGoodCidName(String cid) {
        if(cid==null
                ||cid.equals("")
                ||cid.contains(" ")
                ||cid.contains("@")
                ||cid.contains("/")
        )return false;
        return true;
    }
    public static String getCidRegisterData(String name) {
        return getCidData(name,REGISTER);
    }

    public static String getMasterData(String master, String masterPubKey, String priKeyCipher) {
        Feip data = new Feip();
        data.setType(FEIP);
        data.setSn(String.valueOf(6));
        data.setName(Master);
        data.setVer(String.valueOf(6));

        MasterOpData masterOpData = new MasterOpData();
        masterOpData.setMaster(master);
        masterOpData.setPromise(MasterOpData.PROMISE);
        masterOpData.setCipherPriKey(priKeyCipher);

        data.setData(masterOpData);

        return JsonUtils.toJson(data);
    }
    public static String getCidUnregisterData() {
        return getCidData(null,UNREGISTER);
    }

    public static String getCidData(String name,String op) {
        Feip data = new Feip();
        data.setType(FEIP);
        data.setSn(String.valueOf(3));
        data.setName(CID);
        data.setVer(String.valueOf(4));

        CidOpData cidOpData = new CidOpData();
        cidOpData.setOp(op);
        if(name!=null) cidOpData.setName(name);

        data.setData(cidOpData);
        return JsonUtils.toJson(data);
    }
}
