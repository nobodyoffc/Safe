package com.fc.fc_ajdk.data.fcData;

import com.fc.fc_ajdk.config.Settings;
import com.fc.fc_ajdk.constants.CodeMessage;
import com.fc.fc_ajdk.data.fchData.Block;
import com.fc.fc_ajdk.handlers.AccountHandler;
import com.fc.fc_ajdk.handlers.Handler;
import com.fc.fc_ajdk.handlers.SessionHandler;
import com.fc.fc_ajdk.utils.FchUtils;

import java.io.IOException;
import java.util.List;

public class ReplyBody extends FcObject {
    protected String requestId;
    protected Op op;
    protected Integer code;
    protected String message;
    protected Integer nonce;
    protected Long time;
    protected Long balance;
    protected Object data;
    protected List<String> last;
    protected Long got;
    protected Long total;
    protected Long bestHeight;
    protected String bestBlockId; //For rollback checking
    protected transient String sid;
    protected transient AccountHandler accountHandler;
    protected transient SessionHandler sessionHandler;
    protected transient Settings settings;
    protected transient String finalJson;

    public ReplyBody() {
    }

    public ReplyBody(Settings settings) {
        this.sid = settings.getSid();
        this.settings = settings;
        if (settings.getHandler(Handler.HandlerType.ACCOUNT) != null)
            accountHandler = (AccountHandler) settings.getHandler(Handler.HandlerType.ACCOUNT);
        if (settings.getHandler(Handler.HandlerType.SESSION) != null)
            sessionHandler = (SessionHandler) settings.getHandler(Handler.HandlerType.SESSION);
    }

    public void setBestBlock() {
        Block block = settings.getBestBlock();

        this.bestHeight = block.getHeight();
        this.bestBlockId = block.getId();
    }

    public void set0Success() {
        set0Success(null);
    }

    public void set0Success(Object data) {
        code = CodeMessage.Code0Success;
        message = CodeMessage.getMsg(code);
        if(data!=null)this.data = data;
    }


    public void setOtherError(String otherError) {
        code = CodeMessage.Code1020OtherError;
        if(otherError!=null)message = otherError;
        else message = CodeMessage.getMsg(code);
    }

    @Override
    public String toString(){return toNiceJson();}

    public void clean() {
        code=null;
        message=null;
        nonce=null;
        balance=null;
        data=null;
        last=null;
    }
    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getNonce() {
        return nonce;
    }

    public void setNonce(Integer nonce) {
        this.nonce = nonce;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public List<String> getLast() {
        return last;
    }

    public void setLast(List<String> last) {
        this.last = last;
    }

    public Long getGot() {
        return got;
    }

    public void setGot(Long got) {
        this.got = got;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Long getBestHeight() {
        return bestHeight;
    }

    public void setBestHeight(Long bestHeight) {
        this.bestHeight = bestHeight;
    }


    public Op getOp() {
        return op;
    }


    public void setOp(Op op) {
        this.op = op;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getBestBlockId() {
        return bestBlockId;
    }

    public void setBestBlockId(String bestBlockId) {
        this.bestBlockId = bestBlockId;
    }

    public void Set0Success() {
        setCodeMessage(CodeMessage.Code0Success);
    }

    public void set1020Other(String message) {
        setCodeMessage(CodeMessage.Code1020OtherError);
        if(message!=null)setMessage(message);
    }

    public void setCodeMessage(Integer code) {
        this.code = code;
        this.message = CodeMessage.getMsg(code);
    }
}
