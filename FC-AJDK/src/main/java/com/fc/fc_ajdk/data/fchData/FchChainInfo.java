package com.fc.fc_ajdk.data.fchData;

import com.fc.fc_ajdk.clients.ApipClient;
import com.fc.fc_ajdk.constants.Constants;
import com.fc.fc_ajdk.constants.FieldNames;
import com.fc.fc_ajdk.constants.IndicesNames;

import com.fc.fc_ajdk.data.apipData.BlockInfo;
import com.fc.fc_ajdk.data.fcData.FcObject;
import com.fc.fc_ajdk.utils.DateUtils;
import com.fc.fc_ajdk.utils.FchUtils;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.NumberUtils;
import com.fc.fc_ajdk.clients.NaSaClient.NaSaRpcClient;
import com.fc.fc_ajdk.utils.http.AuthType;
import com.fc.fc_ajdk.utils.http.RequestMethod;
import com.google.protobuf.Api;

import java.io.IOException;
import java.util.*;

@SuppressWarnings("unused")
public class FchChainInfo extends FcObject {
    public static final long MAX_REQUEST_COUNT = 1000;
    public static final long DEFAULT_COUNT = 100;
    private String time;
    private String height;
    private String blockId;
    private String totalSupply;
    private String circulating;
    private String difficulty;
    private String hashRate;
    private String chainSize;
    private String coinbaseMine;
    private String coinbaseFund;
    private final String initialCoinbaseMine= Constants.INITIAL_COINBASE_MINE;
    private final String initialCoinbaseFund= Constants.INITIAL_COINBASE_FUND;
    private final String mineReductionRatio = Constants.MINE_REDUCTION_RATIO;
    private final String fundReductionRatio = Constants.FUND_REDUCTION_RATIO;
    private final String reducePerBlocks = Constants.REDUCE_PER_BLOCKS;
    private final String reductionStopsAtHeight = Constants.REDUCTION_STOPS_AT_HEIGHT;
    private final String stableAnnualIssuance = Constants.STABLE_ANNUAL_ISSUANCE;
    private final String mineMatureDays = Constants.MINE_MATURE_DAYS;
    private final String fundMatureDays = Constants.FUND_MATURE_DAYS;
    private final String daysPerYear = Constants.DAYS_PER_YEAR_STR;
    private final String blockTimeMinute = Constants.BLOCK_TIME_MINUTE;
    private final String genesisBlockId = Constants.GENESIS_BLOCK_ID;
    private final String startTime = String.valueOf(Constants.START_TIME);
    private String year;
    private String daysToNextYear;
    private String heightOfNextYear;
//
//    public static void main(String[] args) throws IOException {
//
//        long height1 = 2000000;
//        ChainInfo freecashInfo = new ChainInfo();
//        freecashInfo.infoBest("http://localhost:8332","username","password");
//        System.out.println(freecashInfo.toNiceJson());
//
//        ChainInfo freecashInfo1 = new ChainInfo();
//        NewEsClient newEsClient = new NewEsClient();
//        ElasticsearchClient esClient = newEsClient.getSimpleEsClient();
//        freecashInfo1.infoByHeight(height1,esClient);
//        System.out.println(freecashInfo1.toNiceJson());
//
//        Map<Long, String> timeDiffMap = difficultyHistory(0, 1704321137,100 ,esClient);
//        JsonTools.gsonPrint(timeDiffMap);
//
//        Map<Long, String> timeHashRateMap = hashRateHistory(0, 1704321137,18 ,esClient);
//        JsonTools.gsonPrint(timeHashRateMap);
//
//        Map<Long, Long> blockTimefMap = blockTimeHistory(0, 1704321137,1000 ,esClient);
//
//        System.out.println(timeDiffMap.size());
//        System.out.println(timeHashRateMap.size());
//        System.out.println(blockTimefMap.size());
//
//        newEsClient.shutdownClient();
//    }

    public String toNiceJson(){
        return JsonUtils.toNiceJson(this);
    }

