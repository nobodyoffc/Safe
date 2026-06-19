package com.fc.safe.utils;

public enum ChooseMode {
    CHOOSE_ONE_RETURN,  // For the case of isSingleChoice null (no checkbox, click returns)
    CHOOSE_ONE,         // For the case of isSingleChoice true (radio button mode)
    CHOOSE_MULTI, // For the case of isSingleChoice false (checkbox mode)
    CHOOSE_MULTI_WITHOUT_EDIT, // For the case of checkbox mode without edit icon
    WITHOUT_CHOOSE

}