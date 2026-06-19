package com.fc.fc_ajdk.data.feipData;

import com.fasterxml.jackson.core.util.ByteArrayBuilder;

import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fcData.FcObject;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.Hex;


public class Mail extends FcObject {
    private String alg;
    private String cipher;
    private String cipherSend;
    private String cipherReci;
    private String textId;

    private String sender;
    private String recipient;
    private Long birthTime;
    private Long birthHeight;
    private Long lastHeight;
    private Boolean active;


    //On chain detail
    private String mailId;
    private Long time;
    private String from;
    private String to;
    private transient String fromCid;
    private transient String toCid;
    private String content;

    public byte[] toBytes(){
        try(ByteArrayBuilder bab = new ByteArrayBuilder()) {
            bab.write(BytesUtils.longToBytes(time));
            bab.write(Hex.fromHex(mailId));
            byte[] fromBytes = KeyTools.addrToHash160(from);
            bab.write(fromBytes);
            if(to ==null) bab.write(fromBytes);
            else bab.write(KeyTools.addrToHash160(to));
            if(fromCid!=null){
                byte[] fromCidBytes = fromCid.getBytes();
                bab.write(fromCidBytes.length);
                bab.write(fromCidBytes);
            }else bab.write(0);

            if(toCid!=null){
                byte[] toCidBytes = toCid.getBytes();
                bab.write(toCidBytes.length);
                bab.write(toCidBytes);
            }else bab.write(0);
            
            bab.write(content.getBytes());
            return bab.toByteArray();
        }catch (Exception e){
            return null;
        }
    }


    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public String getMailId() {
        return mailId;
    }

    public void setMailId(String mailId) {
        this.mailId = mailId;
    }

    public byte[] getIdBytes() {
        return Hex.fromHex(this.mailId);
    }

    public String getFromCid() {
        return fromCid;
    }

    public void setFromCid(String fromCid) {
        this.fromCid = fromCid;
    }

    public String getToCid() {
        return toCid;
    }

    public void setToCid(String toCid) {
        this.toCid = toCid;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getAlg() {
        return alg;
    }

    public void setAlg(String alg) {
        this.alg = alg;
    }
    public String getCipherSend() {
        return cipherSend;
    }
    public void setCipherSend(String cipherSend) {
        this.cipherSend = cipherSend;
    }
    public String getCipherReci() {
        return cipherReci;
    }
    public void setCipherReci(String cipherReci) {
        this.cipherReci = cipherReci;
    }
    public Long getBirthTime() {
        return birthTime;
    }
    public void setBirthTime(Long birthTime) {
        this.birthTime = birthTime;
    }
    public Long getBirthHeight() {
        return birthHeight;
    }
    public void setBirthHeight(Long birthHeight) {
        this.birthHeight = birthHeight;
    }
    public Long getLastHeight() {
        return lastHeight;
    }
    public void setLastHeight(Long lastHeight) {
        this.lastHeight = lastHeight;
    }
    public String getSender() {
        return sender;
    }
    public void setSender(String sender) {
        this.sender = sender;
    }
    public String getRecipient() {
        return recipient;
    }
    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }
    public String getTextId() {
        return textId;
    }
    public void setTextId(String textId) {
        this.textId = textId;
    }

    public String getCipher() {
        return cipher;
    }

    public void setCipher(String cipher) {
        this.cipher = cipher;
    }

}