    public static Map<Long,Long> blockTimeHistory(long startTime, long endTime, long count, ApipClient apipClient){
        if(count>0)count += 1;
        else count = DEFAULT_COUNT+1;
        if(count>MAX_REQUEST_COUNT)count=MAX_REQUEST_COUNT;

        return apipClient.blockTimeHistory(startTime,endTime, Math.toIntExact(count), RequestMethod.POST, AuthType.FC_SIGN_BODY);
    }


    public static Map<Long,String> difficultyHistory(long startTime, long endTime, long count, ApipClient apipClient){

        return apipClient.difficultyHistory(startTime,endTime, Math.toIntExact(count), RequestMethod.POST, AuthType.FC_SIGN_BODY);
    }

    public static Map<Long,String> hashRateHistory(long startTime, long endTime, long count, ApipClient apipClient){
        return apipClient.hashRateHistory(startTime,endTime, Math.toIntExact(count),RequestMethod.POST,AuthType.FC_SIGN_BODY);
    }

    private static long estimateHeight(long startTime) {
        if(startTime< Constants.START_TIME)return -1;
        return (startTime - Constants.START_TIME) / 60;
    }

    public void infoBest(NaSaRpcClient naSaRpcClient){
        NaSaRpcClient.BlockchainInfo blockchainInfo = naSaRpcClient.getBlockchainInfo();
        this.height= String.valueOf(blockchainInfo.getBlocks());
        this.blockId=blockchainInfo.getBestblockhash();
        this.time = DateUtils.longToTime(((long)blockchainInfo.getMediantime())*1000,DateUtils.LONG_FORMAT);

        this.difficulty= NumberUtils.numberToPlainString(String.valueOf(blockchainInfo.getDifficulty()),"0");
        double hashRate = FchUtils.difficultyToHashRate(blockchainInfo.getDifficulty());
        this.hashRate= NumberUtils.numberToPlainString(String.valueOf(hashRate),"0");
        this.chainSize= NumberUtils.numberToPlainString(String.valueOf(blockchainInfo.getSize_on_disk()),null);

        infoByHeight(Long.parseLong(this.height));

    }
    public void infoByHeight(long height, ApipClient apipClient){
        String heightStr = String.valueOf(height);
        this.height= heightStr;
        BlockInfo block;
        Map<String, BlockInfo> blockMap = apipClient.blockByHeights(RequestMethod.POST, AuthType.FC_SIGN_BODY, heightStr);
        block = blockMap.get(heightStr);
        double difficulty = FchUtils.bitsToDifficulty(block.getBits());
        double hashRate = FchUtils.difficultyToHashRate(difficulty);
        this.difficulty= NumberUtils.numberToPlainString(String.valueOf(difficulty),"0");
        this.hashRate= NumberUtils.numberToPlainString(String.valueOf(hashRate),"0");
        this.blockId=block.getId();
        this.time =DateUtils.longToTime(((long)block.getTime()) *1000,DateUtils.LONG_FORMAT);

        infoByHeight(height);
    }
    public void infoByHeight(long height){

        double totalSupply = 0;
        double circulating = 0;
        double coinbaseMine = 25;
        double coinbaseFund = 25;
        long blockPerYear = Long.parseLong(Constants.DAYS_PER_YEAR_STR)*24*60;
        height = height+1;

        long years = height / blockPerYear;

        for(int i=0;i<years;i++){
            totalSupply += blockPerYear * (coinbaseMine+coinbaseFund);
            if(years<40) {
                coinbaseMine *= 0.8;
                coinbaseFund *= 0.5;
            }
        }
        totalSupply += height % blockPerYear * (coinbaseMine+coinbaseFund);
        this.totalSupply = NumberUtils.numberToPlainString(String.valueOf(totalSupply),"0");
        this.year= String.valueOf(years+1);
        this.coinbaseMine= NumberUtils.numberToPlainString(String.valueOf(coinbaseMine),"8");//String.valueOf(NumberTools.roundDouble8(coinbaseMine));
        this.coinbaseFund= NumberUtils.numberToPlainString(String.valueOf(coinbaseFund),"8");
        long blocksRemainingThisYear = blockPerYear - height % blockPerYear;
        long daysToNextYear = blocksRemainingThisYear / (24 * 60);
        this.daysToNextYear=String.valueOf(daysToNextYear);
        heightOfNextYear=String.valueOf(blocksRemainingThisYear+height);

        long daysImmatureThisYear = 400-daysToNextYear;
        if(daysImmatureThisYear > 100)daysImmatureThisYear=100;
        long daysImmatureLastYear = 100-daysImmatureThisYear;

        circulating = totalSupply
                -(daysImmatureThisYear*1440*(coinbaseMine+coinbaseFund))
                -(daysImmatureLastYear*1440*(coinbaseMine/0.8+coinbaseFund/0.5));
        this.circulating = NumberUtils.numberToPlainString(String.valueOf(circulating),"0");
    }

