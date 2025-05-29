package com.fc.fc_ajdk.data.apipData;

import com.fc.fc_ajdk.constants.FieldNames;
import com.fc.fc_ajdk.constants.Values;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Sort {
    private String field;
    private String order = Values.DESC;

    public Sort() {
    }

    public Sort(String field, String order) {
        if (order.equals(Values.DESC) || order.equals(Values.ASC)) {
            this.field = field;
            this.order = order;
        }
    }

    public static ArrayList<Sort> inputSortList(BufferedReader br) {
        ArrayList<Sort> sortList = new ArrayList<>();
        String input;
        try {
            while (true) {
                Sort sort = new Sort();
                System.out.println("Input the field name. 'q' to finish: ");
                input = br.readLine();
                if ("q".equals(input)) break;
                sort.setField(input);

                while (true) {
                    System.out.println("Input the order. " + Values.DESC + " or " + Values.ASC + ": ");
                    input = br.readLine();
                    if ("q".equals(input)) break;
                    if ("desc".equals(input)) {
                        sort.setOrder(input);
                        break;
                    }
                    if ("asc".equals(input)) {
                        sort.setOrder(input);
                        break;
                    }
                    System.out.println("Wrong input. Try again.");
                }
                sortList.add(sort);
            }
        } catch (IOException e) {
            System.out.println("BufferReader wrong.");
            return null;
        }
        return sortList;
    }

    public static ArrayList<Sort> makeSortList(String field1, Boolean isAsc1, String field2, Boolean isAsc2, String field3, Boolean isAsc3) {
        ArrayList<Sort> sortList = new ArrayList<>();
        Sort sort = new Sort();
        sort.setField(field1);
        if (isAsc1)
            sort.setOrder(Values.ASC);
        sortList.add(sort);

        if (field2 != null) {
            Sort sort1 = new Sort();
            sort1.setField(field2);
            if (isAsc2) sort1.setOrder(Values.ASC);
            sortList.add(sort1);
        }

        if (field3 != null) {
            Sort sort1 = new Sort();
            sort1.setField(field3);
            if (isAsc3) sort1.setOrder(Values.ASC);
            sortList.add(sort1);
        }
        return sortList;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

}
