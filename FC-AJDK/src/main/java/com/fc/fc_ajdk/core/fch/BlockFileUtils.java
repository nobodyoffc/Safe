package com.fc.fc_ajdk.core.fch;

import com.fc.fc_ajdk.constants.IndicesNames;
import com.fc.fc_ajdk.data.fchData.Block;
import java.io.File;
import java.io.IOException;

public class BlockFileUtils {

    public static int getFileOrder(String currentFile) {
        String s = String.copyValueOf(currentFile.toCharArray(), 3, 5);
        return Integer.parseInt(s);
    }

    public static String getLastBlockFileName(String blockFilePath) {
        for (int i = 0; ; i++) {
            String blockFileName = getFileNameWithOrder(i);
            File file = new File(blockFilePath + blockFileName);
            if (!file.exists()) {
                if (i > 0) {
                    return getFileNameWithOrder(i - 1);
                }
            }
        }
    }

    public static String getFileNameWithOrder(int i) {
        return "blk" + String.format("%05d", i) + ".dat";
    }

    public static String getNextFile(String currentFile) {
        return getFileNameWithOrder(getFileOrder(currentFile) + 1);
    }
}