    public String getTotalSupply() {
        return totalSupply;
    }

    public void setTotalSupply(String totalSupply) {
        this.totalSupply = totalSupply;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getHashRate() {
        return hashRate;
    }

    public void setHashRate(String hashRate) {
        this.hashRate = hashRate;
    }

    public long estimateHeight() {
        return Long.parseLong(height);
    }

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public String getChainSize() {
        return chainSize;
    }

    public void setChainSize(String chainSize) {
        this.chainSize = chainSize;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getCoinbaseMine() {
        return coinbaseMine;
    }

    public void setCoinbaseMine(String coinbaseMine) {
        this.coinbaseMine = coinbaseMine;
    }

    public String getCoinbaseFund() {
        return coinbaseFund;
    }

    public void setCoinbaseFund(String coinbaseFund) {
        this.coinbaseFund = coinbaseFund;
    }

    public String getDaysToNextYear() {
        return daysToNextYear;
    }

    public void setDaysToNextYear(String daysToNextYear) {
        this.daysToNextYear = daysToNextYear;
    }

    public String getHeightOfNextYear() {
        return heightOfNextYear;
    }

    public void setHeightOfNextYear(String heightOfNextYear) {
        this.heightOfNextYear = heightOfNextYear;
    }

    public String getDaysPerYear() {
        return Constants.DAYS_PER_YEAR_STR;
    }

    public String getMineMutualDays() {
        return Constants.MINE_MATURE_DAYS;
    }

    public String getFundMutualDays() {
        return Constants.FUND_MATURE_DAYS;
    }

    public String getBlockTimeMinute() {
        return Constants.BLOCK_TIME_MINUTE;
    }

    public String getInitialCoinbaseMine() {
        return Constants.INITIAL_COINBASE_MINE;
    }
    public String getInitialCoinbaseFund() {
        return Constants.INITIAL_COINBASE_FUND;
    }

    public String getMineReductionRatio() {
        return Constants.MINE_REDUCTION_RATIO;
    }

    public long getStartTime() {
        return Constants.START_TIME;
    }

    public String getGenesisBlockId() {
        return genesisBlockId;
    }

    public String getCirculating() {
        return circulating;
    }

    public void setCirculating(String circulating) {
        this.circulating = circulating;
    }

    public String getMineMatureDays() {
        return Constants.MINE_MATURE_DAYS;
    }

    public String getFundMatureDays() {
        return Constants.FUND_MATURE_DAYS;
    }

    public String getFundReductionRatio() {
        return Constants.FUND_REDUCTION_RATIO;
    }

    public String getReducePerBlocks() {
        return Constants.REDUCE_PER_BLOCKS;
    }

    public String getReductionStopsAtHeight() {
        return Constants.REDUCTION_STOPS_AT_HEIGHT;
    }

    public String getStableAnnualIssuance() {
        return Constants.STABLE_ANNUAL_ISSUANCE;
    }

    public String getHeight() {
        return height;
    }

    public void setHeight(String height) {
        this.height = height;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }
}
