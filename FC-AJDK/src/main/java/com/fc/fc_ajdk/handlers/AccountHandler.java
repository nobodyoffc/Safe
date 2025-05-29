package com.fc.fc_ajdk.handlers;

import com.fc.fc_ajdk.data.apipData.BlockInfo;
import com.fc.fc_ajdk.data.apipData.Fcdsl;
import com.fc.fc_ajdk.ui.Inputer;
import com.fc.fc_ajdk.ui.Menu;
import com.fc.fc_ajdk.config.Settings;
import com.fc.fc_ajdk.ui.Shower;
import com.fc.fc_ajdk.clients.ApipClient;

import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.data.fcData.FcEntity;
import com.fc.fc_ajdk.data.fcData.FcObject;
import com.fc.fc_ajdk.data.fcData.ReplyBody;
import com.fc.fc_ajdk.core.fch.FchMainNetwork;
import com.fc.fc_ajdk.clients.NaSaClient.NaSaRpcClient;
import org.jetbrains.annotations.NotNull;

import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.data.fchData.OpReturn;
import com.fc.fc_ajdk.data.fchData.SendTo;
import com.fc.fc_ajdk.data.feipData.Service;
import com.fc.fc_ajdk.data.feipData.serviceParams.Params;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.TimberLogger;

import com.fc.fc_ajdk.utils.FchUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.MapQueue;
import com.fc.fc_ajdk.utils.NumberUtils;
import com.fc.fc_ajdk.utils.ObjectUtils;
import com.fc.fc_ajdk.utils.http.AuthType;
import com.fc.fc_ajdk.utils.http.RequestMethod;

import com.fc.fc_ajdk.constants.Constants;
import com.fc.fc_ajdk.constants.FieldNames;
import com.fc.fc_ajdk.constants.Values;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.fc.fc_ajdk.constants.Constants.DEFAULT_DISPLAY_LIST_SIZE;
import static com.fc.fc_ajdk.constants.FieldNames.BIRTH_HEIGHT;
import static com.fc.fc_ajdk.constants.FieldNames.CASH_ID;
import static com.fc.fc_ajdk.constants.FieldNames.FID_VIA_MAP;
import static com.fc.fc_ajdk.constants.FieldNames.HEIGHT;
import static com.fc.fc_ajdk.constants.FieldNames.ID;
import static com.fc.fc_ajdk.constants.FieldNames.ISSUER;
import static com.fc.fc_ajdk.constants.FieldNames.LAST_BLOCK_ID;
import static com.fc.fc_ajdk.constants.FieldNames.LAST_EXPENSE;
import static com.fc.fc_ajdk.constants.FieldNames.LAST_HEIGHT;
import static com.fc.fc_ajdk.constants.FieldNames.LAST_INCOME;
import static com.fc.fc_ajdk.constants.FieldNames.OWNER;
import static com.fc.fc_ajdk.constants.FieldNames.TIME;
import static com.fc.fc_ajdk.constants.FieldNames.TO;
import static com.fc.fc_ajdk.constants.FieldNames.USER_BALANCE_MAP;
import static com.fc.fc_ajdk.constants.FieldNames.VALUE;
import static com.fc.fc_ajdk.constants.FieldNames.VIA;

public class AccountHandler extends Handler<FcEntity> {
    final static String TAG = "AccountHandler";
    public static final String DEALER_MIN_BALANCE = "dealerMinBalance";
    public static final String MIN_DISTRIBUTE_BALANCE ="minDistributeBalance";
    public static final String DISTRIBUTE_DAYS = "distributeDays";
    public static final Long DEFAULT_DEALER_MIN_BALANCE = 100000000L;
    public static final Long DEFAULT_MIN_DISTRIBUTE_BALANCE = 5 * 100000000L;
    public static final Long DEFAULT_MIN_PAYMENT = 1000L;
    public static final long DEFAULT_DISTRIBUTE_DAYS = 10;

    private static final int MAX_USER_BALANCE_CACHE_SIZE = 2;
    private static final int MAX_FID_VIA_CACHE_SIZE = 2;
    private static final int MAX_VIA_BALANCE_CACHE_SIZE = 2;

    private static final String USER_BALANCE = "user_balance";
    private static final String FID_CONSUME_VIA = "fid_consume_via";
    private static final String VIA_BALANCE = "via_balance";
    private static final String N_PRICE = "n_price";

    public String dbPath;
    private final BufferedReader br;
    private Long myBalance;
    private final MapQueue<String, Long> userBalance;
    private final MapQueue<String, String> fidViaMap;
    private final MapQueue<String, Long> viaBalance;
    private Map<String, Long> payoffMap;
    private long lastUpdateExpenseTime = 0;

    private final String mainFid;
    private final String sid;
    private final ApipClient apipClient;
    private final NaSaRpcClient nasaClient;
    private final CashHandler cashHandler;
    private final Long dealerMinBalance;
    private final Long minDistributeBalance;
    private final Long distributeDays;
    private final byte[] prikey;
    private String startHeight;

    private final Long priceBase;
    private Map<String, Long> nPriceMap;
    private Double orderViaShare;
    private Double consumeViaShare;
    private Double minPay;
    
    // Add these constants at the class level
    private final String redisKeyUserBalance;
    private final String redisKeyFidConsumeVia;
    private final String redisKeyViaBalance;
    // Add these fields

    // Add these constants at the class level
    private String listenPath;

    public AccountHandler(Settings settings){
        super(settings, HandlerType.ACCOUNT, LocalDB.SortType.BIRTH_ORDER, FcEntity.class,true,false);

        this.br = settings.getBr();
        this.mainFid = settings.getMainFid();
        this.prikey = Decryptor.decryptPrikey(settings.getMyPrikeyCipher(),settings.getSymkey());
        this.sid = settings.getService().getId();
        this.dbPath = settings.getDbDir();
        this.apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
        this.nasaClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);
        this.cashHandler = settings.getHandler(Handler.HandlerType.CASH)!=null ?(CashHandler) settings.getHandler(Handler.HandlerType.CASH): new CashHandler(settings);
        Params params = ObjectUtils.objectToClass(settings.getService().getParams(), Params.class);
        String priceStr = params.getPricePerKBytes();
        if(priceStr!=null) {
            double price = Double.parseDouble(priceStr);
            this.priceBase = FchUtils.coinToSatoshi(price);
        }else this.priceBase = 0L;
        String orderViaShareStr = params.getOrderViaShare();
        orderViaShare = Double.parseDouble(orderViaShareStr);
        String consumeViaShareStr = params.getConsumeViaShare();
        consumeViaShare = Double.parseDouble( consumeViaShareStr);
        String minPayStr = params.getMinPayment();
        minPay = Double.parseDouble(minPayStr);


        // Initialize collections

        this.userBalance = new MapQueue<>(MAX_USER_BALANCE_CACHE_SIZE);
        this.fidViaMap = new MapQueue<>(MAX_FID_VIA_CACHE_SIZE);
        this.viaBalance = new MapQueue<>(MAX_VIA_BALANCE_CACHE_SIZE);

        // this.accountDB = useRedis ? new AccountDB(null, sid, dbPath) : new AccountDB(mainFid, sid, dbPath);
        this.payoffMap = new HashMap<>();
        // Add dealer min balance input logic
        if(localDB.getSetting(Settings.DEALER_MIN_BALANCE)!=null) {
            this.dealerMinBalance = (long)localDB.getSetting(Settings.DEALER_MIN_BALANCE);
        }else if(settings.getSettingMap().get(Settings.DEALER_MIN_BALANCE)!=null) {
            this.dealerMinBalance = ObjectUtils.objectToClass(settings.getSettingMap().get(Settings.DEALER_MIN_BALANCE),Long.class);
        }else{
            this.dealerMinBalance = DEFAULT_DEALER_MIN_BALANCE;
            localDB.putSetting(Settings.DEALER_MIN_BALANCE, this.dealerMinBalance);
        }

        // Add min distribute balance input logic
        if(localDB.getSetting(FieldNames.MIN_DISTRIBUTE_BALANCE)!=null) {
            this.minDistributeBalance = (long)localDB.getSetting(FieldNames.MIN_DISTRIBUTE_BALANCE);
        }else if(settings.getSettingMap().get(Settings.MIN_DISTRIBUTE_BALANCE)!=null){
            this.minDistributeBalance = ObjectUtils.objectToClass(settings.getSettingMap().get(Settings.MIN_DISTRIBUTE_BALANCE),Long.class);
            localDB.putSetting(FieldNames.MIN_DISTRIBUTE_BALANCE, this.minDistributeBalance);
        }else{
            this.minDistributeBalance = DEFAULT_MIN_DISTRIBUTE_BALANCE;
            localDB.putSetting(FieldNames.MIN_DISTRIBUTE_BALANCE, this.minDistributeBalance);
        }

