package com.xkyii.spry.admin.constant.enums;


import com.xkyii.spry.admin.constant.CssTag;
import com.xkyii.spry.common.constant.DictionaryEnum;

/**
 * 对应sys_operation_log的status字段
 * @author valarchie
 */
public enum OperationStatusEnum implements DictionaryEnum<Integer> {

    /**
     * 操作状态
     */
    SUCCESS(1, "成功", CssTag.PRIMARY),
    FAIL(0, "失败", CssTag.DANGER);

    public static final String Key = "sys_operation_status";
    private final int value;
    private final String description;
    private final String cssTag;

    OperationStatusEnum(int value, String description, String cssTag) {
        this.value = value;
        this.description = description;
        this.cssTag = cssTag;
    }

    @Override
    public Integer getValue() {
        return value;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String cssTag() {
        return cssTag;
    }

}
