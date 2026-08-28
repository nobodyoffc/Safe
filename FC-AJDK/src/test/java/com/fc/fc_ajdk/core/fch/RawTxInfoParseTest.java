package com.fc.fc_ajdk.core.fch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fc.fc_ajdk.data.fchData.Cash;

import org.junit.Test;

public class RawTxInfoParseTest {

    private static final String TX_JSON = "{\"sender\":\"FEk41Kqjar45fLDriztUDTUkdki7mmcjWK\"," +
            "\"inputs\":[{\"birthTxId\":\"013898a3173567e5687a6ee0a7ab5369e4e23dacf6376266327ee4320acff986\"," +
            "\"birthIndex\":0,\"birthTime\":1783140734,\"value\":199992288}]," +
            "\"outputs\":[{\"fid\":\"F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW\",\"amount\":\"0.2\"}]}";

    @Test
    public void parseTxJsonWithFidAndStringAmountOutput() {
        RawTxInfo rawTxInfo = RawTxInfo.fromJson(TX_JSON, RawTxInfo.class);
        assertNotNull(rawTxInfo);
        assertEquals("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK", rawTxInfo.getSender());

        assertEquals(1, rawTxInfo.getInputs().size());
        Cash input = rawTxInfo.getInputs().get(0);
        assertEquals(Long.valueOf(199992288L), input.getValue());
        assertEquals("013898a3173567e5687a6ee0a7ab5369e4e23dacf6376266327ee4320acff986", input.getBirthTxId());

        assertEquals(1, rawTxInfo.getOutputs().size());
        Cash output = rawTxInfo.getOutputs().get(0);
        assertEquals("F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW", output.getOwner());
        assertEquals(Long.valueOf(20000000L), output.getValue());
        assertEquals(0.2, output.getAmount(), 1e-9);
    }

    @Test
    public void parseOutputWithOwnerAndValueStillWorks() {
        String json = "{\"outputs\":[{\"owner\":\"F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW\",\"value\":20000000}]}";
        RawTxInfo rawTxInfo = RawTxInfo.fromJson(json, RawTxInfo.class);
        assertNotNull(rawTxInfo);
        Cash output = rawTxInfo.getOutputs().get(0);
        assertEquals("F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW", output.getOwner());
        assertEquals(Long.valueOf(20000000L), output.getValue());
    }

    @Test
    public void parseOutputWithNumericAmount() {
        String json = "{\"outputs\":[{\"fid\":\"F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW\",\"amount\":0.2}]}";
        RawTxInfo rawTxInfo = RawTxInfo.fromJson(json, RawTxInfo.class);
        assertNotNull(rawTxInfo);
        assertEquals(Long.valueOf(20000000L), rawTxInfo.getOutputs().get(0).getValue());
    }
}