        if(localDB.getSetting(DISTRIBUTE_DAYS)!=null) {
            this.distributeDays = (long)localDB.getSetting(FieldNames.MIN_DISTRIBUTE_BALANCE);
        }else if(settings.getSettingMap().get(DISTRIBUTE_DAYS)!=null){
            this.distributeDays = ObjectUtils.objectToClass(settings.getSettingMap().get(DISTRIBUTE_DAYS),Long.class);
            localDB.putSetting(DISTRIBUTE_DAYS, this.distributeDays);
        }else{
            this.distributeDays = DEFAULT_MIN_DISTRIBUTE_BALANCE;
            localDB.putSetting(DISTRIBUTE_DAYS, this.distributeDays);
        }

        // Get start height

        redisKeyUserBalance = null;
        redisKeyFidConsumeVia = null;
        redisKeyViaBalance = null;


        if(br!=null)setNPrices(settings.getApiList(),br,false);

        if(this.nPriceMap==null)
            this.nPriceMap = getNPriceMap();

        if(getLastIncome()==null || getLastIncome().isEmpty()) {
            if(br==null)this.startHeight = null;
            else this.startHeight = Inputer.inputLongStr(br, "Input the start height by which the order scanning starts. Enter to start from 0:");
        }else this.startHeight=null;
        initShareAndCost(br, orderViaShare, consumeViaShare, false);

    }

    public void menu(BufferedReader br, boolean isRootMenu) {
        Menu menu = newMenu("Account manager",isRootMenu);
        menu.add("Update Income", () -> updateIncomes(br));
        menu.add("Update Expense", () -> updateExpenses(br));
        menu.add("List Incomes", this::showIncomeList);
        menu.add("List Expenses", this::showExpenseList);
        menu.add("Distribute Balance", () -> distribute(br));
        menu.add("Pay all", () -> settlePayments(br));
        menu.add("Update My Balance", () -> updateMyBalance(br));
        menu.add("Modify Contributor Shares", () -> inputContributorShare(br));
        menu.add("Modify Fixed Costs", () -> inputFixedCost(br));
        if(cashHandler!=null)
            menu.add("Manage My Cashes", () -> cashHandler.menu(br, false));
        menu.add("Test", () -> test());
        
        menu.showAndSelect(br);
    }

    private void findUserBalance(BufferedReader br) {

        String str = Inputer.inputString(br, "Input user's fch address or session name. Press enter to list all users:");

        if ("".equals(str)) {
            Map<String, Long> allBalances = localDB.getAllFromMap(USER_BALANCE_MAP);
            showUserBalanceMap(br,allBalances);
        } else {
            Long balance = checkUserBalance(str);
            if (balance != null) {
                System.out.println("User: " + str + ", Balance: " + FchUtils.satoshiToCoin(balance) + " f");
            } else {
                System.out.println("Failed to find user: " + str);
            }
        }
        Menu.anyKeyToContinue(br);
    }

    private static <T> void showUserBalanceMap(BufferedReader br, Map<String, T> balanceMap) {
        List<Map.Entry<String, T>> entries = new ArrayList<>(balanceMap.entrySet());
        int totalEntries = entries.size();
        int batchSize = Shower.DEFAULT_PAGE_SIZE;
        int currentPosition = 0;

        while (currentPosition < totalEntries) {
            int endPosition = Math.min(currentPosition + batchSize, totalEntries);
            List<Map.Entry<String, T>> batch = entries.subList(currentPosition, endPosition);

            System.out.println("\n=== User Balances (Showing " + (currentPosition + 1) +
                               " to " + endPosition + " of " + totalEntries + ") ===");

            for (Map.Entry<String, T> entry : batch) {
                System.out.println(entry.getKey() + ": " +
                                  FchUtils.satoshiToCoin(Long.parseLong(entry.getValue().toString())) + " f");
            }

            currentPosition = endPosition;

            if (currentPosition < totalEntries) {
                int remaining = totalEntries - currentPosition;
                System.out.println("\n" + remaining + " entries remaining. Continue? (y/n)");
                String response = Inputer.inputString(br, "");
                if (!response.equalsIgnoreCase("y")) {
                    break;
                }
            }
        }
    }

    public void test(){
        String fid = "FD4jHk3bGHijHWJqZddSXaEY9zZdvUbody";
        long value = 10000000;
        updateUserBalance(fid,value);
        System.out.println("Balance:"+checkUserBalance(fid));
        Map<String,Long> bs = new HashMap<>();
        bs.put(fid,20000000L);
        updateUserBalanceMap(bs);
        System.out.println("Balance:"+checkUserBalance(fid));

        String via = "FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7";
        updateViaBalance(fid,value,via);
        System.out.println("Via balance:"+getViaBalance(via));
    }

    private void updateMyBalance(BufferedReader br) {
        boolean balanceUpdate = updateMyBalance();
        if (balanceUpdate) {
            System.out.println("Current balance: " + FchUtils.satoshiToCoin(myBalance) +" f");
        } else {
            System.out.println("Failed to update balance.");
        }
        Menu.anyKeyToContinue(br);
    }

    private void settlePayments(BufferedReader br) {
        boolean settlementResult = settle();
        if (settlementResult) {
            System.out.println("Settlement completed successfully.");
        } else {
            System.out.println("Settlement failed.");
        }
        Menu.anyKeyToContinue(br);
    }

    private void distribute(BufferedReader br) {
        Boolean done = distribute();
        if(done ==null)return;
        if(!done)System.out.println("The balance is insufficient. Some distributions are deferred to next period.");
        done = settle();
        if (done) System.out.println("Distribution completed successfully.");
        Menu.anyKeyToContinue(br);
    }

    private void updateExpenses(BufferedReader br) {
        int newExpenses = updateExpense();
        if (newExpenses == -1) {
            System.out.println("Request too frequent. Please wait before trying again.");
        } else {
            System.out.println("Processed " + newExpenses + " new expenses.");
        }
        Menu.anyKeyToContinue(br);
    }

    private void updateIncomes(BufferedReader br) {
        List<Income> newIncomes = updateIncome();
        if (newIncomes == null) {
            System.out.println("No new incomes found.");
        } else {
            System.out.println("Processed " + newIncomes.size() + " new incomes.");
        }
        Menu.anyKeyToContinue(br);
    }

    /**
     * Performs all periodic update operations in sequence
     */
    public void updateAll() {
        checkAndHandleRollback();
        updateIncome();
        updateExpense();
        updateMyBalance();
    }

    public Long getApiPrice(String name) {
        return nPriceMap.get(name);
    }

    // Inner classes for Income and Expense
    public static class Income extends FcObject {
        private String from;
        private Long value;
        private Long time;
        private Long height;



        // Constructor and getters/setters
        public Income(String id, String from, Long value, Long time, Long height) {
            this.id= id;
            this.from = from;
            this.value = value;
            this.time = time;
            this.height = height;

        }


        public static List<String> getTimestampFieldList(){
            return List.of(TIME);
        }

        public static List<String> getSatoshiFieldList(){
            return List.of(VALUE);
        }
        public static Map<String, String> getHeightToTimeFieldMap() {
            return  new HashMap<>();
        }

        public static Map<String, String> getShowFieldNameAsMap() {
            Map<String,String> map = new HashMap<>();
            map.put(ID,CASH_ID);
            return map;
        }

        public static List<String> getReplaceWithMeFieldList() {
            return List.of(OWNER,ISSUER);
        }

        public static Map<String, Object> getInputFieldDefaultValueMap() {
            return new HashMap<>();
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public Long getValue() {
            return value;
        }

        public void setValue(Long value) {
            this.value = value;
        }

        public Long getTime() {
            return time;
        }

        public void setTime(Long time) {
            this.time = time;
        }

        public Long getHeight() {
            return height;
        }

        public void setHeight(Long height) {
            this.height = height;
        }   
    }

    public static class Expense extends FcObject {
        private String to;
        private Long value;
        private Long time;
        private Long height;
        // Constructor and getters/setters
        public Expense(String id, String to, Long value, Long time, Long height) {
            this.id = id;
            this.to = to;
            this.value = value;
            this.time = time;
            this.height = height;
        }
        public static LinkedHashMap<String,Integer>getFieldWidthMap(){
            LinkedHashMap<String,Integer> map = new LinkedHashMap<>();
            map.put(ID,FcEntity.ID_DEFAULT_SHOW_SIZE);
            map.put(TO,FcEntity.ID_DEFAULT_SHOW_SIZE);
            map.put(VALUE,FcEntity.AMOUNT_DEFAULT_SHOW_SIZE);
            map.put(TIME,FcEntity.TIME_DEFAULT_SHOW_SIZE);
            map.put(HEIGHT,FcEntity.AMOUNT_DEFAULT_SHOW_SIZE);
            return map;
        }


        public static List<String> getTimestampFieldList(){
            return List.of(TIME);
        }

        public static List<String> getSatoshiFieldList(){
            return List.of(VALUE);
        }
        public static Map<String, String> getHeightToTimeFieldMap() {
            return  new HashMap<>();
        }

        public static Map<String, String> getShowFieldNameAsMap() {
            Map<String,String> map = new HashMap<>();
            map.put(ID,CASH_ID);
            return map;
        }

        public static List<String> getReplaceWithMeFieldList() {
            return List.of(OWNER,ISSUER);
        }

        public static Map<String, Object> getInputFieldDefaultValueMap() {
            return new HashMap<>();
        }


        // Getters and setters

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }

        public Long getValue() {
            return value;
        }

        public void setValue(Long value) {
            this.value = value;
        }

        public Long getTime() {
            return time;
        }

        public void setTime(Long time) {
            this.time = time;
        }

        public Long getHeight() {
            return height;
        }

        public void setHeight(Long height) {
            this.height = height;
        }
    }

    /**
     * Updates myBalance using APIP client
     * @return true if update successful, false otherwise
     */
    public boolean updateMyBalance() {
        // Try APIP client first
        if (apipClient != null) {
            Map<String, Long> balanceMap = apipClient.balanceByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, mainFid);
            if (balanceMap != null && !balanceMap.isEmpty()) {
                myBalance = balanceMap.get(mainFid);
                return true;
            }
        }
        
        return false;
    }

    private boolean checkAndHandleRollback() {
        Long lastHeight = (Long)localDB.getState(LAST_HEIGHT);
        String lastBlockId = (String)localDB.getState(LAST_BLOCK_ID);
        boolean isRolledBack = false;

        if (lastHeight == null || lastBlockId == null) {
            return false;
        }

        if (apipClient != null) {
            isRolledBack = isRolledBack(lastHeight, lastBlockId, apipClient);
        }

        if (isRolledBack) {
            // Rollback detected, adjust height by 30 blocks
            long newHeight = Math.max(0, lastHeight - 30);

            // Remove transactions after newHeight
            removeIncomeSinceHeight(newHeight);
            removeExpenseSinceHeight(newHeight);

            // Update height and block ID
            localDB.putState(LAST_HEIGHT, newHeight);
            String blockInfo = String.valueOf(newHeight);
            Map<String, BlockInfo> blocks = apipClient.blockByHeights(RequestMethod.POST, AuthType.FC_SIGN_BODY, blockInfo);
            if (blocks != null) {
                BlockInfo block = blocks.get(blockInfo);
                if(block==null)return false;
                localDB.putState(LAST_BLOCK_ID, block.getId());
            }

            // Clear last income and expense to force rescan from new height
            localDB.putState(LAST_INCOME, new ArrayList<>());
            localDB.putState(LAST_EXPENSE, new ArrayList<>());
            this.startHeight= blockInfo;
            return true;
        }
        return false;
    }

    public static boolean isRolledBack(long lastHeight, String lastBlockId, ApipClient apipClient) {

        if (lastHeight==0 || lastBlockId ==null)return false;

        Map<String, BlockInfo> heightBlockInfoMap = apipClient.blockByHeights(RequestMethod.POST, AuthType.FC_SIGN_BODY, String.valueOf(lastHeight));
        if(heightBlockInfoMap==null)heightBlockInfoMap = apipClient.blockByHeights(RequestMethod.POST, AuthType.FC_SIGN_BODY, String.valueOf(lastHeight));
        if(heightBlockInfoMap==null)throw new RuntimeException("Failed to get last block info. Check APIP service.");

        BlockInfo blockInfo = heightBlockInfoMap.get(String.valueOf(lastHeight));
        return !blockInfo.getId().equals(lastBlockId);
    }

    public boolean isBadBalance(String senderFid) {
        Long balance = checkUserBalance(senderFid);//getBalanceByFid(Settings.addSidBriefToName(talkServer.getService().getSid(),BALANCE), jedis, senderFid);
        if (balance==null || balance < 0) {
            updateIncome();
            balance = checkUserBalance(senderFid);
            return balance == null || balance < 0;
        }
        return false;
    }

        /**
         * Adds a value to the user's balance
         * @param userFid The user's FID
         * @param value The value to add
         * @return The new balance
         */
        public Long updateUserBalance(String userFid, Long value) {
            if(!KeyTools.isGoodFid(userFid) || value==null)return null;

            return updateUserBalanceInLocal(userFid, value);
        }

    private long updateUserBalanceInLocal(String userFid, Long value) {
        Long balanceFromLocal = getBalanceFromLocal(userFid);
        long newBalance  = balanceFromLocal +value;
        if (newBalance <= 0) {
            updateIncome();
            newBalance = getBalanceFromLocal(userFid) +value;
            if (newBalance <= 0) {
                userBalance.remove(userFid);
                removeUserBalanceInLocalDB(userFid);
                return 0;
            }
        }
        Map.Entry<String, Long> oldest = userBalance.put(userFid, newBalance);
        if(oldest!=null)localDB.putInMap(USER_BALANCE_MAP,oldest.getKey(),oldest.getValue());
        return newBalance;
    }

    @NotNull
    private Long getBalanceFromLocal(String userFid) {
        Long currentBalance = userBalance.get(userFid);
        if (currentBalance == null) {
            currentBalance = getUserBalance(userFid);
            if(currentBalance == null)
                currentBalance = 0L;
        }
        return currentBalance;
    }

    public Long userSpend(String userFid, String api,Long length,String via) {
            long amount = (length + 999) / 1000;
            // Get nPrice multiplier
            long nPrice;
            if(api!=null)
                nPrice = getNPrice(api);
            else nPrice=1;
            
            long cost = amount * priceBase * nPrice;
            Long newBalance = updateUserBalance(userFid, -cost);
            if(newBalance>0)updateViaBalance(userFid, cost, via);
            return newBalance;
        }
    
        public void transferBalanceFromOneToMulti(int length, String from, List<String> toFidList, long kbPrice) {
            int kb = (int) Math.ceil(length / 1024.0); // Round up to ensure partial KB counts as 1
            updateUserBalance(from,(-1)*(kbPrice*toFidList.size()+1)*kb);
            for(String fid:toFidList){
                updateUserBalance(fid,kbPrice*kb);
            }
        }

        public void updateUserBalanceMap(Map<String, Long> balanceMap) {
            updateUserBalanceInLocal(balanceMap);
        }

    private void updateUserBalanceInLocal(Map<String, Long> balanceMap) {
        // For each balance to add
        Map<String,Long> moveToLocalDB = new HashMap<>();
        List<String> removedKeyList = new ArrayList<>();
        for (Map.Entry<String, Long> entry : balanceMap.entrySet()) {
            String userFid = entry.getKey();
            Long balanceFromLocal = getBalanceFromLocal(userFid);
            Long value = entry.getValue();
            long newBalance  = balanceFromLocal + value;
            if (newBalance <= 0) {
                updateIncome();
                newBalance = getBalanceFromLocal(userFid) +value;
                if (newBalance <= 0) {
                    userBalance.remove(userFid);
                    removedKeyList.add(userFid);
                    continue;
                }
            }
            Map.Entry<String, Long> oldest = userBalance.put(userFid, newBalance);
            if(oldest!=null)moveToLocalDB.put(oldest.getKey(),oldest.getValue());
        }
        localDB.putAllInMap(USER_BALANCE_MAP, new ArrayList<>(moveToLocalDB.keySet()), new ArrayList<>(moveToLocalDB.values()));
        localDB.removeFromMap(USER_BALANCE_MAP,removedKeyList);
        localDB.commit();
    }
    public void removeUserBalanceInLocalDB(String fid) {
        localDB.removeFromMap(USER_BALANCE_MAP, fid);
        localDB.commit();
    }

    /**
     * Checks if a user has sufficient balance
     * @param userFid The user's FID
     * @return The user's balance if greater than 0, null otherwise
     */
    public Long checkUserBalance(String userFid) {
        if (userFid == null) return null;
        if(userFid.equals(this.mainFid))return FchUtils.coinToSatoshi(this.minPay);
        Long balance;
        
        // First check current balance
        balance = userBalance.get(userFid);
        if (balance != null && balance > 0) {
            return balance;
        }
        balance = getUserBalance(userFid);

        if(balance == null){
            List<Income> newIncomes = updateIncome();
            if (newIncomes != null && !newIncomes.isEmpty()) {
                balance = checkUserBalance(userFid);
            }else{
                return null;
            }
        }
        return balance;
    }

    /**
     * Updates or creates an entry in the FID via map
     * @param userFid The user's FID
     * @param viaFid The via FID to map to
     */
    public void updateFidConsumeVia(String userFid, String viaFid) {
        if (userFid == null || viaFid == null || !KeyTools.isGoodFid(userFid) || !KeyTools.isGoodFid(viaFid)) {
            return;
        }

        localDB.putInMap(FID_VIA_MAP, userFid, viaFid);
        fidViaMap.put(userFid, viaFid);
    }

    public String getFidConsumeVia(String userFid){
        String via;

        via = fidViaMap.get(userFid);
        if(via ==null)via = localDB.getFromMap(FID_VIA_MAP,userFid);
        return via;
    }
       /**
     * Updates via balances based on user transactions
     * @param userFid The user's FID
     * @param value The transaction value
     * @param orderVia the via fid if this is an order, null if consumption
     */
    public void updateViaBalance(String userFid, Long value, @Nullable String orderVia) {
        if(!KeyTools.isGoodFid(userFid))return;
        if(orderVia!=null && !KeyTools.isGoodFid(orderVia))return;
        String viaFid;
        if(orderVia==null) {
            viaFid = fidViaMap.get(userFid);
            if (viaFid == null) {
                viaFid = getFidVia(userFid);
                fidViaMap.put(userFid, viaFid);
            }
        }else{
            viaFid = orderVia;
        }
        if(viaFid==null)return;

        Double sharePercentage = orderVia==null ? getConsumeViaShare():getOrderViaShare() ;
        if (sharePercentage == null) return;

        long viaShare = (long)(value * sharePercentage);
        if(viaShare<=0)return;
        
        Long currentViaBalance = viaBalance.get(viaFid);
        if (currentViaBalance == null) {
            currentViaBalance = 0L;
            Long oldValue = getViaBalance(viaFid);
            if (oldValue != null) currentViaBalance = oldValue;
        }
        Map.Entry<String, Long> oldest = viaBalance.put(viaFid, currentViaBalance + viaShare);
        updateViaBalanceInLocalDB(oldest);
    }

    public String getFidVia(String fid) {
        return localDB.getFromMap(FID_VIA_MAP, fid);
    }

    public void setFidVia(String fid, String viaFid) {
        localDB.putInMap(FID_VIA_MAP, fid, viaFid);
        localDB.commit();
    }


    /**
     * Adds elements from in-memory maps to the corresponding AccountDB maps
     */
    public void saveMapsToLocalDB() {
        // Add user balances
        updateUserBalanceInLocal(userBalance.getMap());
        // Add via balances
        updateViaBalanceMapInLocalDB(viaBalance.getMap());
    }


    // Add this field at the class level
    private long lastUpdateIncomeTime = 0;

    /**
     * Updates income records and related balances
     */
    public List<Income> updateIncome() {
        // Get last income based on storage type
        List<String> lastIncome = getLastIncome();

        // Get new incomes from APIP
        Map<String, Long> userFidValueMap = new HashMap<>();
        List<Cash> newCashes=null;
        Long lastHeight = null;
        String lastBlockId = null;

        while(true){
            if(apipClient!=null){
                // Create Fcdsl query to get new incomes
                Fcdsl fcdsl = new Fcdsl();
                fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(mainFid);
                if((lastIncome==null|| lastIncome.isEmpty()) && startHeight!=null) {
                    fcdsl.getQuery().addNewRange().addNewFields(BIRTH_HEIGHT).addGt(startHeight);
                }
                // Change sort order to DESC to get newest items first
                fcdsl.addSort(BIRTH_HEIGHT, Values.ASC).addSort(ID, Values.DESC);
                fcdsl.addSize(DEFAULT_DISPLAY_LIST_SIZE);
                if (lastIncome!=null && !lastIncome.isEmpty()) {
                    fcdsl.addAfter(lastIncome);
                }

                if (lastIncome!=null && !lastIncome.isEmpty()) {
                    fcdsl.addAfter(lastIncome);
                }
                newCashes = apipClient.cashSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
                ReplyBody responseBody = apipClient.getFcClientEvent().getResponseBody();
                if(responseBody!=null){
                    lastIncome = responseBody.getLast();
                    lastHeight = responseBody.getBestHeight();
                    lastBlockId = responseBody.getBestBlockId();
                }
                if (newCashes == null || newCashes.isEmpty()) {
                    break;
                }
            }else{
                TimberLogger.e(TAG, "No client available to fetch cash data");
                break;
            }
            if(newCashes.size() < DEFAULT_DISPLAY_LIST_SIZE){
                break;
            }
        }

        List<Income> newIncomeList = addToNewIncomeAndUserBalance(userFidValueMap, newCashes);
        if(newIncomeList==null)return null;
        // Store updates based on storage type
        if (!newIncomeList.isEmpty()) {

            updateIncomes(newIncomeList);

            // Update lastHeight and lastBlockId
            if (lastHeight != null) {
                setLastHeight(lastHeight);
                setLastBlockId(lastBlockId);
            }
            // Display using map values instead of list
            System.out.println("Got "+newIncomeList.size()+" new incomes.");
        }

        if(!userFidValueMap.isEmpty()){
            updateUserBalanceMap(userFidValueMap);
        }

        // Update lastIncome in persistent storage
        setLastIncome(lastIncome);

        checkIncomeVia(newCashes);

        return newIncomeList;
    }

    private static List<Income> addToNewIncomeAndUserBalance(Map<String, Long> userFidValueMap, List<Cash> newCashes) {
        if(newCashes==null||newCashes.isEmpty())return null;
        List<Income> incomeList = new ArrayList<>();
        for(Cash cash: newCashes){
            // Skip if issuer is the owner
            if (cash.getIssuer().equals(cash.getOwner())) {
                continue;
            }
            // Create new income record
            Income income = new Income(
                cash.getId(),
                cash.getIssuer(),
                cash.getValue(),
                cash.getBirthTime(),
                cash.getBirthHeight()
            );
            incomeList.add(income);

            // Update user balance
            userFidValueMap.merge(cash.getIssuer(), cash.getValue(), Long::sum);
        }
        return incomeList;
    }

    private void checkIncomeVia(List<Cash> newCashes) {
        if(newCashes==null || newCashes.isEmpty()) return;
        Map<String,String> txIdCashIdMap = new HashMap<>();
        for(Cash cash:newCashes){
            txIdCashIdMap.put(cash.getBirthTxId(), cash.getId());
        }
        Map<String, OpReturn> opReturnMap;
        if (apipClient != null) {
            opReturnMap = apipClient.opReturnByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, txIdCashIdMap.keySet().toArray(new String[0]));
        } else{
            TimberLogger.e(TAG, "No client available to fetch OP_RETURN.");
            return;
        }
        if(opReturnMap==null || opReturnMap.isEmpty()) return;
        for(Map.Entry<String, OpReturn> entry:opReturnMap.entrySet()){
            OpReturn opReturn = entry.getValue();
            Map<String, String> opReturnData = ObjectUtils.objectToMap(opReturn.getOpReturn(), String.class, String.class);
            if(opReturnData==null)continue;
            if(opReturnData.containsKey(VIA)){
                List<Cash> matchingCashes = newCashes.stream()
                    .filter(cash -> cash.getBirthTxId().equals(opReturn.getId()))
                    .collect(Collectors.toList());
                if(matchingCashes.isEmpty())continue;
                for(Cash cash:matchingCashes){
                    updateViaBalance(cash.getIssuer(),cash.getValue(),opReturnData.get(VIA));
                }
            }
        }
    }
            

    /**
     * Updates expense records
     */
    public int updateExpense() {
        // Get last expense based on storage type
        List<String> lastExpense;
        lastExpense = getLastExpense();

        // Get new expenses from APIP
        List<Expense> newExpenseList = new ArrayList<>();
        Long lastHeight = null;
        String lastBlockId = null;

        while(true) {
            List<Cash> newCashes;
            if(apipClient!=null){
                // Create Fcdsl query to get new expenses
                Fcdsl fcdsl = new Fcdsl();
                fcdsl.addNewQuery().addNewTerms().addNewFields(ISSUER).addNewValues(mainFid);
                if((lastExpense==null || lastExpense.isEmpty()) && startHeight!=null) {
                    fcdsl.getQuery().addNewRange().addNewFields(BIRTH_HEIGHT).addGt(startHeight);
                }
                // Change sort order to DESC to get newest items first
                fcdsl.addSort(BIRTH_HEIGHT, Values.ASC).addSort(ID, Values.ASC);
                fcdsl.addSize(DEFAULT_DISPLAY_LIST_SIZE);
                if (lastExpense!=null && !lastExpense.isEmpty()) {
                    fcdsl.addAfter(lastExpense);
                }
                newCashes = apipClient.cashSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
                if(apipClient.getFcClientEvent().getResponseBody().getLast()!=null)
                    lastExpense = apipClient.getFcClientEvent().getResponseBody().getLast();
                if (newCashes == null || newCashes.isEmpty()) {
                    break;
                }

                for (Cash cash : newCashes) {
                    // Create new expense record
                    Expense expense = new Expense(
                        cash.getId(),
                        cash.getOwner(),
                        cash.getValue(),
                        cash.getBirthTime(),
                        cash.getBirthHeight()
                    );
                    
                    newExpenseList.add(expense);
                }
            }else{
                TimberLogger.e(TAG, "No client available to fetch cash data");
                break;
            }
                
            if(newCashes.size() < DEFAULT_DISPLAY_LIST_SIZE){
                break;
            }
        }

        // Store updates based on storage type
        if (!newExpenseList.isEmpty()) {
            updateExpenses(newExpenseList);
            // Update lastHeight and lastBlockId if available
            if (lastHeight != null) {
                setLastHeight(lastHeight);
                setLastBlockId(lastBlockId);
            }
            System.out.println("Got "+newExpenseList.size()+" new expenses.");
        }

        // Update lastExpense in persistent storage
        setLastExpense(lastExpense);

        return newExpenseList.size();  // Return number of new expenses processed
    }
    /**
     * Distributes available balance to various recipients
     * @return true if distribution was successful, false if insufficient funds
     */
    public Boolean distribute() {
        // Update current balance
        long now = System.currentTimeMillis();
        Long lastDistributeTime = (Long) localDB.getState("LastDistributeTime");

        if(lastDistributeTime==null || lastDistributeTime==0){
            localDB.putState("LastDistributeTime",now);
            return null;
        }else if( (now-lastDistributeTime)/ Constants.DAY_TO_MIL_SEC < distributeDays) return null;

        updateAll();
        if(cashHandler!=null)cashHandler.freshCashDB();

        if (!updateMyBalance()) {
            return false;
        }
        // Calculate available balance for distribution

        long validSum = myBalance - dealerMinBalance;
        if(validSum < minDistributeBalance){
            TimberLogger.i(TAG, "The balance is less than the minDistributeBalance("+FchUtils.satoshiToCoin(myBalance)+"<"+FchUtils.satoshiToCoin(minDistributeBalance)+").");
            return false;
        }
        if (validSum <= 0) {
            return false;
        }

        // Get pending payoffMap from AccountDB
        payoffMap.putAll(getPayoffMap());

        validSum = myBalance - dealerMinBalance - calculateTotalPayoff();
        // Process unpaid via balances first
        makePaymentToVia(validSum);

        validSum = myBalance - dealerMinBalance - calculateTotalPayoff();
        
        // Process fixed costs
        if (!makePaymentForFixedCost(validSum)) {
            return false;
        }
        validSum = myBalance - dealerMinBalance - calculateTotalPayoff();

        // Distribute remaining balance as dividends
        return makeDividends(validSum);
    }

    private boolean makePaymentToVia(long availableBalance) {
        Map<String, Long> unpaidViaBalance = getUnpaidViaBalance();
        
        // Handle unpaid balances first
        long unpaidTotal = unpaidViaBalance.values().stream().mapToLong(Long::longValue).sum();
        if (unpaidTotal > 0) {
            if (availableBalance >= unpaidTotal) {
                payoffMap.putAll(unpaidViaBalance);
                unpaidViaBalance.clear();
                clearUnpaidViaBalanceMap();
            } else {
                    // Get any additional balances from AccountDB
                Map<String, Long> finalViaBalances = getViaBalanceMapFromLocalDB();
                if (finalViaBalances != null && !finalViaBalances.isEmpty()) {
                    unpaidViaBalance.putAll(finalViaBalances);
                }
                // Get balances from memory cache
                if(viaBalance.size()>0)
                    unpaidViaBalance.putAll(viaBalance.getMap());

                // Clear sources
                viaBalance.clear();
                clearViaBalanceMapInLocalDB();
                // Update unpaid balances in storage
                updateUnpaidViaBalance(unpaidViaBalance);
                return false;
            }
        }

        // Handle current via balances
        long viaTotal;
        Map<String, Long> finalViaBalance = getViaBalanceMapFromLocalDB();
        finalViaBalance.putAll(viaBalance.getMap());
        viaTotal = finalViaBalance.values().stream().mapToLong(Long::longValue).sum();
        if (availableBalance >= viaTotal) {
            payoffMap.putAll(finalViaBalance);
            clearViaBalanceMapInLocalDB();
            viaBalance.clear();
            return true;
        } else {
            // Not enough funds, move all to unpaid
            unpaidViaBalance.putAll(finalViaBalance);
            updateUnpaidViaBalance(unpaidViaBalance);
            clearViaBalanceMapInLocalDB();
            viaBalance.clear();
            return false;
        }
    }


    public Map<String, Long> getViaBalanceMapFromLocalDB() {
        return localDB.getAllFromMap(FieldNames.VIA_BALANCE_MAP);
    }

    public void clearViaBalanceMapInLocalDB() {
        localDB.clearMap(FieldNames.VIA_BALANCE_MAP);
        localDB.commit();
    }   


    public void setViaBalanceFromLocalDB(String viaFid, Long balance) {
        localDB.putInMap(FieldNames.VIA_BALANCE_MAP, viaFid, balance);
        localDB.commit();
    }

    // Unpaid Via Balance methods
    public Long getUnpaidViaBalance(String viaFid) {
        Long balance = localDB.getFromMap(FieldNames.UNPAID_VIA_BALANCE_MAP, viaFid);
        return balance != null ? balance : 0L;
    }

    public void setNPrices(List<String> apiList, BufferedReader br, boolean reset) {
        if(hasNPrices() && !reset) {
            if(this.nPriceMap == null) {
                nPriceMap = new HashMap<>();
                Map<String, Long> storedPrices = getNPriceMap();
                if(storedPrices != null && !storedPrices.isEmpty()) {
                    nPriceMap.putAll(storedPrices);
                }
            }
            return;
        }
        Map<Integer, String> apiMap = apiListToMap(apiList);
        showAllAPIs(apiMap);
        
        while (true) {
            System.out.println("""
                    Set nPrices:
                    \t'a' to set all nPrices,
                    \t'one' to set all nPrices by 1,
                    \t'zero' to set all nPrices by 0,
                    \tan integer to set the corresponding API,
                    \tor 'q' to quit:\s""");
            String str = null;
            try {
                str = br.readLine();
                if ("".equals(str)) str = br.readLine();
                if (str.equals("q")) return;
                if (str.equals("a")) {
                    setAllNPrices(apiMap, br);
                    System.out.println("Done.");
                    return;
                }
            } catch (Exception e) {
                TimberLogger.e(TAG, "Set nPrice wrong. ", e);
            }
            if (str == null) {
                TimberLogger.e(TAG, "Set nPrice failed. ");
                continue;
            }

            if (str.equals("one")) {
                setAllNPricesWithValue(apiList, 1L);
                return;
            }
            if (str.equals("zero")) {
                setAllNPricesWithValue(apiList, 0L);
                return;
            }
            try {
                int i = Integer.parseInt(str);
                if (i > apiMap.size()) {
                    System.out.println("The integer should be no bigger than " + apiMap.size());
                } else {
                    setNPrice(i, apiMap, br);
                    System.out.println("Done.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Wrong input.");
            }
        }
    }

    private void setAllNPricesWithValue(List<String> apiList, long value) {
        Map<Integer, String> apiMap = apiListToMap(apiList);
        for (Map.Entry<Integer, String> entry : apiMap.entrySet()) {
            setNPrice(entry.getValue(), value);
            if(nPriceMap == null) nPriceMap = new HashMap<>();
            nPriceMap.put(entry.getValue(), value);
        }
        System.out.println("All API prices are set to "+value+".");
    }

    private void setAllNPrices(Map<Integer, String> apiMap, BufferedReader br) throws IOException {
        for (int i : apiMap.keySet()) {
            setNPrice(i, apiMap, br);
        }
    }

    private void setNPrice(int i, Map<Integer, String> apiMap, BufferedReader br) {
        if(this.nPriceMap == null) nPriceMap = new HashMap<>();
        String apiName = apiMap.get(i);
        while (true) {
            try {
                Long n = Inputer.inputLong(br, "Input the multiple number of API " + apiName + ":");
                // Store in local map
                setNPrice(apiName, n);
                nPriceMap.put(apiName, n);
                return;
            } catch (Exception e) {
                System.out.println("Wrong input.");
            }
        }
    }

    public static void showAllAPIs(Map<Integer, String> apiMap) {
        System.out.println("API list:");
        for (int i = 1; i <= apiMap.size(); i++) {
            System.out.println(i + ". " + apiMap.get(i));
        }
    }

    public Map<Integer, String> apiListToMap(List<String> apiList) {

        Map<Integer, String> apiMap = new HashMap<>();
        for (int i = 0; i < apiList.size(); i++) apiMap.put(i + 1, apiList.get(i));
        return apiMap;
    }

    private boolean makePaymentForFixedCost(long availableBalance) {
        Map<String, Long> fixedCostMap = getFixedCostMap();
        Map<String, Long> unpaidFixedCostMap = getUnpaidFixedCostMap();
        
        long totalCost = fixedCostMap.values().stream().mapToLong(Long::longValue).sum();
        long unpaidTotal = unpaidFixedCostMap.values().stream().mapToLong(Long::longValue).sum();
        totalCost += unpaidTotal;
        
        if (availableBalance >= totalCost) {
            payoffMap.putAll(fixedCostMap);
            payoffMap.putAll(unpaidFixedCostMap);
            clearUnpaidFixedCostMap();
            return true;
        } else {
            // Not enough funds, move to unpaid
            unpaidFixedCostMap.putAll(fixedCostMap);
            updateUnpaidFixedCostMap(unpaidFixedCostMap);
            return false;
        }
    }


    private boolean makeDividends(long availableBalance) {
        Map<String, Double> contributorShareMap = getContributorShareMap();
        
        for (Map.Entry<String, Double> entry : contributorShareMap.entrySet()) {
            String contributorFid = entry.getKey();
            Double share = ObjectUtils.objectToClass(entry.getValue(),Double.class);
            long dividend = (long)(availableBalance * share);
            
            if (dividend > 0) {
                payoffMap.merge(contributorFid, dividend, Long::sum);
            }
        }
        
        return true;
    }

    private long calculateTotalPayoff() {
        return payoffMap.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * Settles all pending payments
     * @return true if settlement was successful, false otherwise
     */
    public boolean settle() {
        long validSum = myBalance - dealerMinBalance;
        if(validSum<calculateTotalPayoff()){
            System.out.println("The balance is insufficient for the pay off list.");
            return false;
        }
        if (payoffMap.isEmpty()) {
            payoffMap = getPayoffMap();
            if(payoffMap==null || payoffMap.isEmpty()){
                System.out.println("The payoffMap is empty.");
                return false;
            }
        }
        System.out.println("Settle all payments...");

        // Convert payoffMap to SendTo list
        List<SendTo> sendToList = payoffMap.entrySet().stream()
            .map(entry -> new SendTo(entry.getKey(), FchUtils.satoshiToCoin(entry.getValue())))
            .collect(Collectors.toList());

        // Send payments using CashClient
        String txId;
        if(cashHandler!=null) {
            txId = cashHandler.send(null, null, null, sendToList, null, null, null,  FchMainNetwork.MAINNETWORK, null,null);
            cashHandler.sendResult(null,txId);
        } else {
            txId = CashHandler.send(prikey,sendToList,apipClient, nasaClient);
            if(Hex.isHex32(txId))txId = TxResultType.TX_ID.addTypeAheadResult(txId);
        }

        // Update last settlement height
        Long bestHeight = Settings.getBestHeight(apipClient,null);
        setLastSettleHeight(bestHeight);

        if (!TxResultType.parseType(txId).equals(TxResultType.TX_ID)) {
            // Save payoffMap to AccountDB if transaction was successful but couldn't get height
            System.out.println("Failed to send Tx. Saved payoffMap to localDB. The payments will be send in next distribution.");
            updatePayoffMap(payoffMap);
            payoffMap.clear();
            return false;
        }

        payoffMap.clear();
        clearPayoffMap();
        return true;
    }

    /**
     * Initiates the account with contributor shares, fixed costs, and via share settings
     * @param br BufferedReader for user input
     * @param orderViaShare Share percentage for order via
     * @param consumeViaShare Share percentage for consume via
     */
    public void initShareAndCost(BufferedReader br, Double orderViaShare, Double consumeViaShare, boolean reset) {
        if (!reset) {
            if (!getContributorShareMap().isEmpty()) {
                return;
            }
        }

        // Input contributor shares
        System.out.println("Set the share of contributors.");
        inputContributorShare(br);

        // Input fixed costs
        System.out.println("Set the fixed costs.");
        inputFixedCost(br);

        // Set via shares in Redis or AccountDB

        if (orderViaShare != null)
            setOrderViaShare(orderViaShare);
        else{
            Double inputShare = Inputer.inputDouble(br, "Input the order via share percentage:");
            if(inputShare==null)return;
            setOrderViaShare(NumberUtils.roundDouble8(inputShare/100.0));
        }

        if (consumeViaShare != null){
            setConsumeViaShare(consumeViaShare);
        }else{
            Double inputShare = Inputer.inputDouble(br, "Input the consume via share percentage:");
            if(inputShare==null)return;
            setConsumeViaShare(NumberUtils.roundDouble8(inputShare/100));
        }
    }

    public void inputContributorShare(BufferedReader br) {
        try {
            // Clear existing contributor shares
            Map<String, Double> contributorShareMap = localDB.getAllFromMap(FieldNames.CONTRIBUTOR_SHARE_MAP);
            if(contributorShareMap.size() > 0){
                for(String fid : contributorShareMap.keySet()){
                    System.out.println(fid + " -> " + contributorShareMap.get(fid));
                }
                if(Inputer.askIfYes(br,"There are already "+contributorShareMap.size()+" contributors. Reset all contributors? ")){
                    localDB.clearMap(FieldNames.CONTRIBUTOR_SHARE_MAP);
                    localDB.commit();
                } else return;
            }

            double totalShare = 0.0;
            while (true) {
                String fid = Inputer.inputFid(br, "Input the contributor FID:");
                if (fid == null || fid.isEmpty()) continue;

                Double share;
                if (Inputer.askIfYes(br, "Set the rest of the share percentage ("+(100-totalShare)+"%) for " + fid + "? \n")) {
                    share = NumberUtils.roundDouble4((100 - totalShare)/100);
                    localDB.putInMap(FieldNames.CONTRIBUTOR_SHARE_MAP, fid, share);
                    localDB.commit();
                    break;
                } else {
                    share = Inputer.inputDouble(br, "Input the share percentage for " + fid + ":");
                    if (share == null) continue;
                    totalShare += share;
                }

                if (totalShare > 100) {
                    System.out.println("Total share exceeds 100%. Please try again.");
                    if(Inputer.askIfYes(br,"Reset the share for "+fid+"?")){
                        totalShare = 0;
                        localDB.clearMap(FieldNames.CONTRIBUTOR_SHARE_MAP);
                        continue;
                    }
                    totalShare -= share;
                    continue;
                }
                share = NumberUtils.roundDouble4(share/100);
                localDB.putInMap(FieldNames.CONTRIBUTOR_SHARE_MAP, fid, share);
                localDB.commit();

                if (totalShare == 100) break;
                System.out.println("There is still " + (100 - totalShare) + " left. Please add more contributors.");
            }
        } catch (Exception e) {
            System.out.println("Error adding contributor share: " + e.getMessage());
        }
    }

    public void inputFixedCost(BufferedReader br) {
        Map<String, Long> fixedCostMap = localDB.getAllFromMap(FieldNames.FIXED_COST_MAP);
        if(fixedCostMap.size() > 0){
            for(String fid : fixedCostMap.keySet()){
                System.out.println(fid + " -> " + FchUtils.satoshiToCoin(fixedCostMap.get(fid)));
            }
            if(Inputer.askIfYes(br,"There are already "+fixedCostMap.size()+" fixed costs. Reset all fixed costs? ")){
                localDB.clearMap(FieldNames.FIXED_COST_MAP);
                localDB.commit();
            } else return;
        }
        try {
            while (true) {
                System.out.println("\nInput fid and fixed cost(split by space). Empty line to finish:");
                String line = br.readLine();
                if (line == null || line.trim().isEmpty()) {
                    break;
                }

                String[] parts = line.trim().split("\\s+");
                if (parts.length != 2) {
                    System.out.println("Invalid input format. Please enter fid and cost separated by space.");
                    continue;
                }

                String fid = parts[0];
                double cost;
                try {
                    cost = Double.parseDouble(parts[1]);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid cost value. Please enter a valid number.");
                    continue;
                }

                if (cost <= 0) {
                    System.out.println("Cost must be greater than 0.");
                    continue;
                }
                long costLong = FchUtils.coinToSatoshi(cost);
                localDB.putInMap(FieldNames.FIXED_COST_MAP, fid, costLong);
                localDB.commit();
                System.out.println("Added fixed cost: " + fid + " -> " + cost);
            }
        } catch (Exception e) {
            System.out.println("Error adding fixed cost: " + e.getMessage());
        }
    }


    public void close() {
        // Stop the distribution thread
        saveMapsToLocalDB();
        BytesUtils.clearByteArray(prikey);
    }

    /**
     * Shows income list with pagination
     */
    public void showIncomeList() {
        int currentPage = 0;
        boolean continueShowing = true;
        long totalItems = localDB.getListSize(FieldNames.INCOME_MAP);
        int totalPages = (int) Math.ceil((double) totalItems / Shower.DEFAULT_PAGE_SIZE);

        while (continueShowing) {
            currentPage++;
            // Calculate indices for pagination
            long startIndex = (long) (currentPage - 1) * Shower.DEFAULT_PAGE_SIZE;
            long endIndex = Math.min(startIndex + Shower.DEFAULT_PAGE_SIZE, totalItems);

            // Get items in forward order since they're already stored newest first
            List<Income> incomes = localDB.getRangeFromListReverse(FieldNames.INCOME_MAP, startIndex, endIndex);
            
            if (incomes == null || incomes.isEmpty()) {
                if (currentPage == 1) {
                    System.out.println("No income records found.");
                }
                break;
            }

            System.out.printf("\nPage %d/%d:\n", currentPage, totalPages);
            Shower.showOrChooseList("Your incomes", incomes, null, false, Income.class, br);

            int remainingPages = totalPages - currentPage;
            if (remainingPages > 0) {
                System.out.printf("\nThere are %d more page(s). Press Enter to view more records, or 'q' to quit: ", remainingPages);
            } else {
                System.out.println("\nNo more records. Press any key to continue...");
                try {
                    br.readLine();
                } catch (IOException e) {
                    TimberLogger.e(TAG, "Error reading input", e);
                }
                break;
            }

            try {
                String input = br.readLine();
                if ("q".equalsIgnoreCase(input)) {
                    continueShowing = false;
                }
            } catch (IOException e) {
                TimberLogger.e(TAG, "Error reading input", e);
                break;
            }
        }
    }

    /**
     * Shows expense list with pagination
     */
    public void showExpenseList() {
        int currentPage = 0;
        boolean continueShowing = true;
        long totalItems = localDB.getListSize(FieldNames.EXPENSE_MAP);
        int totalPages = (int) Math.ceil((double) totalItems / Shower.DEFAULT_PAGE_SIZE);

        while (continueShowing) {
            currentPage++;
            // Calculate indices for pagination
            long startIndex = (long) (currentPage - 1) * Shower.DEFAULT_PAGE_SIZE;
            long endIndex = Math.min(startIndex + Shower.DEFAULT_PAGE_SIZE, totalItems);

            // Get items in forward order since they're already stored newest first
            List<Expense> expenses = localDB.getRangeFromListReverse(FieldNames.EXPENSE_MAP, startIndex, endIndex);
            
            if (expenses == null || expenses.isEmpty()) {
                if (currentPage == 1) {
                    System.out.println("No expense records found.");
                }
                break;
            }

            System.out.printf("\nPage %d/%d:\n", currentPage, totalPages);
            Shower.showOrChooseList("Your expenses", expenses, null, false, Expense.class, br);

            int remainingPages = totalPages - currentPage;
            if (remainingPages > 0) {
                System.out.printf("\nThere are %d more page(s). Press Enter to view more records, or 'q' to quit: ", remainingPages);
            } else {
                System.out.println("\nNo more records. Press any key to continue...");
                try {
                    br.readLine();
                } catch (IOException e) {
                    TimberLogger.e(TAG, "Error reading input", e);
                }
                break;
            }

            try {
                String input = br.readLine();
                if ("q".equalsIgnoreCase(input)) {
                    continueShowing = false;
                }
            } catch (IOException e) {
                TimberLogger.e(TAG, "Error reading input", e);
                break;
            }
        }
    }

    public Long getUserBalance(String fid) {
        return localDB.getFromMap(USER_BALANCE_MAP, fid);
    }


    public Long getPriceBase() {
        return priceBase;
    }

    public Map<String, Long> getnPriceMap() {
        return nPriceMap;
    }

    public void setnPriceMap(Map<String, Long> nPriceMap) {
        this.nPriceMap = nPriceMap;
    }

    public Long getNPrice(String apiName) {
        return nPriceMap.getOrDefault(apiName, 1L);
    }

    /**
     * Gets the balance for a specific via FID
     * @param viaFid The via FID to check
     * @return The via's balance if found, null otherwise
     */
    public Long getViaBalance(String viaFid) {
        if (!KeyTools.isGoodFid(viaFid)) {
            return null;
        }

        // First check the in-memory cache
        Long balance = viaBalance.get(viaFid);
        if (balance != null) {
            return balance;
        }
        
        // If not in cache, check the database
        balance = localDB.getFromMap(FieldNames.VIA_BALANCE_MAP, viaFid);
        return balance != null ? balance : 0L;
    }

   // Bulk update methods for maps
   public void updateUserBalanceInLocalDB(Map<String, Long> balances) {
    for (Map.Entry<String, Long> entry : balances.entrySet()) {
        localDB.putInMap(USER_BALANCE_MAP, entry.getKey(), entry.getValue());
    }
    localDB.commit();
}

public void updateViaBalanceMapInLocalDB(Map<String, Long> balances) {
    for (Map.Entry<String, Long> entry : balances.entrySet()) {
        localDB.putInMap(FieldNames.VIA_BALANCE_MAP, entry.getKey(), entry.getValue());
    }
    localDB.commit();
}

public void updateViaBalanceInLocalDB(Map.Entry<String, Long> balance) {
    if(balance == null) return;
    localDB.putInMap(FieldNames.VIA_BALANCE_MAP, balance.getKey(), balance.getValue());
    localDB.commit();
}

public void updateUnpaidViaBalance(Map<String, Long> balances) {
    balances.forEach((viaFid, balance) -> {
        Long existingBalance = getUnpaidViaBalance(viaFid);
        localDB.putInMap(FieldNames.UNPAID_VIA_BALANCE_MAP, viaFid, existingBalance + balance);
    });
    localDB.commit();
}

public void clearUnpaidViaBalanceMap() {
    localDB.clearMap(FieldNames.UNPAID_VIA_BALANCE_MAP);
    localDB.commit();
}

public void clearUnpaidFixedCostMap() {
    localDB.clearMap(FieldNames.UNPAID_FIXED_COST_MAP);
    localDB.commit();
}

public void updateUnpaidFixedCostMap(Map<String, Long> costs) {
    for (Map.Entry<String, Long> entry : costs.entrySet()) {
        localDB.putInMap(FieldNames.UNPAID_FIXED_COST_MAP, entry.getKey(), entry.getValue());
    }
    localDB.commit();
}

public void updateFidViaMapInLocalDB(Map<String, String> fidViaMap) {
    for (Map.Entry<String, String> entry : fidViaMap.entrySet()) {
        localDB.putInMap(FID_VIA_MAP, entry.getKey(), entry.getValue());
    }
    localDB.commit();
}

// Getter methods for entire maps
public Map<String, Long> getUnpaidViaBalance() {
    return localDB.getAllFromMap(FieldNames.UNPAID_VIA_BALANCE_MAP);
}

public Map<String, Long> getFixedCostMap() {
    return localDB.getAllFromMap(FieldNames.FIXED_COST_MAP);
}

public Map<String, Long> getUnpaidFixedCostMap() {
    return localDB.getAllFromMap(FieldNames.UNPAID_FIXED_COST_MAP);
}

public Map<String, Double> getContributorShareMap() {
    return localDB.getAllFromMap(FieldNames.CONTRIBUTOR_SHARE_MAP);
}

// Income/Expense tracking methods
public List<String> getLastIncome() {
    String lastIncome = (String) localDB.getState(LAST_INCOME);
    return lastIncome == null || lastIncome.isEmpty() ? new ArrayList<>() : Arrays.asList(lastIncome.split(","));
}

public void setLastIncome(List<String> lastIncome) {
    if(lastIncome == null || lastIncome.isEmpty()) return;
    localDB.putState(LAST_INCOME, String.join(",", lastIncome));
    localDB.commit();
}

public List<String> getLastExpense() {
    String lastExpense = (String) localDB.getSetting(LAST_EXPENSE);
    return lastExpense == null || lastExpense.isEmpty() ? new ArrayList<>() : Arrays.asList(lastExpense.split(","));
}

public void setLastExpense(List<String> lastExpense) {
    if(lastExpense == null || lastExpense.isEmpty()) return;
    localDB.putSetting(LAST_EXPENSE, String.join(",", lastExpense));
    localDB.commit();
}

public void setLastSettleHeight(Long height) {
    if(height == null) return;
    localDB.putSetting(FieldNames.LAST_SETTLE_HEIGHT, height.toString());
    localDB.commit();
}

// Add new method for batch income updates
public void updateIncomes(List<Income> incomes) {
    localDB.addAllToList(FieldNames.INCOME_MAP, incomes);
    localDB.commit();
}

public String getLastExpenseHeight() {
    return (String) localDB.getSetting(FieldNames.LAST_EXPENSE_HEIGHT);
}

public void setLastExpenseHeight(String height) {
    if(height == null) return;
    localDB.putSetting(FieldNames.LAST_EXPENSE_HEIGHT, height);
    localDB.commit();
}

// Add this method for batch expense updates
public void updateExpenses(List<Expense> expenses) {
    localDB.addAllToList(FieldNames.EXPENSE_MAP, expenses);
    localDB.commit();
}

// Add these methods for payoff management
public void updatePayoffMap(Map<String, Long> payoffs) {
    for (Map.Entry<String, Long> entry : payoffs.entrySet()) {
        localDB.putInMap(FieldNames.PAYOFF_MAP, entry.getKey(), entry.getValue());
    }
    localDB.commit();
}

public Map<String, Long> getPayoffMap() {
    return localDB.getAllFromMap(FieldNames.PAYOFF_MAP);
}

public void clearPayoffMap() {
    localDB.clearMap(FieldNames.PAYOFF_MAP);
    localDB.commit();
}

public void setOrderViaShare(double orderViaShare) {
    localDB.putSetting(FieldNames.ORDER_VIA_SHARE, String.valueOf(orderViaShare));
    localDB.commit();
}

public void setConsumeViaShare(double consumeViaShare) {
    localDB.putSetting(FieldNames.CONSUME_VIA_SHARE, String.valueOf(consumeViaShare));
    localDB.commit();
}

public boolean hasNPrices() {
    // Get all metadata entries and check if any keys start with "nPrice_"
    Map<String, String> metaMap = localDB.getAllFromMap(FieldNames.META_MAP);
    return metaMap.keySet().stream()
        .anyMatch(key -> key.startsWith("nPrice_"));
}

public Map<String, Long> getNPriceMap() {
    Map<String, Long> nPriceMap = new HashMap<>();
    Map<String, String> metaMap = localDB.getAllFromMap(FieldNames.META_MAP);
    
    // Iterate through metaMap and collect all nPrice entries
    metaMap.forEach((key, value) -> {
        if (key.startsWith("nPrice_")) {
            String apiName = key.substring("nPrice_".length());
            try {
                Long price = Long.parseLong(value);
                nPriceMap.put(apiName, price);
            } catch (NumberFormatException e) {
                // Skip invalid values
            }
        }
    });
    
    return nPriceMap;
}

public void setNPrice(String apiName, Long value) {
    if (apiName == null || value == null) return;
    
    localDB.putInMap(FieldNames.META_MAP, "nPrice_" + apiName, value.toString());
    localDB.commit();
}

public void setDealerMinBalance(Long balance) {
    if(balance == null) return;
    // Convert Long to String before storing
    localDB.putSetting(FieldNames.DEALER_MIN_BALANCE, balance.toString());
    localDB.commit();
}

public void setMinDistributeBalance(Long balance) {
    if(balance == null) return;
    // Convert Long to String before storing
    localDB.putSetting(FieldNames.MIN_DISTRIBUTE_BALANCE, balance.toString());
    localDB.commit();
}

public Long getMinDistributeBalance() {
    String balanceStr = (String) localDB.getSetting(FieldNames.MIN_DISTRIBUTE_BALANCE);
    if (balanceStr == null || balanceStr.isEmpty()) {
        return null;
    }
    
    try {
        return Long.parseLong(balanceStr);
    } catch (NumberFormatException e) {
        System.err.println("Invalid min distribute balance format: " + balanceStr);
        return null;
    }
}
public void setLastHeight(Long height) {
    if(height == null) return;
    // Convert Long to String before storing
    localDB.putSetting(LAST_HEIGHT, height.toString());
    localDB.commit();
}

public Long getLastHeight() {
    String heightStr = (String) localDB.getSetting(LAST_HEIGHT);
    if (heightStr == null || heightStr.isEmpty()) {
        return null;
    }
    
    try {
        return Long.parseLong(heightStr);
    } catch (NumberFormatException e) {
        System.err.println("Invalid last height format: " + heightStr);
        return null;
    }
}

public void setLastBlockId(String blockId) {
    if(blockId == null) return;
    localDB.putSetting(LAST_BLOCK_ID, blockId);
    localDB.commit();
}

public String getLastBlockId() {
    return (String) localDB.getSetting(LAST_BLOCK_ID);
}

public void removeIncomeSinceHeight(long height) {
    List<Income> allFromList = localDB.getAllFromList(FieldNames.INCOME_MAP);

    Map<String,Long>retreiveMap = new HashMap<>();
    Iterator<Income> iter = allFromList.iterator();

    while(iter.hasNext()){
        Income income = iter.next();
        long value = income.getValue();
        retreiveMap.merge(income.getFrom(),(-value),Long::sum);
        if(income.getHeight() > height) iter.remove();
    }

    updateUserBalanceMap(retreiveMap);

    localDB.clearList(FieldNames.INCOME_MAP);
    localDB.addAllToList(FieldNames.INCOME_MAP,allFromList);
    localDB.commit();
}

public void removeExpenseSinceHeight(long height) {
    // Get all expense entries
    List<Expense> allFromList = localDB.getAllFromList(FieldNames.EXPENSE_MAP);

    allFromList.removeIf(expense -> expense.getHeight() > height);

    localDB.clearList(FieldNames.EXPENSE_MAP);
    localDB.addAllToList(FieldNames.EXPENSE_MAP,allFromList);
    localDB.commit();
}

    public Long getMyBalance() {
        return myBalance;
    }
    public void setMyBalance(Long myBalance) {
        this.myBalance = myBalance;
    }
    public String getMainFid() {
        return mainFid;
    }
    public String getSid() {
        return sid;
    }
    public Long getDealerMinBalance() {
        return dealerMinBalance;
    }
    public Double getOrderViaShare() {
        return orderViaShare;
    }
    public void setOrderViaShare(Double orderViaShare) {
        this.orderViaShare = orderViaShare;
    }
    public Double getConsumeViaShare() {
        return consumeViaShare;
    }
    public void setConsumeViaShare(Double consumeViaShare) {
        this.consumeViaShare = consumeViaShare;
    }

    public String getListenPath() {
        return listenPath;
    }
    public void setListenPath(String listenPath) {
        this.listenPath = listenPath;
    }
    public long getLastUpdateIncomeTime() {
        return lastUpdateIncomeTime;
    }
    public void setLastUpdateIncomeTime(long lastUpdateIncomeTime) {
        this.lastUpdateIncomeTime = lastUpdateIncomeTime;
    }
    public long getLastUpdateExpenseTime() {
        return lastUpdateExpenseTime;
    }
    public void setLastUpdateExpenseTime(long lastUpdateExpenseTime) {
        this.lastUpdateExpenseTime = lastUpdateExpenseTime;
    }
    public Double getMinPay() {
        return minPay;
    }
    public void setMinPay(Double minPay) {
        this.minPay = minPay;
    }
}
