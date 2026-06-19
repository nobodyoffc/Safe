package com.fc.fc_ajdk.data.fchData;


import android.content.Context;
import android.widget.Toast;

import com.fc.fc_ajdk.core.fch.RawTxInfo;
import com.fc.fc_ajdk.data.fcData.FcObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MultisignTxDetail extends FcObject {
    private String sender;
    private Map<String,String> cashIdAmountMap;
    private List<Cash> sendToList;
    private String opReturn;
    private String mOfN;
    private List<String> signedFidList;
    private List<String> unSignedFidList;
    private Integer restSignNum;


    public static MultisignTxDetail fromMultiSigData(RawTxInfo rawTxInfo, Context context){
        MultisignTxDetail multisignTxDetail = new MultisignTxDetail();

        Multisig multisig = rawTxInfo.getMultisign();
        if(multisig ==null){
            Toast.makeText(context,"Multisig can't be null.",Toast.LENGTH_LONG).show();
            return null;
        }
        if(multisig.getId()==null){
            Toast.makeText(context,"The FID of Multisig can't be null.",Toast.LENGTH_LONG).show();
            return null;
        }
        multisignTxDetail.setSender(multisig.getId());

        // Populate cashIdAmountMap from inputs
        Map<String, String> cashIdAmountMap = new HashMap<>();
        if (rawTxInfo.getInputs() != null && !rawTxInfo.getInputs().isEmpty()) {
            for (Cash cash : rawTxInfo.getInputs()) {
                String cashId = cash.getId();
                if (cashId == null || cashId.isEmpty()) {
                    cashId = cash.makeId();
                }
                // Convert satoshi to FCH for display
                double amountInFch = com.fc.fc_ajdk.utils.FchUtils.satoshiToCoin(cash.getValue());
                String amount = String.valueOf(amountInFch);
                cashIdAmountMap.put(cashId, amount);
            }
        }
        multisignTxDetail.setCashIdAmountMap(cashIdAmountMap);

        multisignTxDetail.setSendToList(rawTxInfo.getOutputs());
        multisignTxDetail.setOpReturn(rawTxInfo.getOpReturn());
        multisignTxDetail.setmOfN(multisig.getM() + "/"+ multisig.getN());
        multisignTxDetail.setUnSignedFidList(new ArrayList<>(rawTxInfo.getMultisign().getFids()));

        Map<String, List<String>> fidSigMap = rawTxInfo.getFidSigMap();
        if(fidSigMap != null && !fidSigMap.isEmpty()) {
            Set<String> signedFidSet = fidSigMap.keySet();
            multisignTxDetail.setSignedFidList(new ArrayList<>(signedFidSet));
            multisignTxDetail.getUnSignedFidList().removeAll(multisignTxDetail.getSignedFidList());
        }

        int restSignNum = multisig.getM();
        if(fidSigMap!=null && !fidSigMap.isEmpty())
            for(String fid: multisig.getFids()){
                if(fidSigMap.get(fid)!=null)restSignNum--;
                if(restSignNum==0)break;
            }

        if(restSignNum<0)restSignNum=0;

        multisignTxDetail.setRestSignNum(restSignNum);
        return multisignTxDetail;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public Map<String, String> getCashIdAmountMap() {
        return cashIdAmountMap;
    }

    public void setCashIdAmountMap(Map<String, String> cashIdAmountMap) {
        this.cashIdAmountMap = cashIdAmountMap;
    }

    public List<Cash> getSendToList() {
        return sendToList;
    }

    public void setSendToList(List<Cash> sendToList) {
        this.sendToList = sendToList;
    }

    public String getmOfN() {
        return mOfN;
    }

    public void setmOfN(String mOfN) {
        this.mOfN = mOfN;
    }

    public List<String> getSignedFidList() {
        return signedFidList;
    }

    public void setSignedFidList(List<String> signedFidList) {
        this.signedFidList = signedFidList;
    }

    public List<String> getUnSignedFidList() {
        return unSignedFidList;
    }

    public void setUnSignedFidList(List<String> unSignedFidList) {
        this.unSignedFidList = unSignedFidList;
    }

    public Integer getRestSignNum() {
        return restSignNum;
    }

    public void setRestSignNum(Integer restSignNum) {
        this.restSignNum = restSignNum;
    }

    public String getOpReturn() {
        return opReturn;
    }

    public void setOpReturn(String opReturn) {
        this.opReturn = opReturn;
    }
}
