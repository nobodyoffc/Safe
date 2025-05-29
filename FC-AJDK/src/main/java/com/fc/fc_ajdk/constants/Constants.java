package com.fc.fc_ajdk.constants;

public class Constants {
    public static final String HOME = "Home";
    public static final String HASH160_F_FCH = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFCgHbzz";
    public static final String HASH160_1_BTC = "1111111111111111111114oLvT2";
    public static final int EMPTY_BLOCK_SIZE = 273;
    public static final Double MIN_FEE_RATE = 0.00001;
    public static final int DEFAULT_DISPLAY_LIST_SIZE = 200;
    public static final int DEFAULT_SESSION_DAYS = 100;
    public static final String YYYYMMDD_HHMMSSSSS = "yyyyMMdd_HHmmssSSS";
    public static final String FUND_FID = "FTqiqAyXHnK7uDTXzMap3acvqADK4ZGzts";
    public static final byte[] MAGIC_BYTES = new byte[]{(byte)0xf9,(byte)0xbe,(byte)0xb4,(byte)0xd9};
    public static final int MAXIMUM_MULTI_SIGNER = 16;
    public static final double MIN_AMOUNT = 0.000001;
    public static final int MAX_AMOUNT = 99999999;

    public static long CDD_CHECK_HEIGHT =3000000;
    public static long CD_REQUIRED =0;
    public static final Long COIN_TO_SATOSHI = 100000000L;
    public static final Long CASH_TO_SATOSHI = 100L;
    public static final Long OneDayInterval = 1440L;
    public static final Long BalanceBackupInterval = OneDayInterval;
    public static final Long RewardInterval = OneDayInterval * 10;
    public static final String UserDir = "user.dir";
    public static final String UserHome = "user.home";
    public static final int MaxRequestSize = 3000;
    public static final int DefaultSize = 20;
    public static final String zeroBlockId = "00000000cbe04361b1d6de82b893a7d8419e76e99dd2073ac0db2ba0e652eea8";
    public static final String MAGIC = "f9beb4d9";

    public static final int MAX_CASH_SIZE = 200;
    public static final int SAFE_CONFIRMATIONS = 30;
    public static final Integer MAX_JUMP_NUM = 20;


    public static final long FchToSatoshi = 100000000;
    public static final double MinPayValue = 0.00001;

    public static final String OPRETURN_FILE_DIR = "opreturn";
    public static final String OPRETURN_FILE_NAME = "opreturn0.byte";
    public static final String FCH_ADDR = "fchAddr";
    public static final String BTC_ADDR = "btcAddr";
    public static final String ETH_ADDR = "ethAddr";
    public static final String LTC_ADDR = "ltcAddr";
    public static final String BCH_ADDR = "bchAddr";
    public static final String DOGE_ADDR = "dogeAddr";
    public static final String TRX_ADDR = "trxAddr";

    public static final String[] VALID_ADDRS = new String[]{FieldNames.FID, BTC_ADDR, ETH_ADDR, LTC_ADDR, DOGE_ADDR, TRX_ADDR};
    public static final String REWARD_HTML_FILE = "index.html";
    public static final String REWARD_HISTORY_FILE = "rewardHistory.json";
    public static final int FCH_LENGTH = 17;
    public static final int DATE_TIME_LENGTH = 19;
    public static final int FID_LENGTH = 34;
    public static final int HEX256_LENGTH = 64;
    public static final int PUBLIC_KEY_BYTES_LENGTH = 33;
    public static final int PRIVATE_KEY_BYTES_LENGTH = 32;
    public static final int SYMKEY_BYTES_LENGTH = 32;

    public static final int IV_BYTES_LENGTH = 16;
    public static final int K_BYTES = 1024;
    public static final int M_BYTES = 1024 * 1024;
    public static final int G_BYTES = 1024 * 1024 * 1024;
    public static final int MAX_FILE_SIZE_M = 200;
    public static final String DOT_FV = ".fv";
    public static final int MaxOpFileSize = 200 * 1024 * 1024;//251658240;
    public static final String FBBP = "FBBP";
    public static final String SESSION_NAME = "SessionName";
    public static final String WEBHOOK_FILE = "webhook.json";
    public static final String APIP = "apip";
    public static final String V1 = "V1";
    public static final String APIP_Account_JSON = "ApipAccount.json";
    ;
    public static final String MAKER_SN = "2";
    public static final String FEIP = "FEIP";
    public static final String CID = "CID";
    public static final String Master = "Master";
    public static final String COINBASE = "coinbase";
    public static final double Dust = 0.00001;

    public static final String ALG_SIGN_TX_BY_CRYPTO_SIGN = "SignTxByCryptoSign@No1_NrC7";
    public static final String CONFIG_JSON = "config.json";
    public static final long DAY_TO_MIL_SEC = 24*60*60*1000;
    public static final int SEC_PER_DAY = 24*60*60;
    public static final String DOT_DECRYPTED = ".decrypted";
    public static int RedisDb4Webhook = 4;
    public static int RedisDb3Mempool = 3;
    public static int RedisDb0Common = 0;
    public static String UrlHead_CID_CASH = "https://cid.cash/APIP";
    public static long DustInSatoshi = 1000;
    public static String Dot_JSON = ".json";
    public final static String FCH_0BlockId = "00000000cbe04361b1d6de82b893a7d8419e76e99dd2073ac0db2ba0e652eea8";
    public final static String DOGE_0BlockId = "1a91e3dace36e2be3bf030a65679fe821aa1d6ef92e7c9902eb318182c355691";
    public static long SatoshiDust = 546;
    public static long TenDayBlocks = 60*24*10;
    public static final int DAYS_PER_YEAR = 400;
    public static final String DAYS_PER_YEAR_STR = "400";
    public static final String MINE_MATURE_DAYS = "10";
    public static final String FUND_MATURE_DAYS = "100";

    public static final int MINE_MATURE_DAYS_INT = 10;
    public static final int FUND_MATURE_DAYS_INT = 100;
    public static final String BLOCK_TIME_MINUTE = "1";
    public static final String INITIAL_COINBASE_MINE ="25";
    public static final String INITIAL_COINBASE_FUND ="25";
    public static final String MINE_REDUCTION_RATIO ="20%";
    public static final String FUND_REDUCTION_RATIO ="50%";
    public static final String REDUCE_PER_BLOCKS ="576000";
    public static final String REDUCTION_STOPS_AT_HEIGHT ="11520000";
    public static final String STABLE_ANNUAL_ISSUANCE = "207,553";
    public static final long START_TIME = 1577836802;
    public static final String GENESIS_BLOCK_ID = "00000000cbe04361b1d6de82b893a7d8419e76e99dd2073ac0db2ba0e652eea8";
    public static final double TWO_POWER_32 = 4294967296D;
    public static final String MINER = "miner";

    public static final long TIMESTAMP_2000 = 946684800000L; // 2000-01-01 00:00:00
    public static final long TIMESTAMP_2100 = 4102444800000L; // 2100-01-01 00:00:00
    public static final long TIMESTAMP_2000_SECONDS = 946684800L; // 2000-01-01 00:00:00 in seconds
    public static final long TIMESTAMP_2100_SECONDS = 4102444800L; // 2100-01-01 00:00:00 in seconds
    public static final long TIMESTAMP_2200 = 4314336000000L; // 2200-01-01 00:00:00
    public static int DAYS_PER_WEEK = 10;
    public static int WEEKS_PER_SEASON = 10;
    public static int SEASONS_PER_YEAR = 4;
    public static int HOURS_PER_DAY = 24;
    public static int MINUTES_PER_HOURS = 60;
}
