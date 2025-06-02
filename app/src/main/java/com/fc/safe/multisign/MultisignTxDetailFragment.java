package com.fc.safe.multisign;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.fc.fc_ajdk.core.fch.RawTxInfo;
import com.fc.fc_ajdk.data.fchData.MultisignTxDetail;
import com.fc.fc_ajdk.data.fchData.Multisign;
import com.fc.fc_ajdk.data.fchData.SendTo;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.safe.R;
import com.fc.safe.tx.view.CashAmountCard;
import com.fc.safe.tx.view.TxOutputCard;
import com.fc.safe.utils.KeyCardManager;

import java.util.Map;

public class MultisignTxDetailFragment extends Fragment {
    private RawTxInfo rawTxInfo;
    private MultisignTxDetail multisignTxDetail;
    private LinearLayout senderContainer;
    private LinearLayout cashContainer;
    private LinearLayout sendToContainer;
    private LinearLayout textContainer;
    private LinearLayout signedFidContainer;
    private LinearLayout unsignedFidContainer;

    public static MultisignTxDetailFragment newInstance(RawTxInfo rawTxInfo) {
        MultisignTxDetailFragment fragment = new MultisignTxDetailFragment();
        fragment.rawTxInfo = rawTxInfo;
        fragment.multisignTxDetail = MultisignTxDetail.fromMultiSigData(rawTxInfo, fragment.getContext());
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_multisign_tx_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        senderContainer = view.findViewById(R.id.senderContainer);
        cashContainer = view.findViewById(R.id.cashContainer);
        sendToContainer = view.findViewById(R.id.sendToContainer);
        textContainer = view.findViewById(R.id.textContainer);
        signedFidContainer = view.findViewById(R.id.signedFidContainer);
        unsignedFidContainer = view.findViewById(R.id.unsignedFidContainer);

        setupSender();
        setupCash();
        setupSendTo();
        setupText();
        setupSignedFids();
        setupUnsignedFids();
    }

    private void setupSender() {
        if (multisignTxDetail.getSender() == null || multisignTxDetail.getSender().isEmpty()) {
            return;
        }
        MultisignKeyCardManager keyCardManager = new MultisignKeyCardManager(getContext(), senderContainer, false);
        Multisign multisign = new Multisign();
        multisign.setId(multisignTxDetail.getSender());
        keyCardManager.addSenderKeyCard(multisign);
    }

    private void setupCash() {
        if (multisignTxDetail.getCashIdAmountMap() == null || multisignTxDetail.getCashIdAmountMap().isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : multisignTxDetail.getCashIdAmountMap().entrySet()) {
            CashAmountCard card = new CashAmountCard(getContext());
            card.setCashId(entry.getKey());
            card.setAmount(entry.getValue());
            cashContainer.addView(card);
        }
    }

    private void setupSendTo() {
        if (multisignTxDetail.getSendToList() == null || multisignTxDetail.getSendToList().isEmpty()) {
            return;
        }
        for (SendTo sendTo: multisignTxDetail.getSendToList()) {
            TxOutputCard card = new TxOutputCard(getContext());
            card.setSendTo(sendTo, getContext());
            sendToContainer.addView(card);
        }
    }

    private void setupText() {
        if (multisignTxDetail.getOpReturn() != null && !multisignTxDetail.getOpReturn().isEmpty()) {
            addTextLine("OpReturn: " + multisignTxDetail.getOpReturn());
        }
        if (multisignTxDetail.getmOfN() != null && !multisignTxDetail.getmOfN().isEmpty()) {
            addTextLine("Required Signs/Total Member: " + multisignTxDetail.getmOfN());
        }
        if (multisignTxDetail.getRestSignNum() != null) {
            addTextLine("Missing: " + multisignTxDetail.getRestSignNum());
        }
    }

    private void addTextLine(String text) {
        TextView textView = new TextView(getContext());
        textView.setText(text);
        textView.setTextColor(getResources().getColor(R.color.field_name, getContext().getTheme()));
        textView.setTypeface(null, android.graphics.Typeface.BOLD);
        textContainer.addView(textView);
    }

    private void setupSignedFids() {
        if (multisignTxDetail.getSignedFidList() == null || multisignTxDetail.getSignedFidList().isEmpty()) {
            return;
        }
        KeyCardManager keyCardManager = new KeyCardManager(getContext(), signedFidContainer, null);
        for (String fid : multisignTxDetail.getSignedFidList()) {
            KeyInfo keyInfo = new KeyInfo(null, fid);
            keyCardManager.addKeyCard(keyInfo);
        }
    }

    private void setupUnsignedFids() {
        if (multisignTxDetail.getUnSignedFidList() == null || multisignTxDetail.getUnSignedFidList().isEmpty()) {
            return;
        }
        KeyCardManager keyCardManager = new KeyCardManager(getContext(), unsignedFidContainer, null);
        for (String fid : multisignTxDetail.getUnSignedFidList()) {
            KeyInfo keyInfo = new KeyInfo(null, fid);
            keyCardManager.addKeyCard(keyInfo);
        }
    }
} 